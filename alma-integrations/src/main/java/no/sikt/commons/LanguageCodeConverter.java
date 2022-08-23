package no.sikt.commons;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

public class LanguageCodeConverter {

    public static final Map<String, String> isoAlpha2ToIsoAlpha3CodeMap = new HashMap<>();

    static {
        Locale[] availableLocales = Locale.getAvailableLocales();
        for (Locale locale : availableLocales) {
            try {
                isoAlpha2ToIsoAlpha3CodeMap.put(locale.getCountry(), locale.getISO3Country());
            } catch (MissingResourceException e) {
                // ignore, is useless anyway
                // Locales without ISO3Country-codes: ji_001, vo_001, sr_CS, en_150, sr_XK_#Cyrl, sr_XK_#Latn,
                // prg_001, es_EA, sq_XK, eo_001, en_DG, es_419, en_001, es_IC, ar_001
                // None of these are used by basebibliotek.
            }
        }
    }

    public static String convertISO31661Alpha2CodeToAlpha3Code(String alpha2Code) {
        return isoAlpha2ToIsoAlpha3CodeMap.get(alpha2Code.toUpperCase(Locale.ROOT));
    }
}
