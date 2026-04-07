package battleaimod.battleai.data;

import battleaimod.battleai.StateNode;
import com.megacrit.cardcrawl.cards.AbstractCard;

import java.util.ArrayList;
import java.util.List;

public class CardSequence implements Comparable<CardSequence> {

    private final List<CardAction> cards;
    private List<AbstractCard> leftoverCards;
    private double fitness;
    private StateNode endState;

    public CardSequence(List<CardAction> cards, List<AbstractCard> leftoverCards) {
        this.cards = new ArrayList<>(cards); // defensive copy
    }

    public CardSequence(List<CardAction> cards, double score, StateNode endState) {
        this.cards = new ArrayList<>(cards); // defensive copy
        this.fitness = score;
        this.endState = endState;
    }

    public List<CardAction> getCards() {
        return cards;
    }

    public void addCard(CardAction card) {
        cards.add(card);
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