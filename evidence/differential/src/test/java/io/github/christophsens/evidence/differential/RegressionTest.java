package io.github.christophsens.evidence.differential;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Minimal repros of differences found so far (see RESULTS.md); they stay as fixed cases. */
class RegressionTest {

    /** Johnzon threw ArithmeticException from JsonNumber.bigDecimalValue() (scale limit 1000) while comparing. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "[1e1000000, 5]|$[?@ > 0]",
        "[1e1000000, 5]|$[?@ == 1e1000000]",
        "[-1e1000000, 1e-1000000]|$[?@ < 1]",
        "{\"a\": 1e-1000000}|$[?@.a != 0]",
        "[1e400, 1e999]|$[?@ >= 1e400]",
    })
    void hugeExponents(String document, String query) {
        List<Differential.Diff> diffs = Differential.check(0, document, Differential.PARSSON.parse(document),
                Differential.JOHNZON.parse(document), query, new Differential.Report());
        assertTrue(diffs.isEmpty(), () -> diffs.toString());
    }
}
