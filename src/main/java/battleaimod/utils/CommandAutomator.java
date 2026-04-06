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

public class CommandAutomator implements PostUpdateSubscriber {

    // Subscribe to BaseMod so it knows to listen to this class
    public YourMainModFile() {
        BaseMod.subscribe(this);
    }

    // Check for key press
    @Override
    public void receivePostUpdate() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            runCommandsFromFile("commands.txt");
        }
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
    public static void runSilentCommand(String commandString) {
        if (commandString == null || commandString.trim().isEmpty()) {
            return;
        }

        String[] tokens = commandString.trim().split(" ");
        String rootCommand = tokens[0].toLowerCase();

        if (ConsoleCommand.commands.containsKey(rootCommand)) {
            try {
                ConsoleCommand.commands.get(rootCommand).execute(tokens, 1);
            } catch (Exception e) {
                System.out.println("Error executing command silently: " + commandString);
                e.printStackTrace();
            }
        } else {
            System.out.println("Command not recognized by BaseMod: " + rootCommand);
        }
    }
}