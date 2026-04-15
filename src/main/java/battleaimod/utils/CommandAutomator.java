package battleaimod.utils;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.devcommands.ConsoleCommand;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

public class CommandAutomator implements PostUpdateSubscriber {

    private final static Queue<String> fightCommands = new ArrayDeque<>();

    // Subscribe to BaseMod so it knows to listen to this class
    public CommandAutomator() {
        BaseMod.subscribe(this);
        readFightCommands();
    }

    // Check for key press
    @Override
    public void receivePostUpdate() {
        //moved to EvolutionManager
    }

    public static void runInitCommands(){
        runCommandsFromFile("InitCommands.txt");
    }

    private static void readFightCommands(){
        File file = new File("FightCommands.txt");

        if (!file.exists()) {
            System.out.println("Could not find the command file at: " + file.getAbsolutePath());
            throw new RuntimeException("Missing FightCommands.txt");
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                fightCommands.add(line);
            }

        } catch (IOException e) {
            System.out.println("An error occurred while reading the FightCommands file!");
        }
    }

    public static void restartCurrentFight(){
        runSilentCommand(fightCommands.peek());
    }

    public static boolean hasNextFight(){
        return !fightCommands.isEmpty();
    }

    public static void advanceNextFight(){
        //DOES NOT RUN THE FIGHT COMMAND
        fightCommands.poll();
    }

    // File reader logic
    public static void runCommandsFromFile(String fileName) {
        File file = new File(fileName);

        if (!file.exists()) {
            System.out.println("Could not find the command file at: " + file.getAbsolutePath());
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            System.out.println("--- Starting Batch Commands from " + fileName + " ---");

            while ((line = br.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

                System.out.println("Executing: " + line);
                runSilentCommand(line);
            }

            System.out.println("--- Finished Batch Commands ---");

        } catch (IOException e) {
            System.out.println("An error occurred while reading the command file!");
            e.printStackTrace();
        }
    }

    // Silent execution logic
    /**
     * Executes a BaseMod console command silently via code.
     */
    public static void runSilentCommand(String commandString) {
        if (commandString == null || commandString.trim().isEmpty()) {
            return;
        }

        // Split the command into an array of words, exactly how the DevConsole does
        String[] tokens = commandString.trim().split(" ");

        try {
            // Pass the tokens directly into BaseMod's built-in execution method
            ConsoleCommand.execute(tokens);
        } catch (Exception e) {
            System.out.println("Error executing command silently: " + commandString);
            e.printStackTrace();
        }
    }
}