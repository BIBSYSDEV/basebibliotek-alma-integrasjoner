package no.sikt.lum;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import no.sikt.commons.Redacter;

public class SensitiveXmlDataRedacter implements Redacter {

    private static final String PATTERN_STRING = "<%s(.*?)</%s>";
    private static final String REPLACEMENT_STRING = "<%s>redacted</%s>";
    private static final Set<String> DEFAULT_TAGS = Set.of("aut", "password");

    private final Map<String, Pattern> patterns;
    private final Map<String, String> replacements;

    /**
     * Redacts content of tags in input based on a set of xml tags. Tag descriptions or properties will be removed.
     **/
    public SensitiveXmlDataRedacter() {
        this(DEFAULT_TAGS);
    }

    public SensitiveXmlDataRedacter(Set<String> tags) {
        patterns = new HashMap<>();
        replacements = new HashMap<>();

        for (String tag : tags) {
            patterns.put(tag, Pattern.compile(String.format(PATTERN_STRING, tag, tag)));
            replacements.put(tag, String.format(REPLACEMENT_STRING, tag, tag));
        }
    }

    @Override
    public String redact(String input) {
        var output = input;

        for (String tag : patterns.keySet()) {
            output = replaceUsingPattern(output, tag);
        }

        return output;
    }

    private String replaceUsingPattern(String input, String tag) {
        return patterns.get(tag)
                   .matcher(input)
                   .replaceAll(replacements.get(tag));
    }

}
