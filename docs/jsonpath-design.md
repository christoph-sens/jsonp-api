# JSONPath (RFC 9535) in Jakarta JSON Processing — design

Status: draft for review. Tracking: christoph-sens/jsonp-api#4 (this document), #5 (API), #6 (streaming),
#7 (configuration).
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
| `Json.createPath(String, Map<String, ?>)` | Same, with the limits of section 5; `JsonProvider.createPath(String, Map)` has the same default as the one-argument method. |
| `JsonPath.getBuffering()`, `JsonPath.matcher(JsonParser)` | Streaming layer, section 6. |

Every `JsonPath` and `Node` method that might be added later (`isSingular()`, `exists`, a structured
path type) can be added as a default method or as a new type; nothing in the first version has to
change for them. Configuration (section 5) is an overload of `createPath`, the way
`createReaderFactory(Map)` sits next to `createReader`.

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
| Query exceeds a limit at evaluation time (section 5) | `JsonException` from `query`, `Matcher.find()` or `Matcher.getValue()` | Message names the limit |
| Query nested deeper than `maxNesting` (section 5) | `JsonException` from `createPath` | As for syntax errors |
| Configuration property with an invalid value (not a non-negative integer) | `JsonException` from `createPath` | Property name |
| `matcher()` for a query of class `UNBOUNDED` (section 6.3) | `JsonException` from `matcher` | |
| Streaming: the parser is not before a JSON value | `IllegalStateException` from the first `find()` | |
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

RFC 9535 section 4.1 asks implementations to defend against crafted queries and documents. The limits
are JSON-P configuration properties, passed to `Json.createPath(String, Map<String, ?>)` as
`createReaderFactory(Map)` takes its properties. The names are constants of `JsonPath`; the values are
non-negative integers, given as a `Number` or a decimal `String`. Properties a provider does not
support are ignored, as elsewhere in JSON-P; an invalid value of one of these properties makes
`createPath` throw `JsonException`. `Json.createPath(String)` uses the defaults. A limit is part of the
`JsonPath`; it applies to every `query` and every `matcher` of that instance.

| Property (`JsonPath.` constant) | Checked at | Purpose | Default |
| --- | --- | --- | --- |
| `jakarta.json.JsonPath.maxNesting` (`MAX_NESTING`) | `createPath` | Nesting of filters, parenthesized expressions and function calls in the query; bounds parser recursion | 64 |
| `jakarta.json.JsonPath.maxRegexSize` (`MAX_REGEX_SIZE`) | `createPath` for literals, evaluation for patterns from the document | Size of a regular expression after expansion of counted repetitions, `(a{1000}){1000}`; beyond it the pattern is invalid and the function returns false | implementation specific (Parsson: 20,000 automaton instructions) |
| `jakarta.json.JsonPath.maxVisitedNodes` (`MAX_VISITED_NODES`) | `query`, `Matcher.find()` | Bounds time: nested descendant segments and filters multiply work | 100,000,000 |
| `jakarta.json.JsonPath.maxDepth` (`MAX_DEPTH`) | `query`, `Matcher.find()` | Depth of the nodes an evaluation reaches, and of `==` comparisons | 1,000 |
| `jakarta.json.JsonPath.maxResultSize` (`MAX_RESULT_SIZE`) | `query` | Bounds memory of `$..*`-style results; does not apply to the matcher, which collects nothing | unlimited |
| `jakarta.json.JsonPath.maxBufferedNodes` (`MAX_BUFFERED_NODES`) | `Matcher.find()`, `Matcher.getValue()` | Bounds what the matcher holds for class `BOUNDED` (section 6.3); never applies to class `NONE` | 10,000,000 |

Decisions:

- **Defaults are part of the spec** where the measure is portable (nesting, depth, result size, visited
  nodes), so the same query on the same document behaves alike across providers. They are Treetrail's
  and Parsson's values. Providers may offer their own way to change the defaults (Parsson reads system
  properties).
- **Visited nodes are counted, not timed.** A visit is each node a selector selects, each node a
  descendant segment walks through and each node a filter tests, within queries inside filters too; the
  matcher also counts each value it reads. Implementations may skip work that cannot change the result
  (an absolute query inside a filter is evaluated once), so the exact count is implementation specific.
  It is never less than the number of selected nodes, which is what the TCK checks, together with a
  generous limit that must not fail.
- **The regular-expression size has an implementation-specific unit.** A portable measure would have to
  count every construct that makes an automaton grow (`(|){100000}` has no characters but 200,000
  instructions in Parsson), which ties the spec to one compilation scheme. The spec requires a unit that
  grows at least linearly with the characters and character classes of the expanded expression and asks
  implementations to document unit and default. Regex matching is linear (section 3.8) independent of
  this limit.
