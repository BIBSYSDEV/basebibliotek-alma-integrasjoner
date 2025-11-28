package no.sikt.lum.reporting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UserReportBuilderTest {

    @Test
    void shouldCreateReportStringWithCorrectNumberOfFailures() {
        var reportBuilder = new UserReportBuilder();
        reportBuilder.addFailure("003", "NTNU");
        reportBuilder.addFailure("003", "NORDFORSK");
        reportBuilder.addFailure("004", "NORDFORSK");

        var report = reportBuilder.generateReport().toString();

        assertThat(report,
                   containsString("003 \t failures:2 \t Could not convert to user \t failed:[NTNU, NORDFORSK]"));
        assertThat(report,
                   containsString("004 \t failures:1 \t Could not convert to user \t failed:[NORDFORSK]"));
    }

    @Test
    void shouldNotCrashOnNullValuesButCountThemLikeTheOthers() {
        var reportBuilder = new UserReportBuilder();
        reportBuilder.addFailure(null, null);
        reportBuilder.addFailure(null, null);

        var report = reportBuilder.generateReport().toString();

        assertThat(report, containsString("failures:2"));
    }

    @Test
    void shouldHandleConcurrentAddFailureCallsCorrectly() {
        var reportBuilder = new UserReportBuilder();

        // Add 1000 failures concurrently from multiple threads
        IntStream.range(0, 1000).parallel().forEach(i -> {
            reportBuilder.addFailure("testLib", "instance" + i);
        });

        var report = reportBuilder.generateReport().toString();
        assertThat(report, containsString("testLib \t failures:1000"));
    }

}
