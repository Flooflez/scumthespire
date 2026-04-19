package battleaimod.battleai.evolution.utils;

import battleaimod.ValueFunctions;
import battleaimod.battleai.StateNode;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import savestate.SaveState;
import savestate.monsters.MonsterState;
import savestate.powers.PowerState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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
        ;
    }


    private static SaveState startState;
    private static SaveState endState;
    private static List<AbstractCard> cardsPlayed;

    private static final Map<String, Integer> powerTotals = new HashMap<>();
    private static boolean cacheValid = false;

    private static final Map<Variables, Double> valueMap = new HashMap<>();

    public static void writeVariablesToFile(String fileName) {
        File file = new File(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
            for (Variables v : Variables.values()) {
                writer.write(v.name());
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Variables to file", e);
        }
    }

    public static void initFuncValues(SaveState startState, SaveState endState, List<AbstractCard> cardsPlayed){
        ValueFunctionManager.startState = startState;
        ValueFunctionManager.endState = endState;
        ValueFunctionManager.cardsPlayed = cardsPlayed;

        ValueFunctionManager.valueMap.put(Variables.SUM_DAMAGE_DEALT,
                (double) ValueFunctionManager.getTotalDamageDealt());

        ValueFunctionManager.valueMap.put(Variables.SUM_MONSTER_HEALTH,
                (double) ValueFunctionManager.getTotalMonsterHealth());

        ValueFunctionManager.valueMap.put(Variables.MONSTERS_REMAINING,
                (double) ValueFunctionManager.getAliveMonsterCount());

        ValueFunctionManager.valueMap.put(Variables.DAMAGE_RECEIVED,
                (double) ValueFunctionManager.getPlayerDamage());

        ValueFunctionManager.valueMap.put(Variables.POWERS_PLAYED,
                (double) ValueFunctionManager.getNumberPowersPlayed());

        ValueFunctionManager.valueMap.put(Variables.SUM_POISON,
                (double) ValueFunctionManager.getPowerTotal("Poison"));

        ValueFunctionManager.valueMap.put(Variables.SUM_WEAK,
                (double) ValueFunctionManager.getPowerTotal("Weakened"));

        ValueFunctionManager.valueMap.put(Variables.SUM_VULNERABLE,
                (double) ValueFunctionManager.getPowerTotal("Vulnerable"));

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

    public static int getNumberPowersPlayed(){
        int sum = 0;
        for(AbstractCard c : cardsPlayed){
            if(c.type == AbstractCard.CardType.POWER){
                sum++;
            }
        }
        return sum;
    }



    private static void computeMonsterPowerTotals() {
        powerTotals.clear();

        if (endState == null ||
                endState.curMapNodeState == null ||
                endState.curMapNodeState.monsterData == null) {
            cacheValid = true;
            return;
        }

        for (MonsterState m : endState.curMapNodeState.monsterData) {
            for (PowerState p : m.powers) {
                powerTotals.merge(p.powerId, p.amount, Integer::sum);
            }
        }

        cacheValid = true;
    }

    public static int getPowerTotal(String powerId) {
        if (!cacheValid) computeMonsterPowerTotals();
        return powerTotals.getOrDefault(powerId, 0);
    }
}
