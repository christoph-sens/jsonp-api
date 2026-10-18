# JSONPath (RFC 9535) in Jakarta JSON Processing — design

Status: draft for review. Tracking: christoph-sens/jsonp-api#4 (this document), #5 (API).
Upstream discussion: jakartaee/jsonp-api#448.

## 1. Goal and scope

Jakarta JSON Processing standardizes JSON Pointer (RFC 6901), JSON Patch (RFC 6902) and JSON Merge
Patch (RFC 7396), but no query language. This proposal adds JSONPath as defined by
[RFC 9535](https://www.rfc-editor.org/rfc/rfc9535) in two layers:

1. **Tree layer**: the full RFC on a `JsonValue` that is already in memory. Results are the RFC's
   nodelist: values with their normalized paths, in the RFC's order.
2. **Streaming layer**: queries evaluated over a `JsonParser`, so that a large document is never
   materialized. It covers the query classes that can be evaluated with no buffering or with bounded
   buffering (section 6), and says so up front for every query.

The streaming layer answers the concern raised in jakartaee/jsonp-api#448 that a JSONPath API on
`JsonValue` forces callers to materialize input and output. The tree layer comes first because it is
the reference semantics: the streaming layer is specified as "selects the same nodes as the tree
layer", which makes it testable.

The API follows the shape of `JsonPointer`: a factory method on `Json` and `JsonProvider`, an
immutable interface in `jakarta.json`, `JsonException` for errors.

## 2. API overview (tree layer)

```java
JsonPath path = Json.createPath("$.store.book[?@.price < 10].title");
List<JsonPath.Node> nodes = path.query(document);       // document: any JsonValue
for (JsonPath.Node node : nodes) {
    JsonValue title = node.getValue();
    String where = node.getPath();                       // "$['store']['book'][2]['title']"
    JsonPointer pointer = node.getPointer();             // "/store/book/2/title"
}
```

| Element | Contract |
| --- | --- |
| `Json.createPath(String)` | Delegates to `JsonProvider.provider().createPath(String)`. |
| `JsonProvider.createPath(String)` | Default throws `UnsupportedOperationException`, as `createPointer` did when it was added in 1.1, so existing providers stay source and binary compatible. |
| `JsonPath` | Immutable, safe for concurrent use. `query(JsonValue)` returns an unmodifiable `List<JsonPath.Node>`. `toString()` returns the query string. |
| `JsonPath.Node` | `getValue()`, `getPath()` (normalized path, RFC 9535 section 2.7), `getPointer()` (RFC 6901). |

Every `JsonPath` and `Node` method that might be added later (streaming entry point, `isSingular()`,
`exists`, a structured path type) can be added as a default method or as a new type; nothing in the
first version has to change for them. Configuration (section 5) is added as an overload of
`createPath`, the way `createReaderFactory(Map)` sits next to `createReader`.

## 3. Semantics of RFC 9535 on JSON-P types

### 3.1 Values and kinds

The query argument is any `JsonValue`, including primitives: `$` on a `JsonString` selects that
string (RFC 9535, section 2.2.2). The kind of a value is its `getValueType()`. Children of a
`JsonObject` are its member values, children of a `JsonArray` its elements (section 1.1).

### 3.2 Numbers

RFC 9535 requires mathematical equality and ordering for numbers that interoperate under I-JSON
(RFC 7493, section 2.2) and allows an implementation-specific comparison for the others
(section 2.3.5.2.2).

- Numbers compare by `JsonNumber.bigDecimalValue()` with `BigDecimal.compareTo`. So `1`, `1.0`,
  `1e0` and `-0` versus `0` are equal; the comparison is exact for every JSON number, which fulfils
  the MUST for I-JSON numbers and is the implementation-specific rule for the others.
- Number literals in a query are parsed exactly (as `BigDecimal`), never through `double`.
- `JsonNumber.equals` is **not** used: it compares `BigDecimal.equals`, which depends on the scale
  (`1.0` is not equal to `1`). The same holds for `JsonArray.equals` and `JsonObject.equals`, which
  are `List`/`Map` equality over those values. `==` in filters needs structural equality with
  numbers by value; implementations implement it themselves, without recursion on the call stack.
- `JsonNumber` cannot hold NaN or infinities (`Json.createValue(double)` rejects them), so the
  question of non-finite numbers does not arise for JSON-P values.

### 3.3 Strings and member names

- Name selectors match by exact string equality: identical sequences of Unicode scalar values, no
  Unicode normalization (section 2.3.1.2).
- `<` on strings compares Unicode scalar values (section 2.3.5.2.2). This is **not**
  `String.compareTo`, which compares UTF-16 code units and orders U+E000–U+FFFF after supplementary
  characters. Implementations compare code points.
- `length()` counts Unicode scalar values (section 2.4.4), not `String.length()`.
- Java strings can contain unpaired surrogates, which RFC 9535 leaves unpredictable (section 1.3).
  JSON-P implementations compare them as code points with their own value; normalized paths contain
  them unescaped, because the grammar of section 2.7 cannot express them. The spec says the result
  for such strings is implementation-specific, so the TCK does not test it.

### 3.4 Duplicate member names

A `JsonObject` is a `Map<String, JsonValue>` and cannot contain duplicate names. Whether a JSON text
with duplicates becomes an object with the first value, the last, or an error is decided by the
reader (`JsonConfig.KEY_STRATEGY`, Parsson's `rejectDuplicateKeys`), before the query runs. The tree
layer therefore never sees duplicates, which matches RFC 9535 section 1.3: behaviour on duplicate
names is not predictable and the RFC does not define it.

The streaming layer does see duplicate names in the event stream. It reports a match for every
occurrence the parser delivers, so that it never buffers to detect duplicates. Applications that
must reject duplicates configure the parser to do so. (Open question Q4.)

### 3.5 JSON null versus missing

JSON null is a value like any other (section 2.6). `$.a` on `{"a": null}` selects one node whose
value is `JsonValue.NULL`; on `{}` it selects nothing. `[?@.a]` is true for the first and false for
the second; `[?@.a == null]` likewise. The API never uses Java `null` for JSON null: `getValue()` is
never `null`.

### 3.6 Nodes, identity and repeated selection

A node is a value together with its location (section 1.1). Two nodes are the same node if and only
if their normalized paths are equal; equal values at different locations are different nodes, and
`JsonValue.NULL`, `TRUE` and `FALSE` are shared instances anywhere in the document.

A node may be selected more than once and then appears that many times in the result: `$[0,0]` on
`[1]` returns two nodes with the path `$[0]` (sections 2.1.2 and 2.5.1.2). Implementations do not
remove duplicates; callers that need distinct nodes compare `getPath()`.

`getValue()` returns the value as the parent's `JsonObject.get` or `JsonArray.get` returns it,
without copying. The specification does not promise object identity (`==`), because the query
argument may be a third-party `JsonValue` implementation that creates values on access.

`getPointer()` returns the RFC 6901 pointer to the same location: each name escaped with `~0`/`~1`,
each index in decimal, the root as the empty pointer. Applied to the query argument it yields the
node's value. (`JsonPointer.getValue` takes a `JsonStructure`; for a primitive query argument only the
root node exists, and its pointer is the empty pointer.)

### 3.7 Order of results

The result is in the RFC's nodelist order. Array elements are in array order. For object members
RFC 9535 does not stipulate an order (sections 2.3.2.2, 2.3.5.2, 2.5.2.2). The spec requires
implementations to use the iteration order of `JsonObject.entrySet()`; for objects created by a JSON-P
reader or builder this is document or insertion order, so results are deterministic across runs and
implementations. (Open question Q2.)

### 3.8 Regular expressions

`match()` and `search()` use I-Regexp (RFC 9485). `java.util.regex` is not a valid implementation:
its syntax and semantics differ (I-Regexp has XSD semantics, RFC 9485 section 4: for example `.`
matches any character except `\n` and `\r`, as the non-normative mapping in section 5 spells out),
and it backtracks, so a crafted pattern takes exponential time. Patterns may come from the
document (`match(@.code, @.pattern)`), so even trusted queries can meet hostile patterns. The spec
requires matching in time linear in the input length for a given pattern. An invalid pattern makes the
function return false (sections 2.4.6, 2.4.7); that includes patterns beyond an implementation's size
limit (RFC 9485, section 8 allows such limits).

The Compliance Test Suite treats `^` and `$` in patterns differently from the letter of RFC 9485, where
`^` is an ordinary character. Treetrail follows the CTS. The TCK takes its conformance cases from the
CTS, so the spec has to pick one reading explicitly. (Open question Q5.)

### 3.9 Functions

The five functions of RFC 9535 (`length`, `count`, `match`, `search`, `value`) are part of the RFC
and are required. Unknown function names and ill-typed calls are syntax errors (sections 2.1, 2.4.3).

## 4. Error model

| Situation | Exception | Information |
| --- | --- | --- |
| Query string is `null`; `query(null)` | `NullPointerException` | |
| Query not well-formed or not valid (section 2.1), including integers outside ±(2^53−1), unknown functions, ill-typed function calls | `JsonException` from `createPath` | Message with the reason, the character position, and an excerpt of the query around it, at most about 60 characters so that long queries do not end up in logs in full |
| Query exceeds a limit at evaluation time (section 5) | `JsonException` from `query` | Message names the limit |
| Streaming: the JSON text is invalid | `JsonParsingException`, as from the parser | `getLocation()` |
| Provider without JSONPath support | `UnsupportedOperationException` | |

Rules taken from RFC 9535 section 2.1:

- All query errors are raised by `createPath`, independent of any document. `query` never raises a
  syntax error, and a structural mismatch (`$.a` on an array, an index out of range) selects nothing
  rather than failing.
- An implementation that cannot evaluate a valid query correctly, because of a limit or resource
  exhaustion, MUST NOT silently return a partial or wrong result: it throws. A `StackOverflowError`
  on deep documents or nested descendant segments is a defect (RFC 9535, section 4.1).

`JsonException` matches `createPointer`. A subclass with `getPosition()` (like `JsonParsingException`
with `getLocation()`) can be added later without breaking callers, because it is still a
`JsonException`. (Open question Q3.)

## 5. Limits for untrusted queries

RFC 9535 section 4.1 asks implementations to defend against crafted queries and documents. The spec
defines these limits; their names and defaults are fixed in the configuration ticket
(christoph-sens/jsonp-api#7), as JSON-P configuration properties passed in a `Map<String, ?>`:

| Limit | Checked at | Purpose | Reference default |
| --- | --- | --- | --- |
| Nesting of filters, parentheses and function calls in the query | `createPath` | Bounds parser recursion | 64 |
| Size of a regular expression after expansion of counted repetitions | `createPath` for literals, evaluation for patterns from the document | `(a{1000}){1000}` | 20,000 automaton instructions; beyond it the pattern is invalid |
| Visited nodes per evaluation | `query` | Bounds time: nested descendant segments and filters multiply work | 100,000,000 |
| Nesting depth of the document | `query` | Bounds walks and structural comparison | 1,000 (Parsson's parser default) |
| Nodes in a result | `query` | Bounds memory of `$..*`-style results | unlimited |

Reference defaults are Treetrail's. The spec states the limits and that exceeding one throws
`JsonException`; it lets implementations choose defaults, but requires a documented default for each.
Regex matching is linear (section 3.8) independent of these limits.

## 6. Streaming layer over `JsonParser`

Specified in christoph-sens/jsonp-api#6; this section fixes the model that ticket builds on.

### 6.1 Model

The caller hands a `JsonParser` to the query and pulls matches. For each match it learns the
normalized path and the parser location, and then either takes the value (`getValue()`) or skips it.
Sketch only, names to be settled in #6:

```java
JsonPath path = Json.createPath("$.orders[*].id");
try (JsonParser parser = Json.createParser(input)) {
    JsonPath.Matcher m = path.matcher(parser);   // throws if the query is not streamable
    while (m.next()) {
        String where = m.getPath();
        JsonValue id = m.getValue();             // or m.skip()
    }
}
```

**Order.** The streaming layer reports matches in **document order**: a match is reported when the
parser reaches the start of its value. The tree layer reports the RFC's nodelist order. For the
no-buffering class (6.2) the two orders are the same. For other queries the streaming layer reports
the same nodes the same number of times, possibly in another order. The reason: RFC order can
require buffering the whole document. `$..a` on `{"x": {...}, "a": 2}` must report `$['a']` before
every match inside `x`, although `x` comes first in the text; `$..[1]` on `[[7, 8], 9]` must report
`9` before `8`. Document order also matches what a caller of a streaming API expects. Callers that
need RFC order use the tree layer. (Open question Q1.)

### 6.2 Streamable query classes

A query falls into the first class whose conditions it meets. The class is a property of the query
alone, so `createPath` can compute it and the API can report it before any input is read.

**Class 1 — no buffering.** Memory independent of the document. Every segment is a child segment
(no `..`), and every selector is one of:

- a name selector,
- a wildcard,
- a non-negative index,
- a slice with non-negative (or omitted) start and end and a positive (or omitted) step,

with the restriction that no two selectors of one segment can select the same child (for example
`['a','b']` and `[0,2]` qualify, `[0,0]`, `[*,0]` and `[0:2,1]` do not). Each match is reported once
and its value is read straight from the parser.

**Class 2 — bounded buffering.** Memory bounded by the size of the largest candidate or match value,
plus a number of array elements fixed by the query, but independent of the size of the rest of the
document. A query is in class 2 if it is not in class 1 and needs only:

- **Filters on the current node** (`[?@.price < 10]`, `[?length(@.tags) > 2]`, `[?@..x]`): the
  candidate child is buffered until the filter is decided, then reported or dropped.
- **Negative indices** (`[-1]`): needs the array length; the last |i| elements are kept in a window.
- **Slices with a negative start or end, or a negative step**: same reason, window bounded by the
  slice parameters (`[-3:]` keeps 3 elements; `[5:1:-2]` keeps at most 4).
- **Selectors of one segment that can select the same child** (`[0,0]`, `[*,0]`): the child is
  buffered so that it can be reported once per selection.
- **Descendant segments** (`$..a`, `$..*`): with document order no match is held back, but matches
  can nest (`$..a` on `{"a": {"a": 1}}`). When the caller takes the outer value, the inner matches
  are reported from that value, which the layer has built anyway; when the caller skips the outer
  value, the layer reads on and still reports the inner matches. The buffer is the outer value the
  caller asked for.

**Class 3 — whole document.** Filters that contain an absolute query (`[?@.price < $.limit]`): the
filter cannot be decided before the end of the document. The streaming layer does not evaluate
these queries; it throws, and the caller uses the tree layer. Materializing silently would defeat
the purpose of the API.

The classification is conservative: class 2 by the rules above may need no buffer for a particular
document. The TCK tests that each streaming result equals the tree result sorted into document order,
and that class 1 and 2 queries are accepted.

How this differs from the first sketch of the classes, which put all selector lists into class 2 and
called descendant segments bounded without qualification: with document order, selector lists that cannot select a child twice
need no buffer (class 1), and descendant segments are bounded only because of document order; under
RFC order they would be class 3.

## 7. Non-goals

- **Custom function extensions.** RFC 9535 section 2.4 lets implementations register more functions
  (IANA subregistry, section 3.2). The first version has no API for that: the five RFC functions only.
  A registration API can be added later through the configuration map or a builder.
- **Modification through paths.** No `JsonPath.replace/remove`. Callers combine `getPointer()` with
  `JsonPointer` or `JsonPatch`, which already define modification.
- **Non-RFC dialects** (Jayway/Goessner syntax, `$.length`, script expressions). Only RFC 9535.
- **Values-only results** (no paths). A possible later optimization through a separate method.
- **Binding** (JSON-B) integration.

## 8. Compatibility and versioning

- The API module stays at Java 11 (`maven-compiler-plugin` release 11). New elements carry
  `@since 2.2`, the next specification version in `api/pom.xml`.
- `JsonProvider.createPath` has a default implementation, so existing providers keep compiling and
  linking; their `Json.createPath` throws `UnsupportedOperationException` until they implement it.
- No new package and no change to `module-info.java`: `JsonPath` lives in `jakarta.json` next to
  `JsonPointer`.

## 9. How this work is done and verified

This proposal is written with AI assistance (Anthropic's Claude, used through Claude Code). Commits made
that way carry a `Co-Authored-By` trailer. AI output is treated as unverified until a test or the
RFC text confirms it. The checks:

- **RFC text.** Each statement about RFC 9535 in this document cites a section and was checked against
  the RFC text.
- **Compliance Test Suite.** Both implementations (Eclipse Parsson, Apache Johnzon) must pass all cases
  of the [JSONPath Compliance Test Suite](https://github.com/jsonpath-standard/jsonpath-compliance-test-suite).
- **TCK.** API tests, conformance tests derived from the CTS, and streaming tests that compare each
  streaming result with the tree result.
- **Differential tests.** Parsson, Johnzon and Treetrail (the reference implementation the port starts
  from, which passes the CTS with every object model it supports) run the same generated queries and
  documents; any difference is a bug in one of them or a gap in the spec.
- **Fuzzing and mutation testing** of the ported code.

## 10. Open questions

- **Q1 Order in the streaming layer**: document order (proposed) or RFC nodelist order with whole-document
  buffering where needed?
- **Q2 Object member order**: require `entrySet()` iteration order (proposed), or leave it unspecified as
  the RFC does?
- **Q3 Exception type**: plain `JsonException` (proposed for now) or a new `JsonPathException` with
  `getPosition()` from the start?
- **Q4 Duplicate names in the streaming layer**: report every occurrence (proposed), or follow
  `JsonConfig.KEY_STRATEGY`, which needs buffering for `FIRST`?
- **Q5 `^` and `$` in `match()`/`search()`**: follow the CTS (as Treetrail does) or RFC 9485 literally?
- **Q6 `Node.equals`**: leave it unspecified (proposed: compare `getPath()`), or define equality as
  same path and equal value?