- **Depth means "reached", for every selector.** A node is reached when a selector selects or tests it or
  a descendant segment walks through it, also inside filters. Reaching a node deeper than `maxDepth`
  throws, whichever selector reaches it; an empty array or object at the limit does not throw, because
  no node below it is reached. One rule for names, indices, wildcards, filters and descendant segments is
  easier to state and to test than a rule for "walks" only, and costs nothing: names and indices reach
  one node per step. The matcher considers every element of an array as a candidate of `[-1]`, while
  `query()` computes the selected index, so the matcher may throw where `query()` does not; the spec
  says so.
- **A limit on streaming buffers** (`maxBufferedNodes`, decided Q7). "Bounded" in class `BOUNDED` means
  bounded by the query and by the tested values, and both can be large: an untrusted query `$[-9007199254740991]`
  makes every element of an array a candidate until the array ends, and `$..[?@.price < 10]` tests the
  children of the root, which for a typical document are most of it. Without a limit, a caller who
  streams to avoid materialization could not rule out materialization. The measure is nodes, not values,
  because one held value can be the whole document. It counts the values the matcher holds because it
  cannot report or decide them yet (waiting matches, tested children), not the values the caller asks
  for. Class `NONE` holds nothing, so the limit never applies to it, even with 0; the TCK checks that,
  which makes the "no buffering" guarantee observable. The default, 10,000,000 nodes, bounds memory to
  roughly a gigabyte, the same order as the visited-nodes default bounds time to seconds: queries that
  stream a record at a time never reach it, queries that effectively materialize a large document do.

## 6. Streaming layer over `JsonParser`

Specified in christoph-sens/jsonp-api#6 (Javadoc of `JsonPath.matcher`, `JsonPath.Matcher` and
`JsonPath.Buffering`).

### 6.1 Model

The caller hands a `JsonParser` to the query and pulls matches. For each match it learns the
normalized path, the JSON Pointer and the location in the text, and then either takes the value or
leaves it:

```java
JsonPath path = Json.createPath("$.orders[*].id");
try (JsonParser parser = Json.createParser(input)) {
    JsonPath.Matcher m = path.matcher(parser);   // JsonException if the query is UNBOUNDED
    while (m.find()) {
        String where = m.getPath();              // $['orders'][7]['id']
        JsonLocation at = m.getLocation();       // line, column, offset of the value
        JsonValue id = m.getValue();             // or leave it: find() skips it
    }
}
```

| Element | Contract |
| --- | --- |
| `JsonPath.getBuffering()` | `NONE`, `BOUNDED` or `UNBOUNDED` (6.3), from the query alone. |
| `JsonPath.matcher(JsonParser)` | Reads nothing yet. The query argument is the value whose first event the parser returns next: a new parser, a parser after `KEY_NAME`, or before an array element. Throws `JsonException` for `UNBOUNDED`. |
| `Matcher.find()` | Advances to the next match; `false` after the last event of the query argument, which is the parser's current event then. Never reads beyond it and never closes the parser. |
| `Matcher.getPath()`, `getPointer()` | As `Node.getPath()`, `Node.getPointer()`, relative to the query argument. |
| `Matcher.getLocation()` | `JsonParser.getLocation()` as it was when the parser's current event was the first event of the match's value. |
| `Matcher.getValue()` | The value, read from the parser if needed (`JsonParser.getValue()` where nothing inside can match). Repeated calls return equal values. |

The values go through the matcher, not through the parser directly, although the parser is positioned
at the match in class `NONE`. Three reasons: the same calls work for all classes, including matches the
matcher had to buffer; the matcher knows whether a value was consumed without relying on
`JsonParser.currentEvent()`, which parsers before 2.1 lack; and taking a value never changes which
nodes are reported (below). Between calls the caller may use the parser's methods that do not move it
(`getString()`, `getLong()`, `getBigDecimal()`, `getLocation()`, `currentEvent()`, ...), so a scalar
can be read without creating a `JsonValue`. A value the caller leaves is skipped with
`skipObject()`/`skipArray()` when nothing inside it can match.

**Which nodes.** The matcher reports the nodes `query(parser.getValue())` returns, each as often. A
node selected twice (`$[0,0]`, `$..*..*`) is reported twice in a row. Taking an outer value does not hide
the matches inside it (`$..a` on `{"a": {"a": 1}}`): they are reported after it, from that value. The
TCK checks this as "the matcher's paths equal the tree layer's paths sorted into document order".

**Duplicate member names.** The parser delivers every occurrence, and each is a node of its own for the
matcher. The tree layer sees whatever the reader made of them. (Open question Q4.)

### 6.2 Order

The streaming layer reports matches in **document order**: a node before the nodes inside its value,
other nodes in the order in which their values start in the text. The tree layer reports the RFC's
nodelist order. Both report the same nodes the same number of times; the order differs whenever a
segment selects children out of document order or a descendant segment meets nested matches:

