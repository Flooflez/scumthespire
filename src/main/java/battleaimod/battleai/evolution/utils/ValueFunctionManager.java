package battleaimod.battleai.evolution.utils;

import battleaimod.ValueFunctions;
import battleaimod.battleai.StateNode;
import savestate.SaveState;

import java.util.HashMap;
import java.util.Map;

public class ValueFunctionManager {

    //IMPORTANT: DO NOT CHANGE THE ORDER OF THESE
    //PLEASE APPEND NEW VARIABLES ONLY TO THE END
    public enum Variables{
        SUM_DAMAGE_DEALT,
        DAMAGE_RECEIVED,
        MONSTERS_REMAINING,
        SUM_MONSTER_HEALTH,
        POWERS_PLAYED,
        SUM_POISON,
        SUM_WEAK,
        SUM_VULNERABLE;
    }


    private static SaveState startState;
    private static SaveState endState;

    private static final Map<Variables, Double> valueMap = new HashMap<>();

    public static void initFuncValues(SaveState startState,SaveState endState){
        ValueFunctionManager.startState = startState;
        ValueFunctionManager.endState = endState;

        ValueFunctionManager.valueMap.put(Variables.SUM_DAMAGE_DEALT,
                (double) ValueFunctionManager.getTotalDamageDealt());

        ValueFunctionManager.valueMap.put(Variables.SUM_MONSTER_HEALTH,
                (double) ValueFunctionManager.getTotalMonsterHealth());

        ValueFunctionManager.valueMap.put(Variables.MONSTERS_REMAINING,
                (double) ValueFunctionManager.getAliveMonsterCount());

        ValueFunctionManager.valueMap.put(Variables.DAMAGE_RECEIVED,
                (double) ValueFunctionManager.getPlayerDamage());

    }

    /**
     * Adds up monster health and accounts for powers that alter the effective health of the enemy
     * such as barricade and unawakened.
     */
    public static int getTotalMonsterHealth() {
        return endState.curMapNodeState.monsterData.stream()
                .map(monster -> {
                    if (monster.powers.stream()
                            .anyMatch(power -> power.powerId
                                    .equals("Barricade"))) {
                        return monster.currentHealth + monster.currentBlock;
                    } else if (monster.powers.stream()
                            .anyMatch(power -> power.powerId
                                    .equals("Unawakened"))) {
                        return monster.currentHealth + monster.maxHealth;
                    }
                    return monster.currentHealth;
                })
                .reduce(Integer::sum)
                .get();
    }

    /**
     * Counts the number of monsters currently alive.
     */
    public static int getAliveMonsterCount() {
        if (endState == null ||
                endState.curMapNodeState == null ||
                endState.curMapNodeState.monsterData == null) {
            return 0;
        }

        return (int) endState.curMapNodeState.monsterData.stream()
                .filter(monster -> monster != null && monster.currentHealth > 0)
                .count();
    }

    public static int getTotalDamageDealt() {
        return ValueFunctions.getTotalMonsterHealth(startState)
                - ValueFunctions.getTotalMonsterHealth(endState);
    }

    public static int getPlayerDamage() {
        return startState.getPlayerHealth() - endState.getPlayerHealth();
    }

    public static double getVariableValue(Variables var){
        return valueMap.getOrDefault(var, 0.0);
    }
}
