package test.utils;

import java.math.BigInteger;
import java.time.LocalDate;
import no.nb.basebibliotek.generated.Eressurser;
import no.nb.basebibliotek.generated.Record;

@SuppressWarnings("checkstyle:MemberName")
public class RecordBuilder {
    private final transient BigInteger rid;
    private final transient LocalDate timestamp;
    private final transient String katsyst;
    private transient String bibltype;
    private transient String bibnr;
    private transient String landkode;
    private transient String epostBest;
    private transient String epostAdr;
    private transient Eressurser eressurser;
    private transient String pAddr;
    private transient String pPostNr;
    private transient String pPostSted;
    private transient String vAddr;
    private transient String vPostNr;
    private transient String vPostSted;
    private transient String inst;

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

    public RecordBuilder withPaddr(String addr, String postNr, String postSted) {
        this.pAddr = addr;
        this.pPostNr = postNr;
        this.pPostSted = postSted;
        return this;
    }

    public RecordBuilder withVaddr(String addr, String postNr, String postSted) {
        this.vAddr = addr;
        this.vPostNr = postNr;
        this.vPostSted = postSted;
        return this;
    }

    public RecordBuilder withEressurser(Eressurser eressurser) {
        this.eressurser = eressurser;
        return this;
    }

    public RecordBuilder withInst(String inst) {
        this.inst = inst;
        return this;
    }

    public RecordBuilder withBiblType(String bibltype) {
        this.bibltype = bibltype;
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
        record.setPadr(pAddr);
        record.setPpostnr(pPostNr);
        record.setPpoststed(pPostSted);
        record.setVadr(vAddr);
        record.setVpostnr(vPostNr);
        record.setVpoststed(vPostSted);
        record.setInst(inst);
        record.setBibltype(bibltype);

        return record;
    }
}
