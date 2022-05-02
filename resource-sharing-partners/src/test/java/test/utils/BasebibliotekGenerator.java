package test.utils;

import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import jakarta.xml.bind.JAXB;
import jakarta.xml.bind.JAXBElement;
import java.io.StringWriter;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import no.nb.basebibliotek.generated.AndreKoder;
import no.nb.basebibliotek.generated.Aut;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Eressurser;
import no.nb.basebibliotek.generated.Link;
import no.nb.basebibliotek.generated.Record;
import no.nb.basebibliotek.generated.UregistrerteFilialer;
import no.nb.basebibliotek.generated.Wressurser;

public class BasebibliotekGenerator {

    public static final String SRU_FIELD_NAME = "SRU";
    public static final String Z_INFO_FIELD_NAME = "z_info";
    public static final String Z_TARGET_FIELD_NAME = "z_target";
    public static final String FILIAL_NAME_FIELD = "filial";
    public static final String NEDLAGT_FIELD_NAME = "nedlagt";
    private static final String BIB_KODE_GAMMEL_FIELD_NAME = "bibkode_gml";
    private static final String BIB_NR_GAMMEL_FIELD_NAME = "bibnr_gml";
    private static final String OAI_FIELD_NAME = "OAI";
    private static final String NNCIP_URI_FIELD_NAME = "nncip_uri";
    private static final String IO_WS_FIELD_NAME = "io_ws";
    private static final String INACTIVE_U = "U";
    private static final String INACTIVE_X = "X";

    public static BaseBibliotek randomBaseBibliotek() {
        var baseBibliotek = new BaseBibliotek();
        baseBibliotek.getRecord().addAll(generateRandomRecords());
        return baseBibliotek;
    }

    public static String toXml(BaseBibliotek baseBibliotek) {
        StringWriter xmlWriter = new StringWriter();
        JAXB.marshal(baseBibliotek, xmlWriter);
        return xmlWriter.toString();
    }

    private static Collection<? extends Record> generateRandomRecords() {
        int maxNumberOfRecords = 10;
        return IntStream.range(0, randomInteger(maxNumberOfRecords) + 1)
                   .boxed()
                   .map(BasebibliotekGenerator::randomRecord)
                   .collect(Collectors.toList());
    }

    private static Record randomRecord(int index) {
        var record = new Record();
        //required fields:
        record.setBibnr(randomString());
        record.setRid(BigInteger.valueOf(index));
        record.setTstamp(randomLocalDate().toString());

        //optional fields:
        if (randomBoolean()) {
            record.setIsil(randomString());
            if (randomBoolean()) {
                record.setIsilAgency(randomString());
            }
        }
        if (randomBoolean()) {
            record.setBibkode(randomString());
        }
        if (randomBoolean()) {
            record.setBibltype(randomString());
        }
        if (randomBoolean()) {
            record.setAut(generateAut());
        }
        if (randomBoolean()) {
            record.setLatLon(randomString());
        }
        if (randomBoolean()) {
            record.setKatsyst(randomString());
        }
        if (randomBoolean()) {
            record.setSamkat(randomString());
        }
        if (randomBoolean()) {
            record.setStengt(generateSetStengtStatus());
        }
        if (randomBoolean()) {
            var start = randomInstant();
            var end = randomInstant(start);
            record.setStengtTil(getGregorianDate(end));
            if (randomBoolean()) {
                record.setStengtFra(getGregorianDate(start));
            }
        }
        if (randomBoolean()) {
            record.setKommnr(randomString());
        }
        if (randomBoolean()) {
            record.setOverordnet(randomString());
        }
        if (randomBoolean()) {
            record.setRelatert(randomString());
        }
        if (randomBoolean()) {
            record.setInst(randomString());
        }
        if (randomBoolean()) {
            record.setInstAlt(randomString());
        }
        if (randomBoolean()) {
            record.setInstEng(randomString());
        }
        if (randomBoolean()) {
            record.setInstKort(randomString());
        }
        if (randomBoolean()) {
            record.setPadr(randomString());
        }
        if (randomBoolean()) {
            record.setPpostnr(randomString());
        }
        if (randomBoolean()) {
            record.setPpoststed(randomString());
        }
        if (randomBoolean()) {
            record.setVadr(randomString());
        }
        if (randomBoolean()) {
            record.setVpostnr(randomString());
        }
        if (randomBoolean()) {
            record.setVpoststed(randomString());
        }
        if (randomBoolean()) {
            record.setBesadr(randomString());
        }
        if (randomBoolean()) {
            record.setLandkode(randomString());
        }
        if (randomBoolean()) {
            record.setTlf(randomString());
        }
        if (randomBoolean()) {
            record.setTlfFj(randomString());
        }
        if (randomBoolean()) {
            record.setEpostAdr(randomString());
        }
        if (randomBoolean()) {
            record.setEpostBest(randomString());
        }
        if (randomBoolean()) {
            record.setUrlHjem(randomString());
        }
        if (randomBoolean()) {
            record.setUrlKat(randomString());
        }
        if (randomBoolean()) {
            record.setAndreNavn(randomString());
        }
        if (randomBoolean()) {
            record.setAvtaler(randomString());
        }
        if (randomBoolean()) {
            record.setOrgnr(randomString());
        }
        if (randomBoolean()) {
            record.setFaktOrgnr(randomString());
        }
        if (randomBoolean()) {
            record.setFaktRef(randomString());
        }
        if (randomBoolean()) {
            record.setFaktInst(randomString());
        }
        if (randomBoolean()) {
            record.setFaktAdr(randomString());
        }
        if (randomBoolean()) {
            record.setFaktPostnr(randomString());
        }
        if (randomBoolean()) {
            record.setFaktPoststed(randomString());
        }
        if (randomBoolean()) {
            record.setBibleder(randomString());
        }
        if (randomBoolean()) {
            record.setFjLeder(randomString());
        }
        if (randomBoolean()) {
            record.setAndreKoder(generateRandomAndreKoder());
        }
        if (randomBoolean()) {
            record.setEressurser(randomEressurser());
        }
        if (randomBoolean()) {
            record.setWressurser(randomWressurser());
        }
        if (randomBoolean()) {
            record.setUregistrerteFilialer(randomUregistrerteFilialer());
        }
        if (randomBoolean()) {
            record.setMerknader(MerknaderGenerator.randomMerknader());
        }

        return record;
    }

