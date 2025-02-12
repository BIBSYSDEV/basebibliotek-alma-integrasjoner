package no.sikt.lum.reporting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.Test;

class UserReportBuilderTest {

    /*
    003 failures:2 Could not convert to user failed:[NTNU, NORDFORSK]
    004 failures:1 Could not convert to user failed:[NORDFORSK]
    */

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

}
