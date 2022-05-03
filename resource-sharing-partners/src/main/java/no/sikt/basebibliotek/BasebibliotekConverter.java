package no.sikt.basebibliotek;

import static nva.commons.core.StringUtils.isEmpty;
import jakarta.xml.bind.JAXBElement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.datatype.XMLGregorianCalendar;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import nva.commons.core.JacocoGenerated;

public class BasebibliotekConverter {
    
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final String NNCIP_URI_FIELD_NAME = "nncip_uri";

    @JacocoGenerated
    public BasebibliotekConverter() {
    }

    public static List<BaseBibliotekBean> convertBasebibliotekToBaseBibliotekBean(BaseBibliotek baseBibliotek) {
        return baseBibliotek
                   .getRecord()
                   .stream()
                   .map(BasebibliotekConverter::convertRecordToBasebibliotekBeanIfBibNrAndLandkodeIsSet)
                   .flatMap(Optional::stream)
                   .collect(Collectors.toList());
    }

    private static Optional<BaseBibliotekBean> convertRecordToBasebibliotekBeanIfBibNrAndLandkodeIsSet(Record record) {
        return isEmpty(record.getBibnr()) || isEmpty(record.getLandkode())
                   ? Optional.empty()
                   : Optional.of(convertRecordToBasebibliotekBean(record));
    }

    private static BaseBibliotekBean convertRecordToBasebibliotekBean(Record record) {
        BaseBibliotekBean baseBibliotekBean = new BaseBibliotekBean();
        baseBibliotekBean.setBibNr(record.getBibnr());
        baseBibliotekBean.setStengt(record.getStengt());
        baseBibliotekBean.setInst(record.getInst());
        baseBibliotekBean.setNncippServer(getNncippServer(record).orElse(null));
        baseBibliotekBean.setKatsyst(record.getKatsyst());
        baseBibliotekBean.setStengtFra(createDateString(record.getStengtFra()).orElse(null));
        baseBibliotekBean.setStengtTil(createDateString(record.getStengtTil()).orElse(null));
        return baseBibliotekBean;
    }

    private static Optional<String> createDateString(XMLGregorianCalendar xmlGregorianCalendar) {
        return Objects.nonNull(xmlGregorianCalendar)
                   ? Optional.of(formatDateSynchronized(xmlGregorianCalendar.toGregorianCalendar().getTime()))
                   : Optional.empty();
    }

    private static String formatDateSynchronized(Date date) {
        synchronized (dateFormat) {
            return dateFormat.format(date);
        }
    }

    private static Optional<String> getNncippServer(Record record) {
        var eressurser = record.getEressurser();
        return Objects.nonNull(eressurser)
                   ? eressurser.getOAIOrSRUOrArielIp()
                         .stream()
                         .filter(BasebibliotekConverter::isNncipUri)
                         .findFirst()
                         .map(BasebibliotekConverter::getJaxbElementValue)
                   : Optional.empty();
    }

    private static boolean isNncipUri(JAXBElement<String> oaiOrSruOrArielIp) {
        return NNCIP_URI_FIELD_NAME.equals(oaiOrSruOrArielIp.getName().getLocalPart());
    }

    private static String getJaxbElementValue(JAXBElement<String> oaiOrSruOrAirelIP) {
        return oaiOrSruOrAirelIP.getValue().trim();
    }
}
