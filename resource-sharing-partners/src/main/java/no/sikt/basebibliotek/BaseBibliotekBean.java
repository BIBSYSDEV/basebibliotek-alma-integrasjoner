package no.sikt.basebibliotek;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import nva.commons.core.JacocoGenerated;
import org.apache.commons.lang3.StringUtils;

public class BaseBibliotekBean {

    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
                                                                  .ofPattern("yyyy-MM-dd");

    private String bibNr = "";
    private String inst = "";
    private String katsyst = "";
    private String nncippServer = "";
    private String stengt = "";
    private Optional<LocalDate> stengtFra = Optional.empty();
    private Optional<LocalDate> stengtTil = Optional.empty();

    public String getBibNr() {
        return bibNr;
    }

    public void setBibNr(String bibNr) {
        this.bibNr = bibNr;
    }

    public String getInst() {
        return inst;
    }

    public void setInst(String inst) {
        this.inst = inst;
    }

    public String getKatsyst() {
        return katsyst;
    }

    public void setKatsyst(String katsyst) {
        this.katsyst = katsyst;
    }

    public String getNncippServer() {
        return nncippServer;
    }

    public void setNncippServer(String nncippServer) {
        this.nncippServer = nncippServer;
    }

    public String getStengtTil() {
        return stengtTil.map(date -> date.format(dateTimeFormatter)).orElse("");
    }

    public void setStengtTil(String stengtTil) {
        try {
            this.stengtTil = Optional.of(LocalDate.parse(stengtTil, dateTimeFormatter));
        } catch (Exception e) {
            this.stengtTil = Optional.empty();
        }
    }

    public String getStengtFra() {
        return stengtFra.map(date -> date.format(dateTimeFormatter)).orElse("");
    }

    public void setStengtFra(String stengtFra) {
        try {
            this.stengtFra = Optional.of(LocalDate.parse(stengtFra, dateTimeFormatter));
        } catch (Exception e) {
            this.stengtFra = Optional.empty();
        }
    }

    @JacocoGenerated
    public String getStengt() {
        return stengt;
    }

    public void setStengt(String stengt) {
        this.stengt = stengt;
    }

    public boolean isOpenAtDate(LocalDate date) {
        if (StringUtils.isNotEmpty(stengt)) {
            return false;
        }
        boolean startDatePresent = stengtFra.isPresent();
        boolean enDatePresent = stengtTil.isPresent();
        boolean isAfterBeginning = stengtFra.map(from -> !from.isAfter(date)).orElse(false);
        boolean isBeforeEnd = stengtTil.map(until -> !until.isBefore(date)).orElse(false);
        boolean twoDatesAvailable =
            startDatePresent && enDatePresent && isAfterBeginning && isBeforeEnd;
        boolean beginningAvailable = startDatePresent && !enDatePresent && isAfterBeginning;
        boolean endAvailable = !startDatePresent && enDatePresent && isBeforeEnd;

        return !(twoDatesAvailable || beginningAvailable || endAvailable);
    }
}