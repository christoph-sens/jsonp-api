package io.github.christophsens.evidence.fuzz;

import static io.github.christophsens.evidence.fuzz.Checks.check;
import static io.github.christophsens.evidence.fuzz.Checks.checkTime;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import com.code_intelligence.jazzer.mutation.annotation.WithUtf8Length;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Fuzz targets for the JSONPath implementations of Eclipse Parsson and Apache Johnzon: everything that takes untrusted
 * input, the query string, the document and the regular expressions in it. Both providers are instantiated directly,
 * so one run covers both. Each target may only throw the exceptions the API documents, results must be consistent
 * (see {@link Checks}), and every input must be processed within {@link Checks#TIME_LIMIT_NANOS} per provider.
 *
 * <p>{@code mvn test} replays the seeds (the JSONPath Compliance Test Suite as vendored in the TCK, and the
 * regression cases below) and the saved findings in {@code src/test/resources/.../JsonPathFuzzTestInputs/<target>/}.
 * {@code mvn test -Pfuzz -Dfuzz.target=<target> -Dfuzz.duration=10m} fuzzes one target.
 */
class JsonPathFuzzTest {

    static final List<JsonProvider> PROVIDERS = List.of(
            new org.eclipse.parsson.JsonProviderImpl(),
            new org.apache.johnzon.core.JsonProviderImpl());

    /** Any string as a query: only {@code JsonException} may occur. */
    @MethodSource("queries")
    @FuzzTest
    void compileAnyQuery(@NotNull @WithUtf8Length(max = 1_000) String query) {
        for (JsonProvider provider : PROVIDERS) {
            Checks.startTiming();
            Checks.compile(provider, query);
            checkTime(provider, query);
        }
    }

    /** Any string as a query on any string as a document, with {@code query()} and with the matcher. */
    @MethodSource("cases")
    @FuzzTest
    void queryAnyDocument(@NotNull @WithUtf8Length(max = 500) String query,
                          @NotNull @WithUtf8Length(max = 5_000) String json) {
        long takeMask = query.hashCode() * 31L + json.hashCode();
        for (JsonProvider provider : PROVIDERS) {
            Checks.run(provider, query, json, takeMask);
        }
    }

    /**
     * A generated query on a generated document. Most inputs are well-formed, so they reach the evaluator, the filters
     * and functions, the buffering of the matcher and the limits rather than the syntax checks.
     */
    @FuzzTest
    void queryGeneratedDocument(FuzzedDataProvider data) {
        Generator generator = new Generator(data);
        String json = generator.document();
        String query = generator.query();
        long takeMask = data.consumeLong();
        for (JsonProvider provider : PROVIDERS) {
            Checks.run(provider, query, json, takeMask);
        }
    }

    /**
     * Any pattern on any string, through {@code match()} and {@code search()}, with the pattern both as a literal of
     * the query and as a value of the document. Neither may throw; both forms must agree, a match implies a search hit,
     * and Parsson and Johnzon must agree (their I-Regexp code has a common origin, so this guards the port, not the
     * semantics; the CTS and the differential tests check those).
     */
    @MethodSource("regularExpressions")
    @FuzzTest
    void matchAnyRegularExpression(@NotNull @WithUtf8Length(max = 500) String pattern,
                                   @NotNull @WithUtf8Length(max = 5_000) String subject) {
        boolean[] first = null;
        for (JsonProvider provider : PROVIDERS) {
            Checks.startTiming();
            JsonArray document = provider.createArrayBuilder()
                    .add(provider.createObjectBuilder().add("s", subject).add("p", pattern))
                    .build();
            String literal = Strings.literal(pattern);
            boolean[] results = new boolean[4];
            Checks.time(() -> {
                results[0] = selects(provider, "$[?match(@.s, " + literal + ")]", document);
                results[1] = selects(provider, "$[?match(@.s, @.p)]", document);
                results[2] = selects(provider, "$[?search(@.s, " + literal + ")]", document);
                results[3] = selects(provider, "$[?search(@.s, @.p)]", document);
            });
            String where = provider.getClass().getName() + ": " + Strings.abbreviate(pattern) + " on "
                    + Strings.abbreviate(subject);
            check(results[0] == results[1], "match() differs for a literal and a pattern from the document, " + where);
            check(results[2] == results[3], "search() differs for a literal and a pattern from the document, " + where);
            check(!results[0] || results[2], "match() without a search() hit, " + where);
            if (first == null) {
                first = results;
            } else {
                check(Arrays.equals(first, results), "Parsson and Johnzon disagree, " + where);
            }
            checkTime(provider, pattern + " on " + subject);
        }
    }

    private static boolean selects(JsonProvider provider, String query, JsonValue document) {
        // No limit can be reached on a one-element array, so neither createPath nor query may throw.
        return provider.createPath(query).query(document).size() == 1;
    }

    // ---- seeds ----

    private static final List<JsonObject> CTS = readCts();

    static Stream<Arguments> queries() {
        return CTS.stream().map(test -> Arguments.of(test.getString("selector")));
    }

    static Stream<Arguments> cases() {
        Stream<Arguments> cts = CTS.stream()
                .filter(test -> !test.getBoolean("invalid_selector", false) && test.containsKey("document"))
                .map(test -> Arguments.of(test.getString("selector"), test.get("document").toString()));
        return Stream.concat(cts, regressionCases());
    }

    /**
     * Inputs of earlier findings (see RESULTS.md). None was a defect of JSONPath; each made the harness or a tolerated
     * parser behavior visible, and stays here so that a change in either is noticed.
     */
    static Stream<Arguments> regressionCases() {
        return Stream.of(
                // The reader accepts trailing text after the first value; the harness reads only the first value.
                Arguments.of("$.*[\"first\",\"second\"]", "[1, 2] a"),
                Arguments.of("$[?@.a!=null]", "[{\"a\":1}] x"),
                // Johnzon's parser takes U+0000 for the end of the input (NoSuchElementException), and has nothing to
                // read in an empty text (NothingToRead from the reader, ArrayIndexOutOfBoundsException from the parser).
                Arguments.of("$[?@.a!=null]", "\u0000[{\"a\":2}]"),
                Arguments.of("$.true", "12\u00003"),
                // Both parsers: an exponent beyond the range of int (NumberFormatException from BigDecimal).
                Arguments.of("$..*", "[1e99999999999]"),
                Arguments.of("$", ""),
                Arguments.of("$..*", "   "),
                // Parsson's parser fails beyond 1,000 levels with a plain RuntimeException.
                Arguments.of("$..*", "[".repeat(1_001) + "]".repeat(1_001)),
                // Parsson's skipObject()/skipArray(), which the matcher uses, looped forever on a truncated text
                // (fixed in christoph-sens/parsson#11).
                Arguments.of("$.b", "{\"a\": {\"x\": 1"),
                Arguments.of("$.b", "[[1, 2"),
                // The matcher's work for routes under pending decisions was not counted (parsson#10, johnzon#5).
                Arguments.of("$..[?@..*]..[-5]..a.a", "[".repeat(270) + "false" + "]".repeat(270)),
                // Results of ~90,000 nodes with deep paths; the work is bounded by the limits.
                Arguments.of("$.*..*..['a','a'].a", "{\"a\":".repeat(300) + "1" + "}".repeat(300)),
                Arguments.of("$..[-4]..[:,'a']", "[".repeat(999) + "1" + "]".repeat(999)));
    }

    static Stream<Arguments> regularExpressions() {
        return Stream.of(
                Arguments.of("a.c", "abc"),
                Arguments.of("[a-z]+[0-9]{2,4}", "abc123"),
                Arguments.of("\\p{Lu}\\p{Ll}*", "Hello"),
                Arguments.of("[^\\n\\r]*", "line"),
                Arguments.of("(a|b)*a(a|b){12}", "abababababababab"),
                Arguments.of("((a+)+)+b", "aaaaaaaaaaaaaaaaaaaaaaaa!"),
                Arguments.of("^ab|cd$", "abcd"),
                Arguments.of("[\\p{L}\\p{Nd}_-]{1,64}", "user_42"),
                Arguments.of("(x{2,3}|y?)*z", "xxyxxxz"),
                Arguments.of("(a|b)*a(){00000001390468697}", "C"),
                Arguments.of("(a{1000}){1000}", "a"),
                Arguments.of("\\d", "1"),
                Arguments.of("(?i)a", "A"));
    }

    private static List<JsonObject> readCts() {
        Path file = Path.of(System.getProperty("evidence.cts", "cts.json"));
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             JsonReader reader = PROVIDERS.get(0).createReader(in)) {
            List<JsonObject> tests = new ArrayList<>();
            for (JsonValue test : reader.readObject().getJsonArray("tests")) {
                tests.add(test.asJsonObject());
            }
            return tests;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the CTS from " + file + " (system property evidence.cts)", e);
        }
    }
}
