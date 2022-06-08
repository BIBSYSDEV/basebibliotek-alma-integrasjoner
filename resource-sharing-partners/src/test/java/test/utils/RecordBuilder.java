package test.utils;

import java.math.BigInteger;
import java.time.LocalDate;
import no.nb.basebibliotek.generated.Eressurser;
import no.nb.basebibliotek.generated.Record;

public class RecordBuilder {
    private transient final BigInteger rid;
    private transient final LocalDate timestamp;
    private transient final String katsyst;
    private transient String bibnr;
    private transient String landkode;
    private transient String epostBest;
    private transient String epostAdr;
    private transient Eressurser eressurser;

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
