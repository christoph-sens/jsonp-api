# Mutation testing of the Parsson JSONPath implementation

[PIT](https://pitest.org) 1.20.0 changes the compiled code of `org.eclipse.parsson.jsonpath` and
`org.eclipse.parsson.jsonpath.regex` (1,601 mutants with the default mutators) and runs the tests that cover each
mutant. A mutant that no test fails on *survived*: either the tests miss a behaviour, or the change does not alter
behaviour (an equivalent mutant) or only its speed or memory.

## Setup

| | |
|---|---|
| Code under test | Parsson fork, branch `jsonpath` (`org.eclipse.parsson.jsonpath`, `.regex`; 5,500 lines) |
| Tests | All of the fork's own tests in `org.eclipse.parsson.jsonpath.*`: 89 test classes including the Compliance Test Suite (`ComplianceTest`, `StreamingComplianceTest`, CTS commit pinned in the fork's pom), the I-Regexp tests and the streaming tests; 212,878 test executions in the run |
| PIT | `pitest-maven` 1.20.0, `pitest-junit5-plugin` 1.2.2, mutators `DEFAULTS`, 6 threads |
| Where the configuration lives | `evidence/mutation/pom.xml` compiles the fork's sources and tests (without `module-info.java`) and runs PIT on them, so the fork's own pom is untouched |
| Machine and time | Apple M5 Pro, Temurin 25.0.3, about 3.5 minutes per run |
| Johnzon | Not run (optional); its port has the same structure, so a run would give a similar picture, but that is an expectation, not a measurement |

Run it with (from `evidence/`, after the fork was built once so that the Compliance Test Suite is downloaded):

```bash
mvn -Pmutation -pl mutation test-compile org.pitest:pitest-maven:mutationCoverage -Dparsson.dir=/path/to/parsson
```

Reports: `mutation/target/pit-reports/index.html`. The survivors are listed in [survivors.csv](survivors.csv).

## Mutation score

"Before" is the fork's tests as they were on `jsonpath`; "after" adds `BoundaryTest`
([christoph-sens/parsson#9](https://github.com/christoph-sens/parsson/pull/9), tests only, no production change).
Score = killed (including timeouts) / generated.

| Package | Mutants | Killed before | Killed after | Score before | Score after |
|---|---:|---:|---:|---:|---:|
| `org.eclipse.parsson.jsonpath` | 1122 | 929 | 951 | 82.8 % | 84.8 % |
| `org.eclipse.parsson.jsonpath.regex` | 479 | 371 | 371 | 77.5 % | 77.5 % |
| **both** | 1601 | 1300 | 1322 | 81.2 % | **82.6 %** |

By class (after):

| Class | Mutants | Killed | Survived | No coverage | Score |
|---|---:|---:|---:|---:|---:|
| Parser | 347 | 292 | 47 | 8 | 84.1 % |
| StreamingMatcher | 339 | 299 | 38 | 2 | 88.2 % |
| Evaluator | 227 | 176 | 49 | 2 | 77.5 % |
| RegexParser | 142 | 118 | 24 | 0 | 83.1 % |
| LazyDfa | 123 | 69 | 48 | 6 | 56.1 % |
| Values | 100 | 89 | 9 | 2 | 89.0 % |
| CharSet | 51 | 45 | 4 | 2 | 88.2 % |
| RegexSimulation | 50 | 46 | 4 | 0 | 92.0 % |
| RegexProgram | 50 | 48 | 2 | 0 | 96.0 % |
| NodeImpl | 39 | 35 | 3 | 1 | 89.7 % |
| IRegexp | 29 | 22 | 7 | 0 | 75.9 % |
| Limits | 25 | 23 | 2 | 0 | 92.0 % |
| JsonPathMessages | 19 | 16 | 3 | 0 | 84.2 % |
| RegexCache | 17 | 10 | 6 | 1 | 58.8 % |
| DfaBudget | 14 | 11 | 3 | 0 | 78.6 % |
| Ast | 12 | 9 | 3 | 0 | 75.0 % |
| JsonPathImpl | 8 | 8 | 0 | 0 | 100.0 % |
| Patterns | 6 | 4 | 2 | 0 | 66.7 % |
| UnicodeCategories | 3 | 2 | 1 | 0 | 66.7 % |

By kind of mutation (after):

| Mutator | Mutants | Survived (incl. no coverage) | Score |
|---|---:|---:|---:|
| RemoveConditional_EQUAL_ELSE | 515 | 94 | 81.7 % |
| ConditionalsBoundary | 175 | 62 | 64.6 % |
| Math | 197 | 31 | 84.3 % |
| BooleanTrueReturnVals | 109 | 28 | 74.3 % |
| VoidMethodCall | 142 | 21 | 85.2 % |
| RemoveConditional_ORDER_ELSE | 175 | 19 | 89.1 % |
| PrimitiveReturns | 48 | 8 | 83.3 % |
| EmptyObjectReturnVals | 31 | 5 | 83.9 % |
| NullReturnVals | 105 | 5 | 95.2 % |
| InvertNegs | 8 | 4 | 50.0 % |
| BooleanFalseReturnVals | 74 | 2 | 97.3 % |
| Increments | 22 | 0 | 100.0 % |


## What the survivors are

279 of the 1,601 mutants survive (24 of them are on lines no test reaches). They were not triaged one by one; this is
from the classes and mutators above and from reading the code at a sample of them.

- **Performance and memory bounds with no effect on results** (the largest group). `LazyDfa` (56 %) and `RegexCache`
  (59 %) hold the lazily built DFA and its budget: mutants in `cache`, `reserve`, `release`, `closureState` change
  how much is cached or when it is flushed, not what matches. Likewise the interrupt check in `Evaluator.visit`
  (`before >>> SHIFT != after >>> SHIFT`) and `StreamingMatcher.containsAbsoluteQuery`, `threshold`: they decide
  how soon a limit or an optimization applies. A test can only see these through timing or memory, which the unit
  tests do not measure (the benchmarks and the fuzzing do).
- **Equivalent mutants.** For example in `Evaluator.slice`, `step >= 0` against `step > 0` (a step of 0 returns
  earlier), `ConditionalsBoundary` at clamps where both sides give the same bound, and `+ 1` or `- 1` in
  a bound that is clamped again afterwards. After the exhaustive slice grid of `BoundaryTest` against the RFC
  algorithm, the slice survivors left are of this kind.
- **Early-exit return values** (`BooleanTrueReturnVals`, 28 survivors, mostly in `select`, `slice`, `selectChildren`):
  the `true` that means "keep going" replacing a `false` that means "stop". A wrong value only changes how much work
  runs after a result limit or a sink stops; the result is the same.
- **Lexical edges of the parser** (`Parser.escape`, `isNameFirst`, `hex4`, `isFunctionNameChar`, `RegexParser.isNormalChar`):
  character ranges at their end points. `BoundaryTest` now covers the name and escape ranges of the query grammar;
  `isFunctionNameChar` and the I-Regexp character classes in `RegexParser` are still open and are the most useful
  place for the next tests.
- **Messages and `toString`** (`JsonPathMessages`, `NodeImpl.toString`): text, not behaviour.

## Proposed minimum

PIT has no notion of "enough"; a floor stops the score from falling when code is added. Proposal, **for Christoph to
confirm**, the achieved score rounded down:

| Scope | Achieved | Proposed minimum |
|---|---:|---:|
| `org.eclipse.parsson.jsonpath` | 84.8 % | 84 % |
| `org.eclipse.parsson.jsonpath.regex` | 77.5 % | 77 % |
| both | 82.6 % | 82 % |

If a gate is added later, `pitest-maven` has `mutationThreshold`; it should go on the whole run and not on a changed
subset, since mutants of unchanged code react to changes in the tests.

## Limits

- One mutator set (`DEFAULTS`); the `STRONGER` and `ALL` sets generate more mutants and lower the score.
- The score depends on the tests of the fork only. The TCK (separate repository) is not part of the run.
- PIT's own report marks 24 mutants as having no coverage; they are in `survivors.csv` with status `NO_COVERAGE`.
