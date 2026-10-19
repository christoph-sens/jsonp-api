package io.github.christophsens.evidence.differential;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Budget and seed come from system properties (see the module pom): {@code evidence.docs}, {@code evidence.queriesPerDoc},
 * {@code evidence.seed}. {@code evidence.replay=<case seed>} re-runs one case from a failure report.
 */
class DifferentialTest {

    private static long prop(String name, long dflt) {
        String v = System.getProperty(name);
        return v == null || v.isBlank() ? dflt : Long.parseLong(v.trim());
    }

    @Test
    void implementationsAgree() {
        int queriesPerDoc = (int) prop("evidence.queriesPerDoc", 40);
        String replay = System.getProperty("evidence.replay");
        Differential.Report report;
        if (replay != null && !replay.isBlank()) {
            report = Differential.replay(Long.parseLong(replay.trim()), queriesPerDoc);
        } else {
            report = Differential.run(prop("evidence.seed", 20261019L), (int) prop("evidence.docs", 5000), queriesPerDoc);
        }
        System.out.println("DIFFERENTIAL " + report.summary());
        assertTrue(report.clean(), () -> "implementations disagree:\n" + report.summary());
    }
}
