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

package ee.jakarta.tck.jsonp.api.jsonpathtests;

import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonPath;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static ee.jakarta.tck.jsonp.api.jsonpathtests.JsonPathUtil.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@see <a href="https://www.rfc-editor.org/rfc/rfc9535">RFC 9535</a>}: JSONPath
 * configuration tests.<br>
 * JSON-P API defines {@link Json#createPath(String, Map)} with the configuration
 * properties of {@link JsonPath} to limit queries from untrusted sources. These tests check
 * the property values, each limit for {@link JsonPath#query} and {@link JsonPath#matcher},
 * and the defaults that the specification fixes.
 */
@Tag(JsonPathUtil.TAG)
@Tag(JsonPathUtil.CONFIG_TAG)
public class JsonPathConfigTests {

  private static JsonPath path(final String query, final String property, final Object value) {
    return Json.createPath(query, Collections.singletonMap(property, value));
  }

  private static String numbers(final int n) {
    final StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < n; i++) {
      sb.append(i == 0 ? "" : ",").append(i);
    }
    return sb.append(']').toString();
  }

  /** Counts the matches of a matcher over {@code json}; limits surface as exceptions. */
  private static int streamCount(final JsonPath path, final String json) {
    try (JsonParser parser = Json.createParser(new StringReader(json))) {
      final JsonPath.Matcher matcher = path.matcher(parser);
      int count = 0;
      while (matcher.find()) {
        count++;
      }
      return count;
    }
  }

  /* Tests */

  /**
   * @testName: jsonPathCreateWithConfigTest
   *
   * @assertion_ids: JSONP:JAVADOC:699;
   *
   * @test_Strategy: Json.createPath(String, Map) and JsonProvider.createPath(String, Map)
   * with an empty map, and with a map of an unknown property, create queries that behave as
   * createPath(String). A null query or map throws NullPointerException; an invalid query
   * throws JsonException.
   */
  @Test
  public void jsonPathCreateWithConfigTest() {
    final Map<String, Object> unknown = new HashMap<>();
    unknown.put("org.example.unknown.property", "anything");
    final JsonValue document = parse(numbers(3));
    final JsonPath[] paths = {
        Json.createPath("$[*]", Collections.<String, Object>emptyMap()),
        Json.createPath("$[*]", unknown),
        JsonProvider.provider().createPath("$[*]", Collections.<String, Object>emptyMap()),
    };
    for (JsonPath path : paths) {
      assertNotNull(path);
      assertEquals("$[*]", path.toString());
      assertEquals(3, path.query(document).size());
      assertEquals(3, streamCount(path, numbers(3)));
    }
    assertThrows(NullPointerException.class, () -> Json.createPath(null, unknown));
    assertThrows(NullPointerException.class, () -> Json.createPath("$", null));
    assertThrows(JsonException.class, () -> Json.createPath("$[", unknown));
  }

  /**
   * @testName: jsonPathConfigValuesTest
   *
   * @assertion_ids: JSONP:JAVADOC:699; JSONP:JAVADOC:700;
   *
   * @test_Strategy: The value of a configuration property is a non-negative integer given
   * as a Number or a decimal String. Negative numbers, non-integers, other strings and other
   * types make createPath throw JsonException, for each of the six properties.
   */
  @Test
  public void jsonPathConfigValuesTest() {
    final JsonValue document = parse(numbers(3));
    for (Object value : new Object[] {2, 2L, "2"}) {
      final JsonPath path = path("$[*]", JsonPath.MAX_RESULT_SIZE, value);
      assertThrows(JsonException.class, () -> path.query(document), "maxResultSize " + value);
    }
    final String[] properties = {JsonPath.MAX_VISITED_NODES, JsonPath.MAX_DEPTH,
        JsonPath.MAX_RESULT_SIZE, JsonPath.MAX_BUFFERED_NODES, JsonPath.MAX_NESTING,
        JsonPath.MAX_REGEX_SIZE};
    for (String property : properties) {
      for (Object value : new Object[] {-1, -1L, "-1", "abc", 1.5, Boolean.TRUE, new Object()}) {
        assertThrows(JsonException.class, () -> path("$", property, value), property + " = " + value);
      }
    }
    assertEquals("jakarta.json.JsonPath.maxVisitedNodes", JsonPath.MAX_VISITED_NODES);
    assertEquals("jakarta.json.JsonPath.maxDepth", JsonPath.MAX_DEPTH);
    assertEquals("jakarta.json.JsonPath.maxResultSize", JsonPath.MAX_RESULT_SIZE);
    assertEquals("jakarta.json.JsonPath.maxNesting", JsonPath.MAX_NESTING);
    assertEquals("jakarta.json.JsonPath.maxRegexSize", JsonPath.MAX_REGEX_SIZE);
  }

  /**
   * @testName: jsonPathMaxResultSizeTest
   *
   * @assertion_ids: JSONP:JAVADOC:700;
   *
   * @test_Strategy: maxResultSize limits the result of query(): a larger result throws
   * JsonException, one of the limit's size does not. It does not apply to the matcher. By
   * default the result size is not limited.
   */
  @Test
  public void jsonPathMaxResultSizeTest() {
    final JsonValue document = parse(numbers(3));
    assertEquals(3, path("$[*]", JsonPath.MAX_RESULT_SIZE, 3).query(document).size());
    assertThrows(JsonException.class, () -> path("$[*]", JsonPath.MAX_RESULT_SIZE, 2).query(document));
    assertEquals(3, streamCount(path("$[*]", JsonPath.MAX_RESULT_SIZE, 2), numbers(3)));
    assertEquals(100_000, Json.createPath("$[*]").query(parse(numbers(100_000))).size());
  }

  /**
   * @testName: jsonPathMaxVisitedNodesTest
   *
   * @assertion_ids: JSONP:JAVADOC:700;
   *
   * @test_Strategy: The count of visited nodes is never less than the number of selected
   * nodes, so a limit below the result size throws JsonException from query() and from the
   * matcher's find(); a limit of 100 times the size of the document does not.
   */
  @Test
  public void jsonPathMaxVisitedNodesTest() {
    final JsonValue document = parse(numbers(10));
    for (String query : new String[] {"$[*]", "$..*", "$[?@ >= 0]"}) {
      assertEquals(10, path(query, JsonPath.MAX_VISITED_NODES, 1_100).query(document).size(), query);
      assertEquals(10, streamCount(path(query, JsonPath.MAX_VISITED_NODES, 1_100), numbers(10)), query);
      assertThrows(JsonException.class, () -> path(query, JsonPath.MAX_VISITED_NODES, 9).query(document),
          query);
      assertThrows(JsonException.class, () -> streamCount(path(query, JsonPath.MAX_VISITED_NODES, 9),
          numbers(10)), query);
    }
  }

  /**
   * @testName: jsonPathMaxDepthTest
   *
   * @assertion_ids: JSONP:JAVADOC:700;
   *
   * @test_Strategy: maxDepth limits how deep a descendant segment walks into the query
   * argument, whose depth is 0: $..* reaches the innermost value of [[[[1]]]], at depth 4,
   * with the limit 4 and throws JsonException with the limit 3, for query() and the matcher.
   * The default is 1,000: $..* walks to depth 1,000 and fails for depth 1,001.
   */
  @Test
  public void jsonPathMaxDepthTest() {
    final String json = "[[[[1]]]]";
    assertEquals(4, path("$..*", JsonPath.MAX_DEPTH, 4).query(parse(json)).size());
    assertEquals(4, streamCount(path("$..*", JsonPath.MAX_DEPTH, 4), json));
    assertThrows(JsonException.class, () -> path("$..*", JsonPath.MAX_DEPTH, 3).query(parse(json)));
    assertThrows(JsonException.class, () -> streamCount(path("$..*", JsonPath.MAX_DEPTH, 3), json));

    final JsonPath descendants = Json.createPath("$..*");
    assertEquals(1_000, descendants.query(nestedArrays(1_000)).size());
    assertThrows(JsonException.class, () -> descendants.query(nestedArrays(1_001)));
  }

  /**
   * @testName: jsonPathMaxDepthReachedTest
   *
   * @assertion_ids: JSONP:JAVADOC:700;
   *
   * @test_Strategy: An evaluation throws JsonException when it reaches a node deeper than
   * maxDepth, whichever selector reaches it: names and indices, wildcards, filters, slices
   * and descendant segments alike, for query() and the matcher. A node that is not reached
   * does not count: an empty array at the limit, or a name that selects nothing.
   */
  @Test
  public void jsonPathMaxDepthReachedTest() {
    final String json = "[[[1]]]";
    for (String query : new String[] {"$[0][0][0]", "$[*][*][*]", "$..*", "$[0][0][?@ == 1]",
        "$[0][0][0:1]"}) {
      assertEquals(1, path(query, JsonPath.MAX_DEPTH, 3).query(parse(json)).size() > 0 ? 1 : 0, query);
      streamCount(path(query, JsonPath.MAX_DEPTH, 3), json);
      assertThrows(JsonException.class, () -> path(query, JsonPath.MAX_DEPTH, 2).query(parse(json)), query);
      assertThrows(JsonException.class, () -> streamCount(path(query, JsonPath.MAX_DEPTH, 2), json), query);
    }
    assertEquals(2, path("$..*", JsonPath.MAX_DEPTH, 2).query(parse("[[[]]]")).size());
    assertEquals(2, streamCount(path("$..*", JsonPath.MAX_DEPTH, 2), "[[[]]]"));
    assertEquals(0, path("$[0][0].x", JsonPath.MAX_DEPTH, 2).query(parse(json)).size());
    assertEquals(0, streamCount(path("$[0][0].x", JsonPath.MAX_DEPTH, 2), json));
  }

  /**
   * @testName: jsonPathMaxBufferedNodesTest
   *
   * @assertion_ids: JSONP:JAVADOC:701;
   *
   * @test_Strategy: maxBufferedNodes limits the nodes the matcher holds because it cannot
   * report or decide them yet; exceeding it throws JsonException from find(). A filter that
   * tests a child of 12 nodes holds them until it is decided, so the limit 11 fails and the
   * limit 1,000 does not. $[-500] keeps at least 500 elements undecided, so the limit 100
   * fails. A query of class NONE holds nothing and works with the limit 0, also when the
   * caller takes every value. The limit does not apply to query().
   */
  @Test
  public void jsonPathMaxBufferedNodesTest() {
    final String candidate = "[{\"a\": 1, \"b\": [1, 2, 3, 4, 5, 6, 7, 8, 9]}]";
    assertEquals(1, streamCount(path("$[?@.a]", JsonPath.MAX_BUFFERED_NODES, 1_000), candidate));
    assertThrows(JsonException.class, () -> streamCount(path("$[?@.a]", JsonPath.MAX_BUFFERED_NODES, 11), candidate));
    assertEquals(1, path("$[?@.a]", JsonPath.MAX_BUFFERED_NODES, 0).query(parse(candidate)).size());

    final String numbers = numbers(1_000);
    assertThrows(JsonException.class, () -> streamCount(path("$[-500]", JsonPath.MAX_BUFFERED_NODES, 100), numbers));
    assertEquals(1, streamCount(path("$[-500]", JsonPath.MAX_BUFFERED_NODES, 10_000), numbers));

    for (String query : new String[] {"$..*", "$[*]", "$[0,0]", "$[::-1]", "$..[0]"}) {
      final JsonPath path = path(query, JsonPath.MAX_BUFFERED_NODES, 0);
      assertEquals(JsonPath.Buffering.NONE, path.getBuffering(), query);
      try (JsonParser parser = Json.createParser(new StringReader("[[1, [2]], {\"a\": [3]}]"))) {
        final JsonPath.Matcher matcher = path.matcher(parser);
        while (matcher.find()) {
          assertNotNull(matcher.getValue(), query);
        }
      }
    }
    assertEquals("jakarta.json.JsonPath.maxBufferedNodes", JsonPath.MAX_BUFFERED_NODES);
  }

  /**
   * @testName: jsonPathMaxNestingTest
   *
   * @assertion_ids: JSONP:JAVADOC:700;
   *
   * @test_Strategy: maxNesting limits the nesting of filter selectors, parenthesized
   * expressions and function calls; createPath throws JsonException beyond it. With the
   * limit 1, $[?@.a] is accepted and $[?(@.a)], $[?@[?@.a]] and $[?length(@) > 1] are not.
   * With the limit 3, $[?@[?length(@) > 1]] is accepted, with 2 it is not. The default is
   * 64: 64 nested filters are accepted, 65 are not.
   */
  @Test
  public void jsonPathMaxNestingTest() {
    assertEquals(1, path("$[?@.a]", JsonPath.MAX_NESTING, 1).query(parse("[{\"a\": 1}]")).size());
    for (String query : new String[] {"$[?(@.a)]", "$[?@[?@.a]]", "$[?length(@) > 1]"}) {
      assertThrows(JsonException.class, () -> path(query, JsonPath.MAX_NESTING, 1), query);
    }
    assertNotNull(path("$[?@[?length(@) > 1]]", JsonPath.MAX_NESTING, 3));
    assertThrows(JsonException.class, () -> path("$[?@[?length(@) > 1]]", JsonPath.MAX_NESTING, 2));
    assertNotNull(Json.createPath(nestedFilters(64)));
    assertThrows(JsonException.class, () -> Json.createPath(nestedFilters(65)));
  }

  /**
   * @testName: jsonPathMaxRegexSizeTest
   *
   * @assertion_ids: JSONP:JAVADOC:700;
   *
   * @test_Strategy: maxRegexSize limits regular expressions in match() and search(), in
   * literals and in patterns from the document; a larger expression is an invalid pattern,
   * so the function returns false. Its unit grows at least linearly with the characters of
   * the expanded expression: with the limit 0 no pattern of a character is valid; with the
   * limit 1,000, 'ab' is valid and 'a{100000}' is not.
   */
  @Test
  public void jsonPathMaxRegexSizeTest() {
    final JsonValue document = parse("[{\"s\": \"ab\", \"p\": \"ab\"}, {\"s\": \"aaa\", \"p\": \"a{100000}\"}]");
    assertEquals(0, path("$[?match(@.s, 'ab')]", JsonPath.MAX_REGEX_SIZE, 0).query(document).size());
    assertEquals(0, path("$[?search(@.s, 'a')]", JsonPath.MAX_REGEX_SIZE, 0).query(document).size());
    assertEquals(1, path("$[?match(@.s, 'ab')]", JsonPath.MAX_REGEX_SIZE, 1_000).query(document).size());
    assertEquals(0, path("$[?search(@.s, 'a{100000}')]", JsonPath.MAX_REGEX_SIZE, 1_000).query(document).size());
    assertEquals(1, path("$[?match(@.s, @.p)]", JsonPath.MAX_REGEX_SIZE, 1_000).query(document).size());
    assertEquals(0, path("$[?match(@.s, @.p)]", JsonPath.MAX_REGEX_SIZE, 0).query(document).size());
  }

  /* Helpers */

  private static JsonValue nestedArrays(final int depth) {
    JsonValue value = Json.createValue(1);
    for (int i = 0; i < depth; i++) {
      final JsonArrayBuilder array = Json.createArrayBuilder();
      value = array.add(value).build();
    }
    return value;
  }

  private static String nestedFilters(final int n) {
    return "$" + "[?@".repeat(n) + "]".repeat(n);
  }
}
