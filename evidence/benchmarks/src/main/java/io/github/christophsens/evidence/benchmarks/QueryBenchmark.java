package io.github.christophsens.evidence.benchmarks;

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

/** Running a compiled query on a bookstore with 100 books (about 11 kB as JSON); the document is parsed beforehand. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class QueryBenchmark {

    @Param({"PARSSON", "JOHNZON", "TREETRAIL", "JAYWAY"})
    public Impl impl;

    @Param({"definite", "wildcard", "filter", "descendant", "function", "regex"})
    public String query;

    private Object document;
    private Impl.Compiled compiled;

    @Setup
    public void setup() {
        document = impl.parse(Data.store(100));
        compiled = impl.compile(Query.byName(query));
    }

    @Benchmark
    public Object run() {
        return compiled.query(document);
    }
}
