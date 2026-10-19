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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonPath;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Helpers shared by the JSONPath tests.
 */
final class JsonPathUtil {

  /**
   * JUnit tag of the JSONPath tests. Implementations of JSON Processing 2.1 or
   * earlier do not support JSONPath; test runs against them exclude this tag.
   */
  static final String TAG = "jsonpath";

  /**
   * JUnit tag of the tests of the streaming layer ({@link JsonPath#matcher}), in addition
   * to {@link #TAG}. Lets CI exclude them for an implementation that does not provide the
   * streaming layer yet; a certification run excludes no tag.
   */
  static final String STREAMING_TAG = "jsonpath-streaming";

  /**
   * JUnit tag of the tests of {@code Json.createPath(String, Map)}, in addition to
   * {@link #TAG}; see {@link #STREAMING_TAG}.
   */
  static final String CONFIG_TAG = "jsonpath-config";

  private JsonPathUtil() {
  }

  /**
   * Parses JSON text with the implementation under test.
   *
   * @param json JSON text
   * @return the parsed value
   */
  static JsonValue parse(final String json) {
    try (JsonReader reader = Json.createReader(new StringReader(json))) {
      return reader.readValue();
    }
  }

  /**
   * Returns the Normalized Paths of the nodes, in order.
   *
   * @param nodes JSONPath query result
   * @return the paths
   */
  static List<String> paths(final List<JsonPath.Node> nodes) {
    final List<String> paths = new ArrayList<>(nodes.size());
    for (JsonPath.Node node : nodes) {
      paths.add(node.getPath());
    }
    return paths;
  }

  /**
   * Compares two JSON values structurally, numbers by {@link java.math.BigDecimal#compareTo}
   * and objects regardless of member order.
   *
   * @param a a value
   * @param b another value
   * @return whether the values are equal
   */
  static boolean sameValue(final JsonValue a, final JsonValue b) {
    if (a.getValueType() != b.getValueType()) {
      return false;
    }
    switch (a.getValueType()) {
      case NUMBER:
        return ((JsonNumber) a).bigDecimalValue().compareTo(((JsonNumber) b).bigDecimalValue()) == 0;
      case STRING:
        return ((JsonString) a).getString().equals(((JsonString) b).getString());
      case ARRAY:
        final JsonArray x = a.asJsonArray();
        final JsonArray y = b.asJsonArray();
        if (x.size() != y.size()) {
          return false;
        }
        for (int i = 0; i < x.size(); i++) {
          if (!sameValue(x.get(i), y.get(i))) {
            return false;
          }
        }
        return true;
      case OBJECT:
        final JsonObject o = a.asJsonObject();
        final JsonObject p = b.asJsonObject();
        if (!o.keySet().equals(p.keySet())) {
          return false;
        }
        for (Map.Entry<String, JsonValue> member : o.entrySet()) {
          if (!sameValue(member.getValue(), p.get(member.getKey()))) {
            return false;
          }
        }
        return true;
      default:
        return true;
    }
  }

  /**
   * Returns a JSONPath string literal in single quotes for the given string. All
   * control characters are written as Unicode escapes, so the literal differs from
   * the form used in Normalized Paths.
   *
   * @param s any string
   * @return the literal
   */
  static String quote(final String s) {
    final StringBuilder sb = new StringBuilder("'");
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '\'' || c == '\\') {
        sb.append('\\').append(c);
      } else if (c < 0x20) {
        sb.append(String.format("\\u%04X", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.append('\'').toString();
  }
}
