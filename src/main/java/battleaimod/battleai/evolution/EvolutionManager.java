package battleaimod.battleai.evolution;

import basemod.interfaces.PostUpdateSubscriber;
import battleaimod.battleai.evolution.utils.WeightedSumFitness;
import battleaimod.utils.CommandAutomator;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import savestate.SaveState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class EvolutionManager implements PostUpdateSubscriber {
    private boolean simRunning = false;
    public static boolean canRunAutoBattler = false;
    private static final Random rand = new Random();
    private List<WeightedSumFitness> population = new ArrayList<>();
    private int currentFitnessIndex;

    private final int MIN_POPULATION = 20;
    private final int ELITES = 5;

    private SaveState startingState;

    private boolean waitingForCombatToSave = false;
    private boolean waitingForCombatToBattle = false;

    //TODO: check if server client both using savestates is gonna explode everything

    @Override
    public void receivePostUpdate() {
        if (!simRunning && Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            simRunning = true;
            initEvolution();
            return;
        }

        if(waitingForCombatToSave){
            if(isInCombat()){
                startingState = new SaveState();
                waitingForCombatToSave = false;
                startNewCombat();
            }
            return;
        }

        if(waitingForCombatToBattle){
            if(isInCombat()){
                EvolutionManager.canRunAutoBattler = true;
                waitingForCombatToBattle = false;
            }
            return;
        }

        if(EvolutionManager.canRunAutoBattler){
            if(combatOver()){ //detect combat is over
                EvolutionManager.canRunAutoBattler = false;
                System.out.println("combat over!!");

                population.get(currentFitnessIndex).setFitnessFitness(calculateFitnessFitness());

                startNewCombat();
            }
        }
    }

    //TODO: check if this works when combat ends and in reward screen
    private boolean combatOver() {
        return isInCombat() && AbstractDungeon.getCurrRoom().isBattleOver; //I added this
    }

    private boolean isInCombat() {
        return CardCrawlGame.isInARun() && AbstractDungeon.currMapNode != null
                && AbstractDungeon.getCurrRoom() != null
                && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
    }

    private void initEvolution(){
        currentFitnessIndex = -1;
        getPopulationFromFile("FitnessFunctions.txt");
        CommandAutomator.readCommands();

        waitingForCombatToSave = true;
    }

    private void startNewCombat(){
        if(currentFitnessIndex == population.size()){
            //finished all fitness functions
            if(CommandAutomator.hasNextFight()){
                //sort
                Collections.sort(population);
                //evolve next gen
                evolvePopulation();

                // go to next combat:
                CommandAutomator.advanceNextFight();
                waitingForCombatToSave = true;
                currentFitnessIndex = -1;

            }
            else {
                //No more combats in the command list -> FINISHED, write to file and end
                System.out.println("Finished all simulations");
                writePopulationToFile("NewFitnessFunctions.txt");
                simRunning = false;
            }
        }
        else {
            //still have fitness funcs to check
            currentFitnessIndex++;
            writeFitnessFunction();
            restartFight();
        }
    }

    private void evolvePopulation() {
        // Assume population is already sorted by fitness (best first)
        List<WeightedSumFitness> newPopulation = new ArrayList<>();

        // 1. Elitism (copy top ELITES without mutation)
        for (int i = 0; i < ELITES && i < population.size(); i++) {
            newPopulation.add(new WeightedSumFitness(population.get(i), false));
        }

        // 2. Generate rest of population
        while (newPopulation.size() < MIN_POPULATION) {
            // Select two parents (simple random selection)
            WeightedSumFitness parent1 = population.get(rand.nextInt(population.size()));
            WeightedSumFitness parent2;
            do {
                parent2 = population.get(rand.nextInt(population.size()));
            } while (parent1 == parent2);

            // Crossover
            WeightedSumFitness child = new WeightedSumFitness(parent1, parent2);

            // Mutation
            child = new WeightedSumFitness(child, true);

            newPopulation.add(child);
        }

        // 3. Replace old population
        population = newPopulation;
    }

    private void writeFitnessFunction() {
        //TODO: for now, we send fitness by file which is dumb but idk networking
        WeightedSumFitness current = population.get(currentFitnessIndex);
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("CurrentFitnessValues.txt"))) {
            writer.write(current.toString());
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write fitness file", e);
        }
    }

    private void restartFight(){
        startingState.loadState();
        waitingForCombatToBattle = true;
    }

    private void getPopulationFromFile(String fileName) {
        population.clear();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines
                if (line.isEmpty()) continue;

                population.add(new WeightedSumFitness(line));
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read population file: " + fileName, e);
        }

        // Pad population if too small
        while (population.size() < MIN_POPULATION) {
            // Pick a random parent from existing population
            WeightedSumFitness parent = population.get(rand.nextInt(population.size()));

            // Mutated copy
            population.add(new WeightedSumFitness(parent, true));
        }
    }

    private void writePopulationToFile(String fileName) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName))) {

            for (WeightedSumFitness individual : population) {
                writer.write(individual.toString());
                writer.newLine();
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to write population to file: " + fileName, e);
        }
    }


    private double calculateFitnessFitness(){
        SaveState endState = new SaveState();

        //TODO: maybe need server to send back additional info that save states can't see
        //wasted block, energy etc.

        int playerHealth = endState.getPlayerHealth();
        int healthLost = startingState.getPlayerHealth() - endState.getPlayerHealth();
        int turnCount = endState.turn;


        return playerHealth * 1.0   // reward ending health
                - healthLost * 5.0   // penalize damage taken
                - turnCount * 2.0;  // penalize slow fights
    }

}
