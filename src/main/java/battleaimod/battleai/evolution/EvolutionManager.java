package battleaimod.battleai.evolution;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import battleaimod.battleai.evolution.utils.WeightedSumFitness;
import battleaimod.utils.CommandAutomator;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import savestate.SaveState;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class EvolutionManager implements PostUpdateSubscriber {
    private boolean simRunning = false;
    public static boolean canRunAutoBattler = false;
    private static final Random rand = new Random();
    private List<WeightedSumFitness> population = new ArrayList<>();
    private int currentFitnessIndex;

    private final int MIN_POPULATION = 4;
    private final int ELITES = 2;

    private SaveState startingState;

    private boolean waitingForCombatToSave = false;
    private boolean waitingForCombatToBattle = false;
    private boolean waitingForDeckUpdate = false;
    private boolean waitingForCombatReset = false;

    private ArrayList<AbstractCard> startingDeck;
    private int startingHp;
    private int startingTurn;


    //TODO: check if server client both using savestates is gonna explode everything

    @Override
    public void receivePostUpdate() {
        if (!simRunning && Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            System.out.println("starting sim");
            simRunning = true;
            initEvolution();
            return;
        }

        if(waitingForDeckUpdate){
            if(checkDeckUpdated()){
                startingHp = AbstractDungeon.player.currentHealth;
                waitingForDeckUpdate = false;
                CommandAutomator.restartCurrentFight();
                waitingForCombatToSave = true;
            }
            return;
        }

        if(waitingForCombatToSave){
            if(isInCombat()){
                startingState = new SaveState();
                startingTurn = GameActionManager.turn;
                waitingForCombatToSave = false;
                startNewCombat();
            }
            return;
        }

        if (waitingForCombatReset) {
            if(AbstractDungeon.player.currentHealth == startingHp && GameActionManager.turn == startingTurn){
                waitingForCombatReset = false;
                startNextGenCombat();
            }
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
                System.out.println("combat over detected!!");

                population.get(currentFitnessIndex).setFitnessFitness(calculateFitnessFitness());

                startNewCombat();
            }
        }
    }

    //TODO: check if this works when combat ends and in reward screen
    private boolean combatOver() {
        return isDead() || (isInCombat() && (AbstractDungeon.getCurrRoom().isBattleOver));
    }

    private boolean isInCombat() {
        return CardCrawlGame.isInARun() && AbstractDungeon.currMapNode != null
                && AbstractDungeon.getCurrRoom() != null
                && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
    }

    private boolean isDead(){
        //return AbstractDungeon.player.isDead || AbstractDungeon.player.isDying;
        return (AbstractDungeon.isScreenUp && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.DEATH);
    }

    private void initEvolution(){
        currentFitnessIndex = -1;
        getPopulationFromFile("FitnessFunctions.txt");
        CommandAutomator.readCommands();

        CommandAutomator.runInitCommands();
        startingDeck = new ArrayList<>(AbstractDungeon.player.masterDeck.group);
        waitingForDeckUpdate = true;

    }

    private boolean checkDeckUpdated() {
        ArrayList<AbstractCard> newDeck =
                new ArrayList<>(AbstractDungeon.player.masterDeck.group);

        //System.out.println("deck size == " + newDeck.size());
        if(newDeck.isEmpty()) return false;

        // Different sizes → definitely different
        if (newDeck.size() != startingDeck.size()) return true;

        Map<String, Integer> countMap = new HashMap<>();

        // Count starting deck
        for (AbstractCard card : startingDeck) {
            String key = card.cardID;
            countMap.put(key, countMap.getOrDefault(key, 0) + 1);
        }

        // Subtract using new deck
        for (AbstractCard card : newDeck) {
            String key = card.cardID;

            if (!countMap.containsKey(key)) return true;

            countMap.put(key, countMap.get(key) - 1);

            if (countMap.get(key) == 0) {
                countMap.remove(key);
            }
        }

        // If empty → exact match
        return !countMap.isEmpty();
    }

    private void startNewCombat(){
        currentFitnessIndex++;
        if(currentFitnessIndex == population.size()){
            //finished all fitness functions
            System.out.println("Finished all fights for: " + CommandAutomator.getCurrentFight());
            CommandAutomator.advanceNextFight();
            if(CommandAutomator.hasNextFight()){

                System.out.println("Sorting and Evolving next gen");
                //sort
                Collections.sort(population);
                //evolve next gen
                evolvePopulation();

                // go to next combat:

                System.out.println("Resetting state");
                waitingForCombatReset = true;
                startingState.loadState();

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

        File file = new File(fileName);

        if (!file.exists()) {
            System.out.println("Could not find the command file at: " + file.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
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

    private void startNextGenCombat(){
        System.out.println("Going to next fight: " + CommandAutomator.getCurrentFight());
        CommandAutomator.restartCurrentFight();
        waitingForCombatToSave = true;
        currentFitnessIndex = -1;
    }
}
