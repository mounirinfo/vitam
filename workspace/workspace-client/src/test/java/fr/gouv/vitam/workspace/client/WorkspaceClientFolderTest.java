package fr.gouv.vitam.workspace.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.common.Entry;

public class WorkspaceClientFolderTest extends WorkspaceClientTest {

    private static final String CONTAINER_NAME = "myContainer";
    private static final String FOLDER_NAME = "myFolder";

    @Override
    protected Application configure() {
        set(TestProperties.LOG_TRAFFIC, true);
        set(TestProperties.DUMP_ENTITY, true);
        forceSet(TestProperties.CONTAINER_PORT, String.valueOf(PORT));

        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.register(JacksonFeature.class);
        mock = mock(ExpectedResults.class);
        resourceConfig.registerInstances(new MockFolderResource(mock));
        return resourceConfig;
    }

    @Path("workspace/v1/containers")
    public static class MockFolderResource {

        private ExpectedResults expectedResponse;

        public MockFolderResource(ExpectedResults expectedResponse) {
            this.expectedResponse = expectedResponse;
        }

        @POST
        @Path("{containerName}/folders")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response create(@PathParam("containerName") String containerName, Entry folder) {
            return expectedResponse.post();
        }

        @DELETE
        @Path("{containerName}/folders/{folderName}")
        public Response delete(@PathParam("containerName") String containerName,
            @PathParam("folderName") String folderName) {
            return expectedResponse.delete();
        }

        @HEAD
        @Path("{containerName}/folders/{folderName}")
        public Response containerExists(@PathParam("containerName") String containerName,
            @PathParam("folderName") String folderName) {

            return expectedResponse.head();
        }

        @GET
        @Path("{containerName}/folders/{folderName}")
        public Response getListUriDigitalObjectFromFolder(@PathParam("containerName") String containerName,
            @PathParam("folderName") String folderName) {
            return expectedResponse.get();
        }

    }

    // create
    @Test(expected = IllegalArgumentException.class)
    public void givenNullParamWhenCreateFolderThenRaiseAnException()throws Exception  {
        client.createFolder(CONTAINER_NAME, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenEmptyParamWhenCreateFolderThenRaiseAnException()throws Exception  {
        client.createFolder(CONTAINER_NAME, "");
    }

    @Test(expected = ContentAddressableStorageServerException.class)
    public void givenServerErrorWhenCreateFolderThenRaiseAnException() throws Exception {
        when(mock.post()).thenReturn(Response.status(Status.INTERNAL_SERVER_ERROR).build());
        client.createFolder(CONTAINER_NAME, FOLDER_NAME);

    }

    @Test(expected = ContentAddressableStorageAlreadyExistException.class)
    public void givenFolderAlreadyExistsWhenCreateFolderThenRaiseAnException() throws Exception {
        when(mock.post()).thenReturn(Response.status(Status.CONFLICT).build());
        client.createFolder(CONTAINER_NAME, FOLDER_NAME);
    }

    @Test
    public void givenFolderNotFoundWhenCreateFolderThenReturnCreated() throws Exception {
        when(mock.post()).thenReturn(Response.status(Status.CREATED).build());
        client.createFolder(CONTAINER_NAME, FOLDER_NAME);
        assertTrue(true);
    }

    // delete
    @Test(expected = IllegalArgumentException.class)
    public void givenNullParamWhenDeleteFolderThenRaiseAnException() throws Exception {
        client.deleteFolder(CONTAINER_NAME, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenEmptyParamWhenDeleteFolderThenRaiseAnException() throws Exception {
        client.deleteFolder(CONTAINER_NAME, "");
    }

    @Test(expected = ContentAddressableStorageServerException.class)
    public void givenServerErrorWhenDeleteFolderThenRaiseAnException() throws Exception {
        when(mock.delete()).thenReturn(Response.status(Status.INTERNAL_SERVER_ERROR).build());
        client.deleteFolder(CONTAINER_NAME, FOLDER_NAME);
    }

    @Test(expected = ContentAddressableStorageNotFoundException.class)
    public void givenFolderNotFoundWhenDeleteFolderThenRaiseAnException() throws Exception {
        when(mock.delete()).thenReturn(Response.status(Status.NOT_FOUND).build());
        client.deleteFolder(CONTAINER_NAME, FOLDER_NAME);
    }

    @Test
    public void givenFolderAlreadyExistsWhenDeleteFolderThenReturnNotContent()throws Exception  {
        when(mock.delete()).thenReturn(Response.status(Status.NO_CONTENT).build());
        client.deleteFolder(CONTAINER_NAME, FOLDER_NAME);
        assertTrue(true);
    }

    // check existence
    @Test(expected = IllegalArgumentException.class)
    public void givenNullParamWhenCheckFolderExistenceThenRaiseAnException() {
        client.folderExists(CONTAINER_NAME, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenEmptyParamWhenCheckFolderExistenceThenRaiseAnException() {
        client.folderExists(CONTAINER_NAME, "");
    }

    @Test
    public void givenFolderAlreadyExistsWhenCheckFolderExistenceThenReturnTrue() {
        when(mock.head()).thenReturn(Response.status(Status.OK).build());
        assertTrue(client.folderExists(CONTAINER_NAME, FOLDER_NAME));
    }

    @Test
    public void givenFolderAlreadyExistsWhenCheckFolderExistenceThenReturnFalse() {
        when(mock.head()).thenReturn(Response.status(Status.NOT_FOUND).build());
        assertFalse(client.folderExists(CONTAINER_NAME, FOLDER_NAME));
    }

    // get URI list
    @Test(expected = IllegalArgumentException.class)
    public void given_NullParam_When_FindingUriObjects_Then_RaiseAnException() {
        client.getListUriDigitalObjectFromFolder(CONTAINER_NAME, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void given_EmptyParam_When_FindingUriObjects_Then_RaiseAnException() {
        client.getListUriDigitalObjectFromFolder(CONTAINER_NAME, "");
    }

    @Test
    public void given_FolderAlreadyExists_When_FindingUriObjects_Then_ReturnList() {
        when(mock.get()).thenReturn(Response.status(Status.OK).entity(Collections.<URI>emptyList()).build());
        List<URI> uris = client.getListUriDigitalObjectFromFolder(CONTAINER_NAME, FOLDER_NAME);
        assertTrue(uris.isEmpty());
    }



}