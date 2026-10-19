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

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * A {@link JsonObject} implemented by the application rather than by the JSON
 * Processing implementation. A JSONPath query argument may be any {@link JsonValue},
 * so implementations must not depend on their own classes.
 */
final class MapJsonObject extends AbstractMap<String, JsonValue> implements JsonObject {

  private final Map<String, JsonValue> members;

  MapJsonObject(final Map<String, JsonValue> members) {
    this.members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
  }

  @Override
  public Set<Map.Entry<String, JsonValue>> entrySet() {
    return members.entrySet();
  }

  @Override
  public JsonArray getJsonArray(final String name) {
    return (JsonArray) get(name);
  }

  @Override
  public JsonObject getJsonObject(final String name) {
    return (JsonObject) get(name);
  }

  @Override
  public JsonNumber getJsonNumber(final String name) {
    return (JsonNumber) get(name);
  }

  @Override
  public JsonString getJsonString(final String name) {
    return (JsonString) get(name);
  }

  @Override
  public String getString(final String name) {
    return getJsonString(name).getString();
  }

  @Override
  public String getString(final String name, final String defaultValue) {
    final JsonValue value = get(name);
    return value instanceof JsonString ? ((JsonString) value).getString() : defaultValue;
  }

  @Override
  public int getInt(final String name) {
    return getJsonNumber(name).intValue();
  }

  @Override
  public int getInt(final String name, final int defaultValue) {
    final JsonValue value = get(name);
    return value instanceof JsonNumber ? ((JsonNumber) value).intValue() : defaultValue;
  }

  @Override
  public boolean getBoolean(final String name) {
    final JsonValue value = get(name);
    if (value == null) {
      throw new NullPointerException(name);
    }
    if (value == JsonValue.TRUE || value.getValueType() == ValueType.TRUE) {
      return true;
    }
    if (value == JsonValue.FALSE || value.getValueType() == ValueType.FALSE) {
      return false;
    }
    throw new ClassCastException(name);
  }

  @Override
  public boolean getBoolean(final String name, final boolean defaultValue) {
    final JsonValue value = get(name);
    if (value != null && value.getValueType() == ValueType.TRUE) {
      return true;
    }
    if (value != null && value.getValueType() == ValueType.FALSE) {
      return false;
    }
    return defaultValue;
  }

  @Override
  public boolean isNull(final String name) {
    final JsonValue value = get(name);
    if (value == null) {
      throw new NullPointerException(name);
    }
    return value.getValueType() == ValueType.NULL;
  }

  @Override
  public ValueType getValueType() {
    return ValueType.OBJECT;
  }
}
