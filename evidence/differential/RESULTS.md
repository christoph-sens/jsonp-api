# Differential tests: Parsson vs. Johnzon vs. Treetrail

Generated documents and queries are run through three independent implementations of RFC 9535 and every
disagreement is recorded: the Parsson fork and the Johnzon fork (both implement the proposed `jakarta.json.JsonPath`
API, written separately) and Treetrail 1.0.0 (`jsonpath-core` + `jsonpath-jsonp`, an unrelated library that passes the
JSONPath Compliance Test Suite and works on the same `JsonValue` trees).

## What is compared

| Check | Compared |
|---|---|
| Parsing | The same JSON text parsed by Parsson and by Johnzon gives the same value (numbers numerically, objects as unordered maps) |
| Compilation | All three accept or all three reject the query (reasons are not compared) |
| Result | The node list: normalized path, JSON Pointer and value, in order. A different order of the same nodes would be counted separately (`orderOnly`, RFC 9535 leaves the order of object members open); it never occurred, because all three iterate members in insertion order |
| Evaluation errors | Either all three throw or none |
| JSON Pointer | In Parsson and Johnzon, the pointer of every node resolves, against the document, to the node's value |
| Streaming | For queries whose `getBuffering()` is `NONE` or `BOUNDED`: `matcher(parser)` of Parsson against Johnzon (path, pointer, value, order), and each against its own tree evaluation (same nodes, as a multiset). `getBuffering()` itself must agree, and `UNBOUNDED` queries must throw from `matcher()` in both |

## Generators

Seeded (`SplittableRandom`, one seed per document, derived from the master seed), so a failing case is replayed with
`-Devidence.replay=<case seed>`; a failure report lists the case seed, the query and the document shrunk by a small
minimizer.

- **Documents** (`DocGen`): nested objects and arrays up to 5 levels, plus long arrays (8 to 32 items, for slices and
  negative indexes) and chains 8 to 20 levels deep (for descendant segments); numbers as written in the text
  (`-0`, `-0.0`, `1E2`, `0.30000000000000004`, `2^53+1`, `Long.MAX_VALUE+1`, 30 digit integers, `1e400`, `1e-400`,
  `4.9e-324`, `1e1000000`, ...); strings and member names with BMP and astral characters, combining marks, control
  characters, quotes, backslashes, empty names and names that look like syntax (`$`, `@`, `*`, `..`, `[`).
- **Queries** (`QueryGen`): all selector kinds (name in single and double quotes with `\uXXXX` escapes and surrogate
  pairs, wildcard, index incl. negative, slice incl. negative, zero and huge steps, filter) in child and descendant
  segments, shorthand and bracket notation, whitespace where the grammar allows it, filters with `==  !=  <  <=  >  >=`,
  `&&  ||  !`, parentheses, existence tests, singular queries (relative and absolute), nested filters and all five
  functions (`length`, `count`, `match`, `search`, `value`) with generated I-Regexp patterns (classes, `\p{..}`,
  quantifiers, groups, alternation, astral ranges, anchors that are literal in I-Regexp, invalid patterns).
  About 5 to 10 % of the queries are invalid on purpose (ill-typed function use, wrong arity, unknown function,
  invalid escapes, lone surrogates, leading zeros, out of range integers, stray characters, whitespace the grammar
  does not allow), to compare which queries are rejected.

## Budget

| Run | Seed | Documents | Queries | Compiled by all | Rejected by all | Nodes selected (queries with a non-empty result) | With filter | Streamed |
|---|---|---|---|---|---|---|---|---|
| CI (default, per JDK 17/21/25) | 20261019 | 5,000 | 200,000 | (the job log prints the counts) | | | | |
| Local, after the Johnzon fix | 20261019 | 30,000 | 1,200,000 | 1,031,165 | 168,835 | 202,556 | 320,937 | 924,960 |
| Local | 7 | 30,000 | 1,200,000 | 1,030,480 | 169,520 | 214,928 | 320,222 | 924,288 |
| Local | 424242 | 300,000 | 12,000,000 | 10,305,070 | 1,694,930 | 2,142,978 | 3,207,270 | 9,243,263 |

Re-run with larger numbers: `mvn test -Devidence.docs=300000 -Devidence.seed=<seed>` in `evidence/`, or "Run workflow"
on the Evidence workflow.

## Differences

### 1. Johnzon: `ArithmeticException` when comparing a number with a huge exponent (fixed)

Found in the first run (seed 20261019, 7 differing queries out of 24,000; every one involved the number `1e1000000`).

Minimal repro:

```
document: [1e1000000, 5]
query:    $[?@ > 0]
Parsson:  $[0], $[1]
Treetrail: $[0], $[1]
Johnzon:  ArithmeticException: BigDecimal scale (-1000000) limit exceeds maximum allowed (1000)
```

The same happened for `==` against `1e1000000` and for `1e-1000000`. The matching streaming and JSON Pointer checks
failed for the same reason. Cause: the filter compared numbers with `JsonNumber.bigDecimalValue()`, which Johnzon's
`JsonNumberImpl` refuses for a scale beyond 1000. Verdict: **Johnzon was wrong.** RFC 9535 gives a number its numeric
value and defines the comparison for every number the document holds (numbers outside the range of IEEE 754 doubles
are an interoperability warning in RFC 9535 and RFC 8259, not an error); a query must not fail
with an unchecked exception that is not a `JsonException` because of the size of a number in the data. Fixed in
christoph-sens/johnzon#4 (falls back to the number's literal, so there is no cost for ordinary numbers); the minimal
repros are in `RegressionTest`. After the fix the three agree.

## No other differences

After the fix, 14.4 million generated queries (over 12.4 million compared results and 11.1 million streamed queries) show no
difference in compilation, results, normalized paths, JSON Pointers, evaluation errors or streaming between the three
implementations. This includes the ordering: all three produce the nodes in the same order, and the streaming matchers
of Parsson and Johnzon produce them in the same order as each other.

## Limits of this evidence

- The implementations share the approach of the proposal (the Johnzon port follows the Parsson one), so agreement of
  those two proves less than agreement with Treetrail, which is independent of both; no query ever separated
  Treetrail from the pair.
- Documents are parsed from text. Documents built with the builder API (Johnzon has three number classes) are covered
  by Johnzon's own tests (`JohnzonNumbersTest`), not here. Documents with duplicate member names are not generated
  (RFC 8259 leaves their meaning open).
- Evaluation limits (`maxVisitedNodes` and the like) have different defaults per implementation and are not compared;
  the documents are small enough not to reach them.
- The generator is random, not exhaustive: zero differences in 14 million cases is not a proof.
