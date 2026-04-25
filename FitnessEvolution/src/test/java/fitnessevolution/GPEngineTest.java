package fitnessevolution;

import io.jenetics.Genotype;
import io.jenetics.ext.util.TreeNode;
import io.jenetics.prog.ProgramGene;
import io.jenetics.prog.op.Op;
import io.jenetics.prog.op.Var;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GPEngineTest {

    private ISeq<Var<Double>> vars;
    private ISeq<Op<Double>> terminals;
    private TreeNode<Op<Double>> template;

    private static final String TEMPLATE_EXPR =
        "SUM_DAMAGE_DEALT - 2.0*DAMAGE_RECEIVED - MONSTERS_REMAINING "
            + "- 0.1*SUM_MONSTER_HEALTH + POWERS_PLAYED";

    @BeforeEach
    void setUp() throws IOException {
        RandomRegistry.random(new Random(7));
        vars = FeatureBankLoader.loadVars(Path.of("ipc/FeatureBank.txt"));
        terminals = OpSet.terminals(vars);
        template = MathExprIO.parseExpression(TEMPLATE_EXPR);
    }

    @Test
    void initialPopulationHasConfiguredSize() {
        GPEngine engine = new GPEngine(OpSet.OPS, terminals, 7, 20, 1L);
        List<Genotype<ProgramGene<Double>>> pop = engine.initialPopulation(template, vars);
        assertEquals(20, pop.size());
    }

    @Test
    void firstIndividualIsTheTemplate() {
        GPEngine engine = new GPEngine(OpSet.OPS, terminals, 7, 20, 1L);
        List<Genotype<ProgramGene<Double>>> pop = engine.initialPopulation(template, vars);
        String seed = MathExprIO.serialize(pop.get(0).gene().toTreeNode());
        assertTrue(seed.contains("SUM_DAMAGE_DEALT"));
        assertTrue(seed.contains("DAMAGE_RECEIVED"));
    }

    @Test
    void stepPreservesPopulationSize() {
        GPEngine engine = new GPEngine(OpSet.OPS, terminals, 7, 20, 1L);
        List<Genotype<ProgramGene<Double>>> pop = engine.initialPopulation(template, vars);

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < pop.size(); i++) {
            scored.add(new Scored(pop.get(i), (double) (pop.size() - i)));
        }

        List<Genotype<ProgramGene<Double>>> next = engine.step(scored);
        assertEquals(pop.size(), next.size());
    }

    @Test
    void stepPreservesTopElite() {
        GPEngine engine = new GPEngine(OpSet.OPS, terminals, 7, 20, 1L);
        List<Genotype<ProgramGene<Double>>> pop = engine.initialPopulation(template, vars);

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < pop.size(); i++) {
            scored.add(new Scored(pop.get(i), (double) (pop.size() - i)));
        }

        String bestExpr = MathExprIO.serialize(
            scored.get(0).genotype().gene().toTreeNode());

        List<Genotype<ProgramGene<Double>>> next = engine.step(scored);
        String nextFirst = MathExprIO.serialize(next.get(0).gene().toTreeNode());
        assertEquals(bestExpr, nextFirst,
            "top-fitness individual should carry over as first elite");
    }

    @Test
    void stepRejectsMismatchedSize() {
        GPEngine engine = new GPEngine(OpSet.OPS, terminals, 7, 20, 1L);
        List<Scored> tooFew = List.of();
        assertThrows(IllegalArgumentException.class, () -> engine.step(tooFew));
    }

    /**
     * With a strongly dominant individual, tournament selection concentrates on
     * the leader and crossover between two clones produces a clone child. The
     * dedupe pass must guarantee no duplicate canonical expressions ship from
     * step.
     */
    @Test
    void stepProducesNoDuplicateCanonicalExpressions() {
        GPEngine engine = new GPEngine(OpSet.OPS, terminals, 7, 20, 1L);
        List<Genotype<ProgramGene<Double>>> pop = engine.initialPopulation(template, vars);

        for (int gen = 0; gen < 5; gen++) {
            List<Scored> scored = new ArrayList<>();
            scored.add(new Scored(pop.get(0), 1_000.0));
            for (int i = 1; i < pop.size(); i++) {
                scored.add(new Scored(pop.get(i), 1.0));
            }
            pop = engine.step(scored);

            Set<String> seen = new HashSet<>();
            for (Genotype<ProgramGene<Double>> g : pop) {
                String key = MathExprIO.serialize(g.gene().toTreeNode());
                assertTrue(seen.add(key),
                    "duplicate canonical expression at gen " + gen + ": " + key);
            }
        }
    }
}
