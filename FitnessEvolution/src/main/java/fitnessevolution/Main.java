package fitnessevolution;

import io.jenetics.Genotype;
import io.jenetics.ext.util.TreeNode;
import io.jenetics.prog.ProgramGene;
import io.jenetics.prog.op.MathExpr;
import io.jenetics.prog.op.Op;
import io.jenetics.prog.op.Var;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * One-shot CLI entry point for the Option B (mod-driven) integration.
 *
 * <ul>
 *   <li><b>First call (no state file):</b> loads FeatureBank + init_template,
 *       builds population 0 (1 template + ramped random), writes
 *       {@link Config#JENETICS_OUTPUT}, saves state, exits 0.</li>
 *   <li><b>Subsequent calls:</b> reads {@link Config#MOD_OUTPUT}, positionally
 *       matches fitnesses against the persisted expressions, steps the GP
 *       engine, writes the next JeneticsOutput.txt, updates state, exits 0.
 *       If ModOutput.txt's first non-blank line is {@code END}, logs and
 *       exits 0 without stepping.</li>
 * </ul>
 *
 * Seed handling: base seed defaults to {@link Config#DEFAULT_SEED}; the
 * effective per-generation seed used to drive crossover/mutation is
 * {@code baseSeed + generation}, which keeps the run reproducible across
 * invocations. Override the base seed via {@code --seed=N}.
 *
 * Exit codes: 0 = normal, 2 = user/config error, 3 = IO error, 4 = internal.
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {}

    public static void main(String[] args) {
        try {
            long baseSeed = parseSeed(args);
            if (Files.exists(Config.stateFile())) {
                runNextGeneration(baseSeed);
            } else {
                runGenerationZero(baseSeed);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            System.exit(3);
        } catch (RuntimeException e) {
            System.err.println("internal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(4);
        }
    }

    // --- generation 0 -------------------------------------------------------

    private static void runGenerationZero(long baseSeed) throws IOException {
        ISeq<Var<Double>> vars = FeatureBankLoader.loadVars(Config.featureBank());
        ISeq<Op<Double>> terminals = OpSet.terminals(vars);
        TreeNode<Op<Double>> template = MathExprIO.parseTemplate(Config.initTemplate());

        long effectiveSeed = baseSeed;
        RandomRegistry.random(new Random(effectiveSeed));

        GPEngine engine = new GPEngine(
            OpSet.OPS, terminals, Config.MAX_DEPTH, Config.POPULATION_SIZE, effectiveSeed);
        List<Genotype<ProgramGene<Double>>> pop =
            engine.initialPopulation(template, vars);

        List<String> canonical = JeneticsIO.writePopulation(pop, Config.jeneticsOutput());
        new EvolutionState(0, baseSeed, canonical).save(Config.stateFile());

        appendLog(String.format(
            "gen=0 bootstrap seed=%d pop=%d template=%s",
            baseSeed, pop.size(), canonical.get(0)));
        System.out.println(
            "Wrote generation 0 (" + pop.size() + " individuals) to "
                + Config.jeneticsOutput());
    }

    // --- generation N+1 -----------------------------------------------------

    private static void runNextGeneration(long baseSeedArg) throws IOException {
        EvolutionState state = EvolutionState.load(Config.stateFile());
        long baseSeed = state.seed();
        if (baseSeedArg != state.seed()) {
            System.err.println(
                "warning: --seed=" + baseSeedArg
                    + " ignored; resuming with persisted seed=" + state.seed());
        }

        if (!Files.exists(Config.modOutput())) {
            throw new IllegalArgumentException(
                "state file exists (generation=" + state.generation()
                    + ") but " + Config.modOutput() + " is missing; "
                    + "write fitnesses there before calling again");
        }
        List<JeneticsIO.ModResult> results =
            JeneticsIO.readModOutput(Config.modOutput());
        if (results == null) {
            appendLog("gen=" + state.generation() + " END received, exiting");
            System.out.println("END received — run stopped.");
            return;
        }

        List<Scored> scored = matchResultsToState(results, state);

        int nextGen = state.generation() + 1;
        long effectiveSeed = baseSeed + nextGen;
        RandomRegistry.random(new Random(effectiveSeed));

        ISeq<Var<Double>> vars = FeatureBankLoader.loadVars(Config.featureBank());
        ISeq<Op<Double>> terminals = OpSet.terminals(vars);
        GPEngine engine = new GPEngine(
            OpSet.OPS, terminals, Config.MAX_DEPTH, Config.POPULATION_SIZE, effectiveSeed);

        List<Genotype<ProgramGene<Double>>> nextPop = engine.step(scored);
        List<String> canonical = JeneticsIO.writePopulation(nextPop, Config.jeneticsOutput());
        new EvolutionState(nextGen, baseSeed, canonical).save(Config.stateFile());

        logGeneration(nextGen, scored, canonical);
        System.out.println(
            "Stepped to generation " + nextGen + " ("
                + nextPop.size() + " individuals) written to "
                + Config.jeneticsOutput());
    }

    // --- helpers ------------------------------------------------------------

    private static List<Scored> matchResultsToState(
        List<JeneticsIO.ModResult> results, EvolutionState state
    ) throws IOException {
        if (results.size() != state.expressions().size()) {
            throw new IllegalArgumentException(
                "ModOutput.txt has " + results.size()
                    + " FITNESS pairs but state expects "
                    + state.expressions().size());
        }
        ISeq<Var<Double>> vars = FeatureBankLoader.loadVars(Config.featureBank());
        ISeq<Op<Double>> terminals = OpSet.terminals(vars);
        // Use a throwaway engine purely for rehydration (no RNG needed here).
        GPEngine rehydrator = new GPEngine(
            OpSet.OPS, terminals, Config.MAX_DEPTH, state.expressions().size(), 0L);
        List<Genotype<ProgramGene<Double>>> pop =
            rehydrator.rehydratePopulation(state.expressions(), vars);

        List<Scored> scored = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            String expected = state.expressions().get(i);
            String echoed = results.get(i).expression();
            String canonicalEcho = canonicalize(echoed, i);
            if (!expected.equals(canonicalEcho)) {
                throw new IllegalArgumentException(
                    "expression mismatch at index " + i
                        + "\n  expected:      " + expected
                        + "\n  got:           " + echoed
                        + "\n  canonicalized: " + canonicalEcho);
            }
            scored.add(new Scored(pop.get(i), results.get(i).fitness()));
        }
        return scored;
    }

    /**
     * Run an echoed expression through our canonical serialization pipeline
     * so the mod can use any semantically-equivalent representation (e.g.
     * a MathExpr.parse round-trip that turns {@code -2.897} into
     * {@code neg(2.897)}) without us rejecting it on a string mismatch.
     */
    private static String canonicalize(String echoed, int index) {
        try {
            return MathExprIO.serialize(MathExpr.parse(echoed).tree());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                "cannot parse echoed expression at index " + index
                    + "\n  raw: " + echoed, e);
        }
    }

    private static long parseSeed(String[] args) {
        for (String a : args) {
            if (a.startsWith("--seed=")) {
                try {
                    return Long.parseLong(a.substring("--seed=".length()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("bad --seed value: " + a, e);
                }
            }
        }
        return Config.DEFAULT_SEED;
    }

    private static void logGeneration(
        int gen, List<Scored> scoredPrev, List<String> nextCanonical
    ) throws IOException {
        double best = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        String bestExpr = "?";
        for (Scored s : scoredPrev) {
            sum += s.fitness();
            if (s.fitness() > best) {
                best = s.fitness();
                bestExpr = s.expr();
            }
        }
        double mean = sum / scoredPrev.size();
        appendLog(String.format(
            "gen=%d prev_best=%.4f prev_mean=%.4f prev_top=%s next_top=%s",
            gen, best, mean, bestExpr, nextCanonical.get(0)));
    }

    private static void appendLog(String message) throws IOException {
        Path logFile = Config.evolutionLog();
        Path parent = logFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String line = Instant.now() + " " + message + "\n";
        Files.writeString(
            logFile,
            line,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND);
        LOG.fine(message);
    }
}
