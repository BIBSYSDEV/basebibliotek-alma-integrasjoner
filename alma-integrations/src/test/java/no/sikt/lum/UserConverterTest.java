package no.sikt.lum;

import static no.sikt.clients.basebibliotek.BaseBibliotekUtils.COUNTRY_CODE_NORWEGIAN;
import static no.sikt.clients.basebibliotek.BaseBibliotekUtils.KATSYST_TIDEMANN;
import static no.sikt.commons.AlmaObjectConverter.PERMANENTLY_CLOSED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.user.generated.User;
import no.sikt.lum.reporting.UserReportBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import test.utils.BasebibliotekGenerator;
import test.utils.RecordBuilder;

class UserConverterTest {

    private static final String BIBNR = "0030100";
    private static final String INST = "Test Bibliotek";
    private static final String BIBLTYPE = "Folkebibliotek";
    private static final String TARGET_ALMA_CODE = "UBO";

    @Test
    void shouldSetUserStatusToInactiveWhenRecordIsPermanentlyClosed() {
        var record = createDefaultRecord();
        record.setStengt(PERMANENTLY_CLOSED);
        var users = convertRecordToUsers(record);

        assertThat(users.size(), is(equalTo(1)));
        assertThat(users.getFirst().getStatus().getValue(), is(equalTo("INACTIVE")));
        assertThat(users.getFirst().getStatus().getDesc(), is(equalTo("Inactive")));
    }

    @Test
    void shouldSetUserStatusToActiveWhenRecordIsNotPermanentlyClosed() {
        var record = createDefaultRecord();
        var users = convertRecordToUsers(record);

        assertThat(users.size(), is(equalTo(1)));
        assertThat(users.getFirst().getStatus().getValue(), is(equalTo("ACTIVE")));
        assertThat(users.getFirst().getStatus().getDesc(), is(equalTo("Active")));
    }

    @ParameterizedTest(name = "should replace ampersand with \"{1}\" for country code {0}")
    @MethodSource("provideCountryCodeAndExpectedAmpersandReplacement")
    void shouldReplaceAmpersandWithCorrectWordForCountryCode(String countryCode, String expectedWord) {
        var record = createRecordWithLandkodeAndInst(countryCode, "Library A & Library B");
        var userConverter = createUserConverter(record);

        var result = userConverter.extractPrettyLibraryNameWithoutAmpersand(record);

        assertThat(result, is(equalTo("Library A " + expectedWord + " Library B")));
    }

    @Test
    void shouldReplaceLinefeedWithHyphen() {
        var record = createRecordWithLandkodeAndInst(COUNTRY_CODE_NORWEGIAN, "Library A\nLibrary B");
        var userConverter = createUserConverter(record);

        var result = userConverter.extractPrettyLibraryNameWithoutAmpersand(record);

        assertThat(result, is(equalTo("Library A - Library B")));
    }

    @Test
    void shouldRemoveMultipleWhiteSpaces() {
        var record = createRecordWithLandkodeAndInst(COUNTRY_CODE_NORWEGIAN, "Library   A");
        var userConverter = createUserConverter(record);

        var result = userConverter.extractPrettyLibraryNameWithoutAmpersand(record);

        assertThat(result, is(equalTo("Library A")));
    }

    static Stream<Arguments> provideCountryCodeAndExpectedAmpersandReplacement() {
        return Stream.of(
            Arguments.of("GB", "and"),
            Arguments.of("US", "and"),
            Arguments.of("CA", "and"),
            Arguments.of("AU", "and"),
            Arguments.of("IE", "and"),
            Arguments.of("NZ", "and"),
            Arguments.of("DE", "und"),
            Arguments.of("AT", "und"),
            Arguments.of("CH", "und"),
            Arguments.of("FR", "et"),
            Arguments.of("BE", "et"),
            Arguments.of("FI", "ja"),
            Arguments.of("EE", "ja"),
            Arguments.of("NL", "en"),
            Arguments.of("SE", "och"),
            Arguments.of("PL", "i"),
            Arguments.of("ES", "y"),
            Arguments.of("PT", "e"),
            Arguments.of("IT", "e"),
            Arguments.of("NO", "og"),
            Arguments.of("DK", "og")
        );
    }

    private Record createRecordWithLandkodeAndInst(String landkode, String inst) {
        return new RecordBuilder(BigInteger.ONE, LocalDate.now(), KATSYST_TIDEMANN)
                   .withBibnr(BIBNR)
                   .withLandkode(landkode)
                   .withInst(inst)
                   .withBiblType(BIBLTYPE)
                   .build();
    }

    private UserConverter createUserConverter(Record record) {
        var baseBibliotek = new BasebibliotekGenerator(record).generateBaseBibliotek();
        return new UserConverter(baseBibliotek, TARGET_ALMA_CODE);
    }

    private Record createDefaultRecord() {
        return new RecordBuilder(BigInteger.ONE, LocalDate.now(), KATSYST_TIDEMANN)
                   .withBibnr(BIBNR)
                   .withLandkode(COUNTRY_CODE_NORWEGIAN)
                   .withInst(INST)
                   .withBiblType(BIBLTYPE)
                   .build();
    }

    private List<User> convertRecordToUsers(Record record) {
        var baseBibliotek = new BasebibliotekGenerator(record).generateBaseBibliotek();
        var userConverter = new UserConverter(baseBibliotek, TARGET_ALMA_CODE);
        var userReportBuilder = new UserReportBuilder();
        return userConverter.toUsers(userReportBuilder);
    }
}