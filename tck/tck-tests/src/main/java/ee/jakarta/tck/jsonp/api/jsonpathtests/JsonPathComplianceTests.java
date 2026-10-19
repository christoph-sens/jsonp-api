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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonPath;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import static ee.jakarta.tck.jsonp.api.jsonpathtests.JsonPathUtil.paths;
import static ee.jakarta.tck.jsonp.api.jsonpathtests.JsonPathUtil.sameValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@see <a href="https://www.rfc-editor.org/rfc/rfc9535">RFC 9535</a>}: JSONPath
 * conformance tests.<br>
 * Runs every test case of the
 * <a href="https://github.com/jsonpath-standard/jsonpath-compliance-test-suite">JSONPath
 * Compliance Test Suite</a> (CTS) through {@link Json#createPath(String)} and
 * {@link JsonPath#query(JsonValue)}. The suite is third-party content under the
 * BSD-2-Clause license; see {@code cts/README.md} next to this class.
 */
@Tag(JsonPathUtil.TAG)
public class JsonPathComplianceTests {

  /** The CTS, relative to this class. */
  private static final String CTS = "cts/cts.json";

  /** Number of test cases in the pinned CTS; guards against a truncated or wrong resource. */
  private static final int CTS_CASES = 715;

  /**
   * @testName: jsonPathComplianceTest
   *
   * @assertion_ids: JSONP:JAVADOC:684; JSONP:JAVADOC:686; JSONP:JAVADOC:688;
   *                 JSONP:JAVADOC:689; JSONP:JAVADOC:690;
   *
   * @test_Strategy: One dynamic test per CTS test case. The CTS is read with the
   * implementation under test. A case with "invalid_selector" passes if
   * Json.createPath throws JsonException. Any other case creates the query, applies it
   * to the case's document and compares the values (numbers by BigDecimal.compareTo)
   * and Normalized Paths of the result, in order, with "result" and "result_paths". A
   * case with several allowed results ("results" and "results_paths", for object
   * members whose order RFC 9535 does not stipulate) passes if the values and paths
   * match one of the alternatives with the same index. For each node, the JSON Pointer
   * applied to the document references the node's value.
   */
  @TestFactory
  public Stream<DynamicTest> jsonPathComplianceTest() {
    final JsonArray cases = load().getJsonArray("tests");
    assertEquals(CTS_CASES, cases.size(), "number of test cases in " + CTS);
    return cases.stream()
        .map(JsonValue::asJsonObject)
        .map(testCase -> DynamicTest.dynamicTest(testCase.getString("name"), () -> run(testCase)));
  }

  private static JsonObject load() {
    try (InputStream in = JsonPathComplianceTests.class.getResourceAsStream(CTS)) {
      assertNotNull(in, "resource " + CTS);
      try (JsonReader reader = Json.createReader(in)) {
        return reader.readObject();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void run(final JsonObject testCase) {
    // Test reports name dynamic tests inconsistently, so every message starts with the CTS name.
    final String name = "CTS \"" + testCase.getString("name") + "\": ";
    final String selector = testCase.getString("selector");
    if (testCase.getBoolean("invalid_selector", false)) {
      assertThrows(JsonException.class, () -> Json.createPath(selector),
          name + "invalid query " + selector);
      return;
    }
    final JsonValue document = testCase.get("document");
    final List<JsonPath.Node> nodes = Json.createPath(selector).query(document);
    final String actual = name + "query " + selector + " on " + document + " selected " + describe(nodes);

    if (testCase.containsKey("result")) {
      assertTrue(matches(testCase.getJsonArray("result"), testCase.getJsonArray("result_paths"), nodes),
          actual + ", expected " + testCase.get("result") + " at " + testCase.get("result_paths"));
    } else {
      final JsonArray results = testCase.getJsonArray("results");
      final JsonArray resultsPaths = testCase.getJsonArray("results_paths");
      assertEquals(results.size(), resultsPaths.size(), name + "number of alternatives");
      boolean matched = false;
      for (int i = 0; i < results.size() && !matched; i++) {
        matched = matches(results.getJsonArray(i), resultsPaths.getJsonArray(i), nodes);
      }
      if (!matched) {
        fail(actual + ", expected one of " + results + " at " + resultsPaths);
      }
    }

    for (JsonPath.Node node : nodes) {
      assertTrue(sameValue(node.getValue(), node.getPointer().getValue((JsonStructure) document)),
          "value at JSON Pointer \"" + node.getPointer() + "\" of " + node.getPath() + ", " + actual);
    }
  }

  private static boolean matches(final JsonArray values, final JsonArray paths,
      final List<JsonPath.Node> nodes) {
    if (values.size() != nodes.size() || paths.size() != nodes.size()) {
      return false;
    }
    for (int i = 0; i < nodes.size(); i++) {
      if (!paths.getString(i).equals(nodes.get(i).getPath())
          || !sameValue(values.get(i), nodes.get(i).getValue())) {
        return false;
      }
    }
    return true;
  }

  private static String describe(final List<JsonPath.Node> nodes) {
    final StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < nodes.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(nodes.get(i).getValue()).append(" at ").append(nodes.get(i).getPath());
    }
    return sb.append(']').toString() + " (paths " + paths(nodes) + ")";
  }
}
