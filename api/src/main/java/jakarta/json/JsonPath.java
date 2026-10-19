/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package jakarta.json;

import java.util.List;

import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;

/**
 * <p>This interface represents an immutable implementation of a JSONPath query
 * as defined by <a href="https://www.rfc-editor.org/rfc/rfc9535">RFC 9535</a>.
 * </p>
 * <p>A JSONPath query, when applied to a {@link JsonValue} (the <i>query argument</i>),
 * selects zero or more nodes of that value. A node is a value together with its
 * location in the query argument, see {@link Node}. The method {@link #query query()}
 * returns the selected nodes.</p>
 * <p>For example, the following code selects the titles of all books cheaper than 10:</p>
 * <pre>{@code
 * JsonPath path = Json.createPath("$.store.book[?@.price < 10].title");
 * for (JsonPath.Node node : path.query(document)) {
 *     JsonValue title = node.getValue();
 *     String location = node.getPath();       // for example $['store']['book'][2]['title']
 * }
 * }</pre>
 *
 * <p>All errors in the query string, including integers outside the range
 * [-(2<sup>53</sup>)+1, (2<sup>53</sup>)-1] and invalid uses of functions, are reported
 * when the query is created ({@link Json#createPath(String)}). A query that is valid never
 * fails because of the structure of the query argument: a name that does not exist or an
 * index outside an array selects nothing.</p>
 *
 * <p>Semantics on JSON Processing values:</p>
 * <ul>
 *     <li>Numbers are compared by their {@link JsonNumber#bigDecimalValue()} with
 *     {@link java.math.BigDecimal#compareTo compareTo}, so {@code 1} and {@code 1.0} are equal,
 *     unlike with {@link JsonNumber#equals JsonNumber.equals}. Arrays and objects in comparisons
 *     are equal if their elements or members are equal in this sense.</li>
 *     <li>Strings are compared by Unicode code points, and member names match only if they are
 *     identical sequences of code points; no Unicode normalization is applied.</li>
 *     <li>JSON {@code null} is a value like any other: {@code $.a} selects a node with the value
 *     {@link JsonValue#NULL} from {@code {"a": null}} and selects nothing from {@code {}}.</li>
 *     <li>The functions {@code length()}, {@code count()}, {@code match()}, {@code search()} and
 *     {@code value()} of RFC 9535 are supported; other function names are errors.
 *     {@code match()} and {@code search()} use I-Regexp
 *     (<a href="https://www.rfc-editor.org/rfc/rfc9485">RFC 9485</a>) and take time linear in the
 *     length of the string they test.</li>
 * </ul>
 *
 * <p>Implementations bound the work a query may do, so that queries and documents from untrusted
 * sources cannot exhaust time or memory. A query that exceeds such a limit fails with a
 * {@link JsonException}; it never returns a partial result. The limits are configured with
 * {@link Json#createPath(String, java.util.Map)} and the properties {@link #MAX_VISITED_NODES},
 * {@link #MAX_DEPTH}, {@link #MAX_RESULT_SIZE}, {@link #MAX_BUFFERED_NODES}, {@link #MAX_NESTING} and
 * {@link #MAX_REGEX_SIZE};
 * {@link Json#createPath(String)} uses the defaults given there.</p>
 *
 * <p>The method {@link #matcher matcher()} evaluates a query over a {@link JsonParser}, so that
 * a large document is never materialized. It reports the same nodes as {@link #query query()},
 * in document order, and lets the caller decide for each match whether to read its value:</p>
 * <pre>{@code
 * JsonPath path = Json.createPath("$.orders[*].id");
 * try (JsonParser parser = Json.createParser(input)) {
 *     JsonPath.Matcher matcher = path.matcher(parser);
 *     while (matcher.find()) {
 *         String location = matcher.getPath();  // for example $['orders'][7]['id']
 *         JsonValue id = matcher.getValue();    // or leave the value unread
 *     }
 * }
 * }</pre>
 * <p>Whether and how much a query must buffer to be evaluated over a parser is a property of the
 * query alone, given by {@link #getBuffering()}; see {@link Buffering}.</p>
 *
 * <p>Instances are immutable and safe for use by multiple concurrent threads.</p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9535">RFC 9535</a>
 * @see Json#createPath(String)
 * @see JsonPointer
 *
 * @since 2.2
 */
