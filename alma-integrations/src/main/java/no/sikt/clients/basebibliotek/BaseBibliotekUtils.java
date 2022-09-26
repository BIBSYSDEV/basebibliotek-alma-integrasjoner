package no.sikt.clients.basebibliotek;

import java.util.Locale;
import java.util.Objects;
import no.nb.basebibliotek.generated.Record;

public abstract class BaseBibliotekUtils {
    public static final String COUNTRY_CODE_NORWEGIAN = "NO";
    public static final String COUNTRY_CODE_GERMAN = "DE";
    public static final String KATSYST_ALMA = "alma";
    public static final String KATSYST_BIBSYS = "bibsys";
    public static final String KATSYST_TIDEMANN = "Tidemann";

    private BaseBibliotekUtils() {
    }

    public static boolean isNorwegian(final Record record) {
        if (Objects.nonNull(record.getLandkode())) {
            return COUNTRY_CODE_NORWEGIAN.equalsIgnoreCase(record.getLandkode());
        } else {
            return false;
        }
    }

    public static boolean isAlmaOrBibsysLibrary(final Record record) {
        if (Objects.nonNull(record.getKatsyst())) {
            return isAlmaOrBibsysLibrary(record.getKatsyst());
        } else {
            return false;
        }
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
