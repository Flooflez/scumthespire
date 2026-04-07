package battleaimod.battleai.data;

import battleaimod.battleai.BattleAiController;
import battleaimod.battleai.StateNode;
import battleaimod.battleai.data.dummycommands.DummyCommand;
import com.megacrit.cardcrawl.cards.AbstractCard;

import java.util.*;

public class CardSequence implements Comparable<CardSequence> {

    private final List<CardAction> cards;
    private final List<AbstractCard> leftoverCardOrder;
    private int leftoverCardIndex;

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
    }

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
                leftoverCardOrder.remove(c);
                CardAction a = BattleAiController.createCardAction(c);
                cards.add(a);

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

    public void populateGridSelectChoices(){
        if(!gridSelectChoicesBuffer.isEmpty()){
            gridSelectChoices = new ArrayDeque<>(gridSelectChoicesBuffer);
        }
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
}