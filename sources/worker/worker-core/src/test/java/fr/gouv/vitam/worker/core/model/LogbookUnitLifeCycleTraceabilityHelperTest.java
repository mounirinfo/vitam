package fr.gouv.vitam.worker.core.model;

import fr.gouv.vitam.common.CharsetUtils;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SedaConstants;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.processing.IOParameter;
import fr.gouv.vitam.common.model.processing.ProcessingUri;
import fr.gouv.vitam.common.model.processing.UriPrefix;
import fr.gouv.vitam.common.security.merkletree.MerkleTreeAlgo;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.model.TraceabilityFile;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.worker.model.LogbookUnitLifeCycleTraceabilityHelper;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

public class LogbookUnitLifeCycleTraceabilityHelperTest {
    private static final String FILE_NAME = "0_operations_20171031_151118.zip";
    private static final String LOGBOOK_OPERATION_START_DATE = "2017-08-17T14:01:07.52";
    private static final String LAST_OPERATION_HASH =
        "MIIEljAVAgEAMBAMDk9wZXJhdGlvbiBPa2F5MIIEewYJKoZIhvcNAQcCoIIEbDCCBGgCAQMxDzANBglghkgBZQMEAgMFADCBgAYLKoZIhvcNAQkQAQSgcQRvMG0CAQEGASkwUTANBglghkgBZQMEAgMFAARA25rjfWLQKTkp0ETrnTQ1b/iZ8fIhx8lsguGt2wv8UuYjtbD5PYZ/LydCEVWBSi5HwJ8E1PZbRIvsH+R2sGhy4QIBARgPMjAxNzA4MTcxNTAxMDZaMYIDzTCCA8kCAQEwfjB4MQswCQYDVQQGEwJmcjEMMAoGA1UECAwDaWRmMQ4wDAYDVQQHDAVwYXJpczEOMAwGA1UECgwFdml0YW0xFDASBgNVBAsMC2F1dGhvcml0aWVzMSUwIwYDVQQDDBxjYV9pbnRlcm1lZGlhdGVfdGltZXN0YW1waW5nAgIAujANBglghkgBZQMEAgMFAKCCASAwGgYJKoZIhvcNAQkDMQ0GCyqGSIb3DQEJEAEEMBwGCSqGSIb3DQEJBTEPFw0xNzA4MTcxNTAxMDZaMC0GCSqGSIb3DQEJNDEgMB4wDQYJYIZIAWUDBAIDBQChDQYJKoZIhvcNAQENBQAwTwYJKoZIhvcNAQkEMUIEQLja866EXeQzwV2WyARNL+C3Gh9jbJQDtmlAtLxbgFjNZkkPHGjY83b0imbLbpCeU7kr3jrvo+dLIOJgSh/IfXMwZAYLKoZIhvcNAQkQAi8xVTBTMFEwTzALBglghkgBZQMEAgMEQCURZjpTzSgWrppLklHIw5xgA8HXuv0mqAnhOCqmsyuuiWcWjCT3H42RDJSWaTCtFP/xa6tgHOynRG+4X5CHKmQwDQYJKoZIhvcNAQENBQAEggIANO7owkRFd4iTLs+RmM0cNrGvy6LhrkaV2r6862E3G5jBmC2Ao8WkI0chahPy+2gHi90M2ykpwTicoRbYBL4s9XlZn2KJ0fA2HZ/f283nacB4ARO+tdQRs7p8vXgyPYC9kO59fa/he7B1o0Mdo6uwba053r7JJplx6hNnCiJM3bB5jTBoxpdb3A2o+cq/TdGqM4MVwYms1jbswF4UDzWBLnKwY4cw/vGCuelw2AU+Q5B11QxrHjXVHaeeVm6ju27YtkGOWthwF3KQ6LEe6xKka+XQZ8kwxHIh523WjrMpoH+B8BTNRerO6KnhxVfHKUKTDO/zpYhPXyKjibg2d3lkRCUa1jtFoBKIBsdvDz0cEoN2XuOkIm9tMpe5pE4gvPRVToTJe7YxZePrvlvmJfwM5RNuNvMqvWlq3CgPj77BePzZGCfSgG91/h0TCAwQXJDEyvk9PJOrjNt4ABNJ6YOxCRF/IeQyEtUpJ9yP13JXOTTRaKkDuueObjnemxvS68rs5h1elqgFdWDCfT9TJtpEmERIT9+8+uf/v8fpSkAFpHKIaZjoAQjIBvJYSvyGZlUCrEUoAtxVgVWGMOXZITc6UADOasv8Fjm10lg+2bgX/KP1S+hH68/lMH6RNKmsC1/BKEsH9+cnsdTJySPS2HjZmfZ5FK695FRQpjL5wfgKbK8=";
    private static final String HANDLER_ID = "FINALIZE_LC_TRACEABILITY";
    private static final String LAST_OPERATION = "LogbookLifeCycleTraceabilityHelperTest/lastOperation.json";
    private static final String TRACEABILITY_INFO =
        "LogbookLifeCycleTraceabilityHelperTest/traceabilityInformation.json";
    private static final String UNIT_FILE =
        "LogbookLifeCycleTraceabilityHelperTest/unit.txt";

