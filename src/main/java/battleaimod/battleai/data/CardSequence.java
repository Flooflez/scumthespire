package battleaimod.battleai.data;

import battleaimod.battleai.StateNode;
import com.megacrit.cardcrawl.cards.AbstractCard;

import java.util.ArrayList;
import java.util.List;

public class CardSequence implements Comparable<CardSequence> {

    private final List<AbstractCard> cards;
    private double fitness;
    private StateNode endState;

    public CardSequence() {
        this.cards = new ArrayList<>();
        this.fitness = 0.0;
    }

    public CardSequence(List<AbstractCard> cards, double score, StateNode endState) {
        this.cards = new ArrayList<>(cards); // defensive copy
        this.fitness = score;
        this.endState = endState;
    }

    public List<AbstractCard> getCards() {
        return cards;
    }

    public void addCard(AbstractCard card) {
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