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
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Compiling a query, for code that does not keep compiled paths. Note that Jayway JsonPath caches compiled paths
 * for {@code JsonPath.read(String, ...)} but not for {@code JsonPath.compile}, which is measured here.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CompileBenchmark {

    @Param({"PARSSON", "JOHNZON", "TREETRAIL", "JAYWAY"})
    public Impl impl;

    @Param({"definite", "filter", "function", "regex"})
    public String query;

    private Query q;

    @org.openjdk.jmh.annotations.Setup
    public void setup() {
        q = Query.byName(query);
    }

    @Benchmark
    public Object compile() {
        return impl.compileOnly(q);
    }
}
