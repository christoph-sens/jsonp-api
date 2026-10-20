package io.github.christophsens.evidence.fuzz;

import jakarta.json.JsonException;
import jakarta.json.JsonPath;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The checks of one query on one JSON text with one provider. Only the exceptions the API documents may occur;
 * everything else, including {@link StackOverflowError}, reaches Jazzer as a finding. Results are checked for
 * consistency within the provider (Normalized Paths, JSON Pointers, tree versus stream), not against the other
 * provider: that is the job of the differential tests (evidence/differential).
 */
final class Checks {

    /**
     * Keeps the work of fuzzed queries small so that each input finishes quickly; the other limits have their
     * defaults. The visited-nodes limit is what bounds the time of an evaluation, so it is low enough that
     * {@link #TIME_LIMIT_NANOS} is only exceeded by work the limit does not account for.
     */
    static final Map<String, Object> LIMITS = Map.of(
            JsonPath.MAX_VISITED_NODES, 200_000,
            JsonPath.MAX_RESULT_SIZE, 100_000,
            JsonPath.MAX_BUFFERED_NODES, 1_000_000);

    /**
     * Time the implementation may take for one input (compilation, query() and the matcher, not the checks of this
     * class, which can cost more than the evaluation): far above any honest input of the fuzzed sizes, even with
     * Jazzer's instrumentation, far below an exponential blow-up.
     */
    static final long TIME_LIMIT_NANOS = 5_000_000_000L;

    /** Results up to this size are compared between query() and the matcher; the paths of larger ones take gigabytes. */
    private static final int MAX_COMPARED = 10_000;

    /** Nodes per result whose value and JSON Pointer are checked; a result can have 100,000 nodes. */
    private static final int NODES_TO_CHECK = 1_000;

    /** Normalized Paths that are evaluated again as queries, per input. */
    private static final int PATHS_TO_REEVALUATE = 20;

    /** Nanoseconds spent in the implementation for the current input of this thread. */
    private static final ThreadLocal<long[]> SPENT = ThreadLocal.withInitial(() -> new long[1]);

    private Checks() {}

    static JsonPath compile(JsonProvider provider, String query) {
        try {
            long start = System.nanoTime();
            JsonPath path = provider.createPath(query, LIMITS);
            SPENT.get()[0] += System.nanoTime() - start;
            check(query.equals(path.toString()), "toString() differs from the query string");
            check(path.getBuffering() != null, "getBuffering() returned null");
            return path;
        } catch (JsonException expected) {
            return null; // the only exception createPath may throw
        }
    }

    /** Reads the text into a tree with the provider's reader, or returns null if the reader rejects it. */
    static JsonValue read(JsonProvider provider, String json) {
        try (JsonReader reader = provider.createReader(new StringReader(json))) {
            return reader.readValue();
        } catch (JsonException expected) {
            return null; // invalid JSON (JsonParsingException)
        } catch (RuntimeException e) {
            if (isKnownParserDefect(e)) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Exceptions that the JSON parsers and readers of Parsson and Johnzon throw for some invalid texts instead of
     * {@code JsonParsingException}. The code that throws them is the same on the master branches of Eclipse Parsson
     * and Apache Johnzon; it is not JSONPath, and the matcher lets exceptions of the parser pass through, as its
     * Javadoc says. Only exactly these are tolerated, recognized by type and by the class that throws them; see
     * RESULTS.md, "Findings outside JSONPath".
     * <ul>
     *     <li>Parsson: nesting deeper than {@code org.eclipse.parsson.maxDepth} (1,000) gives a plain
     *     {@code RuntimeException} from {@code JsonParserImpl$Stack.push}.</li>
     *     <li>Johnzon: a text without a value gives {@code JsonReaderImpl$NothingToRead} (an
     *     {@code IllegalStateException}) from the reader. The parser takes U+0000 for the end of the input: a text
     *     that starts with it gives {@code NoSuchElementException}, a number followed by it and more characters
     *     ({@code 12\u00003}) {@code NumberFormatException} from {@code getBigDecimal()}; an empty text gives
     *     {@code ArrayIndexOutOfBoundsException}. These three come from {@code JsonStreamParserImpl} (the first
     *     frame outside the JDK).</li>
     *     <li>Both: a number whose exponent is beyond the range of {@code int} ({@code 1e99999999999}) gives
     *     {@code NumberFormatException} from {@code BigDecimal}, called by Parsson's {@code JsonTokenizer} or by
     *     Johnzon's {@code JsonStreamParserImpl}.</li>
     * </ul>
     */
    static boolean isKnownParserDefect(RuntimeException e) {
        String thrower = "";
        for (StackTraceElement frame : e.getStackTrace()) {
            if (!frame.getClassName().startsWith("java.")) {
                thrower = frame.getClassName() + "." + frame.getMethodName();
                break;
            }
        }
        String type = e.getClass().getName();
        return type.equals("java.lang.RuntimeException") && thrower.equals("org.eclipse.parsson.JsonParserImpl$Stack.push")
                || type.equals("org.apache.johnzon.core.JsonReaderImpl$NothingToRead")
                || (e instanceof NoSuchElementException || e instanceof ArrayIndexOutOfBoundsException
                        || e instanceof NumberFormatException)
                        && thrower.startsWith("org.apache.johnzon.core.JsonStreamParserImpl")
                || e instanceof NumberFormatException && thrower.equals("org.eclipse.parsson.JsonTokenizer.getBigDecimal");
    }

    /** Runs the query on the text with {@code query()} and with the matcher and checks both. */
    static void run(JsonProvider provider, String query, String json, long takeMask) {
        startTiming();
        JsonPath path = compile(provider, query);
        if (path != null) {
            JsonValue document = read(provider, json);
            List<JsonPath.Node> nodes = document == null ? null : query(provider, path, document);
            stream(provider, path, json, takeMask, nodes);
        }
        checkTime(provider, query + " on " + json);
    }

    private static List<JsonPath.Node> query(JsonProvider provider, JsonPath path, JsonValue document) {
        List<JsonPath.Node> nodes;
        long start = System.nanoTime();
        try {
            nodes = path.query(document);
        } catch (JsonException expected) {
            return null; // a limit
        } finally {
            SPENT.get()[0] += System.nanoTime() - start;
        }
        int reevaluated = 0;
        for (JsonPath.Node node : nodes.subList(0, Math.min(nodes.size(), NODES_TO_CHECK))) {
            String where = path + " at " + node.getPath();
            JsonValue value = node.getValue();
            check(value != null, "null value " + where);
            check(node.getPath().startsWith("$"), "Normalized Path does not start with $: " + where);
            if (document instanceof JsonStructure structure) {
                JsonValue byPointer = provider.createPointer(node.getPointer().toString()).getValue(structure);
                check(same(byPointer, value), "JSON Pointer " + node.getPointer() + " does not reference the value " + where);
            } else {
                check(node.getPath().equals("$") && node.getPointer().toString().isEmpty(),
                        "a primitive query argument has only the root node: " + where);
            }
            if (reevaluated++ < PATHS_TO_REEVALUATE) {
                // A Normalized Path is a query that selects exactly that node (RFC 9535, section 2.7).
                List<JsonPath.Node> again = provider.createPath(node.getPath()).query(document);
                check(again.size() == 1 && again.get(0).getPath().equals(node.getPath()),
                        "Normalized Path does not select its node again: " + where);
            }
        }
        return nodes;
    }

    private static void stream(JsonProvider provider, JsonPath path, String json, long takeMask,
                               List<JsonPath.Node> nodes) {
        boolean unbounded = path.getBuffering() == JsonPath.Buffering.UNBOUNDED;
        try (JsonParser parser = provider.createParser(new StringReader(json))) {
            JsonPath.Matcher matcher;
            try {
                matcher = path.matcher(parser);
            } catch (JsonException e) {
                check(unbounded, "matcher() rejected a query of class " + path.getBuffering() + ": " + path);
                return;
            }
            check(!unbounded, "matcher() accepted a query of class UNBOUNDED: " + path);
            List<String> paths = new ArrayList<>();
            Map<String, JsonValue> taken = new HashMap<>();
            long lastOffset = -1;
            int i = 0;
            long start = System.nanoTime();
            try {
                while (matcher.find()) {
                    String at = matcher.getPath();
                    if (paths.size() <= MAX_COMPARED) {
                        paths.add(at);
                    }
                    check(at.startsWith("$"), "matcher path does not start with $: " + at);
                    long offset = matcher.getLocation().getStreamOffset();
                    check(offset < 0 || offset >= lastOffset, "matches not in document order at " + at + " for " + path);
                    lastOffset = Math.max(lastOffset, offset);
                    if ((takeMask >>> (i++ & 63) & 1) != 0 && taken.size() <= MAX_COMPARED) {
                        JsonValue value = matcher.getValue();
                        check(value != null, "matcher value null at " + at);
                        check(same(value, matcher.getValue()), "repeated getValue() differs at " + at);
                        taken.put(at, value);
                    }
                }
            } catch (JsonException expected) {
                return; // invalid JSON (JsonParsingException) or a limit
            } catch (RuntimeException e) {
                if (isKnownParserDefect(e)) {
                    return;
                }
                throw e;
            } finally {
                SPENT.get()[0] += System.nanoTime() - start;
            }
            if (nodes != null && nodes.size() <= MAX_COMPARED && paths.size() <= MAX_COMPARED
                    && !hasDuplicateNames(provider, json)) {
                List<String> expected = new ArrayList<>();
                Map<String, JsonValue> values = new HashMap<>();
                for (JsonPath.Node node : nodes) {
                    expected.add(node.getPath());
                    values.putIfAbsent(node.getPath(), node.getValue());
                }
                List<String> actual = new ArrayList<>(paths);
                Collections.sort(expected);
                Collections.sort(actual);
                check(expected.equals(actual), "matcher and query() select different nodes for " + path
                        + ": " + Strings.abbreviate(String.valueOf(actual)) + " vs " + Strings.abbreviate(String.valueOf(expected)));
                for (Map.Entry<String, JsonValue> e : taken.entrySet()) {
                    check(same(e.getValue(), values.get(e.getKey())), "matcher value differs from query() at " + e.getKey());
                }
            }
        }
    }

    /** Whether some object of the (valid) text contains a member name twice; then the tree and the stream differ. */
    private static boolean hasDuplicateNames(JsonProvider provider, String json) {
        Deque<Set<String>> objects = new ArrayDeque<>();
        try (JsonParser parser = provider.createParser(new StringReader(json))) {
            // Only the first value: the matcher reads no further, and the reader may accept trailing text.
            do {
                switch (parser.next()) {
                    case START_OBJECT -> objects.push(new HashSet<>());
                    case END_OBJECT -> objects.pop();
                    case START_ARRAY -> objects.push(Set.of());
                    case END_ARRAY -> objects.pop();
                    case KEY_NAME -> {
                        if (!objects.peek().add(parser.getString())) {
                            return true;
                        }
                    }
                    default -> { }
                }
            } while (!objects.isEmpty());
        }
        return false;
    }

    /**
     * Whether two values of the same provider and text are the same JSON value, by their JSON text. Neither
     * {@code equals} nor {@code toString()} of the values: for numbers whose scale exceeds Johnzon's limit
     * ({@code johnzon.max-big-decimal-scale}, 1,000), such as 1e1000000, Johnzon's {@code JsonNumber.equals} and the
     * {@code toString()} of an array or object that contains one throw ArithmeticException.
     */
    static boolean same(JsonValue a, JsonValue b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        StringBuilder x = new StringBuilder();
        StringBuilder y = new StringBuilder();
        text(a, x);
        text(b, y);
        return x.toString().contentEquals(y);
    }

    private static void text(JsonValue value, StringBuilder out) {
        switch (value.getValueType()) {
            case OBJECT -> {
                out.append('{');
                for (Map.Entry<String, JsonValue> member : value.asJsonObject().entrySet()) {
                    out.append(member.getKey().length()).append(':').append(member.getKey()).append('=');
                    text(member.getValue(), out);
                    out.append(',');
                }
                out.append('}');
            }
            case ARRAY -> {
                out.append('[');
                for (JsonValue element : value.asJsonArray()) {
                    text(element, out);
                    out.append(',');
                }
                out.append(']');
            }
            case STRING -> {
                String string = ((JsonString) value).getString();
                out.append('"').append(string.length()).append(':').append(string);
            }
            default -> out.append(value); // numbers by JsonNumber.toString(), true, false, null
        }
    }

    static void startTiming() {
        SPENT.get()[0] = 0;
    }

    static void time(Runnable implementation) {
        long start = System.nanoTime();
        try {
            implementation.run();
        } finally {
            SPENT.get()[0] += System.nanoTime() - start;
        }
    }

    static void checkTime(JsonProvider provider, String input) {
        long elapsed = SPENT.get()[0];
        check(elapsed <= TIME_LIMIT_NANOS, provider.getClass().getName() + " took " + elapsed / 1_000_000 + " ms: "
                + Strings.abbreviate(input));
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
