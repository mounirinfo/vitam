package fr.gouv.vitam.batch.report.rest.service; /*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/
import fr.gouv.vitam.batch.report.model.ReportBody;
import fr.gouv.vitam.batch.report.rest.repository.EliminationActionObjectGroupRepository;
import fr.gouv.vitam.batch.report.rest.repository.EliminationActionUnitRepository;
import fr.gouv.vitam.common.database.server.mongodb.EmptyMongoCursor;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.bson.Document;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class BatchReportServiceImplTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EliminationActionUnitRepository eliminationActionUnitRepository;

    @Mock
    private EliminationActionObjectGroupRepository eliminationActionObjectGroupRepository;

    @Mock
    private WorkspaceClientFactory workspaceClientFactory;

    @Mock
    private WorkspaceClient workspaceClient;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();


    @Mock
    private EmptyMongoCursor emptyMongoCursor;

    private BatchReportServiceImpl batchReportServiceImpl;

    private String PROCESS_ID = "123456789";
    private int TENANT_ID = 0;

    @Before
    public void setUp() throws Exception {
        batchReportServiceImpl = new BatchReportServiceImpl(eliminationActionUnitRepository,
            eliminationActionObjectGroupRepository,
            workspaceClientFactory);
    }


    @Test
    public void should_append_elimination_object_group_report() throws Exception {
        // Given
        InputStream stream = getClass().getResourceAsStream("/eliminationObjectGroupModel.json");
        ReportBody reportBody = JsonHandler.getFromInputStream(stream, ReportBody.class);
        // When
        // Then
        assertThatCode(() ->
            batchReportServiceImpl
                .appendEliminationActionObjectGroupReport(PROCESS_ID, reportBody.getEntries(), TENANT_ID))
            .doesNotThrowAnyException();
    }

    @Test
    public void should_append_elimination_unit_report() throws Exception {
        // Given
        InputStream stream = getClass().getResourceAsStream("/eliminationObjectGroupModel.json");
        ReportBody reportBody = JsonHandler.getFromInputStream(stream, ReportBody.class);
        // When
        // Then
        assertThatCode(() ->
            batchReportServiceImpl.appendEliminationActionUnitReport(PROCESS_ID, reportBody.getEntries(), TENANT_ID))
            .doesNotThrowAnyException();
    }

    @Test
    public void should_export_objectgroup_report() throws Exception {
        // Given
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);
        Document document = getObjectGroupDocument();
        File folder = this.folder.newFolder();
        Path report = Paths.get(folder.getAbsolutePath(), "objectGroupReport.json");
        initialiseMockWhenPutObjectInWorkspace(report);
        when(emptyMongoCursor.next()).thenReturn(document);
        when(emptyMongoCursor.next()).thenReturn(document);
        when(emptyMongoCursor.hasNext()).thenReturn(true).thenReturn(false);
        when(eliminationActionObjectGroupRepository
            .findCollectionByProcessIdTenant(PROCESS_ID, TENANT_ID))
            .thenReturn(emptyMongoCursor);
        when(workspaceClient.isExistingContainer(PROCESS_ID)).thenReturn(true);
        // When
        batchReportServiceImpl
            .exportEliminationActionObjectGroupReport(PROCESS_ID, "objectGroupReport.json", TENANT_ID);
        // Then
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(report.toFile())))) {
            while (reader.ready()) {
                String line = reader.readLine();
                assertThat(line).isNotNull();
                assertThat(line).contains(
                    "{\"id\":\"aeaaaaaaaafr5hk6abgeualfvd6jsniaaaap\",\"params\":{\"id\":\"aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq\",\"opi\":\"test\",\"objectGroupId\":\"12345687\",\"status\":\"DELETED\",\"deletedParentUnitIds\":[\"aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq\",\"aeaaaaaaaafr5hk6abgeualfvd6jsniaaaar\"]}}");
            }
        }
    }


    @Test
    public void should_export_unit_report() throws Exception {
        // Given
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);
        Document document = getUnitDocument();
        File folder = this.folder.newFolder();
        Path report = Paths.get(folder.getAbsolutePath(), "unitReport.json");
        initialiseMockWhenPutObjectInWorkspace(report);
        when(emptyMongoCursor.next()).thenReturn(document);
        when(emptyMongoCursor.next()).thenReturn(document);
        when(emptyMongoCursor.hasNext()).thenReturn(true).thenReturn(false);
        when(eliminationActionUnitRepository
            .findCollectionByProcessIdTenant(PROCESS_ID, TENANT_ID))
            .thenReturn(emptyMongoCursor);
        when(workspaceClient.isExistingContainer(PROCESS_ID)).thenReturn(true);
        // When
        batchReportServiceImpl
            .exportEliminationActionUnitReport(PROCESS_ID, "objectGroupReport.json", TENANT_ID);
        // Then
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(report.toFile())))) {
            while (reader.ready()) {
                String line = reader.readLine();
                assertThat(line).isNotNull();
                assertThat(line).contains(
                    "{\"id\":\"aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq\",\"params\":{\"id\":\"aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq\",\"opi\":\"test\",\"objectGroupId\":\"12345687\",\"unitId\":\"unitId\",\"status\":\"DESTROY\"}}");
            }
        }
    }

    @Test
    public void should_export_distinct_object_group_of_deleted_units() throws Exception {
        // Given
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);
        String objectGroupId = "aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq";
        File folder = this.folder.newFolder();
        Path report = Paths.get(folder.getAbsolutePath(), "distinct_objectgroup.json");
        initialiseMockWhenPutObjectInWorkspace(report);
        when(emptyMongoCursor.next()).thenReturn(objectGroupId);
        when(emptyMongoCursor.hasNext()).thenReturn(true).thenReturn(false);
        when(eliminationActionUnitRepository
            .distinctObjectGroupOfDeletedUnits(PROCESS_ID, TENANT_ID))
            .thenReturn(emptyMongoCursor);
        when(workspaceClient.isExistingContainer(PROCESS_ID)).thenReturn(true);
        // When
        batchReportServiceImpl.exportEliminationActionDistinctObjectGroupOfDeletedUnits(PROCESS_ID, "distinct_objectgroup_report.json",
            TENANT_ID);
        // Then
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(report.toFile())))) {
            while (reader.ready()) {
                String line = reader.readLine();
                assertThat(line).isNotNull();
            }
        }
    }

    private Document getUnitDocument() {
        Document document = new Document();
        document.put("id", "aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq");
        document.put("distribGroup", null);
        document.put("params", JsonHandler.createObjectNode()
            .put("id", "aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq")
            .put("opi", "test")
            .put("objectGroupId", "12345687")
            .put("unitId", "unitId")
            .put("status", "DESTROY"));
        return document;
    }

    private Document getObjectGroupDocument() throws InvalidParseOperationException {
        Document document = new Document();
        document.put("id", "aeaaaaaaaafr5hk6abgeualfvd6jsniaaaap");
        document.put("distribGroup", null);
        document.put("params", JsonHandler.createObjectNode()
            .put("id", "aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq")
            .put("opi", "test")
            .put("objectGroupId", "12345687")
            .put("status", "DELETED")
            .putPOJO("deletedParentUnitIds",
                Arrays.asList("aeaaaaaaaafr5hk6abgeualfvd6jsniaaaaq", "aeaaaaaaaafr5hk6abgeualfvd6jsniaaaar"))
        );
        return document;
    }

    private void initialiseMockWhenPutObjectInWorkspace(Path report) throws ContentAddressableStorageServerException {
        doAnswer(invocation -> {
            InputStream argumentAt = invocation.getArgumentAt(2, InputStream.class);
            Files.copy(argumentAt, report);
            return null;
        }).when(workspaceClient).putObject(anyString(), anyString(), any(InputStream.class));
    }
}
