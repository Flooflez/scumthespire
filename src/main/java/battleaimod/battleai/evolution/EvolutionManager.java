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
import java.util.List;
import java.util.Random;

public class EvolutionManager implements PostUpdateSubscriber {
    private boolean simRunning = false;
    public static boolean canRunAutoBattler = false;
    private boolean inCombat = false;
    private static Random rand = new Random();
    private final List<WeightedSumFitness> population = new ArrayList<>();
    private int currentFitnessIndex = 0;

    private final int MIN_POPULATION = 20;

    private SaveState startingState;

    //TODO: check if server client both using savestates is gonna explode everything

    @Override
    public void receivePostUpdate() {
        if (!simRunning && Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            simRunning = true;
            initEvolution();
            startNewCombat();
        }
        if(inCombat){
            if(combatOver()){
                System.out.println("combat over!!");
                inCombat = false;
                startNewCombat();
            }
        }
    }

    //TODO: check if this works when combat ends and in reward screen
    private boolean combatOver() {
        return CardCrawlGame.isInARun() && AbstractDungeon.currMapNode != null
                && AbstractDungeon.getCurrRoom() != null
                && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT
                && AbstractDungeon.getCurrRoom().isBattleOver; //I added this
    }

    private void initEvolution(){
        currentFitnessIndex = 0;
        getPopulationFromFile("FitnessFunctions.txt");
        CommandAutomator.readCommands();
        writeFitnessFunction();


        //TODO: make this happen when combat is ready
        startingState = new SaveState();
    }

    private void startNewCombat(){
        if(currentFitnessIndex == population.size()){
            //finished all fitness functions
            if(CommandAutomator.hasNextFight()){
                //first calculate fitness fitness
                //sort, then evolve next gen

                // go to next combat:

                CommandAutomator.advanceNextFight();


                currentFitnessIndex = 0;
                restartFight();

                //TODO: make this happen when combat is ready
                startingState = new SaveState();
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
            restartFight();
        }
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
        inCombat = true;

        //TODO:
        startingState.loadState();
        //load SaveState instead of \/
        //CommandAutomator.runInitCommands();
        //CommandAutomator.restartCurrentFight();

        //TODO: save the state of the start of the combat
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

    private void sortPopulation(){

    }

    private void calculateFitnessFitness(){

    }

}
