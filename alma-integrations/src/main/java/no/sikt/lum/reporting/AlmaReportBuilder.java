package no.sikt.lum.reporting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AlmaReportBuilder implements ReportGenerator {

    // Example: lib1000000 ok:70 failures:2 failed:[MOLDESYS, NTNU]
    public static final String TEMPLATE = "%s \t ok:%s \t failures:%s \t failed:[%s]";
    public static final String FAILED_INSTANCES_DELIMITER = ", ";
    public static final String LINE_BREAK = "\n";

    private final Map<String, Integer> successes;
    private final Map<String, List<String>> failures;
    private final Set<String> allLibraryCodes;

    /**
     * Creates a report builder that builds a report of alma request successes and failures in String format.
     **/
    public AlmaReportBuilder() {
        successes = new HashMap<>();
        failures = new HashMap<>();
        allLibraryCodes = new LinkedHashSet<>();
    }

    public void addSuccess(String libraryCode) {
        allLibraryCodes.add(libraryCode);
        successes.put(libraryCode, successes.getOrDefault(libraryCode, 0) + 1);
    }

    public void addFailure(String libraryCode, String failedInstance) {
        allLibraryCodes.add(libraryCode);
        var failuresForLibrary = failures.getOrDefault(libraryCode, new ArrayList<>());
        failuresForLibrary.add(failedInstance);
        failures.put(libraryCode, failuresForLibrary);
    }

    @Override
    public StringBuilder generateReport() {
        var reportStringBuilder = new StringBuilder();

        allLibraryCodes.forEach(libraryCode -> {
            int successNumber = successes.getOrDefault(libraryCode, 0);
            var failedInstances = failures.getOrDefault(libraryCode, List.of());
            var failNumber = failedInstances.size();
            var failedInstancesString = String.join(FAILED_INSTANCES_DELIMITER, failedInstances);
            var line = String.format(TEMPLATE, libraryCode, successNumber, failNumber, failedInstancesString);

            reportStringBuilder.append(line).append(LINE_BREAK);
        });

        return reportStringBuilder;
    }

}
