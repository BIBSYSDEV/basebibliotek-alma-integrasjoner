package no.sikt.rsp;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlmaCodeProvider {

    private static final Logger logger = LoggerFactory.getLogger(AlmaCodeProvider.class);

    private final Map<String, String> libraryNoToAlmaCodeMap = new HashMap<>();

    public AlmaCodeProvider(final String jsonConfig) {
        if (StringUtils.isNotEmpty(jsonConfig)) {
            final Gson gson = new Gson();
            try {
                ConfigEntry[] configEntries = gson.fromJson(jsonConfig, ConfigEntry[].class);
                if (configEntries != null) {
                    Arrays.stream(configEntries)
                        .filter(configEntry -> StringUtils.isNotEmpty(configEntry.libraryNo) && StringUtils.isNotEmpty(
                            configEntry.almaInstitutionCode))
                        .forEach(
                            configEntry -> libraryNoToAlmaCodeMap.put(configEntry.libraryNo,
                                                                      configEntry.almaInstitutionCode));
                } else {
                    logger.error("No alma code mapping table configured (null or empty)!");
                }
            } catch (JsonSyntaxException e) {
                logger.error("Alma code mapping table configuration is invalid!", e);
            }
        }
    }

    public Optional<String> getAlmaCode(final String libraryNo) {
        final String almaCode = libraryNoToAlmaCodeMap.get(libraryNo);
        if (almaCode == null) {
            return Optional.empty();
        } else {
            return Optional.of(almaCode);
        }
    }

    private static class ConfigEntry {

        private final String libraryNo;
        private final String almaInstitutionCode;

        @JacocoGenerated
        private ConfigEntry(String libraryNo, String almaInstitutionCode) {
            this.libraryNo = libraryNo;
            this.almaInstitutionCode = almaInstitutionCode;
        }
    }
}
