# FitnessEvolution

External Java program that evolves Slay the Spire fitness (evaluator)
expressions as [MathExpr](https://jenetics.io/javadoc/jenetics.prog/7.2/io/jenetics/prog/op/MathExpr.html)
trees using [Jenetics 7.2.0](https://jenetics.io/) genetic programming.

Integrates with `BattleAiMod` via a **mod-driven one-shot** handshake:
the mod writes fitnesses to a file, runs our jar once, reads the next
generation, repeats. No long-lived process, no polling.

**Status:** engine + IPC done, awaiting first live run with the mod.

## Requirements

- Java 17+
- Maven 3.8+
- Nothing else — Jenetics pulls in via Maven Central.

## Folder structure

```
FitnessEvolution/
├── README.md                 -- this file
├── pom.xml                   -- Maven build (Java 17, Jenetics 7.2.0, shade → Main)
├── .gitignore
│
├── ipc/                      -- file-based IPC with the mod
│   ├── FeatureBank.txt       -- (TRACKED) ordered feature names; edit to match what the mod reports
│   ├── init_template.txt     -- (TRACKED) gen-0 seed expression; edit to change the hand-tuned starting point
│   ├── JeneticsOutput.txt    -- (GITIGNORED, created at runtime) we write: one MathExpr per line
│   └── ModOutput.txt         -- (GITIGNORED, created at runtime) the mod writes: FITNESS=<x>/<expr> pairs, or END
│
├── state/                    -- (gitignored) persisted between invocations
│   └── evolution_state.txt   -- generation, seed, canonical expressions
│
├── logs/                     -- (gitignored, .gitkeep preserves dir)
│   └── evolution_log.txt     -- one human-readable line per invocation
│
├── src/main/java/fitnessevolution/
│   ├── Main.java             -- one-shot CLI: gen-0 bootstrap OR gen N+1 step
│   ├── Config.java           -- central paths + population tunables
│   ├── FeatureBankLoader.java-- parse FeatureBank.txt → List<String> / ISeq<Var<Double>>
│   ├── OpSet.java            -- operator set (+,-,*,/) + ephemeral const in [-5,5]
│   ├── MathExprIO.java       -- parseTemplate() / canonical serialize()
│   ├── Scored.java           -- record (Genotype, fitness) with expr() helper
│   ├── GPEngine.java         -- GP driver: initial, rehydrate, step (elitism, tournament, mutate, crossover)
│   ├── EvolutionState.java   -- record (generation, seed, expressions) with save/load
│   ├── JeneticsIO.java       -- writePopulation(), readModOutput()
│   └── OfflineMockRun.java   -- dev harness (see "Debugging without the mod" below)
│
├── src/test/java/fitnessevolution/
│   └── *Test.java            -- 29 unit + integration tests (run with `mvn test`)
│
└── target/                   -- (gitignored) Maven output + shaded jar
```

### Files you do not see in a fresh clone

Three files live inside this directory but are **not committed** — they
are created locally on first run (or by your mod) and are gitignored so
they never sync across machines:

| Path | Who creates it | When |
|---|---|---|
| `ipc/JeneticsOutput.txt` | our jar | first `java -jar` call |
| `ipc/ModOutput.txt`      | your mod | before every non-first `java -jar` call |
| `state/evolution_state.txt` | our jar | first `java -jar` call (auto-created dir + file) |
| `logs/evolution_log.txt` | our jar | first `java -jar` call |

All four are written to paths **relative to the current working
directory** (specifically to `ipc/`, `state/`, `logs/` inside whatever
directory you launch the jar from). Launch from
`FinalProject/scumthespire/FitnessEvolution/` and everything lands where
the tree above shows.

## Important pieces at a glance

- **`ipc/FeatureBank.txt`** — the feature-name contract. Each line is a
  variable name that may appear in any MathExpr we emit; line order is
  the index the mod passes to `MathExpr.eval(double[] features)`. Edit
  this file to add/remove features exposed to evolution.
- **`ipc/init_template.txt`** — the gen-0 seed expression (1 of 20
  individuals in the first population). Edit this file to change the
  hand-tuned starting point.
- **`src/main/java/fitnessevolution/Main.java`** — the CLI entry point.
  Reads the state file to decide whether to bootstrap gen 0 or step
  forward one generation; everything else chains through here.
- **`src/main/java/fitnessevolution/Config.java`** — all paths and
  run-level tunables (`POPULATION_SIZE`, `MAX_DEPTH`, `DEFAULT_SEED`).
  Change anything here rather than hunting through the rest of the code.
- **`src/main/java/fitnessevolution/GPEngine.java`** — the GP algorithm:
  elitism %, tournament k, crossover/mutation rates, parent-pool size.

## Teammate integration (for the mod author)

This is the contract your mod has to satisfy. If you only read one
section, read this one.

### Run-book (per generation)

1. **One-time setup**: check `ipc/FeatureBank.txt` lists every feature
   your mod will report, one per line, in the order your mod hands them
   to MathExpr. Check `ipc/init_template.txt` contains a single-line
   seed expression using only features from the bank. Both files ship
   with sensible defaults — edit only if you add features or want a
   different seed.
2. **Build the jar once**: from this directory,
   ```bash
   mvn -q package
   ```
   produces `target/FitnessEvolution.jar`.
3. **First call** (no `state/evolution_state.txt` present yet — this is
   the case on a fresh clone):
   ```bash
   java -jar target/FitnessEvolution.jar
   ```
   We write 20 canonical MathExpr strings to `ipc/JeneticsOutput.txt`
   and create `state/evolution_state.txt`. Exit code 0.
4. **Read `ipc/JeneticsOutput.txt`** line-by-line — each non-blank line
   is one MathExpr. Evaluate each in a battle; record its fitness.
5. **Write `ipc/ModOutput.txt`** with 20 FITNESS/expression pairs **in
   the same order** you read them:
   ```
   FITNESS=<double>
   <echo the expression verbatim>
   FITNESS=<double>
   <echo expression>
   ...
   ```
6. **Call the jar again**:
   ```bash
   java -jar target/FitnessEvolution.jar
   ```
   We parse ModOutput, step the engine, overwrite JeneticsOutput with
   gen N+1, update state. Exit code 0.
7. Loop 4→6.
8. **To stop cleanly**: put `END` on the first non-blank line of
   `ipc/ModOutput.txt` and call the jar once more. We log + exit 0
   without evolving. State is preserved — you can overwrite ModOutput
   with real pairs later and keep going from the same generation.

To restart from scratch, delete `state/evolution_state.txt`.

### File formats

**`ipc/JeneticsOutput.txt`** (we write): one canonical MathExpr per
non-blank line, exactly 20 lines. Operators limited to `+ - * /`,
variables by name, numeric literals for constants. Example:
```
SUM_DAMAGE_DEALT - 2.0*DAMAGE_RECEIVED - MONSTERS_REMAINING
(DAMAGE_RECEIVED*0.197 + POWERS_PLAYED/DAMAGE_RECEIVED)*(...)
...
```

**`ipc/ModOutput.txt`** (you write): FITNESS/expr pairs OR the literal
`END`. Blank lines between pairs are ignored. The echoed expression
must be **semantically equivalent** to the one we sent — we re-run
your echo through the same canonicalization pipeline
(`MathExpr.parse → canonical serialize`) before comparison, so e.g.
`-2.897` and `neg(2.897)` both match. Echoing the raw string you
received is always safe. FITNESS values must be finite — non-finite
evaluations (NaN, ±Infinity) are rejected; map them to a concrete
sentinel like `0` on your side first.

### Seeding and determinism

Pass `--seed=N` on the **first** call to override the default base seed
(42). On subsequent calls the persisted seed wins; a mismatched
`--seed=` prints a warning and is ignored. Runs are reproducible given
the same base seed and the same sequence of FITNESS values — we derive
a per-generation seed as `baseSeed + generation` and apply it to
Jenetics' `RandomRegistry` on every invocation.

### Error modes

| Exit | Meaning | What to check |
|---:|---|---|
| `0` | success | — |
| `2` | user/config error | state exists but ModOutput.txt missing; expression mismatch; bad `--seed=` |
| `3` | IO error | filesystem permissions / disk |
| `4` | internal error | unexpected exception — please share the stderr stack trace |

Common gotchas:

- **"state file exists but ipc/ModOutput.txt is missing"** — you called
  the jar twice without writing ModOutput between calls. Write it and
  retry.
- **"ModOutput.txt has K FITNESS pairs but state expects 20"** — your
  file has the wrong number of pairs. Check for missing newlines or
  blank trailing lines that might confuse your writer.
- **"expression mismatch at index i"** — the expression you echoed on
  the expression line of pair `i` isn't semantically equivalent to
  what we sent (after canonicalization). Don't reorder pairs, don't
  simplify expressions (`x + 0 → x` is not a match), and make sure
  you're echoing the one that paired with the fitness above it.
- **"non-finite fitness"** — your mod handed us NaN or ±Infinity. Map
  non-finite evaluations to a concrete value (e.g. `0`) before
  writing the FITNESS= line.

## Debugging without the mod

`OfflineMockRun` runs the GP engine **entirely in-process** for 10
generations against a synthetic fitness function
(target value 10.0 at features `[1,1,1,1,1]`, fitness `= -|eval - 10|`;
non-finite evaluations → `-1000`). It does not touch ModOutput.txt /
JeneticsOutput.txt / state files — purely a smoke test for the engine.

```bash
mvn -q exec:java -Dexec.mainClass=fitnessevolution.OfflineMockRun
```

Prints the top-5 individuals per generation and the best-ever
individual at the end. Useful for confirming crossover/mutation are
producing sensible offspring without needing the mod running.

## Running the tests

```bash
mvn test
```

29 tests, all green. Covers feature-bank parsing, operator set,
MathExpr round-trip, GP engine invariants, state file round-trip,
JeneticsIO malformed-input handling, and a 3-generation Main
integration cycle in a tempdir.
