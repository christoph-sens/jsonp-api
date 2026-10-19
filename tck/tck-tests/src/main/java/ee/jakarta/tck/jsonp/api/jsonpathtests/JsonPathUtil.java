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

import jakarta.json.Json;
import jakarta.json.JsonPath;
import jakarta.json.JsonReader;
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
