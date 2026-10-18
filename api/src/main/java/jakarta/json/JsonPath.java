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
 * {@link JsonException}; it never returns a partial result.</p>
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
}