    private static UregistrerteFilialer randomUregistrerteFilialer() {
        var uregistrerteFilialer = new UregistrerteFilialer();
        uregistrerteFilialer.getFilialOrNedlagt().addAll(randomFilialOrNedlagte());
        return uregistrerteFilialer;
    }

    private static Collection<? extends JAXBElement<String>> randomFilialOrNedlagte() {
        var maxNumberOfFilialerOrNedlagte = 10;
        return IntStream.range(0, randomInteger(maxNumberOfFilialerOrNedlagte)).boxed().map(
            BasebibliotekGenerator::randomFilialOrNedlagt).collect(
            Collectors.toList());
    }

    private static JAXBElement<String> randomFilialOrNedlagt(int index) {
        return (index % 2 == 0)
                   ? randomJaxbElementString(FILIAL_NAME_FIELD)
                   : randomJaxbElementString(NEDLAGT_FIELD_NAME);
    }

    private static Wressurser randomWressurser() {
        var wressurser = new Wressurser();
        wressurser.getLink().addAll(randomLinks());
        return wressurser;
    }

    private static Collection<? extends Link> randomLinks() {
        var maxNumberOfLinks = 10;
        return IntStream.range(0, randomInteger(maxNumberOfLinks) + 1).boxed().map(index -> randomLink()).collect(
            Collectors.toList());
    }

    private static Link randomLink() {
        var link = new Link();
        link.setUrl(randomUri().toString());
        if (randomBoolean()) {
            link.setTekst(randomString());
        }
        return link;
    }

    private static Eressurser randomEressurser() {
        var eressurser = new Eressurser();
        eressurser.getOAIOrSRUOrArielIp().addAll(randomOaiOrSruOrArielIps());
        return eressurser;
    }

    private static Collection<? extends JAXBElement<String>> randomOaiOrSruOrArielIps() {
        var maxNumberOfOaiOrSruOrArielIps = 10;
        return IntStream.range(0, randomInteger(maxNumberOfOaiOrSruOrArielIps) + 1).boxed().map(
            BasebibliotekGenerator::randomOaiOrSruOrArielIp).collect(
            Collectors.toList());
    }

    private static JAXBElement<String> randomOaiOrSruOrArielIp(int index) {
        switch (index % 5) {
            case 0:
                return randomJaxbElementString(OAI_FIELD_NAME);
            case 1:
                return randomJaxbElementString(NNCIP_URI_FIELD_NAME);
            case 2:
                return randomJaxbElementString(SRU_FIELD_NAME);
            case 3:
                return randomJaxbElementString(Z_INFO_FIELD_NAME);
            case 4:
                return randomJaxbElementString(Z_TARGET_FIELD_NAME);
            default:
                return randomJaxbElementString(IO_WS_FIELD_NAME);
        }
    }

    private static AndreKoder generateRandomAndreKoder() {
        var andreKoder = new AndreKoder();
        andreKoder.getBibkodeAltOrBibkodeGmlOrBibnrGml().addAll(randomBibKoderOrBibkodeGml());
        return andreKoder;
    }

    private static Collection<? extends JAXBElement<String>> randomBibKoderOrBibkodeGml() {
        var maxNumberBibKoderOrBibKodeGml = 10;
        return IntStream.range(0, randomInteger(maxNumberBibKoderOrBibKodeGml) + 1)
                   .boxed()
                   .map(index -> randomBibKodeOrBibkodeGml())
                   .collect(Collectors.toList());
    }

    private static JAXBElement<String> randomBibKodeOrBibkodeGml() {
        return randomBoolean()
                   ? randomJaxbElementString(BIB_KODE_GAMMEL_FIELD_NAME)
                   : randomJaxbElementString(BIB_NR_GAMMEL_FIELD_NAME);
    }

    private static JAXBElement<String> randomJaxbElementString(String type) {
        return new JAXBElement<>(new QName(type), String.class, randomString());
    }

    private static String generateSetStengtStatus() {
        return randomBoolean() ? INACTIVE_U : INACTIVE_X;
    }

    private static XMLGregorianCalendar getGregorianDate(Instant instantAfter) {
        var calendar = (GregorianCalendar) GregorianCalendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(instantAfter.toEpochMilli());
        return attempt(() -> DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar)).orElseThrow();
    }

    private static Aut generateAut() {
        var aut = new Aut();
        aut.setContent(randomString());
        if (randomBoolean()) {
            aut.setEncrypted(true);
        }
        return aut;
    }
}
