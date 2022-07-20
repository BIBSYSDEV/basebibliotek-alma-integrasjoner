package no.sikt.commons;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.sikt.clients.BaseBibliotekApi;
import no.sikt.rsp.ResourceSharingPartnerHandler;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.s3.S3Client;

public class HandlerUtils {

    public static final int SINGLE_EXPECTED_RECORD = 0;
    public static final String S3_URI_TEMPLATE = "s3://%s/%s";

    public static String readFile(S3Event event, S3Client s3Client) {
        var s3Driver = new S3Driver(s3Client, extractBucketName(event));
        var fileUri = createS3BucketUri(event);
        return s3Driver.getFile(UriWrapper.fromUri(fileUri).toS3bucketPath());
    }

    public static List<String> getBibNrList(String bibNrFile) {
        return Arrays.stream(bibNrFile.split("\n")).map(String::trim).collect(Collectors.toList());
    }

    public static List<BaseBibliotek> generateBasebibliotek(List<String> bibnrList, StringBuilder reportStringBuilder,
                                                            BaseBibliotekApi baseBibliotekApi) {
        final List<BaseBibliotek> basebiblioteks = new ArrayList<>();
        for (final String bibnr : bibnrList) {
            baseBibliotekApi.fetchBasebibliotek(bibnr)
                .ifPresentOrElse(basebiblioteks::add, () ->
                                                          reportStringBuilder
                                                              .append(bibnr)
                                                              .append(
                                                                  ResourceSharingPartnerHandler.COULD_NOT_FETCH_BASEBIBLIOTEK_REPORT_MESSAGE)
                );
        }
        return basebiblioteks;
    }

    public static void reportToS3Bucket(StringBuilder reportStringBuilder, S3Event s3Event, S3Client s3Client,
                                        String reportS3BucketName) throws IOException {
        var report = reportStringBuilder.toString();
        var s3Driver = new S3Driver(s3Client, reportS3BucketName);
        s3Driver.insertFile(UnixPath.of(ResourceSharingPartnerHandler.REPORT_FILE_NAME_PREFIX + extractFilename(s3Event)), report);
    }

    private static String extractBucketName(S3Event event) {
        return event.getRecords().get(SINGLE_EXPECTED_RECORD).getS3().getBucket().getName();
    }

    private static URI createS3BucketUri(S3Event s3Event) {
        return URI.create(String.format(S3_URI_TEMPLATE, extractBucketName(s3Event), extractFilename(s3Event)));
    }

    public static String extractFilename(S3Event event) {
        return event.getRecords().get(SINGLE_EXPECTED_RECORD).getS3().getObject().getKey();
    }
}
