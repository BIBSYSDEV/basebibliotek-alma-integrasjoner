package no.sikt.lum;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.Context;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import test.utils.WireMocker;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.s3.S3Driver;

public class LibraryUserManagementTest {


    private static final String SHARED_CONFIG_BUCKET_NAME_ENV_VALUE = "SharedConfigBucket";
    public static final Context CONTEXT = mock(Context.class);
    private transient FakeS3Client s3Client;
    private transient S3Driver s3Driver;
    private static final Environment mockedEnvironment = mock(Environment.class);
    private transient LibraryUserManagementHandler libraryUserManagementHandler;


    @BeforeEach
    public void init(final WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.ALMA_API_HOST)).thenReturn(
            UriWrapper.fromUri(wmRuntimeInfo.getHttpBaseUrl()).toString());
//        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.SHARED_CONFIG_BUCKET_NAME_ENV_NAME)).thenReturn(
//            SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
//        when(mockedEnvironment.readEnv(
//            LibraryUserManagementHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY)).thenReturn(
//            LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);
        final String fullLibCodeToAlmaCodeMapping = IoUtils.stringFromResources(
            Path.of("fullLibCodeToAlmaCodeMapping.json"));
//        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH), fullLibCodeToAlmaCodeMapping);
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment);
    }

    @Test
    public void shouldBeAbleToReadAndPostRecordToAlma() throws IOException {
//        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(BIBNR_RESOLVABLE_TO_ALMA_CODE,
//                                                                           IoUtils.stringFromResources(
//                                                                               Path.of(BASEBIBLIOTEK_0030100_XML)));

//        final S3Event s3Event = prepareBaseBibliotekFromXml(bibNrToXmlMap);

//        WireMocker.mockAlmaGetResponsePartnerNotFound(NO_0030100_ID);
        WireMocker.mockAlmaPostResponse();
//        Integer response = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
//        verify(getRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_PARTNER + "/" + NO_0030100_ID)));
        verify(postRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_PARTNER)));
//        assertThat(response, is(notNullValue()));
//        assertThat(response, is(1));
    }
}
