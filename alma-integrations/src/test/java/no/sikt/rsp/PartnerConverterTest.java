package no.sikt.rsp;

import static no.sikt.clients.basebibliotek.BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN;
import static no.sikt.clients.basebibliotek.BaseBibliotekUtils.KATSYST_BIBSYS;
import static no.sikt.clients.basebibliotek.BaseBibliotekUtils.KATSYST_TIDEMANN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigInteger;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import test.utils.BasebibliotekGenerator;
import test.utils.RecordBuilder;

class PartnerConverterTest {

    private static final String BIBNR = "0030100";
    private static final String ALMA_CODES = """
         [
           {
             "almaCode": "AASENTUN",
             "libCode": "1152001"
           }
         ]
        """;

    @Test
    void shouldReportFailureWhenRecordIsMissingRequiredFieldsBibnrAndLandkodeButWillOnlyShowLandkodeInException() {
        var record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), KATSYST_TIDEMANN)
                         .withBibnr(null)
                         .withLandkode(null)
                         .build();
        var baseBibliotek = new BasebibliotekGenerator(record).generateBaseBibliotek();
        var almaCodeProvider = new AlmaCodeProvider(ALMA_CODES);
        var partnerConverter = new PartnerConverter(almaCodeProvider, "ill-server", baseBibliotek);

        var exception = assertThrows(RuntimeException.class, partnerConverter::toPartners);

        assertThat(exception.getMessage(), containsString("Could not convert record, missing landkode"));
    }

    @Test
    void shouldFormatExceptionMessageCorrectlyWhenLandkodeIsMissing() {
        var record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), KATSYST_BIBSYS)
                         .withBibnr(BIBNR)
                         .withLandkode(null)
                         .build();
        var baseBibliotek = new BasebibliotekGenerator(record).generateBaseBibliotek();
        var almaCodeProvider = new AlmaCodeProvider(ALMA_CODES);
        var partnerConverter = new PartnerConverter(almaCodeProvider, "ill-server", baseBibliotek);

        var exception = assertThrows(RuntimeException.class,
                                     () -> partnerConverter.logProblemAndThrowException(record));

        assertThat(exception.getMessage(), containsString("Could not convert record, missing landkode"));
    }

    @Test
    void shouldFormatExceptionMessageCorrectlyWhenLandkodeIsPresent() {
        var record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), KATSYST_BIBSYS)
                         .withBibnr(BIBNR)
                         .withLandkode(COUNTRY_CODE_NORWEGIAN)
                         .build();
        var baseBibliotek = new BasebibliotekGenerator(record).generateBaseBibliotek();
        var almaCodeProvider = new AlmaCodeProvider(ALMA_CODES);
        var partnerConverter = new PartnerConverter(almaCodeProvider, "ill-server", baseBibliotek);

        var exception = assertThrows(RuntimeException.class,
                                     () -> partnerConverter.logProblemAndThrowException(record));

        assertThat(exception.getMessage(), containsString("Could not convert record, missing , record"));
    }

}