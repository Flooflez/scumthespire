# FitnessEvolution — Mod Integration

Everything you need to wire `BattleAiMod` into the GP driver. ~10 minutes.

Branch: **`2-level-GP-Jenetics`** on `Flooflez/scumthespire`.

## 1. Build

From `FitnessEvolution/`:

```bash
mvn test      # expect: Tests run: 29, Failures: 0  →  BUILD SUCCESS
mvn package   # produces target/FitnessEvolution.jar
```

Requires **Java 17+**. `java -version` to check.

## 2. How the handshake works

One call per generation. Your mod owns the loop.

```
┌─ (first call) ─────────────────────────────┐
│ java -jar target/FitnessEvolution.jar      │   we write ipc/JeneticsOutput.txt
└────────────────────────────────────────────┘   we create state/evolution_state.txt
                       ▼
┌─ your mod, for each gen ───────────────────┐
│ 1. read  ipc/JeneticsOutput.txt (20 lines) │
│ 2. evaluate each expression in a battle    │
│ 3. write ipc/ModOutput.txt (see format)    │
│ 4. java -jar target/FitnessEvolution.jar   │   we step, overwrite JeneticsOutput
└────────────────────────────────────────────┘
                  ▼ (loop)
             ▼ (when done)
┌─ stop ─────────────────────────────────────┐
│ echo END > ipc/ModOutput.txt               │
│ java -jar target/FitnessEvolution.jar      │   we log + exit 0
└────────────────────────────────────────────┘
```

**Working directory matters** — launch the jar from `FitnessEvolution/` so
relative paths (`ipc/`, `state/`, `logs/`) resolve correctly.

## 3. File formats

**`ipc/JeneticsOutput.txt`** (we write, you read): 20 lines, one MathExpr
each. Operators `+ - * /` only. Variable names come from
`ipc/FeatureBank.txt` (edit that file to add/remove features).

**`ipc/ModOutput.txt`** (you write): 20 FITNESS/expression pairs, **same
order** you read them, **or** the literal `END`.

```
FITNESS=12.5
<echo the expression from line 1 of JeneticsOutput.txt>
FITNESS=-3.0
<echo the expression from line 2>
...
```

- Echo can be the raw line you read (safest) or a
  `MathExpr.parse(...).toString()` round-trip — we re-canonicalize before
  comparing, both work.
- FITNESS **must be finite**. Map `NaN`/`±Infinity` → `0` on your side.

## 4. Exit codes

| Exit | Meaning | What to do |
|---:|---|---|
| `0` | success | read `ipc/JeneticsOutput.txt` |
| `2` | bad input | read stderr — it names the file + line |
| `3` | IO error | check disk / permissions |
| `4` | bug on our side | send me the stderr stack trace |

## 5. Common stderr messages

- **`state file exists but ipc/ModOutput.txt is missing`** — you called
  the jar twice without writing ModOutput between calls.
- **`expression mismatch at index N`** — echoed expression isn't
  semantically equivalent to the one at position N. Don't reorder; don't
  simplify (`x + 0` ≠ `x` for us).
- **`ModOutput.txt has K FITNESS pairs but state expects 20`** — wrong
  number of pairs.
- **`non-finite fitness 'NaN'`** — clamp NaN/Infinity to `0` before
  writing.

## 6. Reset / Debug

| Need | Do |
|---|---|
| Start over from gen 0 | delete `state/evolution_state.txt` |
| See per-gen summaries | `cat logs/evolution_log.txt` |
| Smoke-test engine without your mod | `mvn -q exec:java -Dexec.mainClass=fitnessevolution.OfflineMockRun` (10 gens, synthetic fitness, prints top-5 per gen) |
| Full docs | [`README.md`](README.md) |

## 7. First-run sanity check (before wiring to your mod)

```bash
# gen 0
java -jar target/FitnessEvolution.jar

# fake a mod by echoing expressions back with dummy fitnesses
python3 -c "
lines = open('ipc/JeneticsOutput.txt').read().splitlines()
open('ipc/ModOutput.txt','w').write(
    ''.join(f'FITNESS={-i*0.5}\n{e}\n' for i,e in enumerate(lines)))
"

# gen 1
java -jar target/FitnessEvolution.jar   # should print "Stepped to generation 1"

# stop
echo END > ipc/ModOutput.txt
java -jar target/FitnessEvolution.jar   # should print "END received"
```

If all three calls return exit 0, you're green — plug the same steps into
your mod.

---

Ping me on anything that bites.
