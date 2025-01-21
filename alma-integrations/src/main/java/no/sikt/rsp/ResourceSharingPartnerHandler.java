package no.sikt.rsp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.google.gson.Gson;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.sikt.alma.partners.generated.Partner;
import no.sikt.clients.alma.AlmaPartnerUpserter;
import no.sikt.clients.BaseBibliotekApi;
import no.sikt.clients.alma.HttpUrlConnectionAlmaPartnerUpserter;
import no.sikt.clients.basebibliotek.HttpUrlConnectionBaseBibliotekApi;
import no.sikt.commons.HandlerUtils;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

public class ResourceSharingPartnerHandler implements RequestHandler<S3Event, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ResourceSharingPartnerHandler.class);
    private static final String EVENT = "event";
    public static final String ALMA_API_HOST = "ALMA_API_HOST";
    public static final String COULD_NOT_CONTACT_ALMA_REPORT_MESSAGE = " could not contact Alma\n";
    public static final String COULD_NOT_CONVERT_TO_PARTNER_ERROR_MESSAGE = " Could not convert to partner";
    public static final String COULD_NOT_CONVERT_TO_PARTNER_REPORT_MESSAGE = " could not convert to partner\n";
    public static final String OK_REPORT_MESSAGE = "OK\n";
    public static final String HANDLER_NAME = "rsp";
    private final transient Gson gson = new Gson();

    public static final String ILL_SERVER_ENV_NAME = "ILL_SERVER";
    public static final String SHARED_CONFIG_BUCKET_NAME_ENV_NAME = "SHARED_CONFIG_BUCKET";
    public static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY =
        "LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH";
    private static final String ALMA_API_KEY_ENV_KEY = "ALMA_APIKEY";
    private final transient S3Client s3Client;
    private final transient AlmaPartnerUpserter almaPartnerUpserter;
    private final transient List<Partner> partners;

    private final transient Environment environment;
    public static final String BASEBIBLIOTEK_URI_ENVIRONMENT_NAME = "BASEBIBLIOTEK_REST_URL";

    private final transient String reportS3BucketName;
    public static final String REPORT_BUCKET_ENVIRONMENT_NAME = "REPORT_BUCKET";

    private final transient BaseBibliotekApi baseBibliotekApi;

    @JacocoGenerated
    @SuppressWarnings("unused")
    public ResourceSharingPartnerHandler() {
        this(S3Driver.defaultS3Client().build(), new Environment());
    }

    public ResourceSharingPartnerHandler(S3Client s3Client, Environment environment) {
        this.s3Client = s3Client;
        this.environment = environment;

        final String almaApiKey = environment.readEnv(ALMA_API_KEY_ENV_KEY);
        final URI almaUri = UriWrapper.fromUri(environment.readEnv(ALMA_API_HOST)).getUri();
        this.almaPartnerUpserter = new HttpUrlConnectionAlmaPartnerUpserter(almaApiKey, almaUri);

        final URI basebibliotekUri =
            UriWrapper.fromUri(environment.readEnv(BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).getUri();
        this.baseBibliotekApi = new HttpUrlConnectionBaseBibliotekApi(basebibliotekUri);
        this.partners = new ArrayList<>();
        this.reportS3BucketName = environment.readEnv(REPORT_BUCKET_ENVIRONMENT_NAME);
    }

    @Override
    public Integer handleRequest(S3Event s3event, Context context) {
        logger.info(EVENT + gson.toJson(s3event));

        String illServer = environment.readEnv(ILL_SERVER_ENV_NAME);
        String sharedConfigBucketName = environment.readEnv(SHARED_CONFIG_BUCKET_NAME_ENV_NAME);
        S3Driver driver = new S3Driver(s3Client, sharedConfigBucketName);
        String libCodeToAlmaCodeMappingFilePath = environment.readEnv(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY);
        logger.info("done setting up drivers and reading environment");
        logger.info(sharedConfigBucketName);
        try {
            var bibNrFile = HandlerUtils.readFile(s3event, s3Client);
            logger.info("done collecting bibNrFile");

            final String libCodesToAlmaCodesMappings = driver.getFile(UnixPath.of(libCodeToAlmaCodeMappingFilePath));
            logger.info("done collecting library codes to alma codes mappings");

            AlmaCodeProvider almaCodeProvider = new AlmaCodeProvider(libCodesToAlmaCodesMappings);
            var bibnrList = HandlerUtils.getBibNrList(bibNrFile);
            var reportStringBuilder = new StringBuilder();
            var basebiblioteks = HandlerUtils
                .generateBasebibliotek(bibnrList, reportStringBuilder, baseBibliotekApi);
            partners.addAll(generatePartners(basebiblioteks, reportStringBuilder, almaCodeProvider, illServer));
            int counter = sendToAlmaAndCountSuccess(partners, reportStringBuilder);
            HandlerUtils.reportToS3Bucket(reportStringBuilder, s3event, s3Client, reportS3BucketName, HANDLER_NAME);
            return counter;
        } catch (Exception exception) {
            throw logErrorAndThrowException(exception);
        }
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    private int sendToAlmaAndCountSuccess(List<Partner> partners, StringBuilder reportStringBuilder) {
        var counter = 0;
        for (Partner partner : partners) {
            var charIndexStartOfBibNrInPartnerCode = 3;
            var bibNr = partner
                            .getPartnerDetails()
                            .getCode()
                            .substring(charIndexStartOfBibNrInPartnerCode);

            if (sendToAlma(partner)) {
                counter++;
                reportStringBuilder
                    .append(bibNr)
                    .append(StringUtils.SPACE)
                    .append(OK_REPORT_MESSAGE);
            } else {
                reportStringBuilder
                    .append(bibNr)
                    .append(COULD_NOT_CONTACT_ALMA_REPORT_MESSAGE);
            }
        }
        return counter;
    }

    private Collection<? extends Partner> generatePartners(List<BaseBibliotek> basebiblioteks,
                                                           StringBuilder reportStringBuilder,
                                                           AlmaCodeProvider almaCodeProvider,
                                                           String illServer) {
        var partners = new ArrayList<Partner>();
        for (BaseBibliotek baseBibliotek : basebiblioteks) {
            try {
                partners.addAll(new PartnerConverter(almaCodeProvider, illServer, baseBibliotek).toPartners());
            } catch (Exception e) {
                //Errors in individual libraries should not cause crash in entire execution.
                logger.info(COULD_NOT_CONVERT_TO_PARTNER_ERROR_MESSAGE, e);
                reportStringBuilder
                    .append(baseBibliotek.getRecord().getFirst().getBibnr())
                    .append(COULD_NOT_CONVERT_TO_PARTNER_REPORT_MESSAGE);
            }
        }
        return partners;
    }

    private boolean sendToAlma(Partner partner) {
        return almaPartnerUpserter.upsertPartner(partner);
    }

    public List<Partner> getPartners() {
        return partners;
    }

    private RuntimeException logErrorAndThrowException(Exception exception) {
        logger.error(exception.getMessage());
        return exception instanceof RuntimeException
                   ? (RuntimeException) exception
                   : new RuntimeException(exception);
    }

}