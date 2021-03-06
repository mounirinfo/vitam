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
package fr.gouv.vitam.metadata.rest;

import static com.jayway.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import fr.gouv.vitam.common.client.VitamClientFactory;
import org.jhades.JHades;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.jayway.restassured.RestAssured;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.metadata.api.config.MetaDataConfiguration;
import fr.gouv.vitam.metadata.core.database.collections.MetadataCollections;
import fr.gouv.vitam.metadata.core.database.collections.ObjectGroup;
import fr.gouv.vitam.metadata.core.database.collections.Unit;

/**
 * MetadataRawResource test
 */
public class MetadataRawResourceTest {

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    private static final String METADATA_URI = "/metadata/v1";
    private static final String DATABASE_NAME = "vitam-test";
    private static final String JETTY_CONFIG = "jetty-config-test.xml";
    private static MongodExecutable mongodExecutable;
    static MongodProcess mongod;

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private final static String CLUSTER_NAME = "vitam-cluster";
    private final static String HOST_NAME = "127.0.0.1";

    private static final String SERVER_HOST = "localhost";
    private static JunitHelper junitHelper;
    private static int dataBasePort;
    private static int serverPort;

    private static MetadataMain application;
    private static ElasticsearchTestConfiguration config = null;
    static final int tenantId = 0;
    static final List tenantList = Lists.newArrayList(tenantId);
    private static final Integer TENANT_ID = 0;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // Identify overlapping in particular jsr311
        new JHades().overlappingJarsReport();
        // ES
        try {
            config = JunitHelper.startElasticsearchForTest(tempFolder, CLUSTER_NAME);
        } catch (final VitamApplicationServerException e1) {
            assumeTrue(false);
        }
        junitHelper = JunitHelper.getInstance();

        final List<ElasticsearchNode> nodes = new ArrayList<>();
        nodes.add(new ElasticsearchNode(HOST_NAME, config.getTcpPort()));

        dataBasePort = junitHelper.findAvailablePort();

        final MongodStarter starter = MongodStarter.getDefaultInstance();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .withLaunchArgument("--enableMajorityReadConcern")
            .version(Version.Main.PRODUCTION)
            .net(new Net(dataBasePort, Network.localhostIsIPv6()))
            .build());
        mongod = mongodExecutable.start();

        final List<MongoDbNode> mongo_nodes = new ArrayList<>();
        mongo_nodes.add(new MongoDbNode(SERVER_HOST, dataBasePort));
        // TODO: using configuration file ? Why not ?
        final MetaDataConfiguration configuration =
            new MetaDataConfiguration(mongo_nodes, DATABASE_NAME, CLUSTER_NAME, nodes);
        configuration.setJettyConfig(JETTY_CONFIG);
        VitamConfiguration.setTenants(tenantList);
        serverPort = junitHelper.findAvailablePort();

        File configurationFile = tempFolder.newFile();

        PropertiesUtils.writeYaml(configurationFile, configuration);

        application = new MetadataMain(configurationFile.getAbsolutePath());
        application.start();
        JunitHelper.unsetJettyPortSystemProperty();

        RestAssured.port = serverPort;
        RestAssured.basePath = METADATA_URI;
    }

    @AfterClass
    public static void tearDownAfterClass() {
        if (config == null) {
            return;
        }
        JunitHelper.stopElasticsearchForTest(config);
        try {
            application.stop();
        } catch (final Exception e) {
            // ignore
        }
        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(dataBasePort);
        junitHelper.releasePort(serverPort);
        VitamClientFactory.resetConnections();
    }

    @Before
    public void before() {
        Assume.assumeTrue("Elasticsearch not started but should", config != null);
    }

    @After
    public void tearDown() {
        MetadataCollections.UNIT.getCollection().drop();
        MetadataCollections.OBJECTGROUP.getCollection().drop();
    }

    @RunWithCustomExecutor
    @Test
    public void should_find_objectgroup_on_getByIdRaw() throws Exception {

        String operationId = GUIDFactory.newOperationLogbookGUID(TENANT_ID).getId();
        String unitId = GUIDFactory.newUnitGUID(TENANT_ID).getId();
        String objectGroupId = GUIDFactory.newObjectGroupGUID(TENANT_ID).getId();
        ObjectNode objectGroup =
            (ObjectNode) JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream("objectgroup.json"));
        objectGroup.put("_id", objectGroupId);
        objectGroup.set("_ops", JsonHandler.createArrayNode().add(operationId));
        objectGroup.set("_up", JsonHandler.createArrayNode().add(unitId));
        MetadataCollections.OBJECTGROUP.getCollection().insertOne(new ObjectGroup(objectGroup));
        String reponseString = given()
            .contentType(MediaType.APPLICATION_JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when()
            .get("/raw/objectgroups/" + objectGroupId)
            .then().statusCode(Status.OK.getStatusCode()).extract().body().asString();

        JsonNode responseUnit = JsonHandler.getFromString(reponseString);
        assertThat(responseUnit.get("$results").get(0).get("_nbc").asLong()).isEqualTo(1L);
    }

    @RunWithCustomExecutor
    @Test
    public void should_not_find_objectgroup_on_getByIdRaw() throws Exception {

        String objectGroupId = GUIDFactory.newObjectGroupGUID(TENANT_ID).getId();
        given()
            .contentType(MediaType.APPLICATION_JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when()
            .get("/raw/objectgroups/" + objectGroupId).then()
            .statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @RunWithCustomExecutor
    @Test
    public void should_find_unit_on_getByIdRaw() throws Exception {

        String operationId = GUIDFactory.newOperationLogbookGUID(TENANT_ID).getId();
        String unitId = GUIDFactory.newUnitGUID(TENANT_ID).getId();
        String parentUnitId = GUIDFactory.newUnitGUID(TENANT_ID).getId();
        ObjectNode unit =
            (ObjectNode) JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream("unit.json"));
        unit.put("_id", unitId);
        unit.set("_ops", JsonHandler.createArrayNode().add(operationId));
        unit.set("_up", JsonHandler.createArrayNode().add(parentUnitId));
        unit.put("_nbc", 1L);
        MetadataCollections.UNIT.getCollection().insertOne(new Unit(unit));

        String reponseString = given()
            .contentType(MediaType.APPLICATION_JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when()
            .get("/raw/units/" + unitId)
            .then().statusCode(Status.OK.getStatusCode()).extract().body().asString();

        JsonNode responseUnit = JsonHandler.getFromString(reponseString);
        assertThat(responseUnit.get("$results").get(0).get("_nbc").asLong()).isEqualTo(1L);

    }

    @RunWithCustomExecutor
    @Test
    public void should_not_find_unit_on_getByIdRaw() throws Exception {

        String unitId = GUIDFactory.newUnitGUID(TENANT_ID).getId();
        given()
            .contentType(MediaType.APPLICATION_JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when()
            .get("/raw/units/" + unitId).then()
            .statusCode(Status.NOT_FOUND.getStatusCode());
    }
}
