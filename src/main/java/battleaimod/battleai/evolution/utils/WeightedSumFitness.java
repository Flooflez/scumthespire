package battleaimod.battleai.evolution.utils;

import java.util.Random;

public class WeightedSumFitness implements Comparable<WeightedSumFitness> {
    private static final Random rand = new Random();
    private final double[] weights;
    private double fitness = 0;


    public WeightedSumFitness(String line) {
        String[] parts = line.split(",");

        int expectedLength = ValueFunctionManager.Variables.values().length;
        weights = new double[expectedLength];

        int limit = Math.min(parts.length, expectedLength);
        for (int i = 0; i < limit; i++) {
            weights[i] = Double.parseDouble(parts[i]);
        }
        // Remaining values are already 0.0 by default (no need to explicitly set)
    }

    public WeightedSumFitness(WeightedSumFitness parent1, WeightedSumFitness parent2){
        //Crossover Constructor for Uniform Crossover

        weights = parent1.weights.clone();

        for(int i = 0; i < weights.length; i++){
            if(rand.nextBoolean()){
                weights[i] = parent2.weights[i];
            }
        }

    }

    public WeightedSumFitness(WeightedSumFitness old, boolean mutate){
        weights = old.weights.clone();
        if(mutate){
            for(int i = 0; i < weights.length; i++){
                weights[i] = mutateWeight(weights[i]);
            }
        }
    }

    public void setFitnessFitness(double fitness) {
        this.fitness = fitness;
    }

    public double evaluate(){
        double sum = 0;
        ValueFunctionManager.Variables[] vars = ValueFunctionManager.Variables.values();
        for(int i = 0; i < weights.length;i++){
            sum += ValueFunctionManager.getVariableValue(vars[i]) * weights[i];
        }
        return sum;
    }

    public double mutateWeight(double oldWeight) {
        double r = rand.nextDouble();

        if (r < 0.9) {
            double sigma = 0.1;
            oldWeight *= (1 + rand.nextGaussian() * sigma);
        } else {
            double factor = 0.2 + rand.nextDouble() * 4.8;
            oldWeight *= factor;
        }

        return Math.max(1e-6, Math.min(1e6, oldWeight));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < weights.length; i++) {
            sb.append(weights[i]);
            if (i < weights.length - 1) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    @Override
    public int compareTo(WeightedSumFitness other) {
        // Higher score = better (sorted descending)
        return Double.compare(other.fitness, this.fitness);
    }

}
