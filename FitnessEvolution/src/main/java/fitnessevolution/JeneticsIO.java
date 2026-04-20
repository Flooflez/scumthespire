package fitnessevolution;

import io.jenetics.Genotype;
import io.jenetics.prog.ProgramGene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * File-IO for the Option B (mod-driven one-shot) handshake.
 *
 * JeneticsOutput.txt (we write):
 * <pre>
 * &lt;MathExpr 1&gt;
 * &lt;MathExpr 2&gt;
 * ...
 * </pre>
 * One expression per non-empty line. No READY flag — presence of the file
 * with {@link Config#POPULATION_SIZE} lines signals "ready for evaluation".
 *
 * ModOutput.txt (teammate writes):
 * <pre>
 * FITNESS=&lt;double&gt;
 * &lt;MathExpr echoed verbatim from our JeneticsOutput&gt;
 * FITNESS=&lt;double&gt;
 * &lt;MathExpr&gt;
 * ...
 * </pre>
 * Or, to stop the run, the single line {@code END} (as the first
 * non-blank line).
 */
public final class JeneticsIO {

    public static final String FITNESS_PREFIX = "FITNESS=";

    private JeneticsIO() {}

    /** Result of a single evaluated individual as reported by the mod. */
    public record ModResult(double fitness, String expression) {}

    /**
     * Serialize each genotype via {@link MathExprIO#serialize} and write one
     * expression per line to {@code outFile}. Returns the canonical
     * expression strings in the same order so the caller can persist them
     * for later positional matching.
     */
    public static List<String> writePopulation(
        List<Genotype<ProgramGene<Double>>> population,
        Path outFile
    ) throws IOException {
        Path parent = outFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> canonical = new ArrayList<>(population.size());
        StringBuilder sb = new StringBuilder();
        for (Genotype<ProgramGene<Double>> g : population) {
            String expr = MathExprIO.serialize(g.gene().toTreeNode());
            canonical.add(expr);
            sb.append(expr).append('\n');
        }
        Files.writeString(outFile, sb.toString());
        return canonical;
    }

    /**
     * Parse ModOutput.txt. Returns {@code null} if the mod signalled
     * {@link Config#END_SENTINEL END} (as the first non-blank line).
     *
     * @throws IOException          if the file is missing or unreadable
     * @throws IllegalStateException if the content is malformed
     */
    public static List<ModResult> readModOutput(Path modOutputFile)
        throws IOException {
        List<String> raw = Files.readAllLines(modOutputFile);
        List<String> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException(
                "ModOutput.txt is empty — expected FITNESS= pairs or END");
        }
        if (lines.get(0).equalsIgnoreCase(Config.END_SENTINEL)) {
            return null;
        }
        return parseFitnessPairs(lines, modOutputFile);
    }

    private static List<ModResult> parseFitnessPairs(
        List<String> lines, Path source
    ) {
        if (lines.size() % 2 != 0) {
            throw new IllegalStateException(
                source + ": expected an even number of non-blank lines "
                    + "(FITNESS=<d> / <expr> pairs), got " + lines.size());
        }
        List<ModResult> results = new ArrayList<>(lines.size() / 2);
        for (int i = 0; i < lines.size(); i += 2) {
            String fitnessLine = lines.get(i);
            String exprLine = lines.get(i + 1);
            if (!fitnessLine.startsWith(FITNESS_PREFIX)) {
                throw new IllegalStateException(
                    source + " line " + (i + 1)
                        + ": expected '" + FITNESS_PREFIX + "<double>', got: "
                        + fitnessLine);
            }
            double fitness = parseFitness(fitnessLine, source, i + 1);
            results.add(new ModResult(fitness, exprLine));
        }
        return results;
    }

    private static double parseFitness(String line, Path source, int lineNum) {
        String raw = line.substring(FITNESS_PREFIX.length()).trim();
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                source + " line " + lineNum + ": bad fitness value '" + raw + "'", e);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalStateException(
                source + " line " + lineNum + ": non-finite fitness '" + raw
                    + "' (must be finite; mod should map NaN/±Infinity to a "
                    + "concrete sentinel such as 0)");
        }
        return value;
    }
}
