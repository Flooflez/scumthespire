package battleaimod.battleai;


import battleaimod.ValueFunctions;
import battleaimod.battleai.data.CardAction;
import battleaimod.battleai.data.CardSequence;
import battleaimod.battleai.data.dummycommands.DummyCommand;
import battleaimod.battleai.data.dummycommands.DummyEndCommand;
import battleaimod.utils.FileLogger;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import ludicrousspeed.Controller;
import ludicrousspeed.simulator.commands.CardCommand;
import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.EndCommand;
import savestate.CardState;
import savestate.SaveState;
import savestate.SaveStateMod;

import java.util.*;

public class BattleAiController implements Controller {
    private final int maxTurnLoads;

    public int targetTurn;
    public int targetTurnJump;

    public PriorityQueue<TurnNode> turns = new PriorityQueue<>();

    // The best winning result unless the AI gave up in which case it will contain the chosen death
    // path
    public StateNode bestEnd;

    // If it doesn't work out just send back a path to kill the players so the game doesn't get
    // stuck.
    public StateNode deathNode = null;

    // The state the AI is currently processing from
    public TurnNode committedTurn = null;

    // The target turn that will be loaded if/when the max turn loads is hit
    public TurnNode bestTurn = null;
    public TurnNode backupTurn = null;

    public int startingHealth;
    public boolean isDone = false;
    public final SaveState startingState;
    public int expectedDamage = 0;
    private boolean initialized;

    //evolution stuff
    private Queue<List<CardAction>> sequences;
    private Queue<DummyCommand> dummyCommandQueue;
    private List<CardAction> currentCardList;
    private List<CardSequence> finalSequences;
    private StateNode currentState;
    private StateNode startStateNode;
    private boolean shouldRunEndCommand;

    // EXPERIMENTAL
    public static final boolean SHOULD_SHOW_TREE = false;



    public BattleAiController(SaveState state, int maxTurnLoads) {
        SaveStateMod.runTimes = new HashMap<>();
        targetTurn = 8;
        targetTurnJump = 6;

        bestEnd = null;
        startingState = state;
        initialized = false;

        System.err.println("loading state from constructor");
        startingState.loadState();

        this.maxTurnLoads = maxTurnLoads;
    }
    private Queue<List<CardAction>> generateInitPop(int populationSize) {
        Queue<List<CardAction>> population = new ArrayDeque<>();

        if (AbstractDungeon.player == null || AbstractDungeon.player.hand == null) {
            return population;
        }

        for (int p = 0; p < populationSize; p++) {

            List<CardAction> sequence = new ArrayList<>();

            AbstractDungeon.player.hand.refreshHandLayout();
            int energy = EnergyPanel.totalCount;

            // Copy + shuffle hand
            List<AbstractCard> cards = new ArrayList<>(AbstractDungeon.player.hand.group);
            Collections.shuffle(cards);

            for (AbstractCard card : cards) {

                if (card == null) continue;

                int cost = card.costForTurn;

                // Skip unplayable
                if (cost == -2) {
                    continue;
                }

                // X-cost card
                if (cost == -1) {
                    CardAction action = createRandomAction(card);
                    if (action != null) {
                        sequence.add(action);
                    }
                    break; // consumes all energy
                }

                // Normal cost
                if (cost <= energy) {
                    CardAction action = createRandomAction(card);
                    if (action != null) {
                        sequence.add(action);
                        energy -= cost;
                    }
                }

                if (energy <= 0) {
                    break;
                }
            }

            population.add(sequence);
        }

        return population;
    }


