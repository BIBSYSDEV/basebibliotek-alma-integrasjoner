package test.utils;

public class RecordSpecification {
    private final boolean withBibnr;
    private final boolean withLandkode;

    public RecordSpecification(boolean withBibnr, boolean withLandkode) {
        this.withBibnr = withBibnr;
        this.withLandkode = withLandkode;
    }

    public boolean getWithBibnr() {
        return withBibnr;
    }

    public boolean getWithLandkode() {
        return withLandkode;
    }
}