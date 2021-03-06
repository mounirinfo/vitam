/*******************************************************************************
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
package fr.gouv.vitam.processing.management.rest;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.client.VitamClientFactory;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessAction;
import fr.gouv.vitam.common.model.ProcessPause;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.processing.common.ProcessingEntry;
import fr.gouv.vitam.processing.common.config.ServerConfiguration;
import fr.gouv.vitam.processing.data.core.management.WorkspaceProcessDataManagement;
import fr.gouv.vitam.processing.distributor.v2.ProcessDistributorImpl;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;

import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({WorkspaceClientFactory.class, WorkspaceProcessDataManagement.class})
public class ProcessManagementResourceTest {

    private static final String CONF_FILE_NAME = "processing.conf";

    private static final String DATA_URI = "/processing/v1";
    private static final String NOT_EXITS_WORKFLOW_ID = "workflowJSONv3";
    private static final String EXITS_WORKFLOW_ID = "PROCESS_SIP_UNITARY";
    private static final String CONTAINER_NAME = "sipContainer";
    private static final String OPERATION_URI = "/operations";
    private static final String WORKFLOWS_URI = "/workflows";
    private static final String OPERATION_ID_URI = "/operations/xyz";
    private static final String FORCE_PAUSE_URI = "/forcepause";
    private static final String REMOVE_FORCE_PAUSE_URI = "/removeforcepause";
    private static final String ID = "identifier4";
    private static JunitHelper junitHelper;
    private static int port;
    private static ProcessManagementMain application = null;
    private static final Integer TENANT_ID = 0;

    private static final String CONTEXT_ID = "DEFAULT_WORKFLOW";
    private static WorkspaceClient workspaceClient;
    private static WorkspaceProcessDataManagement processDataManagement;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        LogbookOperationsClientFactory.changeMode(null);
        junitHelper = JunitHelper.getInstance();
        port = junitHelper.findAvailablePort();

        PowerMockito.mockStatic(WorkspaceProcessDataManagement.class);
        processDataManagement = PowerMockito.mock(WorkspaceProcessDataManagement.class);
        PowerMockito.when(WorkspaceProcessDataManagement.getInstance()).thenReturn(processDataManagement);
        PowerMockito.when(processDataManagement.getProcessWorkflowFor(Matchers.eq(1), Matchers.anyString()))
            .thenReturn(new HashMap<>());
        application = new ProcessManagementMain(CONF_FILE_NAME);
        application.start();
        RestAssured.port = port;
        RestAssured.basePath = DATA_URI;

    }

    @AfterClass
    public static void shutdownAfterClass() {
        try {
            if (application != null) {
                application.stop();
            }
        } catch (final Exception e) {
        }
        junitHelper.releasePort(port);
        VitamClientFactory.resetConnections();
    }

    /**
     * Test server status should return 200
     */
    @Test
    public void shouldGetStatusReturnNoContent() throws Exception {
        get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());
    }

    @Ignore
    @Test
    public void shouldReturnResponseAccepted() throws Exception {

        WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        mockStatic(WorkspaceClientFactory.class);
        WorkspaceClient workspaceClient = mock(WorkspaceClient.class);
        when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);

        Mockito.when(workspaceClient.getListUriDigitalObjectFromFolder(anyObject(), anyObject()))
            .thenReturn(new RequestResponseOK().addResult(Collections.<URI>emptyList()));

        // Mock ProcessDistributorImplFactory + ProcessDistributorImpl + Worker response
        ProcessDistributorImpl processDistributorImpl = Mockito.mock(ProcessDistributorImpl.class);

        ItemStatus itemStatus = new ItemStatus();
        itemStatus.increment(StatusCode.OK);

        Mockito.when(processDistributorImpl.distribute(anyObject(), anyObject(), anyObject(), anyObject()))
            .thenReturn(itemStatus);


        final GUID processId = GUIDFactory.newGUID();
        final String operationByIdURI = OPERATION_URI + "/" + processId.getId();

        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.INIT.getValue(),
                GlobalDataRest.X_REQUEST_ID, processId.toString(), GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(new ProcessingEntry(processId.getId(), EXITS_WORKFLOW_ID)).when()
            .post(operationByIdURI).then()
            .statusCode(Status.CREATED.getStatusCode());
        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.RESUME.getValue(),
                GlobalDataRest.X_REQUEST_ID, processId.toString(), GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(new ProcessingEntry(processId.getId(), EXITS_WORKFLOW_ID)).when()
            .post(operationByIdURI).then()
            .statusCode(Status.ACCEPTED.getStatusCode());
    }

    /**
     * Test with an empty step in the workflow
     *
     * @throws Exception
     */
    @Test
    public void shouldReturnPreconditionFailedIfNotStepFound() throws Exception {
        WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        mockStatic(WorkspaceClientFactory.class);
        WorkspaceClient workspaceClient = mock(WorkspaceClient.class);
        when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);

        Mockito.when(workspaceClient.getListUriDigitalObjectFromFolder(anyObject(), anyObject()))
            .thenReturn(new RequestResponseOK().addResult(Collections.<URI>emptyList()));

        final GUID processId = GUIDFactory.newGUID();
        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.INIT.getValue(),
                GlobalDataRest.X_REQUEST_ID, processId.toString(), GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(new ProcessingEntry(CONTAINER_NAME, NOT_EXITS_WORKFLOW_ID)).when()
            .post(OPERATION_ID_URI).then()
            .statusCode(Status.CREATED.getStatusCode());
        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.RESUME.getValue(),
                GlobalDataRest.X_REQUEST_ID, processId.toString(), GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(new ProcessingEntry(CONTAINER_NAME, NOT_EXITS_WORKFLOW_ID)).when()
            .post(OPERATION_ID_URI).then()
            .statusCode(Status.PRECONDITION_FAILED.getStatusCode());
    }

    /**
     * Try to resume an already running process
     *
     * @throws Exception
     */
    @Ignore
    @Test
    public void shouldReturnResponseUauthorized() throws Exception {

        WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        mockStatic(WorkspaceClientFactory.class);
        WorkspaceClient workspaceClient = mock(WorkspaceClient.class);
        when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);

        Mockito.when(workspaceClient.getListUriDigitalObjectFromFolder(anyObject(), anyObject()))
            .thenReturn(new RequestResponseOK().addResult(Collections.<URI>emptyList()));

        // Mock ProcessDistributorImplFactory + ProcessDistributorImpl + Worker response

        ProcessDistributorImpl processDistributorImpl = Mockito.mock(ProcessDistributorImpl.class);

        ItemStatus itemStatus = new ItemStatus();
        itemStatus.increment(StatusCode.OK);

        Mockito.when(processDistributorImpl.distribute(anyObject(), anyObject(), anyObject(), anyObject()))
            .thenReturn(itemStatus);


        final GUID processId = GUIDFactory.newGUID();
        final String operationByIdURI = OPERATION_URI + "/" + processId.getId();

        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.INIT.getValue(),
                GlobalDataRest.X_REQUEST_ID, processId.toString(), GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(new ProcessingEntry(processId.getId(), EXITS_WORKFLOW_ID)).when()
            .post(operationByIdURI).then()
            .statusCode(Status.CREATED.getStatusCode());
        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.RESUME.getValue(),
                GlobalDataRest.X_REQUEST_ID, processId.toString(), GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(new ProcessingEntry(processId.getId(), EXITS_WORKFLOW_ID)).when()
            .post(operationByIdURI).then()
            .statusCode(Status.ACCEPTED.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.RESUME.getValue(),
                GlobalDataRest.X_REQUEST_ID, processId.toString(), GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(new ProcessingEntry(processId.getId(), EXITS_WORKFLOW_ID)).when()
            .post(operationByIdURI).then()
            .statusCode(Status.UNAUTHORIZED.getStatusCode());
    }

    @Test
    public void shouldReturnResponseNOTFOUNDIfheadWorkflowByIdNotFound() throws Exception {
        given()
            .contentType(ContentType.JSON).when()
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.PAUSE.getValue(),
                GlobalDataRest.X_REQUEST_ID, ID, GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .head(OPERATION_ID_URI).then()
            .statusCode(Status.NOT_FOUND.getStatusCode());
    }

    /**
     * Cancel an not existing process workflow
     *
     * @throws Exception
     */
    @Test
    public void shouldReturnPreconditionFaildIfcancelWorkflowById() throws Exception {
        WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        mockStatic(WorkspaceClientFactory.class);
        WorkspaceClient workspaceClient = mock(WorkspaceClient.class);
        when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);

        Mockito.when(workspaceClient.getListUriDigitalObjectFromFolder(anyObject(), anyObject()))
            .thenReturn(new RequestResponseOK().addResult(Collections.<URI>emptyList()));

        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_CONTEXT_ID, CONTEXT_ID, GlobalDataRest.X_ACTION, ProcessAction.PAUSE.getValue(),
                GlobalDataRest.X_REQUEST_ID, ID, GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(ID).when()
            .delete(OPERATION_ID_URI).then()
            .statusCode(Status.PRECONDITION_FAILED.getStatusCode());
    }

    /**
     * Get the workflow definitions
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnOkWhenGetWorkflowDefinitions() throws Exception {
        WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        mockStatic(WorkspaceClientFactory.class);
        WorkspaceClient workspaceClient = mock(WorkspaceClient.class);
        when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);

        Mockito.when(workspaceClient.getListUriDigitalObjectFromFolder(anyObject(), anyObject()))
            .thenReturn(new RequestResponseOK().addResult(Collections.<URI>emptyList()));

        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_REQUEST_ID, ID, GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().get(WORKFLOWS_URI).then()
            .statusCode(Status.OK.getStatusCode()).assertThat()
            .body(containsString(EXITS_WORKFLOW_ID));

    }



    /**
     * Apply forced pause tests
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnGoodStatusWhenForcePause() throws Exception {
        ProcessPause pause = new ProcessPause(null, 0, null);
        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(pause).when()
            .post(FORCE_PAUSE_URI)
            .then().assertThat()
            .statusCode(Status.OK.getStatusCode());

        //error when both tenant and type are null
        pause.setTenant(null);
        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(pause).when()
            .post(FORCE_PAUSE_URI)
            .then().assertThat()
            .statusCode(Status.BAD_REQUEST.getStatusCode())
            .body(containsString("be null"));

        pause.setType("INGEST");
        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(pause).when()
            .post(FORCE_PAUSE_URI)
            .then().assertThat()
            .statusCode(Status.OK.getStatusCode());


        //Inexisting type
        pause.setType("BAD_INGEST");
        given()
            .contentType(ContentType.JSON)
            .headers(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(pause).when()
            .post(FORCE_PAUSE_URI)
            .then().assertThat()
            .statusCode(Status.BAD_REQUEST.getStatusCode())
            .body(containsString("is not a valid process type"));
        ;
    }
}
