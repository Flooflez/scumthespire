package battleaimod.battleai.data;

import battleaimod.battleai.BattleAiController;
import battleaimod.battleai.StateNode;
import battleaimod.battleai.data.dummycommands.DummyCommand;
import battleaimod.utils.FileLogger;
import com.megacrit.cardcrawl.cards.AbstractCard;

import java.util.*;

public class CardSequence implements Comparable<CardSequence> {
    //TODO: there may be an issue with AbstractCards being saved directly vs saved as copies

    private final List<CardAction> cards;

    private final List<AbstractCard> leftoverCardOrder;
    private int leftoverCardIndex;

    private List<AbstractCard> cardsCreated;

    private Queue<AbstractCard> gridSelectChoices;
    private List<AbstractCard> gridSelectChoicesBuffer;
    //for first generation when we are presented with a grid select menu, pick randomly and add to buffer
    //subsequent generations will read selections from the buffer via the gridSelectChoices queue
    //if the card is not available to be chosen, a random one will be picked and the card will be overwritten in the buffer

    private double fitness;
    private StateNode endState;

    public CardSequence(List<CardAction> cards, List<AbstractCard> leftoverCards) {
        this.cards = new ArrayList<>(cards); // defensive copy
        this.leftoverCardOrder = new ArrayList<>(leftoverCards); //copy for evolution
        Collections.shuffle(this.leftoverCardOrder);
        leftoverCardIndex = 0;

        gridSelectChoicesBuffer = new ArrayList<>();
        cardsCreated = new ArrayList<>();
    }

    public CardSequence(CardSequence sequenceToCopy){
        this.cards = new ArrayList<>(sequenceToCopy.getCards());
        this.leftoverCardOrder = new ArrayList<>(sequenceToCopy.leftoverCardOrder);
        leftoverCardIndex = 0;

        gridSelectChoicesBuffer = new ArrayList<>(sequenceToCopy.gridSelectChoicesBuffer);

        //we want to copy all buffer to queue on copy
        gridSelectChoices = new ArrayDeque<>();
        gridSelectChoices.addAll(gridSelectChoicesBuffer);

        //cardsCreated = new ArrayList<>(sequenceToCopy.cardsCreated);
        cardsCreated = new ArrayList<>();

        leftoverCardOrder.addAll(sequenceToCopy.cardsCreated);
        //append newly created cards to end so they can be considered for playing first
        //TODO: this direct append may cause problems. we'll see

    }

//    public CardSequence(CardSequence parent1, CardSequence parent2){
//        //crossover constructor
//        this.cards = new ArrayList<>(parent1.getCards());
//        this.leftoverCardOrder = new ArrayList<>(parent1.leftoverCardOrder);
//
//        leftoverCardIndex = 0;
//
//        gridSelectChoicesBuffer = new ArrayList<>(parent1.gridSelectChoicesBuffer);
//        gridSelectChoices = new ArrayDeque<>();
//        gridSelectChoices.addAll(gridSelectChoicesBuffer);
//
//        cardsCreated = new ArrayList<>();
//
//
//        //TODO:
//        //single point crossover for cards arraylists
//        //save as new arraylist and store it in cards
//        //be careful cards may be different lengths
//
//        //append all
//        leftoverCardOrder.addAll(parent2.leftoverCardOrder);
//
//
//        //don't consider cardsCreated as the result may be very different
//
//    }


    public List<CardAction> getCards() {
        return cards;
    }

    public AbstractCard getNextHandSelectCard(){
        if(leftoverCardIndex == leftoverCardOrder.size()){ //ran out of leftovers
            if (cards.isEmpty()) return null;
            //no more cards, handled in BattleAiController

            else return cards.remove(cards.size()-1).getMainCard();
            //choose last card in play sequence to discard/exhaust
        }
        else{
            return leftoverCardOrder.get(leftoverCardIndex++); //get next leftover and iterate
        }
    }

    public CardAction getNextPlayableCard(int energy){
        for (int i = leftoverCardOrder.size()-1; i >= 0 ; i--) { //reverse to prevent desyncs
            if(i < leftoverCardIndex){
                //out of leftovers, no playable cards
                return null;
            }
            AbstractCard c = leftoverCardOrder.get(i);

            if(c.costForTurn != -2 && c.costForTurn <= energy){
                //if a playable card is found, remove from leftoverCardOrder
                //and add to cards list
                leftoverCardOrder.remove(c);
                CardAction a = CardAction.createCardAction(c);
                if (a != null) {
                    cards.add(a);
                }

                return a;
            }
        }

        return null; //went over all cards, none were playable
    }

    public void addGridSelectChoiceToBuffer(AbstractCard card){
        gridSelectChoicesBuffer.add(card);
    }

    public void changeBufferByIndex(AbstractCard card, int index){
        gridSelectChoicesBuffer.set(index, card);
    }

    public boolean hasGridSelectChoices(){
        return (gridSelectChoicesBuffer == null) || (!gridSelectChoices.isEmpty());
    }

    public AbstractCard getNextGridSelectChoice(){
        if(gridSelectChoices != null && !gridSelectChoices.isEmpty()){
            return gridSelectChoices.poll();
        }
        return null;
    }



    public void addCardsCreated(List<AbstractCard> cardsCreated){
        this.cardsCreated.addAll(cardsCreated);
    }


    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    @Override
    public int compareTo(CardSequence other) {
        // Higher score = better (sorted descending)
        return Double.compare(other.fitness, this.fitness);
    }

    @Override
    public String toString() {
        return "CardSequence{" +
                "cards=" + cards +
                ", score=" + fitness +
                '}';
    }

    public StateNode getEndState() {
        return endState;
    }

    public void setEndState(StateNode endState) {
        this.endState = endState;
    }


    public void mutate() {
        Random rand = new Random();

        // Determine how many times to apply each mutation (tweak max values as desired)
        int swapCardActionsTimes    = rand.nextInt(4); // 0-3 times
        int swapAbstractCardsTimes  = rand.nextInt(4); // 0-3 times
        int swapPlayedLeftoverTimes = rand.nextInt(3); // 0-2 times
        int scrambleLeftoversTimes  = rand.nextInt(2); // 0-1 times
        int scrambleGridTimes       = rand.nextInt(2); // 0-1 times

        // Build a list of mutation "jobs"
        List<Runnable> jobs = new ArrayList<>();
        for(int i = 0; i < swapCardActionsTimes; i++)    jobs.add(this::swapTwoRandomCardActions);
        for(int i = 0; i < swapAbstractCardsTimes; i++)  jobs.add(this::swapTwoRandomAbstractCards);
        for(int i = 0; i < swapPlayedLeftoverTimes; i++) jobs.add(this::swapPlayedAndLeftover);
        for(int i = 0; i < scrambleLeftoversTimes; i++)  jobs.add(this::scrambleLeftovers);
        for(int i = 0; i < scrambleGridTimes; i++)       jobs.add(this::scrambleGridChoices);

        // Shuffle mutation order to be extra random
        Collections.shuffle(jobs, rand);

        // Run all mutation jobs
        for (Runnable job : jobs) {
            job.run();
        }
    }

    public void scrambleLeftovers(){
        Collections.shuffle(leftoverCardOrder);
    }

    public void scrambleGridChoices(){
        List<AbstractCard> list = new ArrayList<>(gridSelectChoices);
        Collections.shuffle(list);
        gridSelectChoices.clear();
    }

    public void swapTwoRandomCardActions() {
        if (cards.size() < 2) return; // Nothing to swap

        Random rng = new Random();
        int idx1 = rng.nextInt(cards.size());
        int idx2 = rng.nextInt(cards.size() - 1);
        if (idx2 >= idx1) idx2++; // Ensure idx2 ≠ idx1

        // Swap
        CardAction temp = cards.get(idx1);
        cards.set(idx1, cards.get(idx2));
        cards.set(idx2, temp);
    }

    public void swapTwoRandomAbstractCards() {
        if (leftoverCardOrder.size() < 2) return; // Nothing to swap

        Random rng = new Random();
        int idx1 = rng.nextInt(leftoverCardOrder.size());
        int idx2 = rng.nextInt(leftoverCardOrder.size() - 1);
        if (idx2 >= idx1) idx2++; // Ensure idx2 ≠ idx1

        // Swap
        AbstractCard temp = leftoverCardOrder.get(idx1);
        leftoverCardOrder.set(idx1, leftoverCardOrder.get(idx2));
        leftoverCardOrder.set(idx2, temp);
    }

    public void swapPlayedAndLeftover() {
        if (cards.isEmpty() || leftoverCardOrder.isEmpty()) return; // Nothing to swap

        Random rand = new Random();

        // 1. Pick random CardAction from cards
        int cardActionIdx = rand.nextInt(cards.size());
        CardAction selectedAction = cards.get(cardActionIdx);
        if(selectedAction == null){
            FileLogger.log("size: " + cards.size());
            FileLogger.log("cardActionIdx:"+ cardActionIdx);
            for(CardAction c : cards){
                if(c == null) FileLogger.log("null");
                else FileLogger.log(c.toString());
            }
        }
        AbstractCard actionCard = selectedAction.getMainCard();

        // 2. Pick random AbstractCard from leftovers, retry CardAction.createCardAction as needed
        CardAction newAction = null;
        int maxTries = leftoverCardOrder.size(); // Prevent infinite loop
        int tries = 0, cardIdx = -1;

        while (tries < maxTries) {
            cardIdx = rand.nextInt(leftoverCardOrder.size());
            AbstractCard leftoverCard = leftoverCardOrder.get(cardIdx);
            newAction = CardAction.createCardAction(leftoverCard);
            if (newAction != null) {
                break;
            }
            tries++;
        }

        if (newAction == null) return; // Could not find a valid card/action to swap

        // 3. Do the swap: put newAction into cards, put actionCard into leftoverCardOrder
        cards.set(cardActionIdx, newAction);
        leftoverCardOrder.set(cardIdx, actionCard);
    }
}