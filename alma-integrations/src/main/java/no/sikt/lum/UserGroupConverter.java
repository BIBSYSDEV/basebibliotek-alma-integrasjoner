package no.sikt.lum;

import java.util.Arrays;
import java.util.List;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.user.generated.User;
import no.sikt.alma.user.generated.User.UserGroup;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;

public class UserGroupConverter {

    public static final List<String> VALID_USER_GROUPS = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "11",
                                                                       "12", "13", "14", "15", "16", "17", "20", "21",
                                                                       "22", "23", "24", "25", "50");
    public static final String NOTFOUND = "NOTFOUND";

    public static UserGroup extractUserGroup(Record record) {
        String libraryNumber = record.getBibnr().replaceAll("\\w+-", StringUtils.EMPTY_STRING);
        BibKategori bibKategori = konverterBibKategori(libraryNumber, record.getBibltype());
        String patronCategory = bibKategori.getCode();
        User.UserGroup userGroup = new User.UserGroup();
        if (VALID_USER_GROUPS.contains(patronCategory)) {
            userGroup.setValue(patronCategory);
        } else {
            userGroup.setValue(NOTFOUND);
        }
        return userGroup;
    }

    /**
     * NB! gammelt kode, overført fra gode gamle webframework.
     * Produserer code for tilordning av User Group for libbruker som skal importeres til Alma, basert på feltene
     * &lt;bibnr&gt; og &lt;bibltype&gt; fra Base bibliotek.
     *
     * @param libnr     Heltall som brukes til å å bestemme bibliotekskategori
     * @param kundeType Streng som inneholder en liste med kundetyper. Separator mellom elementer er \"[+]\"
     * @return Bibliotekskategorien som skal brukes til ved Almaimport
     */

    //Libnr inneholder informasjon. Kan skape krøll hvis et bibliotek går fra å være et høyskole bibliotek til
    // universitetsbibliotek.
    @JacocoGenerated
    @SuppressWarnings({"PMD.AvoidLiteralsInIfCondition", "PMD.DataflowAnomalyAnalysis"})
    public static BibKategori konverterBibKategori(String libnr, String kundeType) {
        if (libnr == null) {
            throw new RuntimeException("libnr er null");
        }
        BibKategori kat = null;
        char pos1 = libnr.charAt(0);
        char pos2 = libnr.charAt(1);
        char pos3 = libnr.charAt(2);
        String customerType2 = null;
        String customerType1;

        if (kundeType != null && kundeType.contains("+")) {
            customerType1 = kundeType.split(
                "[+]")[0].trim(); // ta vare på første forekomst og fjern eventuelle whitespace
            customerType2 = kundeType.split(
                "[+]")[1].trim(); // ta vare på andre forekomst og fjern eventuelle whitespace
        } else {
            customerType1 = kundeType != null ? kundeType.trim() : null;
        }

        if (pos1 == '0') {
            kat = BibKategori.NasjonalBiblioteket;
        }
        if (kat == null) {
            if (pos1 == '6') {
                if (pos2 == '5' || pos2 == '6' || pos2 == '7' || pos2 == '8' || pos2 == '9') {
                    kat = BibKategori.Danmark;
                } else if (pos2 == '1') {
                    kat = BibKategori.Finland;
                } else if (pos2 == '3') {
                    kat = BibKategori.Sverige;
                } else if (pos2 == '4') {
                    if (pos3 == '7' || pos3 == '8' || pos3 == '9') {
                        kat = BibKategori.Island_Faroyene_Gronland;
                    }
                }
            }
        }
        if (kat == null) {
            if (pos1 == '7') {
                kat = BibKategori.Europeisk;
            } else if (pos1 == '8') {
                kat = BibKategori.Verden;
            }
        }

        if (kat == null) {
            kat = finnBibKategoriFraKundetype(customerType1);
            if (kat == null && customerType2 != null) {
                kat = finnBibKategoriFraKundetype(customerType2);
            }
        }
        if (kat == null) {
            if (pos1 == '2') {
                kat = BibKategori.Folkebibliotek;
            } else if (pos1 == '3' || pos1 == '4') {
                kat = BibKategori.GrunnkoleOgVideregaaendeBib;
            } else if (pos1 == '5') {
                kat = BibKategori.BedriftsBibNorge;
            }
            if (kat == null) {
                kat = BibKategori.Ukjent;
            }
        }
        return kat;
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    private static BibKategori finnBibKategoriFraKundetype(String customerType) {
        BibKategori kat = null;

        if (matches(customerType, "UNB", "UNI")) {
            kat = BibKategori.UniversitetsBibNorge;
        }
        if (matches(customerType, "HØY")) {
            kat = BibKategori.HoyskoleBibNorge;
        }
        if (matches(customerType, "FIR", "ORG")) {
            kat = BibKategori.BedriftsBibNorge;
        }
        if (matches(customerType, "FAG", "AVD", "ARK", "MUS")) {
            kat = BibKategori.AndreFagOgForskningsbibliotek;
        }
        if (matches(customerType, "FBI", "FIL", "FYB", "FEN")) {
            kat = BibKategori.Folkebibliotek;
        }
        if (matches(customerType, "GSK", "VGS", "FHS")) {
            kat = BibKategori.GrunnkoleOgVideregaaendeBib;
        }
        return kat;
    }

    /**
     * utility method for string matching on multiple values.
     *
     * @param input  string to check
     * @param values matching criteria
     * @return true if any criteria matches input (case ignored)
     */
    private static boolean matches(String input, String... values) {
        for (String value : values) {
            if (input.trim().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public enum BibKategori {
        NasjonalBiblioteket(15),
        Danmark(20),
        Finland(22),
        Sverige(21),
        Island_Faroyene_Gronland(23),
        Europeisk(24),
        Verden(25),
        UniversitetsBibNorge(11),
        HoyskoleBibNorge(12),
        BedriftsBibNorge(13),
        AndreFagOgForskningsbibliotek(14),
        Folkebibliotek(16),
        GrunnkoleOgVideregaaendeBib(17),
        Ukjent(-1);

        public final int code;

        BibKategori(int code) {
            this.code = code;
        }

        public String getCode() {
            return String.valueOf(code);
        }
    }
}