- `$..b` on `{"a": {"b": 1}, "b": 2}`: RFC order is `$['b']` (2), then `$['a']['b']` (1), because the
  descendant segment applies `b` to the root before it visits `a`. Document order is 1, then 2.
- `$['b','a']` on `{"a": 1, "b": 2}`: RFC order is `b` then `a` (selector order); document order `a`
  then `b`.
- `$..[1]` on `[[7, 8], 9]`: RFC order 9 then 8; document order 8 then 9.

RFC order can require buffering the whole document (`$..b` must report the root's `b`, which may be the
last member, before anything inside `a`). Document order is also what a caller of a streaming API
expects. Callers that need RFC order use the tree layer. (Open question Q1.)

An earlier draft of this section said both orders are the same for class 1 (no buffering). That is
wrong for selector lists in non-document order (`$['b','a']`, `$[2,0]`); the spec now says document
order for every query, and states when the two orders are the same: if the query has no descendant
segment, every segment has a single selector and no slice has a negative step (and object members
iterate in text order, as for values a JSON-P reader reads). Then every match is at the same depth and
each segment selects children in document order, so RFC order is document order. The TCK checks this
without sorting.

**Decision (Q1): document order.** RFC order has real advantages: it is the order of the standard and of
the CTS, `query()` and `matcher()` would be interchangeable, and some queries express an order on purpose
(`$[::-1]`, `$[-1,0]`, `$['name','id']`). They do not outweigh the costs:

- RFC order makes the most common streaming queries non-streamable: `$..id` must report the root's `id`,
  which may come last, before every `id` inside, so it needs the whole document.
- A hybrid (RFC order within a node's selector list, document order across descendant segments) keeps
  none of the main advantages: as soon as `..` occurs, the result is again "the same nodes in another
  order", so `query()` and `matcher()` are still not interchangeable and the TCK still sorts. It adds
  buffering to selector lists (`$['b','a']` holds `a`, possibly a large value, until `b`), and a rule
  under which `$['b','a']` and `$..['b','a']` order differently.
- The queries whose order is the point (`$[::-1]`, `$[-1,0]`) ask by their meaning for something read
  later; they are a case for `query()`, not for a stream.

One rule, "the matcher reports what it reads in the order it reads it", is easy to state, to implement
alike and to test, and the condition above tells callers exactly when they can rely on RFC order.

### 6.3 Buffering classes

The class is a property of the query alone, computed by `createPath` and returned by `getBuffering()`, so
all implementations agree on it and the TCK tests it exactly. The rules look at the segments of the
query; queries inside filters run on a buffered candidate and matter only for absolute queries.

The question behind the rules: can a selector decide whether it selects a child at the moment the
parser reaches the child? Names, wildcards, non-negative indices and slices counted from the start can.
Filters need the child's value. Negative indices and slices counted from the end need the array's
length. With document order, a node selected by several selectors, or reached by several descendant
segments, is simply reported several times at that point.

**`NONE` — no buffering.** No filter selector, every index non-negative, every slice with start and end
non-negative or omitted and a step that is omitted or at least -1. Descendant segments and selector
lists are unrestricted: `$.store.book[*].title`, `$..id`, `$['a','b']`, `$[0,0]`, `$[1:5:2]`,
`$[::-1]`. Guarantee: when `find()` returns true, the parser's current event is the first event of the
match's value, unless the match lies inside a value the caller took (then the match is reported from
that value), or it repeats the previous match and the caller took that value. The matcher holds no values
other than the ones the caller asked for. Step -1 qualifies: `[5:1:-1]` selects the indices 2 to 5 that
exist, which is known for each index on its own.

**`BOUNDED` — buffering bounded by the query and the tested values.** Everything not in the other two
classes:

- Filters with only relative queries (`[?@.price < 10]`, `[?length(@.tags) > 2]`, `[?@..x]`): each child
  the filter tests is held until its value ends and the filter is decided.
- Negative indices `[-n]` and slices with a start or end `-n`: at most the last `n` elements of an array
  are undecided at any time.
- Slices with a step below -1 and a non-negative start `s` (`[5:1:-2]`): which indices are selected
  depends on whether the array has more than `s` elements, so up to `s + 1` elements are held until
  element `s` or the end of the array is reached.

The matcher holds the values of matches and candidates that depend on such a decision, and nothing else;
a match that comes after an undecided one in document order waits for it. Memory depends on these
values, not on the size of the rest of the document. These values can be large, and the spec says so: a
filter tests the children of the nodes it applies to, so `$[?@.a]` on `{"x": <large>}` holds the large
value, and a filter in a descendant segment applies to the root, so `$..[?@.price < 10]` holds the
children of the root, which are usually most of the document. `$..book[?@.price < 10]` tests only book
elements. That is why `maxBufferedNodes` exists (section 5). Implementations may hold less than the
whole tested child, for example only the members a filter refers to when the candidate cannot be a
match, as long as the results are the same.

