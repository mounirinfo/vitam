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

package fr.gouv.vitam.worker.core.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.client.ClientMockResultHelper;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.ArchiveUnitProfileModel;
import fr.gouv.vitam.common.model.administration.ArchiveUnitProfileStatus;
import fr.gouv.vitam.common.model.processing.IOParameter;
import fr.gouv.vitam.common.model.processing.ProcessingUri;
import fr.gouv.vitam.common.model.processing.UriPrefix;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static fr.gouv.vitam.common.json.SchemaValidationStatus.SchemaValidationStatusEnum.NOT_AU_JSON_VALID;
import static fr.gouv.vitam.common.json.SchemaValidationStatus.SchemaValidationStatusEnum.RULE_DATE_FORMAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({WorkspaceClientFactory.class, AdminManagementClientFactory.class})
public class CheckArchiveUnitProfileActionPluginTest {

    private CheckArchiveUnitProfileActionPlugin plugin;
    private WorkspaceClient workspaceClient;
    private WorkspaceClientFactory workspaceClientFactory;
    private AdminManagementClient adminManagementClient;
    private AdminManagementClientFactory adminManagementClientFactory;

    private static final String ARCHIVE_UNIT = "checkArchiveUnitProfileActionPlugin/archive-unit_OK.json";
    private static final String ARCHIVE_UNIT_INVALID = "checkArchiveUnitProfileActionPlugin/archive-unit_Invalid.json";
    private static final String ARCHIVE_UNIT_INVALID_DATE =
        "checkArchiveUnitProfileActionPlugin/archive-unit_Invalid_date.json";
    private static final String ARCHIVE_UNIT_INVALID_RULE_START_DATE =
            "checkArchiveUnitProfileActionPlugin/archive-unit_KO_Rule_StartDate.json";
    private static final String ARCHIVE_UNIT_INVALID_XML =
        "checkArchiveUnitProfileActionPlugin/archive-unit_Invalid.xml";
    private static final String ARCHIVE_UNIT_INVALID_DESCRIPTION =
            "checkArchiveUnitProfileActionPlugin/archive-unit_KO_DescriptionLevel.json";
    private static final String ARCHIVE_UNIT_FINAL = "checkArchiveUnitProfileActionPlugin/archive-unit_OK_final.json";

    private static final String ARCHIVE_UNIT_SCHEMA =
            "checkArchiveUnitProfileActionPlugin/archive-unit-schema.json";
    private static final String ARCHIVE_UNIT_SCHEMA_DESCRIPTION =
            "checkArchiveUnitProfileActionPlugin/archive-unit-schema-with-mandatory-description.json";
    private static final String ARCHIVE_UNIT_SCHEMA_CUSTOM_DESCRIPTION_LEVEL =
                "checkArchiveUnitProfileActionPlugin/archive-unit-schema-description-level.json";
    private static final String ARCHIVE_UNIT_SCHEMA_CUSTOM_START_DATE =
            "checkArchiveUnitProfileActionPlugin/archive-unit-schema-startdate-format.json";
    private static final String GUID_MAP_JSON = "GUID_TO_ARCHIVE_ID_MAP.json";


    private final InputStream archiveUnit;
    private final InputStream archiveUnitFinal;

    private final InputStream archiveUnitInvalid;
    private final InputStream archiveUnitInvalidDate;
    private final InputStream archiveUnitInvalidRuleStartDate;
    private final InputStream archiveUnitInvalidDescription;
    private final InputStream archiveUnitInvalidXml;
    private final InputStream archiveUnitSchema;
    private final InputStream archiveUnitSchemaWithDescription;
    private final InputStream archiveUnitSchemaCustomDescriptionLevel;
    private final InputStream archiveUnitSchemaCustomStartDate;

    private List<IOParameter> out;
    private List<IOParameter> in;

    private HandlerIOImpl handlerIO;
    private GUID guid = GUIDFactory.newGUID();

    private final WorkerParameters params =
        WorkerParametersFactory.newWorkerParameters().setUrlWorkspace("http://localhost:8083")
            .setUrlMetadata("http://localhost:8083")
            .setObjectNameList(Lists.newArrayList("archiveUnit.json"))
            .setObjectName("archiveUnit.json").setCurrentStep("currentStep")
            .setContainerName(guid.getId()).setLogbookTypeProcess(LogbookTypeProcess.INGEST);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();


    public CheckArchiveUnitProfileActionPluginTest() throws FileNotFoundException {
        archiveUnit = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT);
        archiveUnitFinal = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_FINAL);
        archiveUnitInvalid = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_INVALID);
        archiveUnitInvalidDate = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_INVALID_DATE);
        archiveUnitInvalidXml = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_INVALID_XML);
        archiveUnitInvalidRuleStartDate = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_INVALID_RULE_START_DATE);
        archiveUnitInvalidDescription = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_INVALID_DESCRIPTION);
        archiveUnitSchema = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_SCHEMA);
        archiveUnitSchemaWithDescription = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_SCHEMA_DESCRIPTION);
        archiveUnitSchemaCustomDescriptionLevel = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_SCHEMA_CUSTOM_DESCRIPTION_LEVEL);
        archiveUnitSchemaCustomStartDate = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_SCHEMA_CUSTOM_START_DATE);
    }

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(WorkspaceClientFactory.class);
        workspaceClient = mock(WorkspaceClient.class);
        workspaceClientFactory = mock(WorkspaceClientFactory.class);
        PowerMockito.when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        PowerMockito.when(WorkspaceClientFactory.getInstance().getClient()).thenReturn(workspaceClient);

        PowerMockito.mockStatic(AdminManagementClientFactory.class);
        adminManagementClient = mock(AdminManagementClient.class);
        adminManagementClientFactory = mock(AdminManagementClientFactory.class);
        PowerMockito.when(AdminManagementClientFactory.getInstance()).thenReturn(adminManagementClientFactory);
        PowerMockito.when(AdminManagementClientFactory.getInstance().getClient()).thenReturn(adminManagementClient);

        plugin = new CheckArchiveUnitProfileActionPlugin(adminManagementClientFactory);

        String objectId = "objectId";
        handlerIO = new HandlerIOImpl(guid.getId(), "workerId", com.google.common.collect.Lists.newArrayList(objectId));
        handlerIO.setCurrentObjectId(objectId);

        out = new ArrayList<>();
        out.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.MEMORY, "unitId.json")));
        handlerIO.addOutIOParameters(out);

        in = new ArrayList<>();
        in.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.WORKSPACE, "Maps/GUID_TO_ARCHIVE_ID_MAP.json")));
        final InputStream guidMapInfo =
                PropertiesUtils.getResourceAsStream(GUID_MAP_JSON);
        when(workspaceClient.getObject(anyObject(), eq("Maps/GUID_TO_ARCHIVE_ID_MAP.json")))
                .thenReturn(Response.status(Status.OK).entity(guidMapInfo).build());
        handlerIO.addInIOParameters(in);

        File tempFolder = temporaryFolder.newFolder();
        SystemPropertyUtil.set("vitam.tmp.folder", tempFolder.getAbsolutePath());
    }

    @After
    public void clean() {
        handlerIO.partialClose();
    }

    @Test
    public void givenWorkspaceNotExistWhenExecuteThenReturnResponseFATAL() throws ProcessingException {
        final WorkspaceClientFactory mockedWorkspaceFactory = mock(WorkspaceClientFactory.class);
        PowerMockito.when(WorkspaceClientFactory.getInstance()).thenReturn(mockedWorkspaceFactory);
        PowerMockito.when(mockedWorkspaceFactory.getClient()).thenReturn(workspaceClient);

        final ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.FATAL);
    }

    @Test
    public void givenCorrectArchiveUnitJsonWhenExecuteThenReturnResponseOK() throws Exception {
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
                .thenReturn(Response.status(Status.OK).entity(archiveUnit).build());
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
                .thenReturn(createArchiveUnitProfile(archiveUnitSchema));
        final ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);
    }

    @Test
    public void givenFinalArchiveUnitJsonWhenExecuteThenReturnResponseOK() throws Exception {
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
            .thenReturn(Response.status(Status.OK).entity(archiveUnitFinal).build());
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
                .thenReturn(createArchiveUnitProfile(archiveUnitSchema));
        final ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);
    }

    @Test
    public void givenInvalidArchiveUnitJsonWhenExecuteThenReturnResponseKO() throws Exception {
        // Invalid archive unit -> missing title in it
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
            .thenReturn(Response.status(Status.OK).entity(archiveUnitInvalid).build());
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
            .thenReturn(createArchiveUnitProfile(archiveUnitSchema));
        final ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
        assertThat(response.getGlobalOutcomeDetailSubcode()).isEqualTo(NOT_AU_JSON_VALID.name());
    }

    @Test
    public void givenInvalidArchiveUnitXMLWhenExecuteThenReturnResponseKO() throws Exception {
        // Invalid archive unit -> XML File
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
            .thenReturn(Response.status(Status.OK).entity(archiveUnitInvalidXml).build());
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
                .thenReturn(createArchiveUnitProfile(archiveUnitSchema));
        final ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
        assertTrue(response.getGlobalOutcomeDetailSubcode().equals(
                CheckArchiveUnitProfileActionPlugin.CheckArchiveUnitProfileSchemaStatus.INVALID_UNIT.name()));
    }

    @Test
    public void givenInvalidDateArchiveUnitJsonWhenExecuteThenReturnResponseKO() throws Exception {
        // Schema only allowing dates with time (hours/min/sec)
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
                .thenReturn(Response.status(Status.OK).entity(archiveUnitInvalidRuleStartDate).build());
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
                .thenReturn(createArchiveUnitProfile(archiveUnitSchemaCustomStartDate));
        ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
        assertTrue(response.getGlobalOutcomeDetailSubcode().equals(RULE_DATE_FORMAT.name()));
    }

    @Test
    public void givenInvalidAccessRuleDateArchiveUnitJsonWhenExecuteThenReturnResponseKO() throws Exception {
        // Year is greater than 9000
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
                .thenReturn(Response.status(Status.OK).entity(archiveUnitInvalidDate).build());
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
                .thenReturn(createArchiveUnitProfile(archiveUnitSchemaCustomStartDate));
        final ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
        assertTrue(response.getGlobalOutcomeDetailSubcode().equals(RULE_DATE_FORMAT.name()));
    }

    @Test
    public void givenNoDescriptionArchiveUnitJsonWhenExecuteThenReturnResponseKO() throws Exception {
        // archive unit has no description when schema requires one
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
            .thenReturn(Response.status(Status.OK).entity(archiveUnit).build());
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
                .thenReturn(createArchiveUnitProfile(archiveUnitSchemaWithDescription));

        final ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
        assertThat(response.getGlobalOutcomeDetailSubcode()).isEqualTo(NOT_AU_JSON_VALID.name());
    }

    @Test
    public void givenInvalidDescriptionLevelInArchiveUnitJsonWhenExecuteThenReturnResponseKO() throws Exception {
        // archive unit has no description when schema requires one
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
                .thenReturn(Response.status(Status.OK).entity(archiveUnitInvalidDescription).build());
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
                .thenReturn(createArchiveUnitProfile(archiveUnitSchemaCustomDescriptionLevel));

        final ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
        assertThat(response.getGlobalOutcomeDetailSubcode()).isEqualTo(NOT_AU_JSON_VALID.name());
    }

    @Test
    public void givenInactiveOrEmptySchemaControlAUProfileInArchiveUnitJsonWhenExecuteThenReturnResponseKO() throws Exception {
        // archive unit has no description when schema requires one
        when(workspaceClient.getObject(anyObject(), eq("Units/archiveUnit.json")))
            .thenReturn(Response.status(Status.OK).entity(archiveUnit).build());
        //Inactive Status
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
            .thenReturn(createCustomArchiveUnitProfile(archiveUnitSchema, ArchiveUnitProfileStatus.INACTIVE, CtrlSchemaValueSetterFlagEnum.NOT_SET));

        ItemStatus response = plugin.execute(params, handlerIO);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertThat(response.getGlobalOutcomeDetailSubcode())
            .isEqualTo(CheckArchiveUnitProfileActionPlugin.CheckArchiveUnitProfileSchemaStatus.INACTIVE_STATUS.name());

        //Empty control schema
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
            .thenReturn(createCustomArchiveUnitProfile(archiveUnitSchema, ArchiveUnitProfileStatus.ACTIVE, CtrlSchemaValueSetterFlagEnum.NOT_SET));

        response = plugin.execute(params, handlerIO);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertThat(response.getGlobalOutcomeDetailSubcode())
            .isEqualTo(CheckArchiveUnitProfileActionPlugin.CheckArchiveUnitProfileSchemaStatus.EMPTY_CONTROL_SCHEMA.name());

        //JSon Empty control schema
        InputStream archiveUnitSchemaBis = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_SCHEMA);
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
           .thenReturn(createCustomArchiveUnitProfile(archiveUnitSchemaBis, ArchiveUnitProfileStatus.ACTIVE, CtrlSchemaValueSetterFlagEnum.SET_AS_EMPTY_JSON));

        response = plugin.execute(params, handlerIO);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertThat(response.getGlobalOutcomeDetailSubcode())
            .isEqualTo(CheckArchiveUnitProfileActionPlugin.CheckArchiveUnitProfileSchemaStatus.EMPTY_CONTROL_SCHEMA.name());

        //All OK
        archiveUnitSchemaBis = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_SCHEMA);
        when(adminManagementClient.findArchiveUnitProfiles(anyObject()))
            .thenReturn(createCustomArchiveUnitProfile(archiveUnitSchemaBis, ArchiveUnitProfileStatus.ACTIVE, CtrlSchemaValueSetterFlagEnum.SET));

        response = plugin.execute(params, handlerIO);
        assertEquals(StatusCode.OK, response.getGlobalStatus());
    }

    private RequestResponse createArchiveUnitProfile(InputStream schema) throws InvalidParseOperationException {
        ArchiveUnitProfileModel archiveUnitProfileModel = new ArchiveUnitProfileModel();
        archiveUnitProfileModel.setIdentifier("AUP_0001");
        archiveUnitProfileModel.setId(GUIDFactory.newProfileGUID(0).toString());

        JsonNode node = JsonHandler.getFromInputStream(schema);
        archiveUnitProfileModel.setControlSchema(JsonHandler.unprettyPrint(node));
        return ClientMockResultHelper.createReponse(archiveUnitProfileModel);
    }

    private RequestResponse createCustomArchiveUnitProfile(InputStream schema, ArchiveUnitProfileStatus aupStatus, CtrlSchemaValueSetterFlagEnum ctrlSchemaSetterFlag)
        throws InvalidParseOperationException, IOException {
        ArchiveUnitProfileModel archiveUnitProfileModel = new ArchiveUnitProfileModel();
        archiveUnitProfileModel.setIdentifier("AUP_0001");
        archiveUnitProfileModel.setId(GUIDFactory.newProfileGUID(0).toString());
        archiveUnitProfileModel.setStatus(aupStatus);

        if(ctrlSchemaSetterFlag.SET.equals(ctrlSchemaSetterFlag)) {
            JsonNode node = JsonHandler.getFromInputStream(schema);
            archiveUnitProfileModel.setControlSchema(JsonHandler.unprettyPrint(node));
        }

        if(ctrlSchemaSetterFlag.SET_AS_EMPTY_JSON.equals(ctrlSchemaSetterFlag)) {
            archiveUnitProfileModel.setControlSchema(new String("{}"));
        }

        return ClientMockResultHelper.createReponse(archiveUnitProfileModel);
    }

    private enum  CtrlSchemaValueSetterFlagEnum {
        SET,
        NOT_SET,
        SET_AS_EMPTY_JSON
    };
}
