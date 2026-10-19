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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonPath;
import jakarta.json.JsonPath.Buffering;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static ee.jakarta.tck.jsonp.api.jsonpathtests.JsonPathUtil.parse;
import static ee.jakarta.tck.jsonp.api.jsonpathtests.JsonPathUtil.sameValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@see <a href="https://www.rfc-editor.org/rfc/rfc9535">RFC 9535</a>}: JSONPath
 * streaming tests.<br>
 * JSON-P API defines {@link JsonPath#matcher(JsonParser)} to evaluate a query over a
 * {@link JsonParser} and {@link JsonPath#getBuffering()} to classify queries. These tests
 * check that the matcher reports the nodes of {@link JsonPath#query(JsonValue)} in document
 * order, each as often, whichever values the caller takes; the buffering classes; the parser
 * state before and after each match; locations; and errors.
 */
@Tag(JsonPathUtil.TAG)
@Tag(JsonPathUtil.STREAMING_TAG)
public class JsonPathStreamingTests {

  /** The CTS, relative to this class. */
  private static final String CTS = "cts/cts.json";

  /** Which values the caller takes from the matcher. */
  private enum Take {
    NONE, ALL, EVERY_OTHER
  }

  /** A match as the matcher reported it. */
  private static final class Match {
    final String path;
    final String pointer;
    final JsonLocation location;
    final JsonValue value;

    Match(final String path, final String pointer, final JsonLocation location, final JsonValue value) {
      this.path = path;
      this.pointer = pointer;
      this.location = location;
      this.value = value;
    }
  }

  /** Where each value of a JSON text starts, by JSON Pointer, as a plain parser reports it. */
  private static final class ValueStarts {
    final Map<String, Integer> ordinals = new HashMap<>();
    final Map<String, long[]> locations = new HashMap<>();
  }

  private static JsonParser parser(final String json) {
    return Json.createParser(new StringReader(json));
  }

  /* Tests */

  /**
   * @testName: jsonPathBufferingTest
   *
   * @assertion_ids: JSONP:JAVADOC:691; JSONP:JAVADOC:693;
   *
   * @test_Strategy: getBuffering() returns the class the rules of JsonPath.Buffering give
   * for the syntax of the query: NONE without filters, negative indices and slices counted
   * from the end; UNBOUNDED for absolute queries in filters and for slices with a step
   * below -1, a start that is negative or omitted and an end that is non-negative or
   * omitted; BOUNDED otherwise.
   */
  @Test
  public void jsonPathBufferingTest() {
    final String[] none = {"$", "$.a", "$.store.book[*].title", "$..id", "$..*", "$['a','b']",
        "$[0,0]", "$[*,0]", "$[1:5:2]", "$[::-1]", "$[5:1:-1]", "$[::0]", "$..[0]", "$.a..b[1:]",
        "$[0:2,3]", "$[2:]", "$[:7]"};
    final String[] bounded = {"$[?@.a]", "$..[?@.price < 10]", "$[-1]", "$[-3:]", "$[:-1]",
        "$[1:-1]", "$[5:1:-2]", "$[0::-2]", "$[-1:-4:-2]", "$[?@[?@]]", "$[?length(@) > 1]",
        "$[?match(@.a, 'x.*')]", "$[?@[::-2]]", "$[?@[?@.a == 1]]", "$.a[0, -1]"};
    final String[] unbounded = {"$[?@.price < $.limit]", "$[?$]", "$[?@[?$.x]]",
        "$[?count($..a) > 1]", "$[?!(@.a == $.b)]", "$[?match(@.a, $.p)]", "$[::-2]",
        "$[-1::-2]", "$[:0:-3]", "$[-2:1:-2]", "$.a[1][::-2].b", "$[0][?@ == $[1]]"};
    for (String query : none) {
      assertEquals(Buffering.NONE, Json.createPath(query).getBuffering(), query);
    }
    for (String query : bounded) {
      assertEquals(Buffering.BOUNDED, Json.createPath(query).getBuffering(), query);
    }
    for (String query : unbounded) {
      assertEquals(Buffering.UNBOUNDED, Json.createPath(query).getBuffering(), query);
    }
  }

  /**
   * @testName: jsonPathMatcherRejectsUnboundedTest
   *
   * @assertion_ids: JSONP:JAVADOC:692;
   *
   * @test_Strategy: matcher() throws JsonException for a query of class UNBOUNDED and reads
   * nothing from the parser, so the caller can evaluate query(parser.getValue()) instead.
   */
  @Test
  public void jsonPathMatcherRejectsUnboundedTest() {
    final JsonPath path = Json.createPath("$.items[?@.price < $.limit]");
    try (JsonParser parser = parser("{\"items\": [{\"price\": 1}, {\"price\": 3}], \"limit\": 2}")) {
      assertThrows(JsonException.class, () -> path.matcher(parser));
      assertEquals(Event.START_OBJECT, parser.next());
      assertEquals(1, path.query(parser.getValue()).size());
    }
    assertThrows(NullPointerException.class, () -> Json.createPath("$").matcher(null));
  }

  /**
   * @testName: jsonPathMatcherComplianceTest
   *
   * @assertion_ids: JSONP:JAVADOC:692; JSONP:JAVADOC:694; JSONP:JAVADOC:695;
   *                 JSONP:JAVADOC:696; JSONP:JAVADOC:697; JSONP:JAVADOC:698;
   *
   * @test_Strategy: One dynamic test per valid CTS test case. A query of class UNBOUNDED
   * must be rejected by matcher(). For any other, the case's document is written as JSON
   * text and the matcher runs over a parser of it three times: taking no values, all
   * values, and every other value. Each time, the Normalized Paths and JSON Pointers of the
   * matches must equal those of query() on the same text, sorted stably into document order;
   * taken values must equal the values of query(); each location must equal the location a
   * plain parser of the text reports at the first event of that value. After the last match
   * the parser has read the whole text. For a query of class NONE, the current event of the
   * parser at each match is the first event of the match's value.
   */
  @TestFactory
  public Stream<DynamicTest> jsonPathMatcherComplianceTest() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (JsonValue testCase : loadCts().getJsonArray("tests")) {
      final JsonObject test = testCase.asJsonObject();
      if (test.getBoolean("invalid_selector", false)) {
        continue;
      }
      final String name = test.getString("name");
      final String selector = test.getString("selector");
      final String json = test.get("document").toString();
      tests.add(DynamicTest.dynamicTest(name, () -> {
        final JsonPath path = Json.createPath(selector);
        if (path.getBuffering() == Buffering.UNBOUNDED) {
          try (JsonParser parser = parser(json)) {
            assertThrows(JsonException.class, () -> path.matcher(parser),
                "CTS \"" + name + "\": matcher() of UNBOUNDED " + selector);
          }
        } else {
          assertStreamsLikeQuery(selector, json, "CTS \"" + name + "\": ");
        }
      }));
    }
    return tests.stream();
  }

  /**
   * @testName: jsonPathMatcherIndicesAndSlicesTest
   *
   * @assertion_ids: JSONP:JAVADOC:692; JSONP:JAVADOC:694; JSONP:JAVADOC:695;
   *                 JSONP:JAVADOC:698;
   *
   * @test_Strategy: Index and slice selectors with start, end and step from a grid of
   * positive, negative and omitted values, in queries of several shapes, on arrays of
   * length 0 to 9: the matcher reports what query() selects, in document order, as in
   * jsonPathMatcherComplianceTest. Indices and slices counted from the end are the selectors
   * that need the length of an array.
   */
  @TestFactory
  public Stream<DynamicTest> jsonPathMatcherIndicesAndSlicesTest() {
    final String[] bounds = {"", "0", "1", "2", "4", "-1", "-2", "-4", "9"};
    final String[] steps = {"", "1", "2", "-1", "-2", "-3", "0"};
    final List<String> selectors = new ArrayList<>(Arrays.asList("0", "2", "-1", "-3", "8", "-9"));
    for (String start : bounds) {
      for (String end : bounds) {
        for (String step : steps) {
          selectors.add(start + ":" + end + (step.isEmpty() ? "" : ":" + step));
        }
      }
    }
    final List<DynamicTest> tests = new ArrayList<>();
    for (String selector : selectors) {
      tests.add(DynamicTest.dynamicTest("[" + selector + "]", () -> {
        for (int length = 0; length < 10; length++) {
          final StringBuilder array = new StringBuilder("[");
          for (int i = 0; i < length; i++) {
            array.append(i == 0 ? "" : ",").append("{\"v\":").append(i).append('}');
          }
          final String json = array.append(']').toString();
          for (String query : new String[] {"$[" + selector + "]", "$[" + selector + "].v",
              "$[" + selector + ",1]", "$..[" + selector + "]"}) {
            if (Json.createPath(query).getBuffering() != Buffering.UNBOUNDED) {
              assertStreamsLikeQuery(query, json, "");
              assertStreamsLikeQuery("$[*]" + query.substring(1),
                  "[" + json + ",[[1,2]]," + json + "]", "");
            }
          }
        }
      }));
    }
    return tests.stream();
  }

  /**
   * @testName: jsonPathMatcherOrderTest
   *
   * @assertion_ids: JSONP:JAVADOC:694; JSONP:JAVADOC:695;
   *
   * @test_Strategy: The matcher reports matches in document order, which differs from the
   * order of query() for descendant segments and for selectors in non-document order.
   */
  @Test
  public void jsonPathMatcherOrderTest() {
    assertEquals(Arrays.asList("$['a']['b']", "$['b']"), paths("$..b", "{\"a\": {\"b\": 1}, \"b\": 2}"));
    assertEquals(Arrays.asList("$['a']", "$['b']"), paths("$['b','a']", "{\"a\": 1, \"b\": 2}"));
    assertEquals(Arrays.asList("$[0][1]", "$[1]"), paths("$..[1]", "[[7, 8], 9]"));
    assertEquals(Arrays.asList("$[0]", "$[2]"), paths("$[2,0]", "[1, 2, 3]"));
    assertEquals(Arrays.asList("$[0]", "$[1]"), paths("$[?@ > 0]", "[1, 2]"));
  }

  /**
   * @testName: jsonPathMatcherRfcOrderTest
   *
   * @assertion_ids: JSONP:JAVADOC:694; JSONP:JAVADOC:695;
   *
   * @test_Strategy: For a query without descendant segments, with a single selector per
   * segment and no slice with a negative step, the matcher reports the matches in the order
   * of query() on a value read from the same text, without sorting.
   */
  @Test
  public void jsonPathMatcherRfcOrderTest() {
    final String store = "{\"store\": {\"book\": [{\"title\": \"A\", \"price\": 8}, "
        + "{\"title\": \"B\", \"price\": 12, \"isbn\": \"x\"}, {\"price\": 9, \"title\": \"C\"}], "
        + "\"bicycle\": {\"price\": 399, \"color\": \"red\"}}, \"z\": [[1, 2], [3, [4, 5]], 6]}";
    final String[] queries = {"$", "$.store.book[*].title", "$.store.*.price", "$.store.*.*", "$.*",
        "$.store.book[?@.price < 10].title", "$.store.book[-1]", "$.store.book[1:]", "$.store.book[:-1].*",
        "$.z[*][*]", "$.z[*][-1]", "$.z[?@[0] > 1][1]", "$.z[0:3:2]", "$[*][*][*]"};
    for (String query : queries) {
      final JsonPath path = Json.createPath(query);
      assertEquals(JsonPathUtil.paths(path.query(parse(store))), paths(query, store), query);
    }
  }

  /**
   * @testName: jsonPathMatcherRepeatedMatchTest
   *
   * @assertion_ids: JSONP:JAVADOC:694; JSONP:JAVADOC:698;
   *
   * @test_Strategy: A node selected twice ($[0,0]) is reported twice in a row. For a query
   * of class NONE the parser is at the first event of the value both times; after
   * getValue() it is at the last event of the value, and getValue() returns equal values
   * for the same match. A scalar match leaves the parser at its event, where the caller can
   * read it with getInt().
   */
  @Test
  public void jsonPathMatcherRepeatedMatchTest() {
    try (JsonParser parser = parser("[{\"a\": 1}, 2]")) {
      final JsonPath.Matcher matcher = Json.createPath("$[0,0,1]").matcher(parser);
      assertTrue(matcher.find());
      assertEquals("$[0]", matcher.getPath());
      assertEquals(Event.START_OBJECT, parser.currentEvent());
      assertTrue(matcher.find());
      assertEquals("$[0]", matcher.getPath());
      assertEquals(Event.START_OBJECT, parser.currentEvent());
      final JsonValue value = matcher.getValue();
      assertEquals(Event.END_OBJECT, parser.currentEvent());
      assertTrue(sameValue(parse("{\"a\": 1}"), value), "value of $[0]");
      assertEquals(value, matcher.getValue());
      assertTrue(matcher.find());
      assertEquals("$[1]", matcher.getPath());
      assertEquals(Event.VALUE_NUMBER, parser.currentEvent());
      assertEquals(2, parser.getInt());
      assertTrue(sameValue(parse("2"), matcher.getValue()), "value of $[1]");
      assertEquals(Event.VALUE_NUMBER, parser.currentEvent());
      assertFalse(matcher.find());
      assertEquals(Event.END_ARRAY, parser.currentEvent());
    }
  }

  /**
   * @testName: jsonPathMatcherNestedMatchesTest
   *
   * @assertion_ids: JSONP:JAVADOC:694; JSONP:JAVADOC:698;
   *
   * @test_Strategy: Taking the value of a match does not hide the matches inside it: for
   * $..a, after the caller takes the outer value, the inner matches are reported with values
   * equal to the corresponding parts of the outer value.
   */
  @Test
  public void jsonPathMatcherNestedMatchesTest() {
    try (JsonParser parser = parser("{\"a\": {\"a\": {\"a\": 1}}, \"b\": 2}")) {
      final JsonPath.Matcher matcher = Json.createPath("$..a").matcher(parser);
      assertTrue(matcher.find());
      assertEquals("$['a']", matcher.getPath());
      final JsonObject outer = matcher.getValue().asJsonObject();
      assertTrue(matcher.find());
      assertEquals("$['a']['a']", matcher.getPath());
      assertTrue(sameValue(outer.get("a"), matcher.getValue()), "value of $['a']['a']");
      assertTrue(matcher.find());
      assertEquals("$['a']['a']['a']", matcher.getPath());
      assertEquals("/a/a/a", matcher.getPointer().toString());
      assertTrue(sameValue(parse("1"), matcher.getValue()), "value of $['a']['a']['a']");
      assertFalse(matcher.find());
    }
  }

  /**
   * @testName: jsonPathMatcherParserPositionTest
   *
   * @assertion_ids: JSONP:JAVADOC:692; JSONP:JAVADOC:694; JSONP:JAVADOC:695;
   *                 JSONP:JAVADOC:696;
   *
   * @test_Strategy: The query argument is the value whose first event the parser returns
   * next, here the value of a member after its KEY_NAME event. Paths and pointers are
   * relative to it. When find() returns false, the parser is at the last event of that value
   * and has read nothing more, so the caller continues with the next member.
   */
  @Test
  public void jsonPathMatcherParserPositionTest() {
    final String json = "{\"header\": {\"a\": 0}, \"items\": [{\"a\": 1}, {\"a\": 2}], "
        + "\"trailer\": {\"a\": 3}}";
    try (JsonParser parser = parser(json)) {
      assertEquals(Event.START_OBJECT, parser.next());
      assertEquals(Event.KEY_NAME, parser.next());
      assertEquals(Event.START_OBJECT, parser.next());
      parser.skipObject();
      assertEquals(Event.KEY_NAME, parser.next());
      assertEquals("items", parser.getString());
      final JsonPath.Matcher matcher = Json.createPath("$[*].a").matcher(parser);
      assertTrue(matcher.find());
      assertEquals("$[0]['a']", matcher.getPath());
      assertEquals("/0/a", matcher.getPointer().toString());
      assertTrue(matcher.find());
      assertTrue(sameValue(parse("2"), matcher.getValue()), "value of $[1]['a']");
      assertFalse(matcher.find());
      assertEquals(Event.END_ARRAY, parser.currentEvent());
      assertEquals(Event.KEY_NAME, parser.next());
      assertEquals("trailer", parser.getString());
    }
  }

  /**
   * @testName: jsonPathMatcherErrorsTest
   *
   * @assertion_ids: JSONP:JAVADOC:694; JSONP:JAVADOC:695; JSONP:JAVADOC:696;
   *                 JSONP:JAVADOC:697; JSONP:JAVADOC:698;
   *
   * @test_Strategy: find() throws IllegalStateException if the parser's next event does not
   * start a value. getPath(), getPointer(), getLocation() and getValue() throw
   * IllegalStateException before the first match and after find() returned false. Invalid
   * JSON makes find() throw JsonParsingException.
   */
  @Test
  public void jsonPathMatcherErrorsTest() {
    try (JsonParser parser = parser("[]")) {
      parser.next();
      final JsonPath.Matcher matcher = Json.createPath("$").matcher(parser);
      assertThrows(IllegalStateException.class, matcher::find);
    }
    try (JsonParser parser = parser("[1]")) {
      final JsonPath.Matcher matcher = Json.createPath("$[0]").matcher(parser);
      assertThrows(IllegalStateException.class, matcher::getPath);
      assertThrows(IllegalStateException.class, matcher::getPointer);
      assertThrows(IllegalStateException.class, matcher::getLocation);
      assertThrows(IllegalStateException.class, matcher::getValue);
      assertTrue(matcher.find());
      assertFalse(matcher.find());
      assertThrows(IllegalStateException.class, matcher::getPath);
      assertThrows(IllegalStateException.class, matcher::getValue);
    }
    try (JsonParser parser = parser("[1, 2,")) {
      final JsonPath.Matcher matcher = Json.createPath("$[*]").matcher(parser);
      assertThrows(JsonParsingException.class, () -> {
        while (matcher.find()) {
          assertNotNull(matcher.getPath());
        }
      });
    }
  }

  /**
   * @testName: jsonPathMatcherBoundedTest
   *
   * @assertion_ids: JSONP:JAVADOC:693; JSONP:JAVADOC:694;
   *
   * @test_Strategy: Buffering for a query of class BOUNDED does not depend on the size of
   * the rest of the document: on an array of 10,000 small objects, the first match of each
   * query is reported before the parser has read the first half of the text.
   */
  @Test
  public void jsonPathMatcherBoundedTest() {
    final StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < 10_000; i++) {
      sb.append(i == 0 ? "" : ",").append("{\"a\": [").append(i).append(", ").append(i).append("]}");
    }
    final String json = sb.append(']').toString();
    for (String query : new String[] {"$[*].a[-1]", "$[?@.a[0] == 0].a", "$..[?@ == 1]",
        "$[*].a[1::-2]", "$[*][?@[0] > 0]"}) {
      final JsonPath path = Json.createPath(query);
      assertEquals(Buffering.BOUNDED, path.getBuffering(), query);
      try (JsonParser parser = parser(json)) {
        final JsonPath.Matcher matcher = path.matcher(parser);
        assertTrue(matcher.find(), query);
        assertTrue(parser.getLocation().getStreamOffset() < json.length() / 2,
            query + ": first match reported at offset " + parser.getLocation().getStreamOffset());
      }
    }
  }

  /* Helpers */

  private static List<String> paths(final String query, final String json) {
    final List<String> paths = new ArrayList<>();
    try (JsonParser parser = parser(json)) {
      final JsonPath.Matcher matcher = Json.createPath(query).matcher(parser);
      while (matcher.find()) {
        paths.add(matcher.getPath());
      }
    }
    return paths;
  }

  /**
   * Asserts that the matcher reports what query() selects from the same text, in document
   * order, whichever values the caller takes, with the locations a plain parser reports; and,
   * for class NONE, that the parser is at each match.
   */
  private static void assertStreamsLikeQuery(final String query, final String json, final String name) {
    final JsonPath path = Json.createPath(query);
    final ValueStarts starts = valueStarts(json);
    final List<JsonPath.Node> expected = new ArrayList<>(path.query(parse(json)));
    expected.sort((a, b) -> Integer.compare(
        starts.ordinals.get(a.getPointer().toString()), starts.ordinals.get(b.getPointer().toString())));
    final String context = name + query + " on " + json;
    for (Take take : Take.values()) {
      final List<Match> actual = stream(path, json, take);
      final List<String> expectedPaths = new ArrayList<>();
      for (JsonPath.Node node : expected) {
        expectedPaths.add(node.getPath());
      }
      final List<String> actualPaths = new ArrayList<>();
      for (Match match : actual) {
        actualPaths.add(match.path);
      }
      assertEquals(expectedPaths, actualPaths, context + ", taking " + take + " values");
      for (int i = 0; i < actual.size(); i++) {
        final Match match = actual.get(i);
        final JsonPath.Node node = expected.get(i);
        assertEquals(node.getPointer().toString(), match.pointer, context + ": pointer of " + match.path);
        final long[] location = starts.locations.get(match.pointer);
        assertEquals(location[0], match.location.getLineNumber(), context + ": line of " + match.path);
        assertEquals(location[1], match.location.getColumnNumber(), context + ": column of " + match.path);
        assertEquals(location[2], match.location.getStreamOffset(), context + ": offset of " + match.path);
        if (match.value != null) {
          assertTrue(sameValue(node.getValue(), match.value), context + ": value of " + match.path);
        }
      }
    }
    if (path.getBuffering() == Buffering.NONE) {
      final JsonPath unbuffered = Json.createPath(query, Map.of(JsonPath.MAX_BUFFERED_NODES, 0));
      for (Take take : Take.values()) {
        assertEquals(expected.size(), stream(unbuffered, json, take).size(),
            context + ": class NONE with maxBufferedNodes 0, taking " + take + " values");
      }
      try (JsonParser parser = parser(json)) {
        final JsonPath.Matcher matcher = path.matcher(parser);
        while (matcher.find()) {
          final Event event = parser.currentEvent();
          assertTrue(event != Event.KEY_NAME && event != Event.END_OBJECT && event != Event.END_ARRAY,
              context + ": parser at " + event + " for " + matcher.getPath());
          assertEquals(matcher.getLocation().getStreamOffset(), parser.getLocation().getStreamOffset(),
              context + ": parser not at the first event of " + matcher.getPath());
        }
      }
    }
  }

  private static List<Match> stream(final JsonPath path, final String json, final Take take) {
    final List<Match> matches = new ArrayList<>();
    try (JsonParser parser = parser(json)) {
      final JsonPath.Matcher matcher = path.matcher(parser);
      int i = 0;
      while (matcher.find()) {
        final boolean taken = take == Take.ALL || (take == Take.EVERY_OTHER && i % 2 == 0);
        final JsonValue value = taken ? matcher.getValue() : null;
        final JsonLocation location = matcher.getLocation();
        matches.add(new Match(matcher.getPath(), matcher.getPointer().toString(), location, value));
        i++;
      }
      assertFalse(parser.hasNext(), "the matcher reads the whole document, " + path);
      assertFalse(matcher.find(), "find() stays false, " + path);
    }
    return matches;
  }

  /** Records ordinal and location of the first event of each value of a JSON text, by JSON Pointer. */
  private static ValueStarts valueStarts(final String json) {
    final ValueStarts starts = new ValueStarts();
    final Deque<String> containers = new ArrayDeque<>();
    final Deque<int[]> indices = new ArrayDeque<>();
    String name = null;
    try (JsonParser parser = parser(json)) {
      while (parser.hasNext()) {
        final Event event = parser.next();
        if (event == Event.KEY_NAME) {
          name = parser.getString();
          continue;
        }
        if (event == Event.END_OBJECT || event == Event.END_ARRAY) {
          containers.pop();
          indices.pop();
          continue;
        }
        final String pointer;
        if (containers.isEmpty()) {
          pointer = "";
        } else if (indices.peek()[0] >= 0) {
          pointer = containers.peek() + "/" + indices.peek()[0]++;
        } else {
          pointer = containers.peek() + "/" + name.replace("~", "~0").replace("/", "~1");
        }
        final JsonLocation location = parser.getLocation();
        starts.ordinals.put(pointer, starts.ordinals.size());
        starts.locations.put(pointer, new long[] {location.getLineNumber(), location.getColumnNumber(),
            location.getStreamOffset()});
        if (event == Event.START_OBJECT || event == Event.START_ARRAY) {
          containers.push(pointer);
          indices.push(new int[] {event == Event.START_ARRAY ? 0 : -1});
        }
      }
    }
    return starts;
  }

  private static JsonObject loadCts() {
    try (InputStream in = JsonPathStreamingTests.class.getResourceAsStream(CTS)) {
      assertNotNull(in, "resource " + CTS);
      try (JsonReader reader = Json.createReader(in)) {
        return reader.readObject();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
