package no.sikt.rsp;

import jakarta.xml.bind.JAXB;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.generated.Partner;
import no.sikt.alma.generated.PartnerDetails;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartnerConverter {

    private static final Logger logger = LoggerFactory.getLogger(PartnerConverter.class);
    private static final String COULD_NOT_CONVERT_RECORD = "Could not convert record to partner, missing %s, record: "
                                                           + "%s";
    private static final int AVG_SUPPLY_TIME = 1;
    public static final int DELIVERY_DELAY = 0;
    public static final String LENDING_WORKFLOW = "Lending";
    public static final boolean BORROWING_IS_SUPPORTED = true;
    public static final String BORROWING_WORKFLOW = "Borrowing";
    public static final boolean LENDING_IS_SUPPORTED = true;

    @JacocoGenerated
    public PartnerConverter() {

    }

    public static List<Partner> convertBasebibliotekToPartners(BaseBibliotek baseBibliotek) {
        return baseBibliotek
                   .getRecord()
                   .stream()
                   .map(PartnerConverter::convertRecordToPartnerOptional)
                   .flatMap(Optional::stream)
                   .collect(Collectors.toList());
    }

    private static String toXml(Record record) {
        StringWriter xmlWriter = new StringWriter();
        JAXB.marshal(record, xmlWriter);
        return xmlWriter.toString();
    }

    private static Optional<Partner> convertRecordToPartnerOptional(Record record) {
        return satisfiesConstraints(record)
                   ? Optional.of(convertRecordToPartner(record))
                   : returnEmptyAndLogProblem(record);
    }

    private static Partner convertRecordToPartner(Record record) {
        var partner = new Partner();
        partner.setPartnerDetails(extractPartnerDetailsFromRecord(record));
        partner.setContactInfo(ContactInfoConverter.extractContactInfoFromRecord(record));
        return partner;
    }

    private static Optional<Partner> returnEmptyAndLogProblem(Record record) {
        var missingParameters = (Objects.nonNull(record.getLandkode()) ? StringUtils.EMPTY_STRING : "landkode")
                                + " "
                                + ((Objects.nonNull(record.getBibnr()) ? StringUtils.EMPTY_STRING : "bibNr"));
        logger.info(String.format(COULD_NOT_CONVERT_RECORD, missingParameters, PartnerConverter.toXml(record)));
        return Optional.empty();
    }

    private static boolean satisfiesConstraints(Record record) {
        return Objects.nonNull(record.getIsil())
               || Objects.nonNull(record.getBibnr()) && Objects.nonNull(record.getLandkode());
    }

    private static PartnerDetails extractPartnerDetailsFromRecord(Record record) {
        var partnerDetails = new PartnerDetails();
        partnerDetails.setCode(extractIsilCode(record));
        partnerDetails.setName(extractName(record));
        partnerDetails.setAvgSupplyTime(AVG_SUPPLY_TIME);
        partnerDetails.setDeliveryDelay(DELIVERY_DELAY);
        partnerDetails.setLendingWorkflow(LENDING_WORKFLOW);
        partnerDetails.setLendingSupported(LENDING_IS_SUPPORTED);
        partnerDetails.setBorrowingSupported(BORROWING_IS_SUPPORTED);
        partnerDetails.setBorrowingWorkflow(BORROWING_WORKFLOW);
        return partnerDetails;
    }

    private static String extractName(Record record) {
        return Objects.nonNull(record.getInst())
                   ? record.getInst().replaceAll("\n", " - ")
                   : StringUtils.EMPTY_STRING;
    }

    private static String extractIsilCode(Record record) {
        return Objects.nonNull(record.getIsil())
                   ? record.getIsil()
                   : record.getLandkode().toUpperCase(Locale.ROOT) + record.getBibnr();
    }
}
