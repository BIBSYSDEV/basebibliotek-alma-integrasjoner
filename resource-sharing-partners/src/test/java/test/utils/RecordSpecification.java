package test.utils;

public class RecordSpecification {

    private final boolean withBibnr;
    private final boolean withLandkode;

    private final String nncipUri;
    private final boolean withStengtFra;
    private final boolean withStengtTil;
    private final boolean withPaddr;
    private final boolean withVaddr;

    public RecordSpecification(boolean withBibnr, boolean withLandkode, String nncipUri, boolean withStengtFra,
                               boolean withStengtTil, boolean withPaddr, boolean withVaddr) {
        this.withBibnr = withBibnr;
        this.withLandkode = withLandkode;
        this.nncipUri = nncipUri;
        this.withStengtFra = withStengtFra;
        this.withStengtTil = withStengtTil;
        this.withPaddr = withPaddr;
        this.withVaddr = withVaddr;
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

    public boolean getWithPaddr() {
        return withPaddr;
    }

    public boolean getWithVaddr() {
        return withVaddr;
    }
}