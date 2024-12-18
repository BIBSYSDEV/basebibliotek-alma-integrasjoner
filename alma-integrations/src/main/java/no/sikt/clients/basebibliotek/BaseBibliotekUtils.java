package no.sikt.clients.basebibliotek;

import java.util.Locale;
import java.util.Objects;
import no.nb.basebibliotek.generated.Record;

public final class BaseBibliotekUtils {
    public static final String COUNTRY_CODE_NORWEGIAN = "NO";
    public static final String COUNTRY_CODE_GERMAN = "DE";
    public static final String KATSYST_ALMA = "alma";
    public static final String KATSYST_BIBSYS = "bibsys";
    public static final String KATSYST_TIDEMANN = "Tidemann";

    private BaseBibliotekUtils() {
    }

    public static boolean isNorwegian(final Record record) {
        return COUNTRY_CODE_NORWEGIAN.equalsIgnoreCase(record.getLandkode());
    }

    public static boolean isAlmaOrBibsysLibrary(final Record record) {
        return Objects.nonNull(record.getKatsyst()) && isAlmaOrBibsysLibrary(record.getKatsyst());
    }

    public static boolean isAlmaOrBibsysLibrary(final String katsyst) {
        return katsyst
                   .toLowerCase(Locale.ROOT)
                   .contains(KATSYST_BIBSYS)
               || katsyst
                      .toLowerCase(Locale.ROOT)
                      .contains(KATSYST_ALMA);
    }
}
