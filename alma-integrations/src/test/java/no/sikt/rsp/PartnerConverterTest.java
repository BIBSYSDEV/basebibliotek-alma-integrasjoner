package no.sikt.rsp;

import static no.sikt.clients.basebibliotek.BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN;
import static no.sikt.clients.basebibliotek.BaseBibliotekUtils.KATSYST_BIBSYS;
import static no.sikt.clients.basebibliotek.BaseBibliotekUtils.KATSYST_TIDEMANN;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.stream.Stream;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import no.sikt.alma.partners.generated.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest(name = "stengtFra={0}, stengtTil={1} should give status {2}")
    @MethodSource("provideStengtIntervalArguments")
    void shouldCalculateCorrectStatusBasedOnStengtInterval(String stengtFraDesc,
                                                           String stengtTilDesc,
                                                           Status expectedStatus) {
        var record = new RecordBuilder(BigInteger.ONE, LocalDate.now(), KATSYST_BIBSYS)
                         .withBibnr(BIBNR)
                         .withLandkode(COUNTRY_CODE_NORWEGIAN)
                         .withStengtFra(toXmlCalendar(stengtFraDesc))
                         .withStengtTil(toXmlCalendar(stengtTilDesc))
                         .build();
        var baseBibliotek = new BasebibliotekGenerator(record).generateBaseBibliotek();
        var almaCodeProvider = new AlmaCodeProvider(ALMA_CODES);
        var partnerConverter = new PartnerConverter(almaCodeProvider, "ill-server", baseBibliotek);

        var partners = partnerConverter.toPartners();

        assertThat(partners.getFirst().getPartnerDetails().getStatus(), is(equalTo(expectedStatus)));
    }

    private static Stream<Arguments> provideStengtIntervalArguments() {
        return Stream.of(
            // TRUE cases - currentDateIsInStengtInterval returns true → INACTIVE
            Arguments.of("past", "future", Status.INACTIVE),   // Branch A: fra in past, til in future
            Arguments.of("past", "null", Status.INACTIVE),     // Branch B: fra in past, til not set
            Arguments.of("null", "future", Status.INACTIVE),   // Branch C: fra not set, til in future

            // FALSE cases - currentDateIsInStengtInterval returns false → ACTIVE
            Arguments.of("null", "null", Status.ACTIVE),       // both not set
            Arguments.of("null", "past", Status.ACTIVE),       // fra not set, til in past
            Arguments.of("past", "past", Status.ACTIVE),       // both in past (interval ended)
            Arguments.of("future", "null", Status.ACTIVE),     // fra in future, til not set
            Arguments.of("future", "past", Status.ACTIVE),     // fra in future, til in past
            Arguments.of("future", "future", Status.ACTIVE)    // both in future (interval not started)
        );
    }

    private static XMLGregorianCalendar toXmlCalendar(String description) {
        if ("null".equals(description)) {
            return null;
        }
        long offsetMillis = "past".equals(description)
                                ? -DAY_IN_MILLISECONDS
                                : DAY_IN_MILLISECONDS;
        var calendar = (GregorianCalendar) GregorianCalendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(Instant.now().toEpochMilli() + offsetMillis);
        return attempt(() -> DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar)).orElseThrow();
    }

    private static final long DAY_IN_MILLISECONDS = 1000L * 60 * 60 * 24;

}