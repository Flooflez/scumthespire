package battleaimod.battleai;


import battleaimod.ValueFunctions;
import battleaimod.battleai.data.CardAction;
import battleaimod.battleai.data.CardSequence;
import battleaimod.battleai.data.dummycommands.DummyCommand;
import battleaimod.battleai.data.dummycommands.DummyGridSelectCommand;
import battleaimod.battleai.data.dummycommands.DummyHandSelectCommand;
import battleaimod.battleai.data.dummycommands.GeneralDummyCommand;
import battleaimod.utils.FileLogger;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import ludicrousspeed.Controller;
import ludicrousspeed.simulator.commands.*;
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
    private Queue<CardSequence> sequences;
    private Deque<DummyCommand> dummyCommandQueue;
    private CardSequence currentCardSeq;
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
    private Queue<CardSequence> generateInitPop(int populationSize) {
        Queue<CardSequence> population = new ArrayDeque<>();

        if (AbstractDungeon.player == null || AbstractDungeon.player.hand == null) {
            return population;
        }

        for (int p = 0; p < populationSize; p++) {

            List<CardAction> cardActionList = new ArrayList<>();
            Set<AbstractCard> usedCards = new HashSet<>();

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
                    CardAction action = createCardAction(card);
                    if (action != null) {
                        cardActionList.add(action);
                        usedCards.add(card);
                    }
                    break; // consumes all energy
                }

                // Normal cost
                if (cost <= energy) {
                    CardAction action = createCardAction(card);
                    if (action != null) {
                        cardActionList.add(action);
                        usedCards.add(card);
                        energy -= cost;
                    }
                }

                if (energy <= 0) {
                    break;
                }
            }

            // --- Compute leftover cards ---
            List<AbstractCard> leftoverCards = new ArrayList<>();
            for (AbstractCard card : cards) {
                if (card != null && !usedCards.contains(card)) {
                    leftoverCards.add(card);
                }
            }

            CardSequence sequence = new CardSequence(cardActionList, leftoverCards);
            population.add(sequence);
        }

        return population;
    }


    private CardAction createCardAction(AbstractCard card) {
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

    private Deque<DummyCommand> actionsToCommands(List<CardAction> cardActionList){
        Deque<DummyCommand> commands = new ArrayDeque<>();
        for(CardAction action: cardActionList){
            commands.addAll(action.getDummyCommands());
        }
        commands.add(new GeneralDummyCommand(new EndCommand()));
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
                    if (currentCardSeq != null) {
                        //eval score, add to final sorting list
                        double turnFitness = getFitness(startStateNode, currentState);
                        currentCardSeq.setFitness(turnFitness);
                        currentCardSeq.setEndState(currentState);
                        finalSequences.add(currentCardSeq);
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
                        currentCardSeq = sequences.poll(); //just so we can save it and evolve later
                        dummyCommandQueue = actionsToCommands(currentCardSeq.getCards());
                        //get queue of commands to run

                        currentState = startStateNode;
                        startStateNode.saveState.loadState(); //reset sim to start
                    }
                    return; //return here to ensure states properly loaded

                }

                //This code will run if dummyCommandQueue has commands to run still:

                //check if there is a UI menu: this func will add commands if needed
                checkUICommands(dummyCommandQueue, currentCardSeq); //TODO infinite loop since it takes 2 commands to process
                FileLogger.log("success");

                Command cmd = dummyCommandQueue.poll().getRealCommand();
                if(cmd == null){
                    FileLogger.log("cmd was null, skipping cmd");
                    //TODO: if needed, save DummyCard from poll and implement toString()
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


    private void checkUICommands(Deque<DummyCommand> dummyCommandQueue, CardSequence cardSequence){
        FileLogger.log("checking UI commands");
        if (isInHandSelect()) {
            FileLogger.log("getting card");
            AbstractCard card = cardSequence.getNextHandSelectCard();
            FileLogger.log("finished get");
            if(card != null){//null shouldn't happen since isInHandSelect will be false
                FileLogger.log("putting commands in");
                dummyCommandQueue.offerFirst(new GeneralDummyCommand(HandSelectConfirmCommand.INSTANCE));
                dummyCommandQueue.offerFirst(new DummyHandSelectCommand(card));
            }
            return;
        }

        if (isInGridSelect()) {
            //includes Scry, Seek, Headbutt etc.

            dummyCommandQueue.offerFirst(new GeneralDummyCommand(GridSelectConfrimCommand.INSTANCE));
            //this one is harder since we don't know the set of cards we are going to see,
            //and we don't know where the card is going (discard, draw pile, hand)

            if (cardSequence.hasGridSelectChoices()){
                //try to use the saved choice
                AbstractCard selectedCard = cardSequence.getNextGridSelectChoice();
                dummyCommandQueue.offerFirst(new DummyGridSelectCommand(selectedCard));
            }
            else{
                //random
                List<AbstractCard> group = AbstractDungeon.gridSelectScreen.targetGroup.group;
                int randIndex = new Random().nextInt(group.size());
                AbstractCard randomCard = group.get(randIndex);

                dummyCommandQueue.offerFirst(new DummyGridSelectCommand(randomCard));
                cardSequence.addGridSelectChoiceToBuffer(randomCard); //setup for next time
            }
            return;
        }

        if (isInCardRewardSelect()) { //literally just for Discover, just pick 0 every time, change later if needed
            dummyCommandQueue.offerFirst(new GeneralDummyCommand(new CardRewardSelectCommand(0)));
//            for(int i = 0; i < AbstractDungeon.cardRewardScreen.rewardGroup.size(); ++i) {
//
//            }
        }

    }



    private static boolean isInGridSelect() {
        FileLogger.log("checking grid select menu open");
        return isInDungeon() && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && AbstractDungeon.isScreenUp && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID;
    }


    private static boolean isInHandSelect() {
        FileLogger.log("checking hand select menu open");
        return isInDungeon() && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && AbstractDungeon.isScreenUp && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT;
    }

    private static boolean isInCardRewardSelect() {
        FileLogger.log("checking card reward menu open");
        return isInDungeon() && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && AbstractDungeon.isScreenUp && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD;
    }

    private static boolean isInDungeon() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY && AbstractDungeon.isPlayerInDungeon() && AbstractDungeon.currMapNode != null;
    }


}
