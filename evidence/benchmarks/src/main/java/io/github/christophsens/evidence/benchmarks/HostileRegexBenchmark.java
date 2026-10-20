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

/**
 * A regular expression that backtracks exponentially in {@code java.util.regex} ({@code ((a+)+)+b} on a string of
 * n times "a" and a "!"). RFC 9535 requires I-Regexp (RFC 9485), which Parsson, Johnzon and Treetrail run on an
 * automaton; Jayway JsonPath passes the pattern to java.util.regex.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class HostileRegexBenchmark {

    @Param({"PARSSON", "JOHNZON", "TREETRAIL", "JAYWAY"})
    public Impl impl;

    @Param({"10", "15", "20", "24"})
    public int length;

    private Object document;
    private Impl.Compiled compiled;

    @Setup
    public void setup() {
        document = impl.parse("[{\"s\":\"" + "a".repeat(length) + "!\"}]");
        compiled = impl.compile(new Query("hostile", "$[?match(@.s, '((a+)+)+b')]", "$[?(@.s =~ /((a+)+)+b/)]"));
    }

    @Benchmark
    public Object run() {
        return compiled.query(document);
    }
}
