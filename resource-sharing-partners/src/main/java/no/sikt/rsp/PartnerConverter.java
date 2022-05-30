package no.sikt.rsp;

import jakarta.xml.bind.JAXB;
import jakarta.xml.bind.JAXBElement;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.datatype.XMLGregorianCalendar;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.generated.EmailDetails;
import no.sikt.alma.generated.GeneralUserIdType;
import no.sikt.alma.generated.IsoDetails;
import no.sikt.alma.generated.NcipP2PDetails;
import no.sikt.alma.generated.Partner;
import no.sikt.alma.generated.PartnerDetails;
import no.sikt.alma.generated.PartnerDetails.LocateProfile;
import no.sikt.alma.generated.PartnerDetails.SystemType;
import no.sikt.alma.generated.ProfileDetails;
import no.sikt.alma.generated.ProfileType;
import no.sikt.alma.generated.RequestExpiryType;
import no.sikt.alma.generated.Status;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: refactor and remove supress pmd.godclass warning
@SuppressWarnings({"PMD.GodClass", "PMD.AvoidCalendarDateCreation"})
public class PartnerConverter {

    private static final Logger logger = LoggerFactory.getLogger(PartnerConverter.class);
    private static final String EMAIL_PATTERN = ".+@.+";
    private static final String COULD_NOT_CONVERT_RECORD = "Could not convert record, missing %s, record: %s";
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
    public static final String TEMPORARILY_CLOSED = "U";
    public static final String PERMANENTLY_CLOSED = "X";
    private static final String LOCATE_PROFILE_PREFIX = "47BIBSYS_";

    private final AlmaCodeProvider almaCodeProvider;
    private final String interLibraryLoanServer;
    private final BaseBibliotek baseBibliotek;

    public PartnerConverter(AlmaCodeProvider almaCodeProvider, String interLibraryLoanServer,
                            BaseBibliotek baseBibliotek) {
        this.almaCodeProvider = almaCodeProvider;
        this.interLibraryLoanServer = interLibraryLoanServer;
        this.baseBibliotek = baseBibliotek;
    }

    public List<Partner> toPartners() {
        return baseBibliotek
                   .getRecord()
                   .stream()
                   .map(this::convertRecordToPartnerOptional)
                   .flatMap(Optional::stream)
                   .collect(Collectors.toList());
    }

    private String toXml(Record record) {
        StringWriter xmlWriter = new StringWriter();
        JAXB.marshal(record, xmlWriter);
        return xmlWriter.toString();
    }

    private Optional<Partner> convertRecordToPartnerOptional(Record record) {
        return satisfiesConstraints(record)
                   ? Optional.of(convertRecordToPartner(record))
                   : returnEmptyAndLogProblem(record);
    }

    private Partner convertRecordToPartner(Record record) {
        var partner = new Partner();
        partner.setPartnerDetails(extractPartnerDetailsFromRecord(record));
        partner.setContactInfo(ContactInfoConverter.extractContactInfoFromRecord(record));
        return partner;
    }

    private Optional<Partner> returnEmptyAndLogProblem(Record record) {
        var missingParameters = Objects.nonNull(record.getLandkode()) ? StringUtils.EMPTY_STRING : "landkode";
        logger.info(String.format(COULD_NOT_CONVERT_RECORD, missingParameters, toXml(record)));
        return Optional.empty();
    }

    private boolean satisfiesConstraints(Record record) {
        List<String> missingFields = findMissingRequiredFields(record);
        if (!missingFields.isEmpty()) {
            logger.warn(String.format(COULD_NOT_CONVERT_RECORD, missingFields, toXml(record)));
        }

        return missingFields.isEmpty();
    }

    private List<String> findMissingRequiredFields(Record record) {
        final List<String> missingFields = new ArrayList<>();

        if (StringUtils.isEmpty(record.getBibnr())) {
            missingFields.add("bibnr");
        }
        if (StringUtils.isEmpty(record.getLandkode())) {
            missingFields.add("landkode");
        }

        return missingFields;
    }

    private PartnerDetails extractPartnerDetailsFromRecord(Record record) {
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
        partnerDetails.setProfileDetails(extractProfileDetails(record));
        partnerDetails.setStatus(extractStatus(record));

        if (isAlmaOrBibsysLibrary(record)) {
            final String almaCode = almaCodeProvider.getAlmaCode(record.getBibnr()).orElse("");
            partnerDetails.setInstitutionCode(almaCode);
            final LocateProfile locateProfile = extractLocateProfile(almaCode);
            partnerDetails.setLocateProfile(locateProfile);
        } else {
            partnerDetails.setInstitutionCode("");
            partnerDetails.setLocateProfile(null);
        }

        return partnerDetails;
    }

