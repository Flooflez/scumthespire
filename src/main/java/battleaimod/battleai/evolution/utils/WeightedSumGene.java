package battleaimod.battleai.evolution.utils;

public class WeightedSumGene {
    private final ValueFunctionManager.Variables var;
    private final double weight;


    public WeightedSumGene(ValueFunctionManager.Variables var, double weight) {
        this.var = var;
        this.weight = weight;
    }

    public double getWeightedValue(){
        return ValueFunctionManager.getVariableValue(var) * weight;
    }

    public double getWeight() {
        return weight;
    }
}
