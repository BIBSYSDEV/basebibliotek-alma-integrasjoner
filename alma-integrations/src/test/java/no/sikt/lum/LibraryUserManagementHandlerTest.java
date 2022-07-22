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
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.utils.HandlerUtils;
import test.utils.WireMocker;

@WireMockTest
class LibraryUserManagementHandlerTest {

    private static final String SHARED_CONFIG_BUCKET_NAME_ENV_VALUE = "SharedConfigBucket";
    public static final String BIBLIOTEK_REST_PATH = "/basebibliotek/rest/bibnr/";
    public static final String BASEBIBLIOTEK_REPORT = "basebibliotek-report";
    public static final String FULL_LIB_CODE_TO_ALMA_CODE_MAPPING_JSON = "fullLibCodeToAlmaCodeMapping.json";
    private static final String LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH = "/libCodeToAlmaCodeMapping.json";
    private static final String BIBNR_RESOLVABLE_TO_ALMA_CODE = "0030100";
    private static final String BASEBIBLIOTEK_0030100_XML = "bb_0030100.xml";
    private static final String LIB_0030100_ID = "lib0030100";

    public static final Context CONTEXT = mock(Context.class);
    private transient FakeS3Client s3Client;
    private transient S3Driver s3Driver;
    private transient LibraryUserManagementHandler libraryUserManagementHandler;
    private static final Environment mockedEnvironment = mock(Environment.class);
    private int numberOfAlmaInstances;

    @BeforeEach
    public void init(final WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.ALMA_API_HOST)).thenReturn(
            UriWrapper.fromUri(wmRuntimeInfo.getHttpBaseUrl()).toString());
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.BASEBIBLIOTEK_URI_ENVIRONMENT_NAME)).thenReturn(
            UriWrapper.fromUri(wmRuntimeInfo.getHttpBaseUrl()).addChild(BIBLIOTEK_REST_PATH).toString());
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.SHARED_CONFIG_BUCKET_NAME_ENV_NAME)).thenReturn(
            SHARED_CONFIG_BUCKET_NAME_ENV_VALUE);
        when(mockedEnvironment.readEnv(LibraryUserManagementHandler.REPORT_BUCKET_ENVIRONMENT_NAME)).thenReturn(
            BASEBIBLIOTEK_REPORT);
        when(mockedEnvironment.readEnv(
                 LibraryUserManagementHandler.LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH_ENV_KEY)).thenReturn(
            LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH);
        final String fullLibCodeToAlmaCodeMapping = IoUtils.stringFromResources(
            Path.of(FULL_LIB_CODE_TO_ALMA_CODE_MAPPING_JSON));
        numberOfAlmaInstances = StringUtils.countMatches(fullLibCodeToAlmaCodeMapping, "almaCode");
        s3Driver.insertFile(UnixPath.of(LIB_CODE_TO_ALMA_CODE_MAPPING_FILE_PATH), fullLibCodeToAlmaCodeMapping);
        libraryUserManagementHandler = new LibraryUserManagementHandler(s3Client, mockedEnvironment);
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void shouldBeAbleToReadAndPostRecordToAlma() throws IOException {
        final Map<String, String> bibNrToXmlMap = Collections.singletonMap(BIBNR_RESOLVABLE_TO_ALMA_CODE,
                                                                           IoUtils.stringFromResources(
                                                                               Path.of(BASEBIBLIOTEK_0030100_XML)));
        final S3Event s3Event = HandlerUtils.prepareBaseBibliotekFromXml(bibNrToXmlMap, s3Driver);
        WireMocker.mockAlmaGetResponseUserNotFound(LIB_0030100_ID);
        WireMocker.mockAlmaPostResponse();
        Integer response = libraryUserManagementHandler.handleRequest(s3Event, CONTEXT);
        verify(getRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_USERS + "/" + LIB_0030100_ID)));
        verify(postRequestedFor(urlPathEqualTo(WireMocker.URL_PATH_USERS)));
        assertThat(response, is(notNullValue()));
        assertThat(response, is(numberOfAlmaInstances));
    }
}