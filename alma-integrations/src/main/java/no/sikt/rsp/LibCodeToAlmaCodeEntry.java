package no.sikt.rsp;

import no.sikt.json.NotNullOrEmpty;
import nva.commons.core.JacocoGenerated;

public class LibCodeToAlmaCodeEntry {
    public static final String FIELD_IS_NULL_OR_EMPTY_MESSAGE = "Field can not be null or empty";

    @NotNullOrEmpty(message = FIELD_IS_NULL_OR_EMPTY_MESSAGE)
    private final String libCode;
    @NotNullOrEmpty(message = FIELD_IS_NULL_OR_EMPTY_MESSAGE)
    private final String almaCode;

    @JacocoGenerated
    public LibCodeToAlmaCodeEntry(String libCode, String almaCode) {
        this.libCode = libCode;
        this.almaCode = almaCode;
    }

    public String getLibCode() {
        return libCode;
    }

    public String getAlmaCode() {
        return almaCode;
    }
}
