package test.utils;

import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.Random;
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
    public static final String HTTP_NB_NO_BASE_BIBLIOTEK_NAME_SPACE = "http://nb.no/BaseBibliotek";
    private static final int DAY_IN_MILLI_SECONDS = 1000 * 60 * 60 * 24;

    private transient BigInteger currentRid;

    private final transient Collection<? extends Record> records;

    public BasebibliotekGenerator(RecordSpecification specification) {
        this.currentRid = BigInteger.ONE;
        this.records = List.of(generateRecord(
            specification.getBibNr(),
            specification.getWithLandkode(),
            specification.getNncipUri(),
            specification.getWithStengtFra(),
            specification.getWithStengtTil(),
            specification.getWithPaddr(),
            specification.getWithVaddr(),
            specification.getWithIsil(),
            specification.getKatsys(),
            specification.getEressursExcludes(),
            specification.getStengt(), true));
    }

    @SuppressWarnings("PMD.NullAssignment")
    public BasebibliotekGenerator(Record... records) {
        this.currentRid = null; // not relevant as we do not call generateRecordsFromSpecificationList here
        this.records = Arrays.asList(records);
    }

    public BaseBibliotek generateBaseBibliotek() {
        var baseBibliotek = new BaseBibliotek();
        baseBibliotek.getRecord().addAll(records);
        return baseBibliotek;
    }

    public static String toXml(BaseBibliotek baseBibliotek) {
        StringWriter xmlWriter = new StringWriter();
        JAXB.marshal(baseBibliotek, xmlWriter);
        return xmlWriter.toString();
    }

    private Record generateRecord(String bibnr,
                                  boolean shouldHaveLandkode,
                                  String specifiedNncipUri,
                                  boolean withStengtFra,
                                  boolean withStengtTil,
                                  boolean withPaddr,
                                  boolean withVaddr,
                                  boolean withIsil,
                                  String katsys,
                                  List<String> eressursExcludes,
                                  String stengt,
                                  boolean shouldHaveInst) {
        var record = new Record();
        record.setRid(incrementCurrentRidAndReturnResult());
        record.setTstamp(randomLocalDate().toString());
        record.setBibnr(bibnr);

        //optional fields:
        var start = Instant.now().toEpochMilli() - DAY_IN_MILLI_SECONDS;
        var end = Instant.now().toEpochMilli() + DAY_IN_MILLI_SECONDS;
        if (withStengtFra) {
            record.setStengtFra(getGregorianDate(start));
        }
        if (withStengtTil) {
            record.setStengtTil(getGregorianDate(end));
        }
        if (shouldHaveLandkode) {
            record.setLandkode(randomString());
        }
        if (withIsil) {
            record.setIsil(randomString());
            if (randomBoolean()) {
                record.setIsilAgency(randomString());
            }
        }
        if (Objects.nonNull(specifiedNncipUri)) {
            record.setEressurser(generateEressurserWithSpecifiedNncipServer(specifiedNncipUri));
        } else {
            record.setEressurser(randomEressurser(eressursExcludes));
        }
        record.setKatsyst(katsys);
        if (withPaddr) {
            record.setPadr(randomString());
        }
        if (withPaddr) {
            record.setPpostnr(randomString());
        }
        if (withPaddr) {
            record.setPpoststed(randomString());
        }
        if (withVaddr) {
            record.setVadr(randomString());
        }
        if (withVaddr) {
            record.setVpostnr(randomString());
        }
        if (withVaddr) {
            record.setVpoststed(randomString());
        }
        record.setStengt(stengt);
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
            record.setSamkat(randomString());
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
        if (shouldHaveInst) {
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
            record.setBesadr(randomString());
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
            record.setWressurser(randomWressurser());
        }
        if (randomBoolean()) {
            record.setUregistrerteFilialer(randomUregistrerteFilialer());
        }
        if (randomBoolean()) {
            record.setMerknader(MerknaderGenerator.randomMerknader());
        }
        if (randomBoolean()) {
            record.setBibltype(randomBibltype());
        }

        return record;
    }

    private Eressurser generateEressurserWithSpecifiedNncipServer(String specifiedNncipUri) {
        var eressurser = new Eressurser();
        eressurser.getOAIOrSRUOrArielIp()
            .add(generateJaxbElementString(NNCIP_URI_FIELD_NAME, specifiedNncipUri));
        return eressurser;
    }

    private BigInteger incrementCurrentRidAndReturnResult() {
        setCurrentRid(currentRid.add(BigInteger.ONE));
        return currentRid;
    }

    private void setCurrentRid(BigInteger i) {
        currentRid = i;
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

    private static Eressurser randomEressurser(List<String> eressursExcludes) {
        var eressurser = new Eressurser();
        eressurser.getOAIOrSRUOrArielIp().addAll(randomOaiOrSruOrArielIps(eressursExcludes));
        return eressurser;
    }

    private static Collection<? extends JAXBElement<String>> randomOaiOrSruOrArielIps(List<String> eressursExcludes) {
        var maxNumberOfOaiOrSruOrArielIps = 10;
        return IntStream.range(0, randomInteger(maxNumberOfOaiOrSruOrArielIps) + 1)
            .boxed()
            .map(
                BasebibliotekGenerator::randomOaiOrSruOrArielIp)
            .filter(p -> !eressursExcludes.contains(p.getName().getLocalPart()))
            .collect(
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
        return new JAXBElement<>(new QName(HTTP_NB_NO_BASE_BIBLIOTEK_NAME_SPACE, type), String.class, randomString());
    }

    private static JAXBElement<String> generateJaxbElementString(String type, String value) {
        return new JAXBElement<>(new QName(HTTP_NB_NO_BASE_BIBLIOTEK_NAME_SPACE, type), String.class, value);
    }

    private static XMLGregorianCalendar getGregorianDate(long epochMilli) {
        var calendar = (GregorianCalendar) GregorianCalendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(epochMilli);
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

    private String randomBibltype() {
        String[] bibltypes = new String[]{"UNB", "UNI", "HØY", "FIR", "ORG", "FAG", "AVD", "ARK", "MUS", "FBI", "FIL",
            "FYB", "FEN", "GSK", "VGS", "FHS"};
        int rnd = new Random().nextInt(bibltypes.length);
        return bibltypes[rnd];
    }
}
