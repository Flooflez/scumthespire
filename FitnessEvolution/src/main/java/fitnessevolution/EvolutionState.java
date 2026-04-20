package fitnessevolution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot persisted between one-shot invocations of {@link Main}.
 *
 * Serialized as plain UTF-8 text so teammates can inspect / hand-edit it
 * during debugging. Format:
 * <pre>
 * generation=&lt;int&gt;
 * seed=&lt;long&gt;
 * count=&lt;int&gt;
 * &lt;canonical expression 1&gt;
 * &lt;canonical expression 2&gt;
 * ...
 * </pre>
 *
 * "Canonical expression" = the MathExpr string we wrote to JeneticsOutput.txt
 * for this individual. The mod must echo the same string verbatim in
 * ModOutput.txt so we can match fitnesses positionally.
 */
public record EvolutionState(int generation, long seed, List<String> expressions) {

    private static final String KEY_GEN = "generation=";
    private static final String KEY_SEED = "seed=";
    private static final String KEY_COUNT = "count=";

    public void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(KEY_GEN).append(generation).append('\n');
        sb.append(KEY_SEED).append(seed).append('\n');
        sb.append(KEY_COUNT).append(expressions.size()).append('\n');
        for (String e : expressions) {
            sb.append(e).append('\n');
        }
        Files.writeString(file, sb.toString());
    }

    public static EvolutionState load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.size() < 3) {
            throw new IOException("state file truncated: " + file);
        }
        int generation = parseIntField(lines.get(0), KEY_GEN);
        long seed = parseLongField(lines.get(1), KEY_SEED);
        int count = parseIntField(lines.get(2), KEY_COUNT);
        if (lines.size() < 3 + count) {
            throw new IOException(
                "state file claims count=" + count + " but only "
                    + (lines.size() - 3) + " expression lines present");
        }
        List<String> exprs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            exprs.add(lines.get(3 + i));
        }
        return new EvolutionState(generation, seed, exprs);
    }

    private static int parseIntField(String line, String prefix) throws IOException {
        if (!line.startsWith(prefix)) {
            throw new IOException("expected line starting with '" + prefix + "', got: " + line);
        }
        try {
            return Integer.parseInt(line.substring(prefix.length()).trim());
        } catch (NumberFormatException e) {
            throw new IOException("bad integer for '" + prefix + "': " + line, e);
        }
    }

    private static long parseLongField(String line, String prefix) throws IOException {
        if (!line.startsWith(prefix)) {
            throw new IOException("expected line starting with '" + prefix + "', got: " + line);
        }
        try {
            return Long.parseLong(line.substring(prefix.length()).trim());
        } catch (NumberFormatException e) {
            throw new IOException("bad long for '" + prefix + "': " + line, e);
        }
    }
}