public interface JsonPath {

    /**
     * Configuration property to limit the work of one evaluation: the number of times it reaches a
     * node of the query argument. Each node a selector selects, each node a descendant segment walks
     * through and each node a filter tests counts, also within queries inside filters; with
     * {@link #matcher matcher()}, also each value read from the parser. Implementations may avoid
     * work that cannot change the result, for example evaluate an absolute query inside a filter only
     * once, so the exact count is implementation specific; it is never less than the number of
     * selected nodes. The value is a non-negative integer. The default is 100,000,000.
     * <p>Exceeding the limit throws {@link JsonException} from {@link #query query()} or
     * {@link Matcher#find()}.</p>
     *
     * @see Json#createPath(String, java.util.Map)
     * @since 2.2
     */
    String MAX_VISITED_NODES = "jakarta.json.JsonPath.maxVisitedNodes";

    /**
     * Configuration property to limit how deep an evaluation reaches into the query argument and how
     * deep {@code ==} and {@code !=} compare values. The query argument has depth 0, its children
     * depth 1. An evaluation <i>reaches</i> a node when a selector selects or tests it or a
     * descendant segment walks through it, also within queries inside filters; reaching a node deeper
     * than the limit throws {@link JsonException} from {@link #query query()} or
     * {@link Matcher#find()}. Comparisons count the depth from the compared values and throw when
     * they would compare values nested deeper than the limit. The value is a non-negative integer.
     * The default is 1,000.
     * <p>The matcher reaches every element of an array as a candidate of an index or slice counted
     * from the end, while {@code query()} reaches only the selected ones, so for the same query and
     * document the matcher may throw where {@code query()} does not.</p>
     *
     * @see Json#createPath(String, java.util.Map)
     * @since 2.2
     */
    String MAX_DEPTH = "jakarta.json.JsonPath.maxDepth";

    /**
     * Configuration property to limit the number of nodes in the result of {@link #query query()}.
     * It does not apply to {@link #matcher matcher()}, which does not collect matches. The value is
     * a non-negative integer. By default the number is not limited.
     * <p>Exceeding the limit throws {@link JsonException} from {@link #query query()}.</p>
     *
     * @see Json#createPath(String, java.util.Map)
     * @since 2.2
     */
    String MAX_RESULT_SIZE = "jakarta.json.JsonPath.maxResultSize";

    /**
     * Configuration property to limit what {@link #matcher matcher()} buffers: the number of nodes in
     * the values it holds at one time because it cannot report or decide them when the parser
     * reaches them. These are the matches that wait for a decision of a filter or of an index or
     * slice counted from the end, or for an earlier match; and the children that a filter tests,
     * until the filter is decided. Values the caller requests with {@link Matcher#getValue()}, and
     * the matches inside them, do not count. Implementations may count a node for longer than
     * strictly needed, for example while a value inside it is still held, but never count a node of
     * a held value less than once. The value is a non-negative integer. The default is 10,000,000.
     * <p>A query of class {@link Buffering#NONE NONE} holds no such values, so this limit never
     * applies to it, even with the value 0. Exceeding the limit throws {@link JsonException} from
     * {@link Matcher#find()} or {@link Matcher#getValue()}. The limit does not apply to
     * {@link #query query()}.</p>
     *
     * @see Json#createPath(String, java.util.Map)
     * @since 2.2
     */
    String MAX_BUFFERED_NODES = "jakarta.json.JsonPath.maxBufferedNodes";

    /**
     * Configuration property to limit the nesting of filter selectors, parenthesized expressions
     * and function calls in the query string: {@code $[?(@.a)]} has nesting 2 (a filter and a
     * parenthesized expression inside it), {@code $[?@[?length(@) > 1]]} has nesting 3. The value is
     * a non-negative integer. The default is 64.
     * <p>Exceeding the limit throws {@link JsonException} from
     * {@link Json#createPath(String, java.util.Map) createPath()}.</p>
     *
     * @see Json#createPath(String, java.util.Map)
     * @since 2.2
     */
    String MAX_NESTING = "jakarta.json.JsonPath.maxNesting";

    /**
     * Configuration property to limit the size of a regular expression in {@code match()} and
     * {@code search()} after counted repetitions are expanded, so that {@code (a{1000}){1000}}
     * cannot exhaust memory. The size is measured in a unit chosen by the implementation that
     * grows at least linearly with the number of characters and character classes of the expanded
     * expression; implementations document the unit. A larger expression is treated as an invalid
     * pattern, so the function returns false (RFC 9485, section 8, allows such limits). The value is
     * a non-negative integer. The default is implementation specific and documented by the
     * implementation.
     *
     * @see Json#createPath(String, java.util.Map)
     * @since 2.2
     */
    String MAX_REGEX_SIZE = "jakarta.json.JsonPath.maxRegexSize";

    /**
     * Applies this query to the specified {@code target} and returns the selected nodes.
     * <p>The nodes are in the order defined by RFC 9535: array elements in array order, and
     * members of an object in the iteration order of its {@link JsonObject#entrySet() entrySet()}.
     * A node selected more than once, for example by {@code $[0,0]}, appears as many times in the list.
     * The result is empty if the query selects nothing.</p>
     *
     * @param target the query argument, any JSON value; a primitive value is the only node
     * that a query can select from it
     * @return an unmodifiable list of the selected nodes, possibly empty
     * @throws NullPointerException if {@code target} is {@code null}
     * @throws JsonException if the query exceeds a limit of the implementation
     */
    List<Node> query(JsonValue target);

    /**
     * Returns the query string this JSONPath was created from.
     *
     * @return the query string, as passed to {@link Json#createPath(String)}
     */
    @Override
    String toString();

