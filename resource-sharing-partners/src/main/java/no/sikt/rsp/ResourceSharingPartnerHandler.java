package no.sikt.rsp;

import static nva.commons.core.StringUtils.isEmpty;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.google.gson.Gson;
import jakarta.xml.bind.JAXB;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.sikt.basebibliotek.BaseBibliotekBean;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

public class ResourceSharingPartnerHandler implements RequestHandler<S3Event, List<BaseBibliotekBean>> {

    public static final String INVALID_BASEBIBLIOTEK_XML_ERROR_MESSAGE = "Invalid basebibliotek xml ";
    private static final Logger logger = LoggerFactory.getLogger(ResourceSharingPartnerHandler.class);
    public static final int SINGLE_EXPECTED_RECORD = 0;
    private static final String EVENT = "event";
    private final transient Gson gson = new Gson();

    public static final String S3_URI_TEMPLATE = "s3://%s/%s";

    private final S3Client s3Client;

    @JacocoGenerated
    public ResourceSharingPartnerHandler() {
        this(S3Driver.defaultS3Client().build());
    }

    public ResourceSharingPartnerHandler(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public List<BaseBibliotekBean> handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));
        return attempt(() -> readFile(s3event))
                   .map(this::parseXmlFile)
                   .map(this::convertBasebibliotekToBaseBibliotekBean)
                   .orElseThrow(fail -> logErrorAndThrowException(fail.getException()));
    }

    private List<BaseBibliotekBean> convertBasebibliotekToBaseBibliotekBean(BaseBibliotek baseBibliotek) {
        return baseBibliotek
                   .getRecord()
                   .stream()
                   .map(this::convertRecordToBasebibliotekBeanIfBibNrAndLandkodeIsSet)
                   .flatMap(Optional::stream)
                   .collect(Collectors.toList());
    }

    private Optional<BaseBibliotekBean> convertRecordToBasebibliotekBeanIfBibNrAndLandkodeIsSet(Record record) {
        return isEmpty(record.getBibnr()) || isEmpty(record.getLandkode())
                   ? Optional.empty()
                   : Optional.of(convertRecordToBasebibliotekBean(record));
    }

    private BaseBibliotekBean convertRecordToBasebibliotekBean(Record record) {
        BaseBibliotekBean baseBibliotekBean = new BaseBibliotekBean();
        baseBibliotekBean.setBibNr(record.getBibnr());
        baseBibliotekBean.setStengt(record.getStengt());
        baseBibliotekBean.setInst(record.getInst());
        baseBibliotekBean.setKatsyst(record.getKatsyst());
        return baseBibliotekBean;
    }

    private BaseBibliotek parseXmlFile(String file) {
        return JAXB.unmarshal(new StringReader(file), BaseBibliotek.class);
    }

    private RuntimeException logErrorAndThrowException(Exception exception) {
        logger.error(exception.getMessage());
        return exception instanceof RuntimeException
                   ? (RuntimeException) exception
                   : new RuntimeException(exception);
    }

    private String readFile(S3Event event) {
        var s3Driver = new S3Driver(s3Client, extractBucketName(event));
        var fileUri = createS3BucketUri(event);
        return s3Driver.getFile(UriWrapper.fromUri(fileUri).toS3bucketPath());
    }

    private String extractBucketName(S3Event event) {
        return event.getRecords().get(SINGLE_EXPECTED_RECORD).getS3().getBucket().getName();
    }

    private URI createS3BucketUri(S3Event s3Event) {
        return URI.create(String.format(S3_URI_TEMPLATE, extractBucketName(s3Event), extractFilename(s3Event)));
    }

    private String extractFilename(S3Event event) {
        return event.getRecords().get(SINGLE_EXPECTED_RECORD).getS3().getObject().getKey();
    }
}