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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPath;
import jakarta.json.JsonPointer;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static ee.jakarta.tck.jsonp.api.jsonpathtests.JsonPathUtil.parse;
import static ee.jakarta.tck.jsonp.api.jsonpathtests.JsonPathUtil.paths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@see <a href="https://www.rfc-editor.org/rfc/rfc9535">RFC 9535</a>}: JSONPath
 * API tests.<br>
 * JSON-P API defines {@link jakarta.json.JsonPath} to evaluate RFC 9535 queries on
 * {@link JsonValue}s. These tests check the contract of {@link Json#createPath(String)},
 * {@link JsonPath} and {@link JsonPath.Node}.
 */
@Tag(JsonPathUtil.TAG)
public class JsonPathTests {

  private static final Logger LOGGER = Logger.getLogger(JsonPathTests.class.getName());

  /** Sample document used by most tests. */
  private static final String STORE = "{"
      + "\"store\": {"
      + "  \"name\": \"Corner Shop\","
      + "  \"items\": ["
      + "    {\"id\": 1, \"title\": \"Kettle\", \"price\": 25.50, \"tags\": [\"kitchen\", \"electric\"]},"
      + "    {\"id\": 2, \"title\": \"Mug\", \"price\": 4, \"tags\": [\"kitchen\"]},"
      + "    {\"id\": 3, \"title\": \"Lamp\", \"price\": 12.0, \"tags\": [], \"discontinued\": true},"
      + "    {\"id\": 4, \"title\": \"Pen\", \"price\": 1.5, \"colour\": null}"
      + "  ],"
      + "  \"address\": {\"city\": \"Bonn\", \"zip\": \"53111\"}"
      + "},"
      + "\"limit\": 10"
      + "}";

  /* Tests */

  /**
   * @testName: jsonPathCreateTest
   *
   * @assertion_ids: JSONP:JAVADOC:684; JSONP:JAVADOC:685; JSONP:JAVADOC:687;
   *
   * @test_Strategy: Create JSONPath queries of each kind of RFC 9535 selector and
   * segment with Json.createPath and with JsonProvider.createPath. Each call returns
   * a JsonPath whose toString() returns the query string exactly as passed,
   * including insignificant whitespace.
   */
  @Test
  public void jsonPathCreateTest() {
    final String[] queries = {
        "$",
        "$.a",
        "$['a']",
        "$[\"a\"]",
        "$.*",
        "$[*]",
        "$[0]",
        "$[-1]",
        "$[1:3]",
        "$[::-1]",
        "$[0, 'a', *]",
        "$..a",
        "$..[0]",
        "$..*",
        "$[?@.a]",
        "$[?!@.a]",
        "$[?@.a == 1 && @.b != 'x' || @.c <= 2.5e3]",
        "$[?(@.a > 1)]",
        "$[?@.a == $.b]",
        "$[?length(@.a) > 2]",
        "$[?count(@.*) == 1]",
        "$[?match(@.a, 'a.*')]",
        "$[?search(@.a, 'b')]",
        "$[?value(@..a) == 1]",
        "$[9007199254740991]",
        "$[-9007199254740991]",
        "$[-9007199254740991:9007199254740991:9007199254740991]",
        "$[ 0 , 1 ]",
        "$\t.a\n.b",
        "$['\\u00e9\\'\\\\']",
    };
    for (String query : queries) {
      LOGGER.info(" - createPath(\"" + query + "\")");
      final JsonPath path = Json.createPath(query);
      assertNotNull(path, "Json.createPath(\"" + query + "\")");
      assertEquals(query, path.toString(), "toString() of " + query);
      final JsonPath fromProvider = JsonProvider.provider().createPath(query);
      assertNotNull(fromProvider, "JsonProvider.createPath(\"" + query + "\")");
      assertEquals(query, fromProvider.toString(), "toString() of " + query);
    }
  }

  /**
   * @testName: jsonPathCreateInvalidTest
   *
   * @assertion_ids: JSONP:JAVADOC:684; JSONP:JAVADOC:685;
   *
   * @test_Strategy: Call Json.createPath and JsonProvider.createPath with strings that
   * are not well-formed or not valid JSONPath queries (RFC 9535, section 2.1): syntax
   * errors, leading or trailing whitespace, integers outside the I-JSON range in index
   * and slice selectors, unknown functions and ill-typed function calls. Each call
   * throws JsonException with a message; no document is needed to detect the error.
   */
  @Test
  public void jsonPathCreateInvalidTest() {
    final String[] queries = {
        "",
        "a",
        "@",
        "$.",
        "$..",
        "$.a.",
        "$[",
        "$]",
        "$[]",
        "$['a'",
        "$['a]",
        "$[a]",
        " $",
        "$ ",
        "$.a b",
        "$[01]",
        "$[-0]",
        "$[1.0]",
        "$[0x1]",
        "$[9007199254740992]",
        "$[-9007199254740992]",
        "$[0:9007199254740992]",
        "$[::-9007199254740992]",
        "$['\\x']",
        "$['\u0001']",
        "$['\\ud800']",
        "$[?]",
        "$[?@.a ==]",
        "$[?@.a = 1]",
        "$[?(@.a]",
        "$[?@.* == 1]",
        "$[?@..a == 1]",
        "$[?1]",
        "$[?foo(@.a)]",
        "$[?length(@.a)]",
        "$[?length(@.*) == 1]",
        "$[?length(@.a, @.b) == 1]",
        "$[?count(1) == 1]",
        "$[?count(@.a)]",
        "$[?match(@.a) == true]",
        "$[?match(@.a)]",
        "$[?match(@.a, 'a') == true]",
        "$[?value(@.a)]",
        "$.length()",
    };
    for (String query : queries) {
      LOGGER.info(" - createPath(\"" + query + "\") is invalid");
      final JsonException e = assertThrows(JsonException.class, () -> Json.createPath(query),
          "Json.createPath(\"" + query + "\")");
      assertNotNull(e.getMessage(), "Exception message for " + query);
      assertFalse(e.getMessage().isEmpty(), "Exception message for " + query);
      assertThrows(JsonException.class, () -> JsonProvider.provider().createPath(query),
          "JsonProvider.createPath(\"" + query + "\")");
    }
  }

  /**
   * @testName: jsonPathNullArgumentsTest
   *
   * @assertion_ids: JSONP:JAVADOC:684; JSONP:JAVADOC:685; JSONP:JAVADOC:686;
   *
   * @test_Strategy: Json.createPath(null), JsonProvider.createPath(null) and
   * JsonPath.query(null) throw NullPointerException.
   */
  @Test
  public void jsonPathNullArgumentsTest() {
    assertThrows(NullPointerException.class, () -> Json.createPath(null),
        "Json.createPath(null)");
    assertThrows(NullPointerException.class, () -> JsonProvider.provider().createPath(null),
        "JsonProvider.createPath(null)");
    final JsonPath path = Json.createPath("$.a");
    assertThrows(NullPointerException.class, () -> path.query(null), "query(null)");
  }

  /**
   * @testName: jsonPathQueryTest
   *
   * @assertion_ids: JSONP:JAVADOC:684; JSONP:JAVADOC:686; JSONP:JAVADOC:688;
   *                 JSONP:JAVADOC:689;
   *
   * @test_Strategy: Apply queries with name, wildcard, index, slice and filter
   * selectors, child and descendant segments, absolute and relative queries in
   * filters, and the five functions of RFC 9535 to a sample document. Check the
   * Normalized Paths of the selected nodes, in order, and that the value of each node
   * is the value at that location in the document.
   */
  @Test
  public void jsonPathQueryTest() {
    final JsonValue doc = parse(STORE);
    final String items = "$['store']['items']";
    assertQuery(doc, "$", "$");
    assertQuery(doc, "$.store.name", "$['store']['name']");
    assertQuery(doc, "$['store']['address']['city']", "$['store']['address']['city']");
    assertQuery(doc, "$[\"store\"][\"address\"][\"zip\"]", "$['store']['address']['zip']");
    assertQuery(doc, "$.store.items[*].title", items + "[0]['title']", items + "[1]['title']",
        items + "[2]['title']", items + "[3]['title']");
    assertQuery(doc, "$.store.items[0].id", items + "[0]['id']");
    assertQuery(doc, "$.store.items[-1].id", items + "[3]['id']");
    assertQuery(doc, "$.store.items[0:2].id", items + "[0]['id']", items + "[1]['id']");
    assertQuery(doc, "$.store.items[1:].id", items + "[1]['id']", items + "[2]['id']",
        items + "[3]['id']");
    assertQuery(doc, "$.store.items[::2].id", items + "[0]['id']", items + "[2]['id']");
    assertQuery(doc, "$.store.items[::-1].id", items + "[3]['id']", items + "[2]['id']",
        items + "[1]['id']", items + "[0]['id']");
    assertQuery(doc, "$.store.items[3,0].id", items + "[3]['id']", items + "[0]['id']");
    assertQuery(doc, "$.store.items[0]['id','title']", items + "[0]['id']", items + "[0]['title']");
    assertQuery(doc, "$.store.items[?@.price < 10].title", items + "[1]['title']",
        items + "[3]['title']");
    assertQuery(doc, "$.store.items[?@.price < $.limit].id", items + "[1]['id']",
        items + "[3]['id']");
    assertQuery(doc, "$.store.items[?@.price == 12].id", items + "[2]['id']");
    assertQuery(doc, "$.store.items[?@.price == 25.5].id", items + "[0]['id']");
    assertQuery(doc, "$.store.items[?@.discontinued].id", items + "[2]['id']");
    assertQuery(doc, "$.store.items[?!@.tags].id", items + "[3]['id']");
    assertQuery(doc, "$.store.items[?@.tags[?@ == 'electric']].id", items + "[0]['id']");
    assertQuery(doc, "$.store.items[?@.price > 2 && @.price < 20].id", items + "[1]['id']",
        items + "[2]['id']");
    assertQuery(doc, "$.store.items[?@.id == 1 || @.title == 'Pen'].id", items + "[0]['id']",
        items + "[3]['id']");
    assertQuery(doc, "$.store.items[?length(@.tags) == 0].id", items + "[2]['id']");
    assertQuery(doc, "$.store.items[?length(@.title) == 3].id", items + "[1]['id']",
        items + "[3]['id']");
    assertQuery(doc, "$.store.items[?count(@.tags[*]) > 1].id", items + "[0]['id']");
    assertQuery(doc, "$.store.items[?match(@.title, 'M.*')].id", items + "[1]['id']");
    assertQuery(doc, "$.store.items[?search(@.title, 'en')].id", items + "[3]['id']");
    assertQuery(doc, "$.store.items[?value(@..price) > 20].id", items + "[0]['id']");
    assertQuery(doc, "$..title", items + "[0]['title']", items + "[1]['title']",
        items + "[2]['title']", items + "[3]['title']");
    assertQuery(doc, "$..tags[0]", items + "[0]['tags'][0]", items + "[1]['tags'][0]");
    assertQuery(doc, "$.store..city", "$['store']['address']['city']");
    assertQuery(doc, "$.nothing");
    assertQuery(doc, "$.store.items[4]");
    assertQuery(doc, "$.store.items[?@.price > 100]");
  }

  /**
   * @testName: jsonPathQueryValuesTest
   *
   * @assertion_ids: JSONP:JAVADOC:686; JSONP:JAVADOC:688;
   *
   * @test_Strategy: Check the values returned by Node.getValue() for strings, numbers,
   * booleans, arrays and objects: each is equal to the value that the parent's
   * JsonObject.get or JsonArray.get returns, and the root node's value is equal to the
   * query argument.
   */
  @Test
  public void jsonPathQueryValuesTest() {
    final JsonObject doc = parse(STORE).asJsonObject();
    final JsonObject store = doc.getJsonObject("store");
    final JsonArray items = store.getJsonArray("items");

    final List<JsonPath.Node> root = Json.createPath("$").query(doc);
    assertEquals(1, root.size(), "$ selects the root");
    assertEquals(doc, root.get(0).getValue(), "value of the root node");

    final List<JsonPath.Node> titles = Json.createPath("$.store.items[*].title").query(doc);
    assertEquals(4, titles.size(), "number of titles");
    for (int i = 0; i < titles.size(); i++) {
      assertEquals(items.getJsonObject(i).get("title"), titles.get(i).getValue(), "title " + i);
    }
    assertEquals(Json.createValue("Kettle"), titles.get(0).getValue(), "first title");

    final List<JsonPath.Node> prices = Json.createPath("$.store.items[*].price").query(doc);
    for (int i = 0; i < prices.size(); i++) {
      assertEquals(items.getJsonObject(i).get("price"), prices.get(i).getValue(), "price " + i);
    }
    assertEquals(0, new BigDecimal("25.50").compareTo(
        ((JsonNumber) prices.get(0).getValue()).bigDecimalValue()), "first price");

    final JsonValue discontinued = single(doc, "$.store.items[2].discontinued").getValue();
    assertEquals(JsonValue.TRUE, discontinued, "boolean value");
    assertEquals(JsonValue.ValueType.TRUE, discontinued.getValueType(), "boolean value type");

    assertEquals(items, single(doc, "$.store.items").getValue(), "array value");
    assertEquals(store.get("address"), single(doc, "$.store.address").getValue(), "object value");
    assertEquals(items.get(1), single(doc, "$.store.items[?@.title == 'Mug']").getValue(),
        "filtered value");
  }

  /**
   * @testName: jsonPathNullValueTest
   *
   * @assertion_ids: JSONP:JAVADOC:686; JSONP:JAVADOC:688;
   *
   * @test_Strategy: JSON null is a value like any other: a name selector selects a
   * member whose value is null, and Node.getValue() returns JsonValue.NULL, never a
   * Java null. A missing member selects nothing. In filters, existence tests and
   * comparisons with null distinguish null from missing.
   */
  @Test
  public void jsonPathNullValueTest() {
    final List<JsonPath.Node> member = Json.createPath("$.a").query(parse("{\"a\": null}"));
    assertEquals(1, member.size(), "$.a on {\"a\": null}");
    assertEquals("$['a']", member.get(0).getPath(), "path of null member");
    assertNullValue(member.get(0).getValue());

    assertTrue(Json.createPath("$.a").query(parse("{}")).isEmpty(), "$.a on {}");

    final List<JsonPath.Node> element = Json.createPath("$[0]").query(parse("[null]"));
    assertEquals(1, element.size(), "$[0] on [null]");
    assertNullValue(element.get(0).getValue());

    final List<JsonPath.Node> root = Json.createPath("$").query(JsonValue.NULL);
    assertEquals(1, root.size(), "$ on null");
    assertNullValue(root.get(0).getValue());

    final JsonValue array = parse("[{\"a\": null}, {}, {\"a\": 1}, {\"a\": false}]");
    assertQuery(array, "$[?@.a]", "$[0]", "$[2]", "$[3]");
    assertQuery(array, "$[?!@.a]", "$[1]");
    assertQuery(array, "$[?@.a == null]", "$[0]");
    assertQuery(array, "$[?@.a != null]", "$[1]", "$[2]", "$[3]");
    assertQuery(array, "$[*].a", "$[0]['a']", "$[2]['a']", "$[3]['a']");
    assertQuery(parse("[null, 0, false, \"\"]"), "$[?@ == null]", "$[0]");
  }

  /**
   * @testName: jsonPathPrimitiveArgumentTest
   *
   * @assertion_ids: JSONP:JAVADOC:686; JSONP:JAVADOC:688; JSONP:JAVADOC:689;
   *                 JSONP:JAVADOC:690;
   *
   * @test_Strategy: The query argument can be any JSON value. For a string, number,
   * true, false and null, $ selects the argument itself with the Normalized Path $ and
   * the empty JSON Pointer; queries that need an object or array select nothing.
   */
  @Test
  public void jsonPathPrimitiveArgumentTest() {
    final JsonValue[] values = {
        Json.createValue("text"), Json.createValue(42), Json.createValue(new BigDecimal("1.5")),
        JsonValue.TRUE, JsonValue.FALSE, JsonValue.NULL
    };
    for (JsonValue value : values) {
      LOGGER.info(" - query argument " + value);
      final List<JsonPath.Node> nodes = Json.createPath("$").query(value);
      assertEquals(1, nodes.size(), "$ on " + value);
      assertEquals(value, nodes.get(0).getValue(), "value of $ on " + value);
      assertEquals("$", nodes.get(0).getPath(), "path of $ on " + value);
      assertEquals("", nodes.get(0).getPointer().toString(), "pointer of $ on " + value);
      for (String query : new String[] {"$.a", "$[0]", "$.*", "$[*]", "$..*", "$[0:1]",
          "$[?@ == 1]", "$..a"}) {
        assertTrue(Json.createPath(query).query(value).isEmpty(), query + " on " + value);
      }
    }
  }

  /**
   * @testName: jsonPathStructuralMismatchTest
   *
   * @assertion_ids: JSONP:JAVADOC:686;
   *
   * @test_Strategy: A valid query never fails because of the structure of the query
   * argument: names on arrays, indices on objects, indices outside an array, a slice
   * with step 0 and comparisons of values of different types select nothing.
   */
  @Test
  public void jsonPathStructuralMismatchTest() {
    final JsonValue array = parse("[1, \"a\", [2], {\"0\": 3}]");
    final JsonValue object = parse("{\"0\": 1, \"a\": {\"b\": 2}}");
    assertQuery(array, "$.a");
    assertQuery(array, "$['0']");
    assertQuery(array, "$[4]");
    assertQuery(array, "$[-5]");
    assertQuery(array, "$[9007199254740991]");
    assertQuery(array, "$[-9007199254740991]");
    assertQuery(array, "$[::0]");
    assertQuery(array, "$[5:]");
    assertQuery(array, "$[2:1]");
    assertQuery(array, "$[0].a.b");
    assertQuery(array, "$[0][0]");
    assertQuery(array, "$[?@ < 'b']", "$[1]");
    assertQuery(array, "$[?@ > 0]", "$[0]");
    assertQuery(array, "$[?@.a.b == 1]");
    assertQuery(array, "$[?length(@) == 1]", "$[1]", "$[2]", "$[3]");
    assertQuery(object, "$[0]");
    assertQuery(object, "$[0:1]");
    assertQuery(object, "$['0']", "$['0']");
    assertQuery(object, "$.a.b.c");
    assertQuery(object, "$.a[0]");
  }

  /**
   * @testName: jsonPathOrderTest
   *
   * @assertion_ids: JSONP:JAVADOC:686; JSONP:JAVADOC:689;
   *
   * @test_Strategy: The result is in the order of RFC 9535: array elements in array
   * order and members of an object in the iteration order of its entrySet(), for
   * objects from a reader, from a builder and for an application-provided JsonObject.
   * A descendant segment applies its selectors to a node before its descendants.
   */
  @Test
  public void jsonPathOrderTest() {
    final JsonObject read = parse("{\"m\": 1, \"z\": 2, \"a\": 3}").asJsonObject();
    assertQuery(read, "$.*", expectedMemberPaths(read));
    assertQuery(read, "$[*]", expectedMemberPaths(read));

    final JsonObject built = Json.createObjectBuilder()
        .add("b", 1).add("c", 2).add("a", 3).add("e", 4).add("d", 5).build();
    assertQuery(built, "$.*", expectedMemberPaths(built));

    final Map<String, JsonValue> members = new LinkedHashMap<>();
    members.put("z", Json.createValue(1));
    members.put("y", Json.createArrayBuilder().add(2).add(3).build());
    members.put("x", Json.createValue("v"));
    final JsonObject custom = new MapJsonObject(members);
    assertQuery(custom, "$.*", "$['z']", "$['y']", "$['x']");
    assertQuery(custom, "$..*", "$['z']", "$['y']", "$['x']", "$['y'][0]", "$['y'][1]");
    assertQuery(custom, "$[?@ == 'v']", "$['x']");
    assertQuery(custom, "$.y[1]", "$['y'][1]");

    assertQuery(parse("[[7, 8], 9]"), "$..[1]", "$[1]", "$[0][1]");
    assertQuery(parse("[3, 1, 2]"), "$[*]", "$[0]", "$[1]", "$[2]");
    assertQuery(parse("[3, 1, 2]"), "$[2, 0, 1]", "$[2]", "$[0]", "$[1]");
    assertQuery(parse("[3, 1, 2]"), "$[?@ > 1]", "$[0]", "$[2]");
    assertQuery(parse("{\"a\": [{\"a\": 1}, {\"a\": 2}]}"), "$..a", "$['a']", "$['a'][0]['a']",
        "$['a'][1]['a']");
  }

  /**
   * @testName: jsonPathDuplicateSelectionTest
   *
   * @assertion_ids: JSONP:JAVADOC:686; JSONP:JAVADOC:689;
   *
   * @test_Strategy: A node selected more than once appears as many times in the
   * result, each time with the same Normalized Path. Equal values at different
   * locations are different nodes with different paths.
   */
  @Test
  public void jsonPathDuplicateSelectionTest() {
    assertQuery(parse("[1]"), "$[0,0]", "$[0]", "$[0]");
    assertQuery(parse("[1, 2]"), "$[*, 0]", "$[0]", "$[1]", "$[0]");
    assertQuery(parse("[1, 2, 3]"), "$[0:2, 1:]", "$[0]", "$[1]", "$[1]", "$[2]");
    assertQuery(parse("{\"a\": 1}"), "$['a', 'a', 'b']", "$['a']", "$['a']");
    assertQuery(parse("[true, true]"), "$[*]", "$[0]", "$[1]");
    assertQuery(parse("[[1], [1]]"), "$[?@[0] == 1]", "$[0]", "$[1]");
  }

  /**
   * @testName: jsonPathNormalizedPathTest
   *
   * @assertion_ids: JSONP:JAVADOC:689;
   *
   * @test_Strategy: Node.getPath() returns the Normalized Path of RFC 9535, section
   * 2.7: member names in single quotes with the escapes of the normalized-path
   * grammar (\', \\, \b, \f, \n, \r, \t and \\u00xx in lower case for other control
   * characters) and every other character unescaped, array indices as non-negative
   * decimal integers, also when the query used a negative index or slice.
   */
  @Test
  public void jsonPathNormalizedPathTest() {
    final Map<String, String> names = new LinkedHashMap<>();
    names.put("a", "$['a']");
    names.put("", "$['']");
    names.put(" ", "$[' ']");
    names.put("0", "$['0']");
    names.put("a'b", "$['a\\'b']");
    names.put("a\\b", "$['a\\\\b']");
    names.put("\"", "$['\"']");
    names.put("/", "$['/']");
    names.put("~", "$['~']");
    names.put("\b", "$['\\b']");
    names.put("\f", "$['\\f']");
    names.put("\n", "$['\\n']");
    names.put("\r", "$['\\r']");
    names.put("\t", "$['\\t']");
    names.put("\u0000", "$['\\u0000']");
    names.put("\u0007", "$['\\u0007']");
    names.put("\u000b", "$['\\u000b']");
    names.put("\u000e", "$['\\u000e']");
    names.put("\u001f", "$['\\u001f']");
    names.put("\u007f", "$['\u007f']");
    names.put("\u00e9", "$['\u00e9']");
    names.put("\u20ac", "$['\u20ac']");
    names.put("\ud800\udc00", "$['\ud800\udc00']");
    names.put("\ud83d\ude00", "$['\ud83d\ude00']");
    names.put("\ue000", "$['\ue000']");
    names.put("$['a']", "$['$[\\'a\\']']");

    final Map<JsonValue, String> expected = new LinkedHashMap<>();
    final JsonObjectBuilder builder = Json.createObjectBuilder();
    int i = 0;
    for (Map.Entry<String, String> name : names.entrySet()) {
      builder.add(name.getKey(), i);
      expected.put(Json.createValue(i), name.getValue());
      i++;
    }
    final JsonObject doc = builder.build();
    final List<JsonPath.Node> nodes = Json.createPath("$.*").query(doc);
    assertEquals(names.size(), nodes.size(), "number of members");
    for (JsonPath.Node node : nodes) {
      assertEquals(expected.get(node.getValue()), node.getPath(), "Normalized Path");
    }
    for (Map.Entry<String, String> name : names.entrySet()) {
      final String query = "$[" + JsonPathUtil.quote(name.getKey()) + "]";
      assertQuery(doc, query, name.getValue());
      assertQuery(doc, name.getValue(), name.getValue());
    }

    final JsonValue array = parse("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]");
    assertQuery(array, "$[-1]", "$[11]");
    assertQuery(array, "$[-12]", "$[0]");
    assertQuery(array, "$[10:]", "$[10]", "$[11]");
    assertQuery(array, "$[-2:]", "$[10]", "$[11]");
    assertQuery(array, "$[11:8:-2]", "$[11]", "$[9]");
    assertQuery(parse("{\"a\": {\"b\": [0, [1, 2]]}}"), "$.a.b[1][-1]", "$['a']['b'][1][1]");
    assertQuery(parse("{\"\\u000B\": 1, \"a\": 2}"), "$[\"\\u000B\"]", "$['\\u000b']");
    assertQuery(parse("{\"\\u000B\": 1, \"a\": 2}"), "$[\"\\u0061\"]", "$['a']");
  }

  /**
   * @testName: jsonPathPointerTest
   *
   * @assertion_ids: JSONP:JAVADOC:690; JSONP:JAVADOC:582; JSONP:JAVADOC:643;
   *
   * @test_Strategy: Node.getPointer() returns the RFC 6901 JSON Pointer of the node:
   * the empty pointer for the root, member names escaped with ~0 and ~1, array indices
   * in decimal. Check the pointer strings and that, applied to the query argument,
   * each pointer and a pointer created from its string reference the node's value.
   */
  @Test
  public void jsonPathPointerTest() {
    final JsonValue doc = parse("{"
        + "\"a\": [10, {\"b\": 20}],"
        + "\"\": 1,"
        + "\"c/d\": 2,"
        + "\"e~f\": 3,"
        + "\"~1\": 4,"
        + "\"g'h\": {\"\\u00e9 \\\"\": [true, null]},"
        + "\"0\": [[], {}],"
        + "\"k\": {\"\": {\"\": 5}}"
        + "}");
    assertPointer(doc, "$", "");
    assertPointer(doc, "$.a", "/a");
    assertPointer(doc, "$.a[0]", "/a/0");
    assertPointer(doc, "$.a[-1].b", "/a/1/b");
    assertPointer(doc, "$['']", "/");
    assertPointer(doc, "$['c/d']", "/c~1d");
    assertPointer(doc, "$['e~f']", "/e~0f");
    assertPointer(doc, "$['~1']", "/~01");
    assertPointer(doc, "$[\"g'h\"][*][1]", "/g'h/\u00e9 \"/1");
    assertPointer(doc, "$['0'][0]", "/0/0");
    assertPointer(doc, "$.k['']['']", "/k//");

    final JsonArray big = Json.createArrayBuilder()
        .add(0).add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8).add(9).add(10).build();
    assertPointer(big, "$[10]", "/10");

    final List<JsonPath.Node> all = Json.createPath("$..*").query(doc);
    assertEquals(18, all.size(), "number of descendants");
    for (JsonPath.Node node : all) {
      final JsonPointer pointer = node.getPointer();
      assertEquals(node.getValue(), pointer.getValue((JsonStructure) doc),
          "value at pointer " + pointer + " of " + node.getPath());
      assertEquals(node.getValue(), Json.createPointer(pointer.toString())
          .getValue((JsonStructure) doc), "value at pointer \"" + pointer + "\" of " + node.getPath());
    }
  }

  /**
   * @testName: jsonPathPointerModifyTest
   *
   * @assertion_ids: JSONP:JAVADOC:690; JSONP:JAVADOC:645; JSONP:JAVADOC:644;
   *
   * @test_Strategy: The JSON Pointer of a node can be used to change the document with
   * JsonPointer and JsonPatch: replace the values selected by a filter, remove a
   * selected member, and check the result with another query.
   */
  @Test
  public void jsonPathPointerModifyTest() {
    JsonStructure doc = (JsonStructure) parse(STORE);
    final List<JsonPath.Node> cheap = Json.createPath("$.store.items[?@.price < 10].price").query(doc);
    assertEquals(2, cheap.size(), "cheap items");
    for (JsonPath.Node node : cheap) {
      doc = node.getPointer().replace(doc, Json.createValue(10));
    }
    assertQuery(doc, "$.store.items[?@.price < 10]");
    assertQuery(doc, "$.store.items[?@.price == 10].id", "$['store']['items'][1]['id']",
        "$['store']['items'][3]['id']");

    final JsonPath.Node colour = single(doc, "$.store.items[?@.colour == null].colour");
    final JsonStructure patched = Json.createPatchBuilder()
        .remove(colour.getPointer().toString())
        .replace(single(doc, "$.store.name").getPointer().toString(), Json.createValue("Shop"))
        .build().apply(doc);
    assertQuery(patched, "$..colour");
    assertEquals(Json.createValue("Shop"), single(patched, "$.store.name").getValue(), "new name");
    assertQuery(doc, "$..colour", "$['store']['items'][3]['colour']");
  }

  /**
   * @testName: jsonPathUnmodifiableResultTest
   *
   * @assertion_ids: JSONP:JAVADOC:686;
   *
   * @test_Strategy: query() returns an unmodifiable list, also when it is empty: add,
   * set, remove, clear and Iterator.remove throw UnsupportedOperationException, and
   * the list is unchanged afterwards. Results of a JsonPath are independent of each
   * other.
   */
  @Test
  public void jsonPathUnmodifiableResultTest() {
    final JsonPath path = Json.createPath("$[*]");
    final List<JsonPath.Node> nodes = path.query(parse("[1, 2]"));
    final JsonPath.Node first = nodes.get(0);
    assertThrows(UnsupportedOperationException.class, () -> nodes.add(first), "add");
    assertThrows(UnsupportedOperationException.class, () -> nodes.add(0, first), "add(int)");
    assertThrows(UnsupportedOperationException.class, () -> nodes.set(1, first), "set");
    assertThrows(UnsupportedOperationException.class, () -> nodes.remove(0), "remove(int)");
    assertThrows(UnsupportedOperationException.class, () -> nodes.remove(first), "remove");
    assertThrows(UnsupportedOperationException.class, () -> nodes.clear(), "clear");
    assertThrows(UnsupportedOperationException.class, () -> nodes.addAll(nodes), "addAll");
    assertThrows(UnsupportedOperationException.class, () -> {
      final Iterator<JsonPath.Node> it = nodes.iterator();
      it.next();
      it.remove();
    }, "Iterator.remove");
    assertEquals(List.of("$[0]", "$[1]"), paths(nodes), "result after modification attempts");

    final List<JsonPath.Node> empty = path.query(parse("{}"));
    assertTrue(empty.isEmpty(), "$[*] on {}");
    assertThrows(UnsupportedOperationException.class, () -> empty.add(first), "add to empty");
    assertTrue(empty.isEmpty(), "empty result after add");

    final List<JsonPath.Node> other = path.query(parse("[3]"));
    assertEquals(List.of("$[0]"), paths(other), "second result");
    assertEquals(List.of("$[0]", "$[1]"), paths(nodes), "first result after second query");
    assertEquals(Json.createValue(1), nodes.get(0).getValue(), "first value after second query");
  }

  /**
   * @testName: jsonPathNumberComparisonTest
   *
   * @assertion_ids: JSONP:JAVADOC:686;
   *
   * @test_Strategy: Numbers in filters compare by their BigDecimal value with
   * compareTo, so 1, 1.0, 1e0 and 10e-1 are equal and -0 equals 0, unlike with
   * JsonNumber.equals; the comparison is exact beyond the range of double. Arrays and
   * objects are equal if their elements or members are equal in this sense.
   */
  @Test
  public void jsonPathNumberComparisonTest() {
    final JsonValue doc = parse("["
        + "{\"a\": 1, \"b\": 1.0},"
        + "{\"a\": 1, \"b\": 1e0},"
        + "{\"a\": 1, \"b\": 10e-1},"
        + "{\"a\": 0, \"b\": -0},"
        + "{\"a\": [1, [2]], \"b\": [1.00, [2.0]]},"
        + "{\"a\": {\"x\": 1, \"y\": {\"z\": 2}}, \"b\": {\"y\": {\"z\": 2e0}, \"x\": 1.0}},"
        + "{\"a\": 9007199254740993, \"b\": 9007199254740992},"
        + "{\"a\": 0.1, \"b\": 0.10000000000000001},"
        + "{\"a\": [1], \"b\": [1, 1]},"
        + "{\"a\": {\"x\": 1}, \"b\": {\"x\": 1, \"y\": 1}},"
        + "{\"a\": 1, \"b\": \"1\"}"
        + "]");
    assertQuery(doc, "$[?@.a == @.b]", "$[0]", "$[1]", "$[2]", "$[3]", "$[4]", "$[5]");
    assertQuery(doc, "$[?@.a != @.b]", "$[6]", "$[7]", "$[8]", "$[9]", "$[10]");
    assertQuery(doc, "$[?@.a > @.b]", "$[6]");
    assertQuery(doc, "$[?@.a < @.b]", "$[7]");
    assertQuery(doc, "$[?@.a >= @.b]", "$[0]", "$[1]", "$[2]", "$[3]", "$[4]", "$[5]", "$[6]");
    assertQuery(parse("[1, 1.0, 1e0, 100e-2, 2, \"1\"]"), "$[?@ == 1]", "$[0]", "$[1]", "$[2]",
        "$[3]");
    assertQuery(parse("[1, 1.0, 2]"), "$[?@ == 1.00]", "$[0]", "$[1]");
    assertQuery(parse("[1.5, 2, -3]"), "$[?@ > 1.4999999999999999999]", "$[0]", "$[1]");

    final JsonArray built = Json.createArrayBuilder()
        .add(new BigDecimal("1.000")).add(1).add(1L).add(1.0d).add(new BigInteger("1"))
        .add(new BigDecimal("1E+1")).build();
    assertQuery(built, "$[?@ == 1]", "$[0]", "$[1]", "$[2]", "$[3]", "$[4]");
    assertQuery(built, "$[?@ == 10]", "$[5]");
  }

  /**
   * @testName: jsonPathStringComparisonTest
   *
   * @assertion_ids: JSONP:JAVADOC:686;
   *
   * @test_Strategy: Strings compare by Unicode code points, not by UTF-16 code
   * units, so U+1F600 is greater than U+E000. Member names match only if they are
   * identical sequences of code points; no Unicode normalization is applied.
   * length() counts code points.
   */
  @Test
  public void jsonPathStringComparisonTest() {
    final JsonValue strings = Json.createArrayBuilder()
        .add("\ue000").add("\ud83d\ude00").add("a").add("ab").add("").build();
    assertQuery(strings, "$[?@ > '\\ue000']", "$[1]");
    assertQuery(strings, "$[?@ < '\\ud83d\\ude00']", "$[0]", "$[2]", "$[3]", "$[4]");
    assertQuery(strings, "$[?@ > 'a']", "$[0]", "$[1]", "$[3]");
    assertQuery(strings, "$[?@ <= 'a']", "$[2]", "$[4]");
    assertQuery(strings, "$[?@ == '\ud83d\ude00']", "$[1]");
    assertQuery(strings, "$[?length(@) == 1]", "$[0]", "$[1]", "$[2]");

    final JsonValue names = Json.createObjectBuilder()
        .add("\u00e9", 1).add("e\u0301", 2).add("\u212b", 3).build();
    assertQuery(names, "$['\u00e9']", "$['\u00e9']");
    assertQuery(names, "$['\\u00e9']", "$['\u00e9']");
    assertQuery(names, "$['e\u0301']", "$['e\u0301']");
    assertQuery(names, "$['\u00c5']");
    assertQuery(names, "$['\u212b']", "$['\u212b']");
  }

  /**
   * @testName: jsonPathRegularExpressionTest
   *
   * @assertion_ids: JSONP:JAVADOC:686;
   *
   * @test_Strategy: match() and search() use I-Regexp (RFC 9485): match() tests the
   * whole string, search() a substring, '.' does not match line terminators, and
   * patterns that are not I-Regexps, such as Java's \d or (?i), make the function
   * return false instead of failing. Patterns may come from the document.
   */
  @Test
  public void jsonPathRegularExpressionTest() {
    final JsonValue strings = Json.createArrayBuilder()
        .add("abc").add("a\nc").add("a\rc").add("ABC").add("1").add("\u00c4").add("b").build();
    assertQuery(strings, "$[?match(@, 'a.c')]", "$[0]");
    assertQuery(strings, "$[?search(@, 'a.c')]", "$[0]");
    assertQuery(strings, "$[?match(@, 'b')]", "$[6]");
    assertQuery(strings, "$[?search(@, 'b')]", "$[0]", "$[6]");
    assertQuery(strings, "$[?match(@, '[a-c]+')]", "$[0]", "$[6]");
    assertQuery(strings, "$[?match(@, '\\\\p{Lu}')]", "$[5]");
    assertQuery(strings, "$[?match(@, '\\\\p{Lu}+')]", "$[3]", "$[5]");
    assertQuery(strings, "$[?match(@, '[0-9]')]", "$[4]");
    assertQuery(strings, "$[?match(@, '\\\\d')]");
    assertQuery(strings, "$[?search(@, '(?i)abc')]");
    assertQuery(strings, "$[?match(@, '[')]");
    assertQuery(strings, "$[?!match(@, '[')]", "$[0]", "$[1]", "$[2]", "$[3]", "$[4]", "$[5]",
        "$[6]");
    assertQuery(strings, "$[?search(@, 'a\\nc')]", "$[1]");

    final JsonValue patterns = parse("["
        + "{\"s\": \"2026-10-18\", \"p\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\"},"
        + "{\"s\": \"x\", \"p\": \"[\"},"
        + "{\"s\": \"x\", \"p\": 1},"
        + "{\"s\": 1, \"p\": \"1\"}"
        + "]");
    assertQuery(patterns, "$[?match(@.s, @.p)]", "$[0]");
    assertQuery(patterns, "$[?search(@.s, @.p)]", "$[0]");
  }

  /**
   * @testName: jsonPathRegularExpressionLinearTimeTest
   *
   * @assertion_ids: JSONP:JAVADOC:686;
   *
   * @test_Strategy: match() and search() take time linear in the length of the
   * string. Patterns that need exponential time with a backtracking matcher, from
   * the query and from the document, are applied to long strings; the queries
   * complete well within the timeout and select nothing.
   */
  @Test
  public void jsonPathRegularExpressionLinearTimeTest() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 5_000; i++) {
      sb.append('a');
    }
    final String input = sb.toString();
    final JsonValue doc = Json.createArrayBuilder()
        .add(Json.createObjectBuilder().add("s", input).add("p", "(a|aa)*c"))
        .add(Json.createObjectBuilder().add("s", input + "!").add("p", "(a*)*b"))
        .build();
    assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
      assertQuery(doc, "$[?match(@.s, '(a|aa)*c')]");
      assertQuery(doc, "$[?search(@.s, '(a*)*b')]");
      assertQuery(doc, "$[?match(@.s, '(a|a)*b')]");
      assertQuery(doc, "$[?match(@.s, @.p)]");
      assertQuery(doc, "$[?search(@.s, @.p)]");
    }, "regular expressions on a string of " + input.length() + " characters");
  }

  /**
   * @testName: jsonPathReuseTest
   *
   * @assertion_ids: JSONP:JAVADOC:684; JSONP:JAVADOC:686;
   *
   * @test_Strategy: A JsonPath is immutable and safe for use by multiple concurrent
   * threads: one instance is applied to different documents, in sequence and from
   * several threads at the same time, and always returns the same result for the same
   * document.
   */
  @Test
  public void jsonPathReuseTest() throws Exception {
    final JsonPath path = Json.createPath("$..[?@.price < 10].title");
    final JsonValue doc = parse(STORE);
    final JsonValue other = parse("[{\"title\": \"x\", \"price\": 1}]");
    final List<String> expected = List.of("$['store']['items'][1]['title']",
        "$['store']['items'][3]['title']");
    assertEquals(expected, paths(path.query(doc)), "first query");
    assertEquals(List.of("$[0]['title']"), paths(path.query(other)), "other document");
    assertEquals(expected, paths(path.query(doc)), "same document again");

    final ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      final List<Callable<Boolean>> tasks = new ArrayList<>();
      for (int t = 0; t < 16; t++) {
        final boolean useOther = t % 2 == 0;
        tasks.add(() -> {
          for (int i = 0; i < 200; i++) {
            final List<String> actual = paths(path.query(useOther ? other : doc));
            if (!actual.equals(useOther ? List.of("$[0]['title']") : expected)) {
              return false;
            }
          }
          return true;
        });
      }
      for (Future<Boolean> result : executor.invokeAll(tasks)) {
        assertTrue(result.get(), "concurrent query returned a different result");
      }
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * @testName: jsonPathDeepDocumentTest
   *
   * @assertion_ids: JSONP:JAVADOC:686;
   *
   * @test_Strategy: Implementations bound the work a query may do and report an
   * exceeded limit with JsonException, never with a partial result. A descendant
   * query on a document nested 100,000 levels deep either returns the correct single
   * node or throws JsonException; any other outcome, such as StackOverflowError, fails.
   */
  @Test
  public void jsonPathDeepDocumentTest() {
    final int depth = 100_000;
    JsonValue doc = Json.createArrayBuilder().add(1).build();
    for (int i = 1; i < depth; i++) {
      doc = Json.createArrayBuilder().add(doc).build();
    }
    final List<JsonPath.Node> nodes;
    try {
      nodes = Json.createPath("$..[?@ == 1]").query(doc);
    } catch (JsonException e) {
      LOGGER.info("Limit exceeded: " + e.getMessage());
      return;
    }
    assertEquals(1, nodes.size(), "nodes with value 1");
    final StringBuilder expected = new StringBuilder("$");
    for (int i = 0; i < depth; i++) {
      expected.append("[0]");
    }
    assertEquals(expected.toString(), nodes.get(0).getPath(), "path of the innermost element");
    assertEquals(Json.createValue(1), nodes.get(0).getValue(), "value of the innermost element");
  }

  /**
   * @testName: jsonPathDeepQueryTest
   *
   * @assertion_ids: JSONP:JAVADOC:684; JSONP:JAVADOC:686;
   *
   * @test_Strategy: A filter with 10,000 nested parentheses or negations either is
   * created and selects the correct nodes, or createPath or query throws
   * JsonException because it exceeds a limit; any other outcome, such as
   * StackOverflowError, fails.
   */
  @Test
  public void jsonPathDeepQueryTest() {
    final int depth = 10_000;
    final StringBuilder parens = new StringBuilder("$[?");
    final StringBuilder nots = new StringBuilder("$[?");
    for (int i = 0; i < depth; i++) {
      parens.append('(');
      nots.append("!(");
    }
    parens.append("@ == 1");
    nots.append("@ == 1");
    for (int i = 0; i < depth; i++) {
      parens.append(')');
      nots.append(')');
    }
    parens.append(']');
    nots.append(']');
    for (String query : new String[] {parens.toString(), nots.toString()}) {
      try {
        final List<JsonPath.Node> nodes = Json.createPath(query).query(parse("[0, 1]"));
        assertEquals(List.of("$[1]"), paths(nodes), "deeply nested query");
      } catch (JsonException e) {
        LOGGER.info("Limit exceeded: " + e.getMessage());
      }
    }
  }

  /* Helpers */

  /**
   * Applies {@code query} to {@code doc} and checks the Normalized Paths of the result
   * and, for a structured document, that the value of each node is the value at its
   * JSON Pointer.
   */
  private static void assertQuery(final JsonValue doc, final String query,
      final String... expectedPaths) {
    LOGGER.info(" - query " + query);
    final List<JsonPath.Node> nodes = Json.createPath(query).query(doc);
    assertEquals(List.of(expectedPaths), paths(nodes), "Normalized Paths of " + query);
    if (doc instanceof JsonStructure) {
      for (JsonPath.Node node : nodes) {
        assertEquals(node.getValue(), node.getPointer().getValue((JsonStructure) doc),
            "value of " + node.getPath() + " for " + query);
      }
    }
  }

  private static void assertPointer(final JsonValue doc, final String query,
      final String expectedPointer) {
    final JsonPath.Node node = single(doc, query);
    assertEquals(expectedPointer, node.getPointer().toString(), "pointer of " + query);
    assertEquals(node.getValue(), node.getPointer().getValue((JsonStructure) doc),
        "value at pointer of " + query);
  }

  private static JsonPath.Node single(final JsonValue doc, final String query) {
    final List<JsonPath.Node> nodes = Json.createPath(query).query(doc);
    if (nodes.size() != 1) {
      fail(query + " selects " + paths(nodes) + ", expected a single node");
    }
    return nodes.get(0);
  }

  private static void assertNullValue(final JsonValue value) {
    assertNotNull(value, "getValue() is never null");
    assertEquals(JsonValue.ValueType.NULL, value.getValueType(), "value type of JSON null");
    assertEquals(JsonValue.NULL, value, "JSON null is JsonValue.NULL");
  }

  private static String[] expectedMemberPaths(final JsonObject object) {
    final List<String> paths = new ArrayList<>();
    for (Map.Entry<String, JsonValue> member : object.entrySet()) {
      paths.add("$['" + member.getKey() + "']");
    }
    return paths.toArray(new String[0]);
  }

}
