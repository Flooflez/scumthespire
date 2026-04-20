package fitnessevolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvolutionStateTest {

    @Test
    void roundTripPreservesAllFields(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("state.txt");
        List<String> exprs = List.of(
            "SUM_DAMAGE_DEALT - 2.0*DAMAGE_RECEIVED",
            "POWERS_PLAYED",
            "MONSTERS_REMAINING + 3.14");
        EvolutionState original = new EvolutionState(5, 42L, exprs);
        original.save(file);

        EvolutionState loaded = EvolutionState.load(file);
        assertEquals(5, loaded.generation());
        assertEquals(42L, loaded.seed());
        assertEquals(exprs, loaded.expressions());
    }

    @Test
    void loadRejectsMissingFields(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("state.txt");
        Files.writeString(file, "generation=0\nseed=1\n");
        assertThrows(IOException.class, () -> EvolutionState.load(file));
    }

    @Test
    void loadRejectsBadHeaderKey(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("state.txt");
        Files.writeString(file, "gen=0\nseed=1\ncount=0\n");
        IOException ex = assertThrows(IOException.class, () -> EvolutionState.load(file));
        assertTrue(ex.getMessage().contains("generation="));
    }

    @Test
    void loadRejectsCountMismatch(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("state.txt");
        Files.writeString(file, "generation=0\nseed=1\ncount=3\nA\nB\n");
        IOException ex = assertThrows(IOException.class, () -> EvolutionState.load(file));
        assertTrue(ex.getMessage().contains("count=3"));
    }

    @Test
    void saveCreatesParentDirectory(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("nested/deep/state.txt");
        new EvolutionState(0, 1L, List.of("X")).save(file);
        assertTrue(Files.exists(file));
    }
}
