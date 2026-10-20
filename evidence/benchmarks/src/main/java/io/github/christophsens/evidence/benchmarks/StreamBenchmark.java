package io.github.christophsens.evidence.benchmarks;

import jakarta.json.JsonPath;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * The streaming matcher against the tree on a bookstore with 92,000 books (about 10 MB as JSON). Three ways to the
 * same values: {@code stream} runs the matcher over a parser on the text; {@code parseAndQuery} parses a tree and
 * queries it (what a caller without a tree has to do); {@code queryTree} queries a tree that exists already.
 * The text is a String held in memory, so no I/O is measured. Only Parsson and Johnzon have a streaming matcher.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class StreamBenchmark {

    @Param({"PARSSON", "JOHNZON"})
    public Impl impl;

    @Param({"definite", "wildcard", "filter"})
    public String query;

    @Param({"92000"})
    public int books;

    private String text;
    private JsonValue tree;
    private JsonPath path;

    @Setup
    public void setup() {
        text = Data.store(books);
        tree = impl.parse(text);
        path = impl.provider.createPath(Query.byName(query).rfc());
    }

    @Benchmark
    public Object stream() {
        List<Object> out = new ArrayList<>();
        try (JsonParser parser = impl.provider.createParser(new StringReader(text))) {
            JsonPath.Matcher m = path.matcher(parser);
            while (m.find()) {
                out.add(m.getValue());
            }
        }
        return out;
    }

    @Benchmark
    public Object parseAndQuery() {
        return values(impl.parse(text));
    }

    @Benchmark
    public Object queryTree() {
        return values(tree);
    }

    private Object values(JsonValue doc) {
        List<Object> out = new ArrayList<>();
        for (JsonPath.Node n : path.query(doc)) {
            out.add(n.getValue());
        }
        return out;
    }
}
