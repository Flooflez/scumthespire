# FitnessEvolution — Roadmap

External Java program using Jenetics to evolve Slay the Spire fitness
expressions as MathExpr trees. Communicates with the BattleAiMod mod via
two flat text files in `ipc/`.

## Locked decisions

| | |
|---|---|
| Stack | Java 17 + Jenetics 7.2.0 (`io.jenetics.prog`) |
| Location | `FinalProject/scumthespire/FitnessEvolution/` |
| Branch | `2-level-GP-Jenetics` (fork: RArgu/scumthespire) |
| Operators | `+`, `-`, `*`, protected `/` (`div(a,0)=1.0`) |
| Constants | `EphemeralConst` in `[-5.0, 5.0]` |
| Max tree depth | 7 |
| Population size | read from ModOutput.txt (dummy default 20) |
| Selection | tournament k=3 over top 50% |
| Elitism | top 10% (2 of 20) copied unchanged |
| Offspring rates | Jenetics defaults (crossover 0.7, mutation 0.1) |
| Init | 1 × `init_template.txt` + 19 × ramped half-and-half |
| IPC | overwrite, READY flag, 1s poll, `END` stops run |
| Invalid handling | protected division; no rejection |
| Logging | human-readable `logs/evolution_log.txt` |

## Algorithm (per generation)

1. Sort population by fitness desc.
2. Copy top 10% to next gen (elites).
3. Parent pool = top 50%.
4. Generate 18 offspring: tournament-select 2 parents (k=3) from pool,
   crossover (p=0.7) then mutate (p=0.1), keep 1 child.
5. Next gen = elites + offspring.

## File formats

### `ipc/FeatureBank.txt`
One feature name per line. Order = variable index used in MathExpr.

### `ipc/init_template.txt`
Single-line MathExpr string. Used as generation-0 seed.

### `ipc/JeneticsOutput.txt` (written by us)
```
READY
<MathExpr 1>
<MathExpr 2>
...
```

### `ipc/ModOutput.txt` (read by us)
```
READY
FITNESS=<double>
<MathExpr matching one from JeneticsOutput>
FITNESS=<double>
<MathExpr>
...
```
`END` in place of `READY` stops the run.

## Checklist

### Phase 0 — Scaffold
- [x] Create `FitnessEvolution/` + pom.xml (Jenetics 7.2.0, Java 17)
- [x] `ipc/` placeholders: FeatureBank.txt, init_template.txt, JeneticsOutput.txt, ModOutput.txt
- [x] `logs/` directory
- [x] `.gitignore` for `target/`, IDE files
- [x] ROADMAP.md (this file)
- [ ] Commit on `2-level-GP-Jenetics`

### Phase 1 — Core GP primitives
- [ ] `FeatureBankLoader` — parse FeatureBank.txt → ordered feature list
- [ ] `ProtectedDiv` — custom `Op<Double>` returning 1.0 on |b| < eps
- [ ] `OpSet` — static registry of `+, -, *, protectedDiv` + ephemeral const
- [ ] `MathExprIO` — parse init_template.txt to `TreeNode<Op<Double>>`;
      serialize any tree via `MathExpr.toString()`
- [ ] Unit tests: protected div, MathExpr round-trip, FeatureBank parse

### Phase 2 — Evolution engine (mocked input)
- [ ] `Population.initial(templatePath, featureBank, size)` — 1 template + (size-1) ramped H&H
- [ ] `Engine` wrapper: tournament(k=3) on top 50%, elitism=2, maxDepth=7
- [ ] `step(List<(expr, fitness)>)` → next generation
- [ ] Serialize population → JeneticsOutput.txt with READY header
- [ ] Parser for ModOutput.txt → `List<(expr, fitness)>`

### Phase 3 — IPC loop
- [ ] `Main` — read init template, write initial JeneticsOutput.txt, poll ModOutput.txt (1s)
- [ ] On READY: parse, evolve, write next JeneticsOutput.txt, clear ModOutput READY
- [ ] On END: flush log, exit clean
- [ ] Log every generation to `logs/evolution_log.txt`

### Phase 4 — Mock harness
- [ ] `MockMod` main class: reads JeneticsOutput.txt, writes ModOutput.txt
      with a toy fitness (e.g., prefer shorter trees)
- [ ] End-to-end dry run: FitnessEvolution ↔ MockMod for ~10 generations
- [ ] Verify log shows tree-size convergence

### Phase 5 — Handoff
- [ ] Protocol doc for the mod teammate (handshake, formats, edge cases)
- [ ] First live run with real mod
