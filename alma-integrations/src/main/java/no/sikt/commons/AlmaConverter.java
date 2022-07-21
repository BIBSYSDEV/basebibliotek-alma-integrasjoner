package no.sikt.commons;

import jakarta.xml.bind.JAXB;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.sikt.rsp.AlmaCodeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AlmaConverter {

    private static final Logger logger = LoggerFactory.getLogger(AlmaConverter.class);
    protected static final String EMAIL_PATTERN = ".+@.+";
    protected static final String COULD_NOT_CONVERT_RECORD = "Could not convert record, missing %s, record: %s";
    public static final String PERMANENTLY_CLOSED = "X";
    public static final String LINEFEED = "\n";
    public static final String INSTITUTION_CODE_PREFIX = "47BIBSYS_";
    protected final transient AlmaCodeProvider almaCodeProvider;
    protected final transient BaseBibliotek baseBibliotek;

    public AlmaConverter(AlmaCodeProvider almaCodeProvider, BaseBibliotek baseBibliotek) {
        this.almaCodeProvider = almaCodeProvider;
        this.baseBibliotek = baseBibliotek;
    }

    protected String toXml(Record record) {
        StringWriter xmlWriter = new StringWriter();
        JAXB.marshal(record, xmlWriter);
        return xmlWriter.toString();
    }

    protected String extractSymbol(final Record record) {
        return record.getLandkode().toUpperCase(Locale.ROOT) + HandlerUtils.HYPHEN + record.getBibnr();
    }

    protected boolean satisfiesConstraints(Record record) {
        List<String> missingFields = findMissingRequiredFields(record);
        if (!missingFields.isEmpty()) {
            logger.warn(String.format(COULD_NOT_CONVERT_RECORD, missingFields, toXml(record)));
        }

        return missingFields.isEmpty();
    }

    protected abstract void logProblemAndThrowException(Record record);

    protected abstract List<String> findMissingRequiredFields(Record record);
}
