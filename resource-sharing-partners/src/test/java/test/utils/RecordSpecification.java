package test.utils;

import java.util.Collections;
import java.util.List;

public class RecordSpecification {
    private final String bibNr;
    private final boolean withLandkode;

    private final String nncipUri;
    private final boolean withStengtFra;
    private final boolean withStengtTil;
    private final boolean withPaddr;
    private final boolean withVaddr;
    private final boolean withIsil;
    private final String katsys;
    private final String stengt;

    private final List<String> eressursExcludes;

    public RecordSpecification(String bibNr, boolean withLandkode, String nncipUri, boolean withStengtFra,
                               boolean withStengtTil, boolean withPaddr, boolean withVaddr, boolean withIsil,
                               String katsys, List<String> eressursExcludes, String stengt) {
        this.bibNr = bibNr;
        this.withLandkode = withLandkode;
        this.nncipUri = nncipUri;
        this.withStengtFra = withStengtFra;
        this.withStengtTil = withStengtTil;
        this.withPaddr = withPaddr;
        this.withVaddr = withVaddr;
        this.withIsil = withIsil;
        this.katsys = katsys;
        this.eressursExcludes = eressursExcludes;
        this.stengt = stengt;
    }

    public RecordSpecification(String bibNr, boolean withLandkode, String nncipUri, boolean withStengtFra,
                               boolean withStengtTil, boolean withPaddr, boolean withVaddr, boolean withIsil,
                               String katsys) {
        this(bibNr, withLandkode, nncipUri, withStengtFra, withStengtTil, withPaddr, withVaddr, withIsil, katsys,
             Collections.emptyList(), null);
    }

    public String getBibNr() {
        return bibNr;
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

    public boolean getWithIsil() {
        return withIsil;
    }

    public String getKatsys() {
        return katsys;
    }

    public List<String> getEressursExcludes() {
        return eressursExcludes;
    }

    public String getStengt() {
        return stengt;
    }
}