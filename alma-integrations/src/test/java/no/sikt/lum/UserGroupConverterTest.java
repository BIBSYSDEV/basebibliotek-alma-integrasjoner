package no.sikt.lum;

import static no.sikt.lum.UserGroupConverter.NOTFOUND;
import static no.sikt.lum.UserGroupConverter.extractUserGroup;
import static no.sikt.lum.UserGroupConverter.konverterBibKategori;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.LocalDate;
import no.nb.basebibliotek.generated.Record;
import no.sikt.lum.UserGroupConverter.BibKategori;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import test.utils.RecordBuilder;

class UserGroupConverterTest {

    // ========== extractUserGroup tests ==========

    @Test
    void shouldReturnValidUserGroup_whenPatronCategoryIsValid() {
        // bibnr "NO-1000000" -> "1000000", with kundetype "UNI" -> UniversitetsBibNorge (code 11)
        Record record = createRecord("NO-1000000", "UNI");
        var userGroup = extractUserGroup(record);
        assertEquals("11", userGroup.getValue());
    }

    @Test
    void shouldReturnNotFound_whenPatronCategoryIsInvalid() {
        // bibnr "NO-1000000" -> "1000000", with null kundetype -> Ukjent (code -1, not in valid list)
        Record record = createRecord("NO-1000000", null);
        var userGroup = extractUserGroup(record);
        assertEquals(NOTFOUND, userGroup.getValue());
    }

    @Test
    void shouldStripPrefixFromBibnr_whenExtractingUserGroup() {
        // bibnr "NO-0123456" -> "0123456", pos1='0' -> NasjonalBiblioteket (code 15)
        Record record = createRecord("NO-0123456", null);
        var userGroup = extractUserGroup(record);
        assertEquals("15", userGroup.getValue());
    }

    // ========== konverterBibKategori - NasjonalBiblioteket ==========

    @Test
    void shouldReturnNasjonalBiblioteket_whenLibnrStartsWith0() {
        assertEquals(BibKategori.NasjonalBiblioteket, konverterBibKategori("012", null));
    }

    // ========== konverterBibKategori - Nordic countries ==========

    @ParameterizedTest
    @ValueSource(strings = {"650", "660", "670", "680", "690"})
    void shouldReturnDanmark_whenPos1Is6AndPos2Is5through9(String libnr) {
        assertEquals(BibKategori.Danmark, konverterBibKategori(libnr, null));
    }

    @Test
    void shouldReturnFinland_whenPos1Is6AndPos2Is1() {
        assertEquals(BibKategori.Finland, konverterBibKategori("610", null));
    }

    @Test
    void shouldReturnSverige_whenPos1Is6AndPos2Is3() {
        assertEquals(BibKategori.Sverige, konverterBibKategori("630", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"647", "648", "649"})
    void shouldReturnIslandFaroyeneGronland_whenPos1Is6Pos2Is4AndPos3Is7Through9(String libnr) {
        assertEquals(BibKategori.Island_Faroyene_Gronland, konverterBibKategori(libnr, null));
    }

    @Test
    void shouldNotReturnIslandFaroyeneGronland_whenPos3IsNot7Through9() {
        // pos1=6, pos2=4, but pos3=0 should fall through to other matching
        assertEquals(BibKategori.Ukjent, konverterBibKategori("640", null));
    }

    // ========== konverterBibKategori - International ==========

    @Test
    void shouldReturnEuropeisk_whenPos1Is7() {
        assertEquals(BibKategori.Europeisk, konverterBibKategori("700", null));
    }

    @Test
    void shouldReturnVerden_whenPos1Is8() {
        assertEquals(BibKategori.Verden, konverterBibKategori("800", null));
    }

    // ========== konverterBibKategori - Position-based Norwegian categories ==========

    @Test
    void shouldReturnFolkebibliotek_whenPos1Is2() {
        assertEquals(BibKategori.Folkebibliotek, konverterBibKategori("200", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"300", "400"})
    void shouldReturnGrunnkoleOgVideregaaendeBib_whenPos1Is3Or4(String libnr) {
        assertEquals(BibKategori.GrunnkoleOgVideregaaendeBib, konverterBibKategori(libnr, null));
    }

    @Test
    void shouldReturnBedriftsBibNorge_whenPos1Is5() {
        assertEquals(BibKategori.BedriftsBibNorge, konverterBibKategori("500", null));
    }

    // ========== konverterBibKategori - Kundetype matching ==========

    @ParameterizedTest
    @ValueSource(strings = {"UNI", "UNB"})
    void shouldReturnUniversitetsBibNorge_whenKundeTypeIsUNIorUNB(String kundeType) {
        assertEquals(BibKategori.UniversitetsBibNorge, konverterBibKategori("100", kundeType));
    }

    @Test
    void shouldReturnHoyskoleBibNorge_whenKundeTypeIsHOY() {
        assertEquals(BibKategori.HoyskoleBibNorge, konverterBibKategori("100", "HØY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIR", "ORG"})
    void shouldReturnBedriftsBibNorge_whenKundeTypeIsFIRorORG(String kundeType) {
        assertEquals(BibKategori.BedriftsBibNorge, konverterBibKategori("100", kundeType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAG", "AVD", "ARK", "MUS"})
    void shouldReturnAndreFagOgForskningsbibliotek_whenKundeTypeMatches(String kundeType) {
        assertEquals(BibKategori.AndreFagOgForskningsbibliotek, konverterBibKategori("100", kundeType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FBI", "FIL", "FYB", "FEN"})
    void shouldReturnFolkebibliotek_whenKundeTypeMatches(String kundeType) {
        assertEquals(BibKategori.Folkebibliotek, konverterBibKategori("100", kundeType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GSK", "VGS", "FHS"})
    void shouldReturnGrunnkoleOgVideregaaendeBib_whenKundeTypeMatches(String kundeType) {
        assertEquals(BibKategori.GrunnkoleOgVideregaaendeBib, konverterBibKategori("100", kundeType));
    }

    // ========== konverterBibKategori - Multiple kundetypes ==========

    @Test
    void shouldHandleMultipleKundeTypes_firstMatches() {
        assertEquals(BibKategori.UniversitetsBibNorge, konverterBibKategori("100", "UNI+HØY"));
    }

    @Test
    void shouldUseSecondKundeType_whenFirstDoesNotMatch() {
        assertEquals(BibKategori.HoyskoleBibNorge, konverterBibKategori("100", "INVALID+HØY"));
    }

    // ========== konverterBibKategori - Edge cases ==========

    @Test
    void shouldReturnUkjent_whenNoMatchFound() {
        assertEquals(BibKategori.Ukjent, konverterBibKategori("100", null));
    }

    @Test
    void shouldThrowException_whenLibnrIsNull() {
        assertThrows(RuntimeException.class, () -> konverterBibKategori(null, "UNI"));
    }

    @Test
    void shouldHandleKundeTypeWithWhitespace() {
        assertEquals(BibKategori.UniversitetsBibNorge, konverterBibKategori("100", " UNI "));
    }

    @Test
    void shouldHandleKundeTypeWithWhitespaceAroundPlus() {
        assertEquals(BibKategori.UniversitetsBibNorge, konverterBibKategori("100", " UNI + HØY "));
    }

    @Test
    void shouldMatchKundeTypeCaseInsensitively() {
        assertEquals(BibKategori.UniversitetsBibNorge, konverterBibKategori("100", "uni"));
        assertEquals(BibKategori.UniversitetsBibNorge, konverterBibKategori("100", "Uni"));
    }

    // ========== Helper methods ==========

    private Record createRecord(String bibnr, String bibltype) {
        return new RecordBuilder(BigInteger.ONE, LocalDate.now(), "katsyst")
            .withBibnr(bibnr)
            .withBiblType(bibltype)
            .build();
    }
}