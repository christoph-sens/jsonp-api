package io.github.christophsens.evidence.benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.json.JsonNumber;
import jakarta.json.JsonPath;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A benchmark is only meaningful if every implementation returns the same values for the same query. */
class BenchmarkFairnessTest {

    private static String norm(Object o) {
        if (o instanceof JsonString s) {
            return s.getString();
        }
        if (o instanceof JsonNumber n) {
            return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(o);
    }

    private static List<String> values(Object result) {
        List<String> out = new ArrayList<>();
        for (Object o : (Collection<?>) result) {
            out.add(norm(o));
        }
        return out;
    }

    @Test
    void sameValuesForEveryQuery() {
        String text = Data.store(100);
        for (Query q : Query.ALL) {
            List<String> expected = values(Impl.TREETRAIL.compile(q).query(Impl.TREETRAIL.parse(text)));
            assertFalse(expected.isEmpty(), q.name() + " selects nothing, it would measure nothing");
            for (Impl impl : Impl.values()) {
                assertEquals(expected, values(impl.compile(q).query(impl.parse(text))), q.name() + " on " + impl);
            }
        }
    }

    @Test
    void hostileRegexIsFalseEverywhere() {
        for (Impl impl : Impl.values()) {
            Object doc = impl.parse("[{\"s\":\"aaaaaaaaaa!\"}]");
            Query q = new Query("hostile", "$[?match(@.s, '((a+)+)+b')]", "$[?(@.s =~ /((a+)+)+b/)]");
            assertEquals(0, ((Collection<?>) impl.compile(q).query(doc)).size(), impl.toString());
        }
    }

    @Test
    void streamingGivesTheTreeValues() {
        String text = Data.store(1000);
        for (Impl impl : new Impl[] {Impl.PARSSON, Impl.JOHNZON}) {
            for (String name : new String[] {"definite", "wildcard", "filter"}) {
                Query q = Query.byName(name);
                JsonPath path = impl.provider.createPath(q.rfc());
                List<String> streamed = new ArrayList<>();
                try (JsonParser parser = impl.provider.createParser(new StringReader(text))) {
                    JsonPath.Matcher m = path.matcher(parser);
                    while (m.find()) {
                        streamed.add(norm(m.getValue()));
                    }
                }
                assertEquals(values(impl.compile(q).query(impl.parse(text))), streamed, name + " on " + impl);
            }
        }
    }
}