    private LocateProfile extractLocateProfile(final String almaCode) {
        if (StringUtils.isEmpty(almaCode)) {
            return null;
        } else {
            final LocateProfile locateProfile = new LocateProfile();

            locateProfile.setValue(LOCATE_PROFILE_PREFIX + almaCode);
            return locateProfile;
        }
    }

    private ProfileDetails extractProfileDetails(Record record) {
        ProfileDetails details = new ProfileDetails();

        Optional<String> nncipUri = extractNncipUri(record);

        Optional<String> email = extractEmail(record);

        if (isAlmaOrBibsysLibrary(record)) {
            details.setProfileType(ProfileType.ISO);

            final IsoDetails isoDetails = new IsoDetails();
            isoDetails.setIllPort(9001);
            isoDetails.setIllServer(interLibraryLoanServer);
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

    private Optional<String> extractEmail(Record record) {
        if (StringUtils.isNotEmpty(record.getEpostBest()) && record.getEpostBest().matches(EMAIL_PATTERN)) {
            return Optional.of(record.getEpostBest());
        } else if (StringUtils.isNotEmpty(record.getEpostAdr()) && record.getEpostAdr().matches(EMAIL_PATTERN)) {
            return Optional.of(record.getEpostAdr());
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> extractNncipUri(Record record) {
        if (record.getEressurser() == null) {
            return Optional.empty();
        } else {
            return record.getEressurser().getOAIOrSRUOrArielIp().stream()
                       .filter(element -> NNCIP_URI.equals(element.getName().getLocalPart()))
                       .map(JAXBElement::getValue)
                       .findFirst();
        }
    }

    private SystemType extractSystemType(Record record) {
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

    private Optional<String> extractHoldingCodeIfAlmaOrBibsysLibrary(Record record) {
        return isAlmaOrBibsysLibrary(record) ? Optional.of(extractHoldingCode(record)) : Optional.empty();
    }

    private boolean isAlmaOrBibsysLibrary(Record record) {
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

    private String extractHoldingCode(Record record) {
        return record.getLandkode().toUpperCase(Locale.ROOT) + record.getBibnr();
    }

    private String extractName(Record record) {
        return Objects.nonNull(record.getInst())
                   ? record.getInst().replaceAll("\n", " - ")
                   : StringUtils.EMPTY_STRING;
    }

    private String extractIsilCode(Record record) {
        return Objects.nonNull(record.getIsil())
                   ? record.getIsil()
                   : record.getLandkode().toUpperCase(Locale.ROOT) + ISIL_CODE_SEPARATOR + record.getBibnr();
    }

    private static Status extractStatus(Record record) {
        return hasTemporaryOrPermanentlyClosedStatus(record)
               || currentDateIsInStengtInterval(record)
                   ? Status.INACTIVE
                   : Status.ACTIVE;
    }

    private static boolean currentDateIsInStengtInterval(Record record) {
        var stengFraIsInTheFuture = isDateInTheFuture(record.getStengtFra());
        var stengTilIsIntheFuture = isDateInTheFuture(record.getStengtTil());
        var stengFraIsNotSet = Objects.isNull(record.getStengtFra());
        var stengTilisNotSet = Objects.isNull(record.getStengtTil());
        return !stengFraIsInTheFuture && stengTilIsIntheFuture
               || !stengFraIsInTheFuture && stengTilisNotSet
               || stengFraIsNotSet && !stengTilisNotSet && stengTilIsIntheFuture;
    }

    private static boolean hasTemporaryOrPermanentlyClosedStatus(Record record) {
        var stengtStatus = StringUtils.isNotEmpty(record.getStengt())
                               ? record.getStengt()
                               : StringUtils.EMPTY_STRING;
        return TEMPORARILY_CLOSED.equalsIgnoreCase(stengtStatus) || PERMANENTLY_CLOSED.equalsIgnoreCase(stengtStatus);
    }

    private static boolean isDateInTheFuture(XMLGregorianCalendar date) {
        boolean future = true;
        if (Objects.nonNull(date)) {
            GregorianCalendar gregorianCalendar = date.toGregorianCalendar();
            Date currentDate = new Date();
            future = currentDate.getTime() < gregorianCalendar.getTime().getTime();
        }
        return future;
    }
}
