package io.github.christophsens.evidence.differential;

import io.github.treetrail.jsonpath.jsonp.JsonpModel;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonPath;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The three implementations behind one shape: compile, query, stream. Results are reduced to comparable strings. */
final class Engines {

    private Engines() {}

    /** One selected node, reduced to strings. The value is canonical (object members sorted, numbers numeric). */
    record Hit(String path, String pointer, String value) {}

    /** Result of an operation: hits, or a failure with the exception class (kept for the report, not compared). */
    record Result(List<Hit> hits, String error) {
        static Result ok(List<Hit> hits) {
            return new Result(hits, null);
        }

        static Result fail(Throwable t) {
            return new Result(null, t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).lines().findFirst().orElse(""));
        }

        boolean failed() {
            return error != null;
        }
    }

    interface Compiled {
        Result query(JsonValue document);
    }

    interface Engine {
        String name();

        /** Parses the text with this engine's own parser; Treetrail uses the Parsson tree. */
        JsonValue parse(String json);

        Compiled compile(String query);
    }

    static final JsonProvider PARSSON = new org.eclipse.parsson.JsonProviderImpl();
    static final JsonProvider JOHNZON = new org.apache.johnzon.core.JsonProviderImpl();

    static final class JsonpEngine implements Engine {
        final String name;
        final JsonProvider provider;

        JsonpEngine(String name, JsonProvider provider) {
            this.name = name;
            this.provider = provider;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public JsonValue parse(String json) {
            try (var reader = provider.createReader(new StringReader(json))) {
                return reader.readValue();
            }
        }

        @Override
        public Compiled compile(String query) {
            JsonPath path = provider.createPath(query);
            return doc -> {
                try {
                    List<Hit> hits = new ArrayList<>();
                    for (JsonPath.Node n : path.query(doc)) {
                        hits.add(new Hit(n.getPath(), n.getPointer().toString(), canon(n.getValue())));
                    }
                    return Result.ok(hits);
                } catch (RuntimeException | StackOverflowError e) {
                    return Result.fail(e);
                }
            };
        }

        JsonPath path(String query) {
            return provider.createPath(query);
        }

        /** Streams the text through the matcher. */
        Result stream(JsonPath path, String json) {
            try (JsonParser parser = provider.createParser(new StringReader(json))) {
                JsonPath.Matcher m = path.matcher(parser);
                List<Hit> hits = new ArrayList<>();
                while (m.find()) {
                    hits.add(new Hit(m.getPath(), m.getPointer().toString(), canon(m.getValue())));
                }
                return Result.ok(hits);
            } catch (RuntimeException | StackOverflowError e) {
                return Result.fail(e);
            }
        }

        /** Checks that every node's pointer leads back to the node's value. Returns null if so, else a description. */
        String pointerProblem(String query, JsonValue doc) {
            if (!(doc instanceof JsonStructure structure)) {
                return null;
            }
            try {
                for (JsonPath.Node n : provider.createPath(query).query(doc)) {
                    String got = canon(n.getPointer().getValue(structure));
                    String want = canon(n.getValue());
                    if (!got.equals(want)) {
                        return name + ": pointer " + n.getPointer() + " resolves to " + got + ", node value is " + want;
                    }
                }
            } catch (RuntimeException e) {
                return name + ": pointer resolution threw " + e;
            }
            return null;
        }
    }

    static final class TreetrailEngine implements Engine {
        @Override
        public String name() {
            return "TREETRAIL";
        }

        @Override
        public JsonValue parse(String json) {
            return new JsonpEngine("", PARSSON).parse(json);
        }

        @Override
        public Compiled compile(String query) {
            io.github.treetrail.jsonpath.JsonPath path = io.github.treetrail.jsonpath.JsonPath.compile(query);
            return doc -> {
                try {
                    List<Hit> hits = new ArrayList<>();
                    for (io.github.treetrail.jsonpath.Node<JsonValue> n : path.query(doc, JsonpModel.INSTANCE)) {
                        hits.add(new Hit(n.path(), n.normalizedPath().toJsonPointer(), canon(n.value())));
                    }
                    return Result.ok(hits);
                } catch (RuntimeException | StackOverflowError e) {
                    return Result.fail(e);
                }
            };
        }
    }

    /** Canonical text of a value: object members sorted by name, numbers by numeric value, strings length-prefixed. */
    static String canon(JsonValue v) {
        StringBuilder sb = new StringBuilder();
        canon(v, sb);
        return sb.toString();
    }

    private static void canon(JsonValue v, StringBuilder sb) {
        switch (v.getValueType()) {
            case OBJECT -> {
                Map<String, JsonValue> sorted = new TreeMap<>(((JsonObject) v));
                sb.append('{');
                sorted.forEach((k, x) -> {
                    sb.append(k.length()).append(':').append(k).append('=');
                    canon(x, sb);
                    sb.append(',');
                });
                sb.append('}');
            }
            case ARRAY -> {
                sb.append('[');
                for (JsonValue x : v.asJsonArray()) {
                    canon(x, sb);
                    sb.append(',');
                }
                sb.append(']');
            }
            case STRING -> {
                String s = ((JsonString) v).getString();
                sb.append('"').append(s.length()).append(':').append(s).append('"');
            }
            case NUMBER -> {
                // bigDecimalValue() is limited in scale by some providers (Johnzon: 1000); the literal is not.
                BigDecimal d = new BigDecimal(((JsonNumber) v).toString());
                if (d.signum() == 0) {
                    sb.append('0');
                } else {
                    BigDecimal s = d.stripTrailingZeros();
                    sb.append(s.unscaledValue()).append('e').append(s.scale() * -1);
                }
            }
            case TRUE -> sb.append("true");
            case FALSE -> sb.append("false");
            case NULL -> sb.append("null");
        }
    }
}
