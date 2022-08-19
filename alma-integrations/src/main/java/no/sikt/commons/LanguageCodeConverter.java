package no.sikt.commons;

import static org.apache.commons.lang3.StringUtils.join;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

public class LanguageCodeConverter {

    public static final String LOCALES_WITHOUT_ISO_3_COUNTRY_CODES = "Locales without ISO3Country-codes: ";
    public static final String COMMA_SEPARATOR = ", ";
    public static final Map<String, String> twoToThreeMap = new HashMap<>();

    static {
        Locale[] availableLocales = Locale.getAvailableLocales();
        List<String> localesWithoutISO3Country = new ArrayList<>();
        for (Locale locale : availableLocales) {
            try {
                twoToThreeMap.put(locale.getCountry(), locale.getISO3Country());
            } catch (MissingResourceException e) {
                localesWithoutISO3Country.add(locale.toString());
                // ignore, is useless anyway
            }
        }
        System.out.println(LOCALES_WITHOUT_ISO_3_COUNTRY_CODES + join(localesWithoutISO3Country, COMMA_SEPARATOR));
    }

    public static String convertISO31661Alpha2CodeToAlpha3Code(String alpha2Code){
        return twoToThreeMap.get(alpha2Code.toUpperCase(Locale.ROOT));
    }
}
