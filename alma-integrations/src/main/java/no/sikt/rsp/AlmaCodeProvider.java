package no.sikt.rsp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import no.sikt.json.AnnotatedDeserializer;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlmaCodeProvider {

    private static final Logger logger = LoggerFactory.getLogger(AlmaCodeProvider.class);

    /* default */ static final String EMPTY_MAPPING_TABLE_MESSAGE = "Alma code mapping table is empty.";
    /* default */ static final String INVALID_JSON_MESSAGE = "Alma code mapping JSON is invalid.";

    private static final Map<String, String> libCodeToAlmaCodeMap = new ConcurrentHashMap<>();

    public AlmaCodeProvider(final String jsonConfig) {
        if (StringUtils.isNotEmpty(jsonConfig)) {
            Gson gson =
                new GsonBuilder()
                    .registerTypeAdapter(LibCodeToAlmaCodeEntry.class, new AnnotatedDeserializer<>())
                    .create();
            try {
                LibCodeToAlmaCodeEntry[] entries = gson.fromJson(jsonConfig, LibCodeToAlmaCodeEntry[].class);
                if (entries != null) {
                    Arrays.stream(entries)
                        .forEach(entry -> libCodeToAlmaCodeMap.put(entry.getLibCode(), entry.getAlmaCode()));
                } else {
                    logger.error(EMPTY_MAPPING_TABLE_MESSAGE);
                    throw new RuntimeException(EMPTY_MAPPING_TABLE_MESSAGE);
                }
            } catch (JsonSyntaxException e) {
                logger.error(INVALID_JSON_MESSAGE, e);
                throw new RuntimeException(INVALID_JSON_MESSAGE, e);
            }
        } else {
            logger.error(EMPTY_MAPPING_TABLE_MESSAGE);
            throw new RuntimeException(EMPTY_MAPPING_TABLE_MESSAGE);
        }
    }

    public Optional<String> getAlmaCode(final String libraryNo) {
        final String almaCode = libCodeToAlmaCodeMap.get(libraryNo);
        if (almaCode == null) {
            return Optional.empty();
        } else {
            return Optional.of(almaCode);
        }
    }

    public List<String> getAvailableAlmaCodes() {
        return new ArrayList<>(libCodeToAlmaCodeMap.keySet());
    }
}
