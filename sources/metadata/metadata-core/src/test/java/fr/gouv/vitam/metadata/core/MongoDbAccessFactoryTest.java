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
package fr.gouv.vitam.metadata.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mongodb.MongoClient;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;

import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.metadata.api.config.MetaDataConfiguration;
import fr.gouv.vitam.metadata.core.database.collections.ElasticsearchAccessMetadata;
import fr.gouv.vitam.metadata.core.database.collections.MongoDbAccessMetadataImpl;


public class MongoDbAccessFactoryTest {

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private final static String CLUSTER_NAME = "vitam-cluster";
    private final static String HOST_NAME = "127.0.0.1";

    private static List<ElasticsearchNode> nodes;

    private static final String DATABASE_HOST = "localhost";
    static MongoDbAccessMetadataImpl mongoDbAccess;
    static MongodExecutable mongodExecutable;
    static MongodProcess mongod;
    private static ElasticsearchTestConfiguration config = null;
    static final int tenantId = 0;
    static final List tenantList = new ArrayList() {
        {
            add(tenantId);
        }
    };

    private static ElasticsearchAccessMetadata esClient;

    @Rule
    public MongoRule mongoRule =
        new MongoRule(MongoDbAccessMetadataImpl.getMongoClientOptions(), "vitam-test", "Unit", "ObjectGroup");

    private MongoClient mongoClient = mongoRule.getMongoClient();

    @BeforeClass
    public static void beforeClass() throws Exception {
        try {
            config = JunitHelper.startElasticsearchForTest(tempFolder, CLUSTER_NAME);
        } catch (final VitamApplicationServerException e1) {
            assumeTrue(false);
        }

        nodes = new ArrayList<>();
        nodes.add(new ElasticsearchNode(HOST_NAME, config.getTcpPort()));
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        final List<ElasticsearchNode> nodes = new ArrayList<>();
        nodes.add(new ElasticsearchNode(HOST_NAME, config.getTcpPort()));

        esClient = new ElasticsearchAccessMetadata(CLUSTER_NAME, nodes);
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDown() throws Exception {
        if (config == null) {
            return;
        }

        JunitHelper.stopElasticsearchForTest(config);
        esClient.close();
    }

    @Test
    public void testCreateFn() {
        final List<MongoDbNode> mongo_nodes = new ArrayList<>();
        mongo_nodes.add(new MongoDbNode(DATABASE_HOST, mongoClient.getAddress().getPort()));
        final MetaDataConfiguration config = new MetaDataConfiguration(mongo_nodes, "vitam-test", CLUSTER_NAME, nodes);
        VitamConfiguration.setTenants(tenantList);
        mongoDbAccess = MongoDbAccessMetadataFactory.create(config);
        assertNotNull(mongoDbAccess);
        assertEquals("vitam-test", mongoDbAccess.getMongoDatabase().getName());
        mongoDbAccess.close();
    }
}
