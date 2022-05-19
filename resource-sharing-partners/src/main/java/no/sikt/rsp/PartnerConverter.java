package no.sikt.rsp;

import jakarta.xml.bind.JAXB;
import jakarta.xml.bind.JAXBElement;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.generated.EmailDetails;
import no.sikt.alma.generated.GeneralUserIdType;
import no.sikt.alma.generated.IsoDetails;
import no.sikt.alma.generated.NcipP2PDetails;
import no.sikt.alma.generated.Partner;
import no.sikt.alma.generated.PartnerDetails;
import no.sikt.alma.generated.PartnerDetails.SystemType;
import no.sikt.alma.generated.ProfileDetails;
import no.sikt.alma.generated.ProfileType;
import no.sikt.alma.generated.RequestExpiryType;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartnerConverter {

    private static final Logger logger = LoggerFactory.getLogger(PartnerConverter.class);
    private static final String EMAIL_PATTERN = ".+@.+";
    private static final String COULD_NOT_CONVERT_RECORD = "Could not convert record to partner, missing %s, record: "
                                                           + "%s";
    private static final int AVG_SUPPLY_TIME = 1;
    private static final int DELIVERY_DELAY = 0;
    private static final String LENDING_WORKFLOW = "Lending";
    private static final boolean BORROWING_IS_SUPPORTED = true;
    private static final String BORROWING_WORKFLOW = "Borrowing";
    private static final boolean LENDING_IS_SUPPORTED = true;
    private static final String ISIL_CODE_SEPARATOR = "-";
    private static final String BIBSYS = "bibsys";
    private static final String ALMA = "alma";
    public static final String OTHER = "OTHER";
    public static final int RESENDING_OVERDUE_MESSAGE_INTERVAL = 7;
    public static final String NNCIP_URI = "nncip_uri";

    @JacocoGenerated
    public PartnerConverter() {

    }

    public static List<Partner> convertBasebibliotekToPartners(String illServer, BaseBibliotek baseBibliotek) {
        return baseBibliotek
                   .getRecord()
                   .stream()
                   .map(record -> convertRecordToPartnerOptional(illServer, record))
                   .flatMap(Optional::stream)
                   .collect(Collectors.toList());
    }

    private static String toXml(Record record) {
        StringWriter xmlWriter = new StringWriter();
        JAXB.marshal(record, xmlWriter);
        return xmlWriter.toString();
    }

    private static Optional<Partner> convertRecordToPartnerOptional(String illServer, Record record) {
        return satisfiesConstraints(record)
                   ? Optional.of(convertRecordToPartner(illServer, record))
                   : returnEmptyAndLogProblem(record);
    }

    private static Partner convertRecordToPartner(String illServer, Record record) {
        var partner = new Partner();
        partner.setPartnerDetails(extractPartnerDetailsFromRecord(illServer, record));
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
        return Objects.nonNull(record.getBibnr()) && Objects.nonNull(record.getLandkode());
    }

    private static PartnerDetails extractPartnerDetailsFromRecord(String illServer, Record record) {
        var partnerDetails = new PartnerDetails();
        partnerDetails.setCode(extractIsilCode(record));
        partnerDetails.setName(extractName(record));
        partnerDetails.setAvgSupplyTime(AVG_SUPPLY_TIME);
        partnerDetails.setDeliveryDelay(DELIVERY_DELAY);
        partnerDetails.setLendingWorkflow(LENDING_WORKFLOW);
        partnerDetails.setLendingSupported(LENDING_IS_SUPPORTED);
        partnerDetails.setBorrowingSupported(BORROWING_IS_SUPPORTED);
        partnerDetails.setBorrowingWorkflow(BORROWING_WORKFLOW);
        partnerDetails.setHoldingCode(extractHoldingCodeIfAlmaOrBibsysLibrary(record).orElse(null));
        partnerDetails.setSystemType(extractSystemType(record));

        partnerDetails.setProfileDetails(extractProfileDetails(illServer, record));

        return partnerDetails;
    }

    private static ProfileDetails extractProfileDetails(String illServer, Record record) {
        ProfileDetails details = new ProfileDetails();

        Optional<String> nncipUri = extractNncipUri(record);

        Optional<String> email = extractEmail(record);

        if (isAlmaOrBibsysLibrary(record)) {
            details.setProfileType(ProfileType.ISO);

            final IsoDetails isoDetails = new IsoDetails();
            isoDetails.setIllPort(9001);
            isoDetails.setIllServer(illServer);
            isoDetails.setIsoSymbol(record.getBibnr());
            isoDetails.setSharedBarcodes(true);

            details.setIsoDetails(isoDetails);
        } else if (nncipUri.isPresent()) {
            details.setProfileType(ProfileType.NCIP_P_2_P);

            final NcipP2PDetails ncipP2PDetails = new NcipP2PDetails();

            RequestExpiryType expiryType = new RequestExpiryType();
            expiryType.setDesc("No expiry");
            expiryType.setValue("NO_EXPIRY");
            ncipP2PDetails.setRequestExpiryType(expiryType);
            ncipP2PDetails.setIllServer(nncipUri.get());
            ncipP2PDetails.setPartnerSymbol(record.getBibnr());
            GeneralUserIdType generalUserIdType = new GeneralUserIdType();
            generalUserIdType.setDesc("barcode");
            generalUserIdType.setValue("BARCODE");
            ncipP2PDetails.setGeneralUserIdType(generalUserIdType);

            email.ifPresent(ncipP2PDetails::setEmailAddress);
            ncipP2PDetails.setResendingOverdueMessageInterval(RESENDING_OVERDUE_MESSAGE_INTERVAL);
            details.setNcipP2PDetails(ncipP2PDetails);
        } else {
            details.setProfileType(ProfileType.EMAIL);
            final EmailDetails emailDetails = new EmailDetails();

            emailDetails.setEmail(email.orElse(""));

            details.setEmailDetails(emailDetails);
        }
        return details;
    }

    private static Optional<String> extractEmail(Record record) {
        if (StringUtils.isNotEmpty(record.getEpostBest()) && record.getEpostBest().matches(EMAIL_PATTERN)) {
            return Optional.of(record.getEpostBest());
        } else if (StringUtils.isNotEmpty(record.getEpostAdr()) && record.getEpostAdr().matches(EMAIL_PATTERN)) {
            return Optional.of(record.getEpostAdr());
        } else {
            return Optional.empty();
        }
    }

    private static Optional<String> extractNncipUri(Record record) {
        if (record.getEressurser() == null) {
            return Optional.empty();
        } else {
            return record.getEressurser().getOAIOrSRUOrArielIp().stream()
                       .filter(element -> NNCIP_URI.equals(element.getName().getLocalPart()))
                       .map(JAXBElement::getValue)
                       .findFirst();
        }
    }

    private static SystemType extractSystemType(Record record) {
        PartnerDetails.SystemType systemTypeValue = new PartnerDetails.SystemType();
        systemTypeValue.setValue(isAlmaOrBibsysLibrary(record)
                                     ? ALMA.toUpperCase(Locale.ROOT)
                                     : OTHER);
        systemTypeValue.setDesc(
            isAlmaOrBibsysLibrary(record)
                ? ALMA.toLowerCase(Locale.ROOT)
                : OTHER.toLowerCase(Locale.ROOT));
        return systemTypeValue;
    }

    private static Optional<String> extractHoldingCodeIfAlmaOrBibsysLibrary(Record record) {
        return isAlmaOrBibsysLibrary(record) ? Optional.of(extractHoldingCode(record)) : Optional.empty();
    }

    private static boolean isAlmaOrBibsysLibrary(Record record) {
        if (Objects.nonNull(record.getKatsyst())) {
            return record.getKatsyst()
                       .toLowerCase(Locale.ROOT)
                       .contains(BIBSYS)
                   || record.getKatsyst()
                          .toLowerCase(Locale.ROOT)
                          .contains(ALMA);
        } else {
            return false;
        }
    }

    private static String extractHoldingCode(Record record) {
        return record.getLandkode().toUpperCase(Locale.ROOT) + record.getBibnr();
    }

    private static String extractName(Record record) {
        return Objects.nonNull(record.getInst())
                   ? record.getInst().replaceAll("\n", " - ")
                   : StringUtils.EMPTY_STRING;
    }

    private static String extractIsilCode(Record record) {
        return Objects.nonNull(record.getIsil())
                   ? record.getIsil()
                   : record.getLandkode().toUpperCase(Locale.ROOT) + ISIL_CODE_SEPARATOR + record.getBibnr();
    }
}
