package no.sikt.lum.reporting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("PMD.AvoidSynchronizedAtMethodLevel")
public class UserReportBuilder implements ReportGenerator {

    // Example: 1000000 failures:2 Could not convert to user failed:[MOLDESYS, NTNU]
    public static final String TEMPLATE = "%s \t failures:%s \t Could not convert to user \t failed:[%s]";
    public static final String FAILED_INSTANCES_DELIMITER = ", ";
    public static final String LINE_BREAK = "\n";

    private final Map<String, List<String>> failures;
    private final Set<String> allLibraryCodes;

    /**
     * Creates a report builder that builds a report of user conversion failures in String format.
     **/
    public UserReportBuilder() {
        failures = new HashMap<>();
        allLibraryCodes = new LinkedHashSet<>();
    }

    public synchronized void addFailure(String libraryCode, String failedInstance) {
        allLibraryCodes.add(libraryCode);
        var failuresForLibrary = failures.getOrDefault(libraryCode, new ArrayList<>());
        failuresForLibrary.add(failedInstance);
        failures.put(libraryCode, failuresForLibrary);
    }

    @Override
    public synchronized StringBuilder generateReport() {
        var reportStringBuilder = new StringBuilder();

        allLibraryCodes.forEach(libraryCode -> {
            var failedInstances = failures.getOrDefault(libraryCode, List.of());
            var failNumber = failedInstances.size();
            var failedInstancesString = String.join(FAILED_INSTANCES_DELIMITER, failedInstances);
            var line = String.format(TEMPLATE, libraryCode, failNumber, failedInstancesString);

            reportStringBuilder.append(line).append(LINE_BREAK);
        });

        return reportStringBuilder;
    }

}
