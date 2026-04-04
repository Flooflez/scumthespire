package battleaimod.battleai;


import battleaimod.ValueFunctions;
import battleaimod.battleai.data.CardSequence;
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
    private Queue<List<AbstractCard>> sequences;
    private Queue<AbstractCard> currentCardQueue;
    private List<AbstractCard> currentCardList;
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

    private Queue<List<AbstractCard>> generateInitPop(int populationSize) {
        Queue<List<AbstractCard>> population = new ArrayDeque<>();

        for (int p = 0; p < populationSize; p++) {

            List<AbstractCard> sequence = new ArrayList<>();

            AbstractDungeon.player.hand.refreshHandLayout();
            int energy = EnergyPanel.totalCount;

            // Build shuffled order of cards
            List<AbstractCard> cards = new ArrayList<>(AbstractDungeon.player.hand.group);
            Collections.shuffle(cards);

            for (AbstractCard card : cards) {

                int cost = card.costForTurn;


                if (cost == -2) { // Skip unplayable
                    continue;
                }



                if (cost == -1) { // X-cost
                    sequence.add(card);
                    break; // consumes all energy
                }


                if (cost <= energy) { // Normal cost
                    sequence.add(card);
                    energy -= cost;
                }

            }

            population.add(sequence);
        }

        return population;
    }

    private Command createCommandForCard(AbstractCard targetCard) {
        int cardIndex = -1;

        for (int i = 0; i < AbstractDungeon.player.hand.group.size(); i++) {
            AbstractCard handCard = AbstractDungeon.player.hand.group.get(i);

            if (handCard.getMetricID().equals(targetCard.getMetricID())) {
                cardIndex = i;
                targetCard = handCard; // use the live instance
                break;
            }
        }

        if (cardIndex == -1) { // Card not found (was removed / transformed / etc.)
            return null;
        }

        // 2. Handle targeted cards
        if (targetCard.target == AbstractCard.CardTarget.ENEMY ||
                targetCard.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {

            for (int j = 0; j < AbstractDungeon.getMonsters().monsters.size(); j++) {
                AbstractMonster monster = AbstractDungeon.getMonsters().monsters.get(j);

                if (!monster.isDeadOrEscaped() &&
                        targetCard.canUse(AbstractDungeon.player, monster)) {

                    return new CardCommand(cardIndex, j,
                            targetCard.cardID + " " + targetCard.name);
                }
            }

            return null; // no valid target
        }

        // 3. Non-targeted cards
        if (targetCard.target == AbstractCard.CardTarget.ALL_ENEMY ||
                targetCard.target == AbstractCard.CardTarget.ALL ||
                targetCard.target == AbstractCard.CardTarget.SELF ||
                targetCard.target == AbstractCard.CardTarget.NONE) {

            if (targetCard.canUse(AbstractDungeon.player, null)) {
                return new CardCommand(cardIndex,
                        targetCard.cardID + " " + targetCard.name);
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

    private double getFitness(StateNode start, StateNode end){
        double damageTaken = StateNode.getPlayerDamage(end);
        double damageDealt = ValueFunctions.getTotalDamageDealt(start.saveState, end.saveState);
        double remainingHP = ValueFunctions.getTotalMonsterHealth(end.saveState);

        double fitness = (damageDealt * 2.0)
                - (damageTaken * 3.0)
                - (remainingHP * 1.0);

        return fitness;
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
                shouldRunEndCommand = false;
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

                sequences = generateInitPop(100);


            }
            else{
                if(currentState.saveState == null){
                    //IMPORTANT: SaveState MUST come in the next step after running a command to ensure effects propagate!
                    currentState.saveState = new SaveState();
                }

                if(currentCardQueue == null || currentCardQueue.isEmpty()){
                    if(shouldRunEndCommand){ //ensure EndCommand is run after all cards
                        Command cmd = new EndCommand();
                        StateNode next = new StateNode(currentState, cmd, this);
                        cmd.execute();
                        currentState = next;
                        shouldRunEndCommand = false;
                        return;
                    }

                    //eval score, add to list
                    if (currentCardList != null) {
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
                        //init
                        currentCardList = sequences.poll();
                        currentCardQueue = new ArrayDeque<>(currentCardList);
                        shouldRunEndCommand = true;

                        currentState = startStateNode;
                        startStateNode.saveState.loadState(); //reset sim to start
                    }
                    return; //return here to ensure states properly loaded

                }

                //if !currentCardQueue.isEmpty, do this stuff
                AbstractCard card = currentCardQueue.poll();
                Command cmd = createCommandForCard(card);
                if(cmd == null){
                    FileLogger.log("cmd was null, skipping card: " + card);
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
