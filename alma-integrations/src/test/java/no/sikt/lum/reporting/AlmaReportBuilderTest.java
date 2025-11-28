package no.sikt.lum.reporting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AlmaReportBuilderTest {

    @Test
    void shouldCreateReportStringWithCorrectNumberOfSuccessesAndFailures() {
        var reportBuilder = new AlmaReportBuilder();
        reportBuilder.addSuccess("lib001");
        reportBuilder.addSuccess("lib002");
        reportBuilder.addFailure("lib003", "NTNU");
        reportBuilder.addFailure("lib003", "NORDFORSK");
        reportBuilder.addSuccess("lib003");
        reportBuilder.addSuccess("lib003");

        var report = reportBuilder.generateReport().toString();

        assertThat(report, containsString("lib001 \t ok:1 \t failures:0 \t failed:[]"));
        assertThat(report, containsString("lib002 \t ok:1 \t failures:0 \t failed:[]"));
        assertThat(report, containsString("lib003 \t ok:2 \t failures:2 \t failed:[NTNU, NORDFORSK]"));
    }

    @Test
    void shouldNotCrashOnNullValuesButCountThemLikeTheOthers() {
        var reportBuilder = new AlmaReportBuilder();
        reportBuilder.addSuccess(null);
        reportBuilder.addSuccess(null);
        reportBuilder.addSuccess(null);
        reportBuilder.addFailure(null, null);
        reportBuilder.addFailure(null, null);

        var report = reportBuilder.generateReport().toString();

        assertThat(report, containsString("ok:3"));
        assertThat(report, containsString("failures:2"));
    }

    @Test
    void shouldHandleConcurrentAddSuccessCallsCorrectly() {
        var reportBuilder = new AlmaReportBuilder();

        // Add 1000 successes concurrently from multiple threads
        IntStream.range(0, 1000).parallel().forEach(i -> {
            reportBuilder.addSuccess("testLib");
        });

        var report = reportBuilder.generateReport().toString();
        assertThat(report, containsString("testLib \t ok:1000"));
    }

    @Test
    void shouldHandleConcurrentAddFailureCallsCorrectly() {
        var reportBuilder = new AlmaReportBuilder();

        // Add 1000 failures concurrently from multiple threads
        IntStream.range(0, 1000).parallel().forEach(i -> {
            reportBuilder.addFailure("testLib", "instance" + i);
        });

        var report = reportBuilder.generateReport().toString();
        assertThat(report, containsString("testLib \t ok:0 \t failures:1000"));
    }

    @Test
    void shouldHandleConcurrentMixedCallsCorrectly() {
        var reportBuilder = new AlmaReportBuilder();

        // Add 500 successes and 500 failures concurrently
        IntStream.range(0, 1000).parallel().forEach(i -> {
            if (i % 2 == 0) {
                reportBuilder.addSuccess("mixedLib");
            } else {
                reportBuilder.addFailure("mixedLib", "fail" + i);
            }
        });

        var report = reportBuilder.generateReport().toString();
        assertThat(report, containsString("mixedLib \t ok:500 \t failures:500"));
    }

}
