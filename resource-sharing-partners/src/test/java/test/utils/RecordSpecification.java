package test.utils;

public class RecordSpecification {

    private final boolean withBibnr;
    private final boolean withLandkode;

    private final String nncipUri;

    public RecordSpecification(boolean withBibnr, boolean withLandkode, String nncipUri) {
        this.withBibnr = withBibnr;
        this.withLandkode = withLandkode;
        this.nncipUri = nncipUri;
    }

    public boolean getWithBibnr() {
        return withBibnr;
    }

    public boolean getWithLandkode() {
        return withLandkode;
    }

    public String getNncipUri() {
        return nncipUri;
    }
}