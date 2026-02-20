package no.sikt.rsp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Path;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlmaCodeProviderTest {

    private static final String SAMPLE_ALMA_CODE = "AHUS";
    private static final String LIBCODE_MATCHING_SAMPLE_ALMA_CODE = "1023001";

    private AlmaCodeProvider almaCodeProvider;

    @BeforeEach
    void setUp() {
        var almaCodeMappings = readAlmaCodeMappings();
        almaCodeProvider = new AlmaCodeProvider(almaCodeMappings);
    }

    @Test
    void shouldGetAlmaCodeFromLibCode() {
        var actual = almaCodeProvider.getAlmaCode(LIBCODE_MATCHING_SAMPLE_ALMA_CODE);

        assertThat(actual.orElseThrow(), equalTo(SAMPLE_ALMA_CODE));
    }

    @Test
    void shouldGetEmptyOptionalWhenLibCodeNotFoundInAlmaCodeMappings() {
        var actual = almaCodeProvider.getAlmaCode("INVALID");

        assertThat(actual.isEmpty(), equalTo(true));
    }

    @Test
    void shouldGetLibCodeFromAlmaCode() {
        var actual = almaCodeProvider.getLibCode(SAMPLE_ALMA_CODE);

        assertThat(actual.orElseThrow(), equalTo(LIBCODE_MATCHING_SAMPLE_ALMA_CODE));
    }

    @Test
    void shouldGetAvailableAlmaCodes() {
        var almaCodes = almaCodeProvider.getAvailableAlmaCodes();

        assertThat(almaCodes.size(), equalTo(241));
        assertThat(almaCodes.contains(SAMPLE_ALMA_CODE), equalTo(true));
    }

    @Test
    void shouldThrowErrorWhenAlmaCodeMappingsIsNull() {
        var exception = assertThrows(RuntimeException.class, () -> new AlmaCodeProvider(null));

        assertThat(exception.getMessage(), equalTo("Alma code mapping table is empty."));
    }

    @Test
    void shouldThrowErrorWhenAlmaCodeMappingsIsInvalid() {
        var invalidAlmaCodeMappings = """
            "invalid" : "json"
            """;

        var exception = assertThrows(RuntimeException.class, () -> new AlmaCodeProvider(invalidAlmaCodeMappings));

        assertThat(exception.getMessage(), equalTo("Alma code mapping JSON is invalid."));
    }

    private String readAlmaCodeMappings() {
        return IoUtils.stringFromResources(Path.of("fullLibCodeToAlmaCodeMapping.json"));
    }

}