package no.sikt.lum;

import java.util.Set;
import java.util.regex.Pattern;
import no.sikt.commons.Redacter;

public class SensitiveXmlDataRedacter implements Redacter {

    public static final String PATTERN_STRING = "<%s(.*?)</%s>";
    public static final String REPLACEMENT_STRING = "<%s>redacted</%s>";
    private final Set<String> tags = Set.of("aut", "password");

    /**
     * Redacts content of tags in input based on a set of xml tags. Tag descriptions or properties will be removed.
     **/
    @Override
    public String redact(String input) {
        var output = input;

        for (String tag : tags) {
            output = replaceUsingPattern(output, tag);
        }

        return output;
    }

    private String replaceUsingPattern(String input, String tag) {
        var patternString = String.format(PATTERN_STRING, tag, tag);
        var pattern = Pattern.compile(patternString);
        var matcher = pattern.matcher(input);
        var replacementString = String.format(REPLACEMENT_STRING, tag, tag);

        return matcher.replaceAll(replacementString);
    }

}