    /**
     * Returns how much this query must buffer when it is evaluated over a {@link JsonParser} with
     * {@link #matcher matcher()}. The result depends on the query alone, see {@link Buffering}.
     * <p>The default implementation throws {@link UnsupportedOperationException}; implementations
     * of this specification override it.</p>
     *
     * @return the buffering class of this query
     * @throws UnsupportedOperationException if the implementation does not support streaming
     *
     * @since 2.2
     */
    default Buffering getBuffering() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a matcher that evaluates this query over the JSON value that {@code parser} reads next,
     * the query argument, and reports the selected nodes one at a time. The parser must be positioned
     * before that value: newly created, after a {@link JsonParser.Event#KEY_NAME KEY_NAME} event, or
     * before an element of an array. This method reads nothing from the parser; the matcher reads
     * the query argument up to its last event and no further, and does not close the parser.
     * <p>The matcher reports the same nodes as {@code query(parser.getValue())}, each as often, but in
     * document order; see {@link Matcher}. A query whose {@link #getBuffering() buffering class} is
     * {@link Buffering#UNBOUNDED UNBOUNDED} is rejected: it may need the whole document, which is
     * what this method avoids. Callers that accept that evaluate {@code query(parser.getValue())}.</p>
     * <p>The default implementation throws {@link UnsupportedOperationException}; implementations
     * of this specification override it.</p>
     *
     * @param parser the parser to read the query argument from
     * @return a matcher over {@code parser}
     * @throws NullPointerException if {@code parser} is {@code null}
     * @throws JsonException if the buffering class of this query is {@link Buffering#UNBOUNDED}
     * @throws UnsupportedOperationException if the implementation does not support streaming
     *
     * @since 2.2
     */
    default Matcher matcher(JsonParser parser) {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>How much a query must buffer when {@link JsonPath#matcher matcher()} evaluates it over a
     * {@link JsonParser}. The class follows from the syntax of the query alone, by the rules below,
     * so all implementations agree on it. The rules look at the segments of the query; queries
     * inside filter selectors are evaluated on buffered values and only matter for
     * {@link #UNBOUNDED} (absolute queries).</p>
     * <p>Matches are reported in document order (see {@link Matcher}). That order makes descendant
     * segments and selector lists cheap: a node is reported when the parser reaches it, and a node
     * selected twice is reported twice at that point. What needs buffering is a selector that cannot
     * decide whether it selects a child when the parser reaches the child: a filter, which needs the
     * child's value, and an index or slice that counts from the end of the array, which needs the
     * array's length.</p>
     *
     * @since 2.2
     */
    enum Buffering {

        /**
         * The query needs no buffering: the matcher holds no values of the query argument except
         * those the caller requests with {@link Matcher#getValue()}. A query is in this class if
         * it contains no filter selector outside of queries inside filters (so none at all), every
         * index selector is non-negative, and every slice selector has a start and an end that are
         * non-negative or omitted and a step that is omitted or at least -1. Name selectors,
         * wildcards, descendant segments and selector lists are allowed without restriction, for
         * example {@code $.store.book[*].title}, {@code $..id}, {@code $['a','b']}, {@code $[0,0]},
         * {@code $[1:5:2]} and {@code $[::-1]}. {@link JsonPath#MAX_BUFFERED_NODES} never applies to
         * these queries.
         */
        NONE,

        /**
         * The query needs buffering bounded by the query and by the size of the values it tests,
         * independent of the size of the rest of the document. A query is in this class if it is
         * not in {@link #NONE} or {@link #UNBOUNDED}, that is, if it contains
         * <ul>
         *     <li>a filter selector whose expression contains no absolute query, such as
         *     {@code $.items[?@.price < 10]}: the matcher may hold each child it tests until the
         *     filter is decided;</li>
         *     <li>a negative index {@code -n}, or a slice with a start or end {@code -n}: the
         *     matcher may hold the last {@code n} elements of an array until it ends;</li>
         *     <li>a slice with a negative step other than -1 and a non-negative start {@code s},
         *     such as {@code $[5:1:-2]}: the matcher may hold {@code s + 1} elements, until it reaches
         *     element {@code s} or the end of the array.</li>
         * </ul>
         * <p>The matcher holds the values of matches and of candidates that depend on such
         * a decision, and nothing else. How much that is depends on the document: a filter tests
         * the children of the nodes it applies to, and a filter in a descendant segment, such as
         * {@code $..[?@.price < 10]}, applies to the query argument itself, so it tests the
         * children of the root, which may make up most of the document. A filter after a selection,
         * such as {@code $..book[?@.price < 10]}, tests only the elements of the selected arrays.
         * {@link JsonPath#MAX_BUFFERED_NODES} bounds what the matcher may hold.</p>
         */
        BOUNDED,

        /**
         * The query may need the whole document, so {@link JsonPath#matcher matcher()} rejects it.
         * A query is in this class if
         * <ul>
         *     <li>a filter selector contains an absolute query, such as
         *     {@code $.items[?@.price < $.limit]}, at any depth: the filter cannot be decided before
         *     {@code $.limit} is read, which may be the last member of the document; or</li>
         *     <li>a slice of one of its segments has a step less than -1, a start that is negative
         *     or omitted, and an end that is non-negative or omitted, such as {@code $[::-2]}: which
         *     elements it selects depends on the length of the array, so every element is a
         *     candidate until the array ends.</li>
         * </ul>
         */
        UNBOUNDED
    }

    /**
     * <p>A node selected by a {@link JsonPath} query: a value of the query argument together
     * with its location in the query argument.</p>
     * <p>Two nodes are the same node if they have the same {@link #getPath() normalized path}.
     * Equal values at different locations are different nodes. Instances of this interface do not
     * define {@code equals}; compare {@link #getPath()} instead.</p>
     *
     * @since 2.2
     */
    interface Node {

        /**
         * Returns the value of this node. It is the value as the parent's
         * {@link JsonObject#get(Object)} or {@link JsonArray#get(int)} returns it, or the query
         * argument itself for the root node. It is never {@code null}; JSON {@code null} is
         * {@link JsonValue#NULL}.
         *
         * @return the value of this node
         */
        JsonValue getValue();

        /**
         * Returns the location of this node as a Normalized Path, defined in
         * <a href="https://www.rfc-editor.org/rfc/rfc9535#section-2.7">section 2.7 of RFC 9535</a>:
         * {@code $} followed by member names in single quotes and array indices in brackets, for
         * example {@code $['store']['book'][0]}. Each node of a query argument has exactly one
         * Normalized Path.
         *
         * @return the Normalized Path of this node
         */
        String getPath();

        /**
         * Returns the location of this node as a JSON Pointer
         * (<a href="http://tools.ietf.org/html/rfc6901">RFC 6901</a>). The pointer
         * of the root node is the empty pointer. Applied to the query argument, the pointer
         * references the value of this node, so it can be used to change the document with
         * {@link JsonPointer} or {@link JsonPatch}.
         *
         * @return the JSON Pointer to this node
         */
        JsonPointer getPointer();
    }

    /**
     * <p>Evaluates a {@link JsonPath} over a {@link JsonParser} and reports the selected nodes, the
     * <i>matches</i>, one at a time. Created by {@link JsonPath#matcher matcher()}. The method
     * {@link #find()} advances to the next match; {@link #getPath()}, {@link #getPointer()},
     * {@link #getLocation()} and {@link #getValue()} describe the current match.</p>
     *
     * <p><b>Matches.</b> For a query argument without duplicate member names, the matches are the
     * nodes that {@link JsonPath#query query()} selects from the same value, each reported as many
     * times as {@code query()} selects it. Where the parser delivers a member name more than once in
     * an object, each occurrence is a node of its own.</p>
     *
     * <p><b>Order.</b> Matches are reported in document order: a node before the nodes inside its
     * value, and nodes in different values in the order in which the values start in the JSON text.
     * A node that is selected several times is reported that many times in a row. This is not the
     * order of {@code query()}, which follows RFC 9535: for {@code $..b} on
     * {@code {"a":{"b":1},"b":2}}, {@code query()} returns {@code $['b']} before {@code $['a']['b']},
     * the matcher reports {@code $['a']['b']} first; for {@code $['b','a']} on {@code {"a":1,"b":2}},
     * {@code query()} returns {@code $['b']} first, the matcher {@code $['a']}. RFC 9535 order can
     * require buffering the whole document; callers that need it use {@code query()}. The two orders
     * are the same if the query has no descendant segment, every segment has a single selector, and
     * no slice has a negative step, provided that the members of each object of the value passed to
     * {@code query()} iterate in the order of the text, as they do for values that a JSON-P reader
     * reads from that text.</p>
     *
     * <p><b>Values.</b> The matcher reads a match's value only if the caller calls
     * {@link #getValue()}. A value the caller does not request is skipped: when nothing inside it can
     * be selected, the matcher skips it with {@link JsonParser#skipObject()} or
     * {@link JsonParser#skipArray()}; otherwise it reads on through the value to the matches inside
     * it. Requesting a value does not change which nodes are reported: the matches inside a value
     * that the caller requested are reported after it, from that value.</p>
     *
     * <p><b>Parser state.</b> The matcher advances the parser; between calls of the matcher, the
     * caller may only call methods of the parser that do not change its state
     * ({@link JsonParser#currentEvent()}, {@link JsonParser#getString()},
     * {@link JsonParser#isIntegralNumber()}, {@link JsonParser#getInt()},
     * {@link JsonParser#getLong()}, {@link JsonParser#getBigDecimal()} and
     * {@link JsonParser#getLocation()}); the effect of any other call is not specified.</p>
     * <ul>
     *     <li>When {@link #find()} returns {@code true} for a query of buffering class
     *     {@link Buffering#NONE NONE}, the current event of the parser is the first event of the
     *     match's value ({@code START_OBJECT}, {@code START_ARRAY} or a {@code VALUE_} event), so its
     *     value can be read right there, unless the match lies inside a value that the caller
     *     requested before, or is the same node as the previous match and the caller requested its
     *     value. For other queries the parser is at that event or after the end of the value.</li>
     *     <li>When {@link #getValue()} reads the value from the parser, the current event of the
     *     parser afterwards is the last event of the value, as after {@link JsonParser#getValue()}.</li>
     *     <li>When {@link #find()} returns {@code false}, the current event of the parser is the last
     *     event of the query argument. The parser has not read further, so the caller can continue
     *     to use it.</li>
     * </ul>
     *
     * <p><b>Errors.</b> {@link jakarta.json.stream.JsonParsingException} and
     * other exceptions of the parser pass through {@link #find()} and {@link #getValue()}. Exceeding
     * a limit of the query (see {@link JsonPath#MAX_VISITED_NODES}, {@link JsonPath#MAX_DEPTH},
     * {@link JsonPath#MAX_BUFFERED_NODES}) throws {@link JsonException}. After an exception the matcher and the parser cannot be used
     * further.</p>
     *
     * <p>A matcher is meant for use by one thread.</p>
     *
     * @since 2.2
     */
    interface Matcher {

        /**
         * Advances to the next match. The first call reads the first event of the query argument.
         *
         * @return {@code true} if there is a next match, {@code false} if the query argument has
         * been read to its end and all its matches have been reported
         * @throws IllegalStateException if the first event the parser returns does not start a JSON
         * value, for example {@code END_ARRAY}
         * @throws JsonException if the evaluation exceeds a limit of the query, or if an i/o error
         * occurs (IOException would be cause of JsonException)
         * @throws jakarta.json.stream.JsonParsingException if the parser encounters invalid JSON
         */
        boolean find();

        /**
         * Returns the location of the current match as a Normalized Path (RFC 9535, section 2.7),
         * relative to the query argument, as {@link Node#getPath()} does.
         *
         * @return the Normalized Path of the current match
         * @throws IllegalStateException if {@link #find()} has not returned {@code true} yet, or
         * has returned {@code false}
         */
        String getPath();

        /**
         * Returns the location of the current match as a JSON Pointer, relative to the query
         * argument, as {@link Node#getPointer()} does.
         *
         * @return the JSON Pointer to the current match
         * @throws IllegalStateException if {@link #find()} has not returned {@code true} yet, or
         * has returned {@code false}
         */
        JsonPointer getPointer();

        /**
         * Returns the location of the current match in the JSON text: the location that
         * {@link JsonParser#getLocation()} returned when the current event of the parser was the
         * first event of the match's value.
         *
         * @return the location of the start of the current match's value
         * @throws IllegalStateException if {@link #find()} has not returned {@code true} yet, or
         * has returned {@code false}
         */
        JsonLocation getLocation();

        /**
         * Returns the value of the current match, reading it from the parser if the matcher has not
         * read it yet. Repeated calls for the same match return equal values. Matches inside the
         * value are still reported.
         *
         * @return the value of the current match, never {@code null}
         * @throws IllegalStateException if {@link #find()} has not returned {@code true} yet, or
         * has returned {@code false}
         * @throws JsonException if the evaluation exceeds a limit of the query, or if an i/o error
         * occurs (IOException would be cause of JsonException)
         * @throws jakarta.json.stream.JsonParsingException if the parser encounters invalid JSON
         */
        JsonValue getValue();
    }
}
