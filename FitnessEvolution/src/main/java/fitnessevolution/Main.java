package fitnessevolution;

import io.jenetics.Genotype;
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
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Generation-agnostic CLI entry point.
 *
 * <p>On every invocation:
 * <ol>
 *   <li>Read {@code ipc/ModOutput.txt} into a list of scored expressions.
 *       {@code END} as the first non-blank line is a clean-exit sentinel.</li>
 *   <li>Let {@code K = results.size()}. If {@code K > POP}: truncate to the
 *       top POP by fitness (warn). If {@code K < POP}: emit the K expressions
 *       unchanged plus {@code (POP - K)} freshly-randomised trees so the mod
 *       can score a full population next round. If {@code K == POP}: run one
 *       GP step and emit the new generation.</li>
 *   <li>Write the resulting POP expressions to {@code ipc/JeneticsOutput.txt}.</li>
 *   <li>Increment the per-run step counter so seed derivation advances.</li>
 * </ol>
 *
 * <p>Reproducibility: callers pass {@code --run-id=<id>} to isolate concurrent
 * runs; we keep a tiny {@code runs/<id>.step} file with the integer step count.
 * Effective RNG seed is {@code baseSeed + step}. Delete the step file to
 * restart the RNG sequence for that run.
 *
 * <p>Exit codes: 0 ok, 2 user/config error, 3 IO error, 4 internal.
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {}

    public static void main(String[] args) {
        try {
            CliArgs cli = parseArgs(args);
            Path stepFile = Config.runStepFile(cli.runId);
            int step = RunCounter.load(stepFile);
            long effectiveSeed = cli.baseSeed + step;
            RandomRegistry.random(new Random(effectiveSeed));

            if (!Files.exists(Config.modOutput())) {
                throw new IllegalArgumentException(
                    Config.modOutput() + " does not exist — the mod must write "
                        + "it before invoking this jar");
            }
            List<JeneticsIO.ModResult> results =
                JeneticsIO.readModOutput(Config.modOutput());
            if (results == null) {                       // END sentinel
                appendLog("run=" + cli.runId + " step=" + step + " END received");
                System.out.println("END received — run stopped.");
                return;                                  // no counter increment
            }

            List<String> outputExprs = produceNextPopulation(results, effectiveSeed);
            Files.createDirectories(Config.jeneticsOutput().getParent());
            Files.writeString(
                Config.jeneticsOutput(),
                String.join("\n", outputExprs) + "\n");

            int nextStep = RunCounter.loadAndIncrement(stepFile);
            appendLog(String.format(
                "run=%s step=%d input_k=%d output_n=%d seed=%d",
                cli.runId, nextStep, results.size(), outputExprs.size(), effectiveSeed));

            System.out.println("run=" + cli.runId + " step=" + nextStep
                + " · wrote " + outputExprs.size() + " expressions to "
                + Config.jeneticsOutput());
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

    // ---- core dispatch -----------------------------------------------------

    private static List<String> produceNextPopulation(
        List<JeneticsIO.ModResult> results, long effectiveSeed
    ) throws IOException {
        int k = results.size();
        int pop = Config.POPULATION_SIZE;
        if (k == 0) {
            throw new IllegalArgumentException(
                "ModOutput.txt has 0 scored expressions — cannot evolve from empty input");
        }

        ISeq<Var<Double>> vars = FeatureBankLoader.loadVars(Config.featureBank());
        ISeq<Op<Double>> terminals = OpSet.terminals(vars);
        GPEngine engine = new GPEngine(
            OpSet.OPS, terminals, Config.MAX_DEPTH, pop, effectiveSeed);

        if (k > pop) {
            System.err.println(
                "warning: ModOutput.txt has " + k + " > " + pop
                    + " entries; truncating to top " + pop + " by fitness");
            results = topNByFitness(results, pop);
            k = pop;
        }

        if (k < pop) {
            return warmupFill(results, engine, vars);
        }
        return evolveStep(results, engine, vars);
    }

    // ---- K < POP: warmup ---------------------------------------------------

    private static List<String> warmupFill(
        List<JeneticsIO.ModResult> results, GPEngine engine, ISeq<Var<Double>> vars
    ) {
        int fillCount = Config.POPULATION_SIZE - results.size();
        List<String> expressions = new ArrayList<>(Config.POPULATION_SIZE);

        // Re-parse + re-serialize to canonicalise anything the mod wrote.
        for (JeneticsIO.ModResult r : results) {
            expressions.add(canonicalise(r.expression()));
        }
        for (Genotype<ProgramGene<Double>> g : engine.randomTrees(fillCount)) {
            expressions.add(MathExprIO.serialize(g.gene().toTreeNode()));
        }
        return expressions;
    }

    // ---- K == POP: one GP step --------------------------------------------

    private static List<String> evolveStep(
        List<JeneticsIO.ModResult> results, GPEngine engine, ISeq<Var<Double>> vars
    ) {
        List<String> canonicalIn = new ArrayList<>(results.size());
        for (JeneticsIO.ModResult r : results) {
            canonicalIn.add(canonicalise(r.expression()));
        }
        List<Genotype<ProgramGene<Double>>> population =
            engine.rehydratePopulation(canonicalIn, vars);

        List<Scored> scored = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            scored.add(new Scored(population.get(i), results.get(i).fitness()));
        }

        List<Genotype<ProgramGene<Double>>> next = engine.step(scored);
        List<String> out = new ArrayList<>(next.size());
        for (Genotype<ProgramGene<Double>> g : next) {
            out.add(MathExprIO.serialize(g.gene().toTreeNode()));
        }
        return out;
    }

    // ---- helpers -----------------------------------------------------------

    private static String canonicalise(String rawExpr) {
        try {
            return MathExprIO.serialize(MathExpr.parse(rawExpr).tree());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                "cannot parse expression from ModOutput.txt: " + rawExpr, e);
        }
    }

    private static List<JeneticsIO.ModResult> topNByFitness(
        List<JeneticsIO.ModResult> results, int n
    ) {
        List<JeneticsIO.ModResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingDouble(JeneticsIO.ModResult::fitness).reversed());
        return new ArrayList<>(sorted.subList(0, n));
    }

    private record CliArgs(String runId, long baseSeed) {}

    private static CliArgs parseArgs(String[] args) {
        String runId = Config.DEFAULT_RUN_ID;
        long baseSeed = Config.DEFAULT_SEED;
        for (String a : args) {
            if (a.startsWith("--run-id=")) {
                runId = a.substring("--run-id=".length()).trim();
                if (runId.isEmpty()) {
                    throw new IllegalArgumentException("--run-id value cannot be empty");
                }
            } else if (a.startsWith("--seed=")) {
                try {
                    baseSeed = Long.parseLong(a.substring("--seed=".length()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("bad --seed value: " + a, e);
                }
            }
        }
        return new CliArgs(runId, baseSeed);
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
