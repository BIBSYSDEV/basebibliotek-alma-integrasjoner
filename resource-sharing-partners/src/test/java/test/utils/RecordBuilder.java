package test.utils;

import java.math.BigInteger;
import java.time.LocalDate;
import no.nb.basebibliotek.generated.Eressurser;
import no.nb.basebibliotek.generated.Record;

public class RecordBuilder {
    private final BigInteger rid;
    private final LocalDate timestamp;
    private final String katsyst;
    private String bibnr;
    private String landkode;
    private String epostBest;
    private String epostAdr;
    private Eressurser eressurser;

    public RecordBuilder(BigInteger rid, LocalDate timestamp, String katsyst) {
        this.rid = rid;
        this.timestamp = timestamp;
        this.katsyst = katsyst;
    }

    public RecordBuilder withBibnr(String bibnr) {
        this.bibnr = bibnr;
        return this;
    }

    public RecordBuilder withLandkode(String landkode) {
        this.landkode = landkode;
        return this;
    }

    public RecordBuilder withEpostBest(String epostBest) {
        this.epostBest = epostBest;
        return this;
    }

    public RecordBuilder withEpostAdr(String epostAdr) {
        this.epostAdr = epostAdr;
        return this;
    }

    public RecordBuilder withEressurser(Eressurser eressurser) {
        this.eressurser = eressurser;
        return this;
    }

    public Record build() {
        Record record = new Record();

        record.setRid(rid);
        record.setTstamp(timestamp.toString());
        record.setKatsyst(katsyst);
        record.setBibnr(bibnr);
        record.setLandkode(landkode);
        record.setEpostBest(epostBest);
        record.setEpostAdr(epostAdr);
        record.setEressurser(eressurser);

        return record;
    }
}
