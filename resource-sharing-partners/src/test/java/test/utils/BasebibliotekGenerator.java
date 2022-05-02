package test.utils;

import static java.util.Objects.nonNull;
import jakarta.xml.bind.JAXB;
import java.io.StringWriter;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;

public class BasebibliotekGenerator {

    private static final String REDACTED = "redacted";

    public static BaseBibliotek randomBaseBibliotek() {
        var baseBibliotek = new BaseBibliotek();
        return baseBibliotek;
    }
}
