# Fuzzing: Parsson and Johnzon

[Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) 0.30.0 fuzzes the JSONPath implementations of the Parsson
fork and the Johnzon fork (`jsonpath` branches) through the proposed `jakarta.json.JsonPath` API. Both
`JsonProviderImpl`s are instantiated directly, so every input runs through both. Coverage feedback comes from the
implementations (`jazzer.instrument=org.eclipse.parsson.**,org.apache.johnzon.core.**`).

## Fuzz targets

| Target | Input | What runs |
|---|---|---|
| `compileAnyQuery` | any string, up to 1,000 bytes | `createPath(query, limits)` |
| `queryAnyDocument` | two strings: query (500 bytes) and JSON text (5,000 bytes) | reader, `query()`, `matcher()` over a parser of the same text |
| `queryGeneratedDocument` | `FuzzedDataProvider`: a generator builds a query and a JSON text from the bytes | as `queryAnyDocument`; about 97 % of the generated queries are valid, so the inputs reach filters, functions, the matcher's buffering and the limits |
| `matchAnyRegularExpression` | two strings: pattern (500 bytes) and subject (5,000 bytes) | `match()` and `search()`, with the pattern as a literal in the query and as a value of the document |

Seeds: the 715 cases of the JSONPath Compliance Test Suite as vendored in the TCK (queries, and query plus document for
the valid ones), the regression cases in `JsonPathFuzzTest.regressionCases()`, 13 regular expressions (Treetrail's,
plus `(a{1000}){1000}`, `\d`, `(?i)a`), and the crash files under
`src/test/resources/io/github/christophsens/evidence/fuzz/JsonPathFuzzTestInputs/`.

The generator (`Generator`) builds documents of up to 400 values with numbers such as `-0`, `1e308`, `1E-400`, `1e1000000`
and 30-digit integers, names and strings with quotes, backslashes, U+0000, astral characters and regex syntax, duplicate
member names, and in one input of 16 a chain of up to 1,100 nested arrays or objects. Queries have all selector kinds
in child and descendant segments, indices and slices with negative, huge and (rarely) invalid values, filters with
`&&`, `||`, `!`, parentheses, comparisons, existence tests, absolute and relative queries, and the five functions.

## What counts as a finding

For each provider and input (`Checks`):

- Only documented exceptions: `JsonException` from `createPath`, from `query()` (a limit) and from the matcher
  (invalid JSON or a limit). Anything else, including `StackOverflowError` and `OutOfMemoryError`, is a finding.
- `toString()` returns the query string; `getBuffering()` is not null; `matcher()` throws if and only if the class is
  `UNBOUNDED`.
- For each node of `query()` (the first 1,000): the value is not null, the Normalized Path starts with `$`, the JSON
  Pointer resolves to the node's value (a primitive document has only the root), and the first 20 Normalized Paths
  select exactly their node again when used as queries (RFC 9535, section 2.7).
- The matcher reports matches in document order (stream offsets never decrease), repeated `getValue()` calls return
  equal values, and, for documents without duplicate names and results up to 10,000 nodes, it reports the same nodes as
  `query()` (as a multiset) with the same values for the matches whose value the input asks to take.
- `match()`/`search()`: neither throws; a literal pattern and the same pattern from the document give the same result;
  a match implies a search hit; Parsson and Johnzon agree.
- Time: the implementation (compilation, `query()`, the matcher; not the checks above) takes at most 5 s per input and
  provider, with Jazzer's instrumentation. Limits for the run: `maxVisitedNodes` 200,000, `maxResultSize` 100,000,
  `maxBufferedNodes` 1,000,000, the others at their defaults (depth 1,000, nesting 64). The visited-nodes limit is what
  bounds evaluation time, so the time check catches work that the limit does not account for.

Results are not compared between Parsson and Johnzon except for regular expressions; the differential tests
([../differential/RESULTS.md](../differential/RESULTS.md)) do that.

## Runs

Local runs in fuzzing mode (`-Pfuzz`), JDK 25 (Temurin 25.0.3), macOS, one target at a time, 15 minutes per target
unless a finding ended it earlier; each run started from the seeds, without a corpus from earlier runs. About 2.8 hours
in total.

Final runs, against the `jsonpath` branches with all fixes (Parsson with #10 and #11, Johnzon with #5) and the final
harness, none with a finding:

| Target | Time | Inputs | Inputs/s | Coverage (edges) |
|---|---|---|---|---|
| `compileAnyQuery` | 15 min | 20,317,085 | 22,549 | 1,821 |
| `queryAnyDocument` | 15 min | 2,722,527 | 3,021 | 4,904 |
| `queryGeneratedDocument` | 15 min | 60,784 | 67 | 4,380 |
| `matchAnyRegularExpression` | 15 min | 1,239,688 | 1,375 | 1,765 |

`queryGeneratedDocument` is slow per input on purpose: its documents and queries are the ones that make the evaluator
and the matcher work (deep chains, nested descendant segments and filters, limits reached). Its coverage is close to
that of `queryAnyDocument` after a fraction of the inputs.

Earlier runs, which led to the findings below:

| Run | Target | Ended by |
|---|---|---|
| 1 | `compileAnyQuery` | 15 min, 19,917,597 inputs, no finding |
| 1 | `queryAnyDocument` | a harness issue (fast-throw exception without a stack trace) after seconds |
| 1 | `queryGeneratedDocument` | finding 1 after 53,102 inputs (372 s for one input) |
| 2 | `queryAnyDocument` | finding 2 after 6,374 inputs (no progress for 21 minutes) |
| 3 to 5 | `queryAnyDocument` | parser defects outside JSONPath (table below), each within a minute |

CI: the [Fuzz workflow](../../.github/workflows/fuzz.yml) fuzzes each target for 2 minutes when a push or pull request
to `evidence` changes the fuzz module, and for longer when started by hand
(`gh workflow run fuzz.yml --ref evidence -f duration=1h`).

## Findings in JSONPath

### 1. Streaming matcher: work for undecided routes not counted (Parsson and Johnzon, fixed)

Found by `queryGeneratedDocument` in the first run, after about 53,000 inputs: Parsson took 372 s (instrumented) for

```
query:    $..[?@..*]..[-5]..a.a
document: about 270 nested arrays around false (546 bytes)
```

`query()` returns at once; the matcher (class `BOUNDED`) was slow, in both implementations, which share the design.
Without instrumentation and with `maxVisitedNodes` large:

| Nested arrays | Visits | Time before | Time after the fix |
|---|---|---|---|
| 40 | 939 | 1 ms | 0 ms |
| 100 | 5,349 | 148 ms | 5 ms |
| 140 | 10,289 | 839 ms | 15 ms |
| 999 | about 500,000 | hours (extrapolated, time grew with depth⁵) | `JsonException` (visit limit) after 7 s with the default limits |

Cause: with nested filters and indices counted from the end that are not decided yet, a node can carry a number of
items (routes under pending decisions) that grows with the square of its depth. Carrying them to each child was not
counted as visits, and merging equal items searched the list linearly. So visits grew with depth², time with depth⁵,
and `maxVisitedNodes` did not bound the time: a 2 KB document and a 21-character query within the default limits kept
a thread busy for hours. Verdict: **both implementations were wrong**: the Javadoc of `MAX_VISITED_NODES` and the
design (section 5, "visited nodes are counted, not timed") rely on the count bounding the work.

Fix (christoph-sens/parsson#10, christoph-sens/johnzon#5): each item carried to a child counts as a visit, and equal
items are found by hash once a node has more than 8. Time is proportional to visits again (visits after the fix are
higher, about depth³/6 for this input, which the limit stops). The same cause made `$..[-1]..[*]` on 4,000 nested
arrays take 21.3 s; after the fix 0.55 s. Regression tests: `StreamingMatcherTest.countsTheWorkForRoutesThatWaitForDecisions`
in both forks, and the crash file `crash-aab9951a...` here. Both forks pass the JSONPath TCK (1,793 tests) with the fix.

### 2. Parsson: the matcher never returns on a truncated document (fixed)

Found by `queryAnyDocument` in the second run: the target made no progress for over 20 minutes; a thread dump showed
`StreamingMatcher.proceed` → `JsonParserImpl.skipObject` → `SkippingContext.skip` → `JsonTokenizer.nextToken`.

```
query:    $.b
document: {"a": {"x": 1        (any truncated object or array the matcher skips)
Parsson:  find() never returns
Johnzon:  JsonParsingException
```

Cause: `skipObject()` and `skipArray()` of Parsson's parser loop forever at the end of a truncated text
(`{`, `{"a":1`, `[1,`, `[{`, ...): `skip()` never checks for EOF, and the tokenizer returns EOF on every call. The
matcher skips values that contain nothing to select with exactly these methods, as the Javadoc of `JsonPath.Matcher`
describes, so a truncated or attacker-controlled stream kept a thread busy forever. The parser code is the same on the
master branch of Eclipse Parsson; any caller of `skipObject()`/`skipArray()` is affected, not only JSONPath. Verdict:
**Parsson was wrong** (the parser must report invalid JSON with `JsonParsingException`).

Fix (christoph-sens/parsson#11): `skip()` throws `JsonParsingException` at EOF. Tests: `JsonParserSkipTest.testSkipTruncatedValue`
and `StreamingMatcherTest.failsOnATruncatedValueItSkips` in the fork, and two regression cases here. The fix is a
candidate for upstream Parsson.

## Findings outside JSONPath

The JSON parsers and readers of both implementations throw undocumented exceptions for some invalid texts. The
throwing code is unchanged from the master branches of Eclipse Parsson and Apache Johnzon; it is not part of the
JSONPath work, so it is not fixed in the forks. (Finding 2 is a parser defect too, but it is fixed in the fork because
it made the matcher hang rather than fail with the wrong exception type.) The matcher passes exceptions of the parser
through, as the Javadoc of `JsonPath.Matcher` says. The harness tolerates exactly these exceptions, recognized by type and by the class that
throws them (`Checks.isKnownParserDefect`), and the inputs are regression seeds.

| Implementation | Input | Exception | Expected |
|---|---|---|---|
| Parsson `JsonParserImpl` | nesting deeper than `org.eclipse.parsson.maxDepth` (1,000), e.g. `[` × 1,001 | `RuntimeException: Input is too deeply nested 1000` (from `Stack.push`) | `JsonParsingException` |
| Johnzon `JsonStreamParserImpl` | a text that starts with U+0000, e.g. `"\u0000[1]"` | `NoSuchElementException` from `next()` (U+0000 is the parser's end-of-input marker) | `JsonParsingException` |
| Johnzon `JsonStreamParserImpl` | a number followed by U+0000 and more characters, e.g. `"12\u00003"` | `NumberFormatException: Bad offset or len arguments for char[] input.` from `getBigDecimal()` | `JsonParsingException` |
| Johnzon `JsonStreamParserImpl` | empty text `""` | `ArrayIndexOutOfBoundsException: Index -2147483648 out of bounds for length 65536` from `hasNext()`/`next()` | `JsonParsingException` |
| Parsson `JsonTokenizer` and Johnzon `JsonStreamParserImpl` | a number whose exponent is beyond the range of `int`, e.g. `[1e99999999999]` | `NumberFormatException: Too many nonzero exponent digits.` from `BigDecimal`, through `getValue()`/`getBigDecimal()` | `JsonParsingException`, or a value |
| Johnzon `JsonReaderImpl` | empty or blank text | `JsonReaderImpl$NothingToRead` (an `IllegalStateException`) from `readValue()` | `JsonParsingException` |

They can be reported upstream separately; they are independent of the JSONPath proposal.

Not a defect, but relevant to callers: for numbers whose scale exceeds `johnzon.max-big-decimal-scale` (1,000), such
as `1e1000000`, Johnzon's `JsonNumber.equals` (and so `JsonArray`/`JsonObject.equals`) and the `toString()` of an
array or object that contains one throw `ArithmeticException`; the limit is deliberate. The harness therefore compares
values with its own serialization (`Checks.same`). JSONPath comparisons do not use `JsonNumber.equals` (see the
differential results, difference 1).

## Observations

- Harness issues found by the first runs, fixed before the runs above: the duplicate-name scan read past the first
  value (both readers accept trailing text after it); the time check measured the checks as well (pointer and value
  checks of ~90,000 nodes); paths of very large results took gigabytes; hot code threw `ArrayIndexOutOfBoundsException`
  without a stack trace (`-XX:-OmitStackTraceInFastThrow`).
- Both implementations reject a number literal whose exponent is beyond the range of `int`, such as
  `$[?@ == 1e99999999999]`, in `createPath` with `JsonException` ("Number out of range"). The grammar of RFC 9535
  allows the literal; it cannot be read exactly as a `BigDecimal`, which the specification chapter requires for
  literals. Failing with `JsonException` is the "indication of overflow" of RFC 9535, section 2.1, but the chapter does
  not say so yet.

## Reproducing

The API and both forks installed in the local repository as in the Evidence workflow, then in `evidence/`:

```bash
mvn test -pl fuzz                                            # replay seeds and saved findings (regression mode)
mvn test -pl fuzz -Pfuzz -Dfuzz.target=queryGeneratedDocument -Dfuzz.duration=15m
```

A finding is written to `fuzz/target/fuzz/<target>/crash-<sha1>`; copy it to
`src/test/resources/io/github/christophsens/evidence/fuzz/JsonPathFuzzTestInputs/<target>/` to replay it in every build.
Note from Treetrail: Jazzer's regression mode did not reproduce a `StackOverflowError` that fuzzing mode found, so the
evidence here comes from fuzzing mode, and the regression seeds guard against regressions only.

CI: the [Fuzz workflow](../../.github/workflows/fuzz.yml) fuzzes each target for a bounded time, with the corpus cached
between runs; the Evidence workflow replays the seeds on every push (`mvn test` in `evidence/`).
