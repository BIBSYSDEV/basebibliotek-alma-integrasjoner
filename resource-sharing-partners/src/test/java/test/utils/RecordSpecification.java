package test.utils;

public class RecordSpecification {

    private final boolean withBibnr;
    private final boolean withLandkode;

    private final String nncipUri;
    private final boolean withStengtFra;
    private final boolean withStengtTil;

    public RecordSpecification(boolean withBibnr, boolean withLandkode, String nncipUri, boolean withStengtFra,
                               boolean withStengtTil) {
        this.withBibnr = withBibnr;
        this.withLandkode = withLandkode;
        this.nncipUri = nncipUri;
        this.withStengtFra = withStengtFra;
        this.withStengtTil = withStengtTil;
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

    public boolean getWithStengtFra() {
        return withStengtFra;
    }

    public boolean getWithStengtTil() {
        return withStengtTil;
    }
}