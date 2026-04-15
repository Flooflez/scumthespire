package battleaimod.battleai.evolution;

import basemod.interfaces.PostUpdateSubscriber;
import battleaimod.battleai.evolution.utils.WeightedSumFitness;
import battleaimod.utils.CommandAutomator;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EvolutionManager implements PostUpdateSubscriber {
    private boolean ready = true;
    private boolean inCombat = false;
    private static Random rand = new Random();
    private final List<WeightedSumFitness> population = new ArrayList<>();

    private final int MIN_POPULATION = 20;

    @Override
    public void receivePostUpdate() {
        if (ready && Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            ready = false;
            initEvolution();
            startCombat();
        }
    }

    private void initEvolution(){
        getPopulationFromFile("FitnessFunctions.txt");
        CommandAutomator.readCommands();
    }

    private void startCombat(){
        if(CommandAutomator.hasNextFight()){
            inCombat = true;
            CommandAutomator.runInitCommands();
            CommandAutomator.restartCurrentFight();
        }



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
}