    private static LocalDateTime LOGBOOK_OPERATION_EVENT_DATE;
    private HandlerIOImpl handlerIO;
    private List<IOParameter> in;
    private WorkspaceClient workspaceClient;
    private WorkspaceClientFactory workspaceClientFactory;
    private LogbookOperationsClientFactory logbookOperationsClientFactory;
    private LogbookOperationsClient logbookOperationsClient;
    private ItemStatus itemStatus;
    private final List<URI> uriListWorkspaceOKUnit = new ArrayList<>();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {

        File vitamTempFolder = folder.newFolder();
        SystemPropertyUtil.set("vitam.tmp.folder", vitamTempFolder.getAbsolutePath());

        LOGBOOK_OPERATION_EVENT_DATE = LocalDateUtil.fromDate(LocalDateUtil.getDate("2016-06-10T11:56:35.914"));

        workspaceClient = mock(WorkspaceClient.class);
        workspaceClientFactory = mock(WorkspaceClientFactory.class);
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);

        logbookOperationsClient = mock(LogbookOperationsClient.class);
        logbookOperationsClientFactory = mock(LogbookOperationsClientFactory.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsClient);

        String objectId = "objectId";
        handlerIO = new HandlerIOImpl(workspaceClient, "Test", "workerId", Lists.newArrayList(objectId));
        handlerIO.setCurrentObjectId(objectId);

        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "Operations/lastOperation.json")));
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "Operations/traceabilityInformation.json")));
        uriListWorkspaceOKUnit
            .add(new URI(URLEncoder.encode("aeaqaaaaaqhgausqab7boak55nw5vqaaaaaq.json", CharsetUtils.UTF_8)));
        itemStatus = new ItemStatus(HANDLER_ID);
    }

    @Test
    @RunWithCustomExecutor
    public void should_extract_correctly_startDate_from_last_event() throws Exception {
        // Given
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);
        handlerIO.addOutIOParameters(in);
        handlerIO.addOutputResult(0, PropertiesUtils.getResourceFile(LAST_OPERATION), false);
        handlerIO.addOutputResult(1, PropertiesUtils.getResourceFile(TRACEABILITY_INFO), false);
        handlerIO.addInIOParameters(in);

        LogbookUnitLifeCycleTraceabilityHelper helper =
            new LogbookUnitLifeCycleTraceabilityHelper(handlerIO, logbookOperationsClient, itemStatus, guid.getId(),
                workspaceClientFactory);

        // When
        helper.initialize();

        // Then
        assertThat(helper.getTraceabilityStartDate())
            .isEqualTo(LocalDateUtil.getFormattedDateForMongo(LOGBOOK_OPERATION_EVENT_DATE));
    }

    @Test
    @RunWithCustomExecutor
    public void should_correctly_save_data_and_compute_start_date_for_first_traceability() throws Exception {
        // Given
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);
        handlerIO.addOutIOParameters(in);
        handlerIO.addOutputResult(0, PropertiesUtils.getResourceFile(LAST_OPERATION), false);
        handlerIO.addOutputResult(1, PropertiesUtils.getResourceFile(TRACEABILITY_INFO), false);
        handlerIO.addInIOParameters(in);

        when(workspaceClient.getListUriDigitalObjectFromFolder(anyObject(), eq(SedaConstants.LFC_UNITS_FOLDER)))
            .thenReturn(new RequestResponseOK().addResult(uriListWorkspaceOKUnit));
        InputStream unitFile = new FileInputStream(PropertiesUtils.findFile(UNIT_FILE));
        when(workspaceClient.getObject(anyObject(),
            eq(SedaConstants.LFC_UNITS_FOLDER + "/aeaqaaaaaqhgausqab7boak55nw5vqaaaaaq.json")))
            .thenReturn(Response.status(Status.OK).entity(unitFile).build());
        when(logbookOperationsClient.selectOperation(anyObject()))
            .thenThrow(new LogbookClientException("LogbookClientException"));

        LogbookUnitLifeCycleTraceabilityHelper helper =
            new LogbookUnitLifeCycleTraceabilityHelper(handlerIO, logbookOperationsClient, itemStatus, guid.getId(),
                workspaceClientFactory);

        final MerkleTreeAlgo algo = new MerkleTreeAlgo(VitamConfiguration.getDefaultDigestType());

        File zipFile = new File(folder.newFolder(), String.format(FILE_NAME));
        TraceabilityFile file = new TraceabilityFile(zipFile);

        // When
        helper.saveDataInZip(algo, file);
        file.close();

        // Then
        assertThat(Files.size(Paths.get(zipFile.getPath()))).isEqualTo(10273L);
    }

    @Test
    @RunWithCustomExecutor
    public void should_extract_correctly_date_and_token_from_last_event() throws Exception {
        // Given
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);
        handlerIO.addOutIOParameters(in);
        handlerIO.addOutputResult(0, PropertiesUtils.getResourceFile(LAST_OPERATION), false);
        handlerIO.addOutputResult(1, PropertiesUtils.getResourceFile(TRACEABILITY_INFO), false);
        handlerIO.addInIOParameters(in);

        LogbookUnitLifeCycleTraceabilityHelper helper =
            new LogbookUnitLifeCycleTraceabilityHelper(handlerIO, logbookOperationsClient, itemStatus, guid.getId(),
                workspaceClientFactory);

        helper.initialize();

        // When
        String date = helper.getPreviousStartDate();
        byte[] token = helper.getPreviousTimestampToken();

        // Then
        assertThat(date).isEqualTo(LOGBOOK_OPERATION_START_DATE);
        assertThat(Base64.encodeBase64String(token)).isEqualTo(LAST_OPERATION_HASH);
    }

    @Test
    @RunWithCustomExecutor
    public void should_extract_correctly_event_number() throws Exception {
        // Given
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);
        handlerIO.addOutIOParameters(in);
        handlerIO.addOutputResult(0, PropertiesUtils.getResourceFile(LAST_OPERATION), false);
        handlerIO.addOutputResult(1, PropertiesUtils.getResourceFile(TRACEABILITY_INFO), false);
        handlerIO.addInIOParameters(in);

        LogbookUnitLifeCycleTraceabilityHelper helper =
            new LogbookUnitLifeCycleTraceabilityHelper(handlerIO, logbookOperationsClient, itemStatus, guid.getId(),
                workspaceClientFactory);

        helper.initialize();

        // When
        Long size = helper.getDataSize();

        // Then
        assertThat(size).isEqualTo(2);
    }
}
