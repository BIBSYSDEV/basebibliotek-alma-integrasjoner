package no.sikt.lum.reporting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.Test;

class AlmaReportBuilderTest {

    /*
    lib001 ok:1 failures:0 failed:[]
    lib002 ok:1 failures:0 failed:[]
    lib003 ok:2 failures:2 failed:[NTNU, NORDFORSK]
    */

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

}