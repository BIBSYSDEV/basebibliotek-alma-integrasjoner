package no.sikt.rsp;

import com.google.gson.Gson;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AlmaCodeProvider {
    private final Map<String, String> libraryNoToAlmaCodeMap = new HashMap<>();

    public AlmaCodeProvider(final String jsonConfig) {
        if (StringUtils.isNotEmpty(jsonConfig)) {
            final Gson gson = new Gson();
            ConfigEntry[] configEntries = gson.fromJson(jsonConfig, ConfigEntry[].class);
            Arrays.stream(configEntries).forEach(configEntry -> libraryNoToAlmaCodeMap.put(configEntry.libraryNo, configEntry.almaCode));
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
        private String libraryNo;
        private String almaCode;

        @JacocoGenerated
        public void setLibraryNo(String libraryNo) {
            this.libraryNo = libraryNo;
        }

        @JacocoGenerated
        public void setAlmaCode(String almaCode) {
            this.almaCode = almaCode;
        }
    }
}
