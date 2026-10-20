# Evidence for the JSONPath proposal

This branch (`evidence`) holds the measurements behind the JSONPath proposal for Jakarta JSON Processing
([jakartaee/jsonp-api#448](https://github.com/jakartaee/jsonp-api/issues/448)). It is based on the `jsonpath` branch
and **is not meant for upstream**: nothing in `evidence/` is part of the API, the TCK or the reactor of this repository.

`evidence/` is a standalone Maven project (Java 17) with one module per kind of evidence:

| Module | What it shows | Status |
|---|---|---|
| [`differential`](differential/) | Generated documents and queries run through Parsson, Johnzon and Treetrail; every disagreement is a bug or an explained difference ([RESULTS.md](differential/RESULTS.md)) | F2 |
| [`benchmarks`](benchmarks/) | JMH: Parsson, Johnzon, Treetrail and Jayway JsonPath on `JsonValue`; compile, query, streaming, hostile regex ([RESULTS.md](benchmarks/RESULTS.md)) | F3 |
| [`fuzz`](fuzz/) | Jazzer fuzzing of query compilation, evaluation, the streaming matcher and I-Regexp in Parsson and Johnzon ([RESULTS.md](fuzz/RESULTS.md)) | F1 |
| [`mutation`](mutation/) | PIT mutation testing of the Parsson JSONPath implementation ([RESULTS.md](mutation/RESULTS.md)); built with `-Pmutation` | F4 |

## Running

The modules need the API (`2.2.0-SNAPSHOT`) and the two implementations from the `jsonpath` branches of
christoph-sens/jsonp-api, christoph-sens/parsson and christoph-sens/johnzon installed in the local repository. The
steps are in [`.github/workflows/evidence.yml`](../.github/workflows/evidence.yml). Then:

```bash
cd evidence
mvn test                                   # CI budget: 5,000 documents x 40 queries, fixed seed
mvn test -Devidence.docs=300000 -Devidence.seed=1   # a longer run, another seed
```

CI runs on every push and pull request to `evidence`; "Run workflow" accepts a larger budget and another seed.