    private CardAction createRandomAction(AbstractCard card) {
        //TODO: Handle HandSelectCommand + GridSelectCommand

        if (card == null || AbstractDungeon.player == null) {
            return null;
        }

        // --- Cards that require enemy target ---
        if (card.target == AbstractCard.CardTarget.ENEMY ||
                card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {

            List<AbstractMonster> validTargets = new ArrayList<>();

            if (AbstractDungeon.getMonsters() != null) {
                for (int i = 0; i < AbstractDungeon.getMonsters().monsters.size(); i++) {
                    AbstractMonster m = AbstractDungeon.getMonsters().monsters.get(i);

                    if (m != null &&
                            !m.isDeadOrEscaped() &&
                            card.canUse(AbstractDungeon.player, m)) {

                        validTargets.add(m);
                    }
                }
            }

            if (validTargets.isEmpty()) {
                return null;
            }

            // Pick random valid monster
            int randIndex = AbstractDungeon.cardRandomRng.random(validTargets.size() - 1);
            AbstractMonster target = validTargets.get(randIndex);

            int enemyIndex = AbstractDungeon.getMonsters().monsters.indexOf(target);

            return new CardAction(card, enemyIndex);
        }

        // --- Non-targeted cards ---
        if (card.target == AbstractCard.CardTarget.ALL_ENEMY ||
                card.target == AbstractCard.CardTarget.ALL ||
                card.target == AbstractCard.CardTarget.SELF ||
                card.target == AbstractCard.CardTarget.NONE) {

            if (card.canUse(AbstractDungeon.player, null)) {
                return new CardAction(card);
            }
        }

        return null;
    }

    private void printMetrics(StateNode start, StateNode end) {
        //log to file since console is not visible/freezes
        FileLogger.log("SIMULATION RESULTS:");
        FileLogger.log("Player HP: " + end.saveState.getPlayerHealth());
        FileLogger.log("Damage taken: " + StateNode.getPlayerDamage(end));
        FileLogger.log("Damage Dealt: " + ValueFunctions.getTotalDamageDealt(start.saveState, end.saveState));
        FileLogger.log("Monster HP: " + ValueFunctions.getTotalMonsterHealth(end.saveState));
        FileLogger.log("Score: " + getFitness(start, end));
        FileLogger.log("==========================");
    }

    private double getFitness(StateNode start, StateNode end) {
        double damageTaken = StateNode.getPlayerDamage(end);
        double damageDealt = ValueFunctions.getTotalDamageDealt(start.saveState, end.saveState);
        double remainingHP = ValueFunctions.getTotalMonsterHealth(end.saveState);
        int remainingMonsters = ValueFunctions.getAliveMonsterCount(end.saveState);

        double fitness = (damageDealt * 2.0)
                - (damageTaken * 3.0)
                - (remainingHP * 1.0)
                - (remainingMonsters * 10.0);

        return fitness;
    }

    private Queue<DummyCommand> actionsToCommands(List<CardAction> cardActionList){
        Queue<DummyCommand> commands = new ArrayDeque<>();
        for(CardAction action: cardActionList){
            commands.addAll(action.getDummyCommands());
        }
        commands.add(new DummyEndCommand());
        return commands;
    }


    public void step() {
//        try{
            if (isDone) {
                return;
            }

            if (!initialized) {
                initialized = true;
                isDone = false;
                bestEnd = null;
                //shouldRunEndCommand = false;
                finalSequences = new ArrayList<>();

                // Match A* init
                SaveStateMod.runTimes = new HashMap<>();
                CardState.resetFreeCards();

                // 1. Load starting state
                SaveState startState = new SaveState();
                startState.loadState();

                startStateNode = new StateNode(null, null, this);
                startStateNode.saveState = startingState;
                currentState = startStateNode;
                //FileLogger.log("turns: " + GameActionManager.turn + " vs " + currentState.saveState.turn);

                startingHealth = startState.getPlayerHealth();

                sequences = generateInitPop(200);

            }
            else{
                if(currentState.saveState == null){
                    //IMPORTANT: SaveState MUST come in the next step after running a command to ensure effects propagate!
                    currentState.saveState = new SaveState();
                }

                if(dummyCommandQueue == null || dummyCommandQueue.isEmpty()){
                    //not null check to skip first loop
                    if (currentCardList != null) {
                        //eval score, add to final sorting list
                        double turnFitness = getFitness(startStateNode, currentState);
                        CardSequence seq = new CardSequence(currentCardList, turnFitness, currentState);
                        finalSequences.add(seq);
                    }

                    if(sequences.isEmpty()){
                        //finished simulating all

                        //sort, get best end
                        Collections.sort(finalSequences);

                        bestEnd = finalSequences.get(0).getEndState();
                        printMetrics(startStateNode, bestEnd);
                        isDone = true;
                        initialized = false;
                    }
                    else{
                        //init next sequence
                        currentCardList = sequences.poll(); //just so we can save it and evolve later
                        dummyCommandQueue = actionsToCommands(currentCardList);
                        //get queue of commands to run

                        currentState = startStateNode;
                        startStateNode.saveState.loadState(); //reset sim to start
                    }
                    return; //return here to ensure states properly loaded

                }

                //This code will run if dummyCommandQueue has commands to run still:
                Command cmd = dummyCommandQueue.poll().getRealCommand();
                if(cmd == null){
                    FileLogger.log("cmd was null, skipping cmd");
                    //TODO: if needed, save DummyCard and implement toString()
                }
                else{
                    StateNode next = new StateNode(currentState, cmd, this);
                    cmd.execute();
                    currentState = next;
                }
            }
//        }catch (Exception e){
//            FileLogger.log(e.getMessage());
//            FileLogger.log(e.getCause().toString());
//            FileLogger.log(Arrays.toString(e.getStackTrace()));
//            //throw new RuntimeException(e);
//        }


    }



    public static List<StateNode> stateNodesToGetToNode(StateNode endNode) {
        ArrayList<StateNode> result = new ArrayList<>();
        StateNode iterator = endNode;
        while (iterator != null) {
            result.add(0, iterator);
            iterator = iterator.parent;
        }

        return result;
    }


    public boolean isDone() {
        return isDone;
    }

    public TurnNode committedTurn() {
        return committedTurn;
    }

    public int turnsLoaded() {
        return 0;
    }

    public int maxTurnLoads() {
        return maxTurnLoads;
    }


}
