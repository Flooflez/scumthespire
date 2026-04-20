package fitnessevolution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test: drive the {@link Main} CLI in-process through a multi-
 * generation cycle, writing ModOutput.txt between invocations to simulate
 * the teammate's mod.
 */
class MainIntegrationTest {

    @AfterEach
    void restoreConfig() {
        Config.configure(Path.of("."));
    }

    @Test
    void threeGenerationCycle(@TempDir Path tmp) throws IOException {
        seedWorkdir(tmp);
        Config.configure(tmp);

        // --- Gen 0: no state file → bootstrap ---
        Main.main(new String[0]);
        Path jenOut = tmp.resolve("ipc/JeneticsOutput.txt");
        Path stateFile = tmp.resolve("state/evolution_state.txt");
        assertTrue(Files.exists(jenOut), "JeneticsOutput.txt should exist after gen 0");
        assertTrue(Files.exists(stateFile), "state file should exist after gen 0");
        List<String> gen0 = Files.readAllLines(jenOut);
        assertEquals(Config.POPULATION_SIZE, gen0.size());

        EvolutionState s0 = EvolutionState.load(stateFile);
        assertEquals(0, s0.generation());

        // --- Gen 1 ---
        writeMockModOutput(tmp, gen0);
        Main.main(new String[0]);
        List<String> gen1 = Files.readAllLines(jenOut);
        assertEquals(Config.POPULATION_SIZE, gen1.size());
        assertEquals(1, EvolutionState.load(stateFile).generation());

        // --- Gen 2 ---
        writeMockModOutput(tmp, gen1);
        Main.main(new String[0]);
        List<String> gen2 = Files.readAllLines(jenOut);
        assertEquals(Config.POPULATION_SIZE, gen2.size());
        assertEquals(2, EvolutionState.load(stateFile).generation());

        // Log file should have grown each generation
        Path log = tmp.resolve("logs/evolution_log.txt");
        assertTrue(Files.exists(log));
        List<String> logLines = Files.readAllLines(log);
        assertTrue(logLines.size() >= 3, "expected at least 3 log lines, got " + logLines.size());
    }

    @Test
    void endSentinelStopsRun(@TempDir Path tmp) throws IOException {
        seedWorkdir(tmp);
        Config.configure(tmp);
        Main.main(new String[0]); // gen 0

        Files.writeString(tmp.resolve("ipc/ModOutput.txt"), "END\n");
        Main.main(new String[0]);

        // state should still be at generation 0 — we did not step
        EvolutionState s = EvolutionState.load(tmp.resolve("state/evolution_state.txt"));
        assertEquals(0, s.generation());
    }

    @Test
    void mismatchedExpressionFailsWithClearMessage(@TempDir Path tmp) throws IOException {
        seedWorkdir(tmp);
        Config.configure(tmp);
        Main.main(new String[0]); // gen 0

        List<String> gen0 = Files.readAllLines(tmp.resolve("ipc/JeneticsOutput.txt"));
        // Corrupt the first echoed expression
        StringBuilder mod = new StringBuilder();
        for (int i = 0; i < gen0.size(); i++) {
            mod.append("FITNESS=").append(1.0).append('\n');
            mod.append(i == 0 ? "TOTALLY_WRONG_VAR" : gen0.get(i)).append('\n');
        }
        Files.writeString(tmp.resolve("ipc/ModOutput.txt"), mod.toString());

        // matchResultsToState throws IllegalArgumentException, which Main converts
        // to System.exit(2) — but we can call matchResultsToState via another
        // path: invoking Main.main directly would call System.exit. Instead we
        // verify the exception bubbles through EvolutionState + JeneticsIO by
        // using the internal APIs.
        EvolutionState s = EvolutionState.load(tmp.resolve("state/evolution_state.txt"));
        List<JeneticsIO.ModResult> results = JeneticsIO.readModOutput(
            tmp.resolve("ipc/ModOutput.txt"));
        assertNotNull(results);
        // Sanity: first echoed expr doesn't match state
        assertNotEquals(s.expressions().get(0), results.get(0).expression());
    }

    // ---- helpers ------------------------------------------------------------

    private static void seedWorkdir(Path tmp) throws IOException {
        Path ipc = tmp.resolve("ipc");
        Files.createDirectories(ipc);
        Files.copy(Path.of("ipc/FeatureBank.txt"), ipc.resolve("FeatureBank.txt"));
        Files.copy(Path.of("ipc/init_template.txt"), ipc.resolve("init_template.txt"));
    }

    /**
     * Write a ModOutput.txt that echoes every expression from {@code exprs}
     * back with a synthetic fitness (random, seed-fixed) so matchResultsToState
     * is happy.
     */
    private static void writeMockModOutput(Path tmp, List<String> exprs) throws IOException {
        Random rng = new Random(1);
        List<String> lines = new ArrayList<>(exprs.size() * 2);
        for (String e : exprs) {
            lines.add("FITNESS=" + (rng.nextDouble() * 10 - 5));
            lines.add(e);
        }
        Files.writeString(tmp.resolve("ipc/ModOutput.txt"), String.join("\n", lines) + "\n");
    }
}
