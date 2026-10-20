# JMH benchmarks: Parsson, Johnzon and Treetrail against Jayway JsonPath

All four implementations query a `jakarta.json.JsonValue` tree, with queries of the same meaning in each one's
syntax. This is a comparison of the proposed API's two implementations with the library most Java code uses today;
Treetrail 1.0.0 is a reference (Parsson's implementation was ported from it, see
[../differential/RESULTS.md](../differential/RESULTS.md)). Jayway JsonPath does not claim RFC 9535 conformance and
solves a wider problem (it also writes, maps and has its own filter language); these numbers say how fast each
library does the work of this benchmark, not which is better.

## Setup

| | |
|---|---|
| Machine | Apple M5 Pro (18 cores), 48 GB, macOS 26.7.1, running other light work during the run |
| JDK | Temurin 25.0.3+9, default heap and GC settings |
| JMH | 1.37, average time per operation. Query, compile and hostile-regex benchmarks: 1 fork, 3 warmup (hostile: 2) and 5 measurement (hostile: 3) iterations of 1 s. Streaming: 1 fork, 3 warmup and 5 measurement iterations of 2 s |
| Implementations | Parsson and Johnzon forks (`jsonpath` branches, API 2.2.0-SNAPSHOT), Treetrail 1.0.0 (`jsonpath-jsonp`), Jayway JsonPath 3.0.0 with its `JakartaJsonProvider` |
| Documents | Parsson and Johnzon query the tree their own parser built. Treetrail and Jayway query the Parsson tree. Bookstore of the JSONPath examples with 100 books (about 11 kB) and with 92,000 books (about 10 MB), `Random(9535)` |
| Results | Every benchmark returns the list of selected **values** (no paths). Jayway runs with `Option.ALWAYS_RETURN_LIST`, so a definite path returns a list too |

Before JMH runs, `BenchmarkFairnessTest` checks that for every query all four return the same values, that the hostile
pattern matches nothing everywhere, and that the streaming matchers return the tree's values.

The queries (RFC 9535 / Jayway):

| Name | RFC 9535 | Jayway |
|---|---|---|
| definite | `$.store.bicycle.color` | same |
| wildcard | `$.store.book[*].title` | same |
| filter | `$.store.book[?@.price < 10 && @.category == 'fiction'].title` | `$.store.book[?(@.price < 10 && @.category == 'fiction')].title` |
| descendant | `$..price` | same |
| function | `$.store.book[?length(@.tags) == 2 && @.price > 5].title` | `$.store.book[?(@.tags.length() == 2 && @.price > 5)].title` |
| regex | `$.store.book[?match(@.author, 'H.*')].title` | `$.store.book[?(@.author =~ /H.*/)].title` |

## Results

One run on one machine; the "±" JMH reports is in `jmh-results.json` next to this file and was mostly below 10 % of
the score. Read ratios of 1.2 or less as equal.

### Running a compiled query (µs/op, 100 books)

| Query | Parsson | Johnzon | Treetrail | Jayway | Jayway / Parsson |
|---|---:|---:|---:|---:|---:|
| definite | 0.04 | 0.04 | 0.04 | 0.09 | 2.6× |
| wildcard | 1.48 | 1.61 | 1.32 | 8.88 | 6.0× |
| filter | 3.85 | 4.49 | 4.89 | 19.34 | 5.0× |
| descendant | 13.87 | 13.98 | 13.59 | 37.24 | 2.7× |
| function | 6.01 | 8.07 | 8.04 | 38.23 | 6.4× |
| regex | 3.63 | 2.87 | 5.05 | 12.08 | 3.3× |

### Compiling a query (µs/op)

| Query | Parsson | Johnzon | Treetrail | Jayway | Jayway / Parsson |
|---|---:|---:|---:|---:|---:|
| definite | 0.052 | 0.052 | 0.059 | 0.073 | 1.4× |
| filter | 0.211 | 0.216 | 0.235 | 0.472 | 2.2× |
| function | 0.359 | 0.341 | 0.345 | 0.471 | 1.3× |
| regex | 0.692 | 0.682 | 0.728 | 0.318 | 0.5× |

### Hostile regular expression `((a+)+)+b` (µs/op)

| String length | Parsson | Johnzon | Treetrail | Jayway |
|---|---:|---:|---:|---:|
| n = 10 | 0.04 | 0.04 | 0.06 | 26.75 |
| n = 15 | 0.04 | 0.04 | 0.06 | 787 |
| n = 20 | 0.05 | 0.04 | 0.07 | 26,033 |
| n = 24 | 0.05 | 0.05 | 0.07 | 483,231 |

### Streaming matcher against the tree (ms/op, 92,000 books, 10 MB of JSON text)

| Query | Impl | `stream` (matcher over the text) | `parseAndQuery` (parse a tree, then query) | `queryTree` (tree exists already) |
|---|---|---:|---:|---:|
| definite | Parsson | 10.7 | 25.9 | <0.01 |
| definite | Johnzon | 21.5 | 44.8 | <0.01 |
| wildcard | Parsson | 28.2 | 28.1 | 3.5 |
| wildcard | Johnzon | 34.7 | 49.2 | 3.6 |
| filter | Parsson | 50.2 | 30.4 | 5.7 |
| filter | Johnzon | 63.8 | 50.9 | 6.7 |

Allocation per operation for `wildcard` (`-prof gc`, `gc.alloc.rate.norm`):

| Impl | `stream` | `parseAndQuery` | `queryTree` |
|---|---:|---:|---:|
| Parsson | 186 MB | 137 MB | 10 MB |
| Johnzon | 184 MB | 208 MB | 10 MB |

## What the numbers say, and what they do not

- **Query time.** On the JsonValue tree, Parsson, Johnzon and Treetrail are within about 1.8× of each other in every
  query (which one is ahead changes by query), and Jayway takes 2.6× to 6.4× as long in this setup.
  Part of the difference may come from how Jayway's `JakartaJsonProvider` reaches into the tree (it wraps objects and
  arrays in proxies); a Jayway run on its own default model is not part of this benchmark.
- **Compile.** Compiling takes well under a microsecond in all four. Jayway was faster than the others for the regex
  query; we did not look into why (it may defer work to the first evaluation), so do not read more into it.
- **Hostile regular expression.** The three RFC 9485 implementations take the same time for n = 10 to 24 (the
  pattern is matched without backtracking; a pre-check may also reject the input early, the point of the case is the
  shape of the curve, not the absolute time). `java.util.regex`, which Jayway uses, grows about 30× for every 5
  more characters, from 27 µs at n = 10 to 0.48 s at n = 24. A query from an untrusted source with such a pattern
  can therefore block a thread in Jayway; RFC 9485 discusses this risk in its security considerations.
- **Streaming.** The matcher is clearly faster than parse-then-query when the query lets it skip most of the
  document (`definite`: 2.4× for Parsson, 2.1× for Johnzon), about equal for Parsson and faster for Johnzon on a
  wildcard, and **slower** than parse-then-query for the filter on Parsson (50 ms against 30 ms) and for Johnzon too
  (64 ms against 51 ms). Streaming also allocated more than parse-then-query for the wildcard in Parsson
  (186 MB against 137 MB per operation). What streaming is for, a bounded peak heap on documents that do not fit
  in memory, is **not measured here**: JMH reports time and allocation, not the memory that stays live. This is a
  finding for the implementations' optimization, not for the API.
- Not measured: documents from a file or a socket, concurrent queries, the effect of the evaluation limits, other
  JDKs, startup time. The benchmarks are not run in CI on every push: see the manual workflow
  `.github/workflows/benchmarks.yml`.
- Jayway's `@.title.length() > n` on a string matched nothing with the `JakartaJsonProvider` in the first version of
  this benchmark (`length()` on a string value); the function query therefore uses `length(@.tags)` /
  `@.tags.length()` on an array. We did not investigate whether that is the provider or the function.

## Reproducing

```bash
cd evidence
mvn -pl benchmarks package          # runs BenchmarkFairnessTest, builds benchmarks/target/benchmarks.jar
java -jar benchmarks/target/benchmarks.jar -rf json -rff jmh-results.json           # about 20 minutes
java -jar benchmarks/target/benchmarks.jar StreamBenchmark -p query=wildcard -prof gc   # allocation
```