**`UNBOUNDED` — may need the whole document.**

- Filters that contain an absolute query anywhere (`[?@.price < $.limit]`, also inside nested filters
  or function arguments): the filter cannot be decided before `$.limit` is read, which may be the last
  member of the document.
- Slices with a step below -1 whose start is negative or omitted and whose end is non-negative or omitted
  (`[::-2]`, `[-1:0:-3]`): the first selected index counts from the end of the array, and which indices
  follow depends on the length modulo the step, so every element is a candidate until the array ends.

`matcher()` rejects these queries with `JsonException` rather than buffering silently, because
materializing would defeat the purpose of the API; the alternative is one line,
`path.query(parser.getValue())`, and the caller decides to accept the memory.

How this differs from the classes presented in jakartaee/jsonp-api#448: there, selector lists and
descendant segments needed buffering, because results were meant to be in RFC order. With document order
they need none. The slices that need the whole array (`[::-2]`) were missing.

### 6.4 Parser state

| When | Parser's current event |
| --- | --- |
| `matcher()` returns | Unchanged; nothing read. |
| `find()` returns true, class `NONE` | First event of the match's value (exceptions above). |
| `find()` returns true, other classes | That event, or after the end of the match's value. |
| `getValue()` read the value from the parser | Last event of the value, as after `JsonParser.getValue()`. |
| `find()` returns false | Last event of the query argument; nothing beyond it read. |

Calls between matcher calls that move the parser (`next()`, `getValue()`, `getObject()`, `skipObject()`,
the stream methods, `close()`) have an unspecified effect.

### 6.5 Limits

`maxVisitedNodes`, `maxDepth` and `maxBufferedNodes` apply per matcher; every value the matcher reads
counts as a visit, plus the visits of filters on buffered candidates. `maxResultSize` does not apply.

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

- The API module is built with `maven-compiler-plugin` release 17, as jakartaee/jsonp-api master is
  since November 2025 (e74e6d7). New elements carry `@since 2.2`, the next specification version in
  `api/pom.xml`.
- `JsonProvider.createPath` (both overloads) has a default implementation, so existing providers keep
  compiling and linking; their `Json.createPath` throws `UnsupportedOperationException` until they
  implement it.
- `JsonPath.getBuffering()` and `JsonPath.matcher()` become abstract methods (decided Q8). `JsonPath` is
  new in 2.2, so there is no implementation to stay compatible with, and JSON-P declares the methods of
  its interfaces abstract unless they are added to an existing interface (as `JsonParser.getObject()`
  in 1.1). An implementation without streaming is not an implementation of 2.2, and an abstract method
  makes that a compile error rather than a runtime `UnsupportedOperationException`. They are default
  methods only until both implementations in the forks provide them, because CI builds Parsson and
  Johnzon against this branch. `JsonProvider.createPath(String, Map)` keeps its default, as
  `JsonProvider` is an existing class.
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

- **Q1 Order in the streaming layer** — decided: document order, with the exact condition under which it
  equals RFC order (section 6.2).
- **Q2 Object member order**: require `entrySet()` iteration order (proposed), or leave it unspecified as
  the RFC does?
- **Q3 Exception type**: plain `JsonException` (proposed for now) or a new `JsonPathException` with
  `getPosition()` from the start?
- **Q4 Duplicate names in the streaming layer** — decided: report every occurrence. The matcher reports
  what the parser delivers; `KEY_STRATEGY` is a reader and builder setting, and following it would need
  buffering for `FIRST` (the first occurrence wins, but it is only known to be the first at the end of the
  object) and would make class `NONE` impossible for any object. Applications that must reject duplicates
  configure the parser to do so.
- **Q5 `^` and `$` in `match()`/`search()`**: follow the CTS (as Treetrail does) or RFC 9485 literally?
- **Q6 `Node.equals`**: leave it unspecified (proposed: compare `getPath()`), or define equality as
  same path and equal value?
- **Q7 Streaming buffer limit** — decided: `maxBufferedNodes`, default 10,000,000 (section 5).
- **Q8 Default methods on `JsonPath`** — decided: abstract, once both implementations in the forks
  provide them (section 8).
- **Q9 Streaming API shape** — decided: values through the matcher (section 6.1). Reading and skipping on
  the parser directly, as sketched in jakartaee/jsonp-api#448, would work only for class `NONE`, would
  need `JsonParser.currentEvent()` to find out what the caller consumed, and would let the caller's
  calls change which nodes are reported. The parser stays readable at a match of class `NONE` with the
  methods that do not move it, so a scalar can still be read without creating a `JsonValue`.
