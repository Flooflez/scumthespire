package battleaimod.battleai.evolution.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WeightedSumFitness {
    private final List<WeightedSumGene> genes;
    private double fitness = 0;

    public WeightedSumFitness(){
        //completely random
        Random rand = new Random();
        genes = new ArrayList<>();
        for (ValueFunctionManager.Variables var : ValueFunctionManager.Variables.values()) {
            genes.add(new WeightedSumGene(var, rand.nextFloat() * rand.nextInt()));
        }
    }

    public WeightedSumFitness(WeightedSumFitness parent1, WeightedSumFitness parent2){
        //Crossover Constructor for Uniform Crossover
        Random rand = new Random();

        //just in case of mismatch cuz of updates
        List<WeightedSumGene> longGenes;
        List<WeightedSumGene> shortGenes;

        if(parent1.genes.size() >= parent2.genes.size()){
            longGenes = parent1.genes;
            shortGenes = parent2.genes;
        }
        else {
            longGenes = parent2.genes;
            shortGenes = parent1.genes;
        }
        genes = new ArrayList<>(longGenes);

        for(int i = 0; i < shortGenes.size(); i++){
            if(rand.nextBoolean()){
                genes.set(i,longGenes.get(i));
            }
            else {
                genes.set(i,shortGenes.get(i));
            }
        }

    }

    public WeightedSumFitness(WeightedSumFitness old, boolean mutate){
        genes = new ArrayList<>(old.genes);
        if(mutate){
            for(WeightedSumGene gene : genes){

            }
        }

    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    public double evaluate(){
        double sum = 0;
        for(WeightedSumGene gene : genes){
            sum += gene.getWeightedValue();
        }
        return sum;
    }
}
