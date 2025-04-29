package no.sikt.commons;

@FunctionalInterface
public interface Redacter {

    /**
     * Redacts content from a given input.
     **/
    String redact(String input);

}
