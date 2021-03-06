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
package fr.gouv.vitam.batch.report.rest.server;

import com.fasterxml.jackson.jaxrs.base.JsonParseExceptionMapper;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import fr.gouv.vitam.batch.report.rest.repository.EliminationActionObjectGroupRepository;
import fr.gouv.vitam.batch.report.rest.repository.EliminationActionUnitRepository;
import fr.gouv.vitam.batch.report.rest.resource.BatchReportResource;
import fr.gouv.vitam.batch.report.rest.service.BatchReportServiceImpl;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.database.server.mongodb.MongoDbAccess;
import fr.gouv.vitam.common.database.server.mongodb.SimpleMongoDBAccess;
import fr.gouv.vitam.common.server.HeaderIdContainerFilter;
import fr.gouv.vitam.common.serverv2.ConfigurationApplication;
import fr.gouv.vitam.common.serverv2.application.CommonBusinessApplication;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

import javax.servlet.ServletConfig;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static fr.gouv.vitam.common.serverv2.application.ApplicationParameter.CONFIGURATION_FILE_APPLICATION;

/**
 * BusinessApplication
 */
public class BusinessApplication extends ConfigurationApplication {

    private final CommonBusinessApplication commonBusinessApplication;

    private Set<Object> singletons;

    /**
     * Constructor
     *
     * @param servletConfig
     */
    public BusinessApplication(@Context ServletConfig servletConfig) {
        String configurationFile = servletConfig.getInitParameter(CONFIGURATION_FILE_APPLICATION);

        singletons = new HashSet<>();

        try (final InputStream yamlIS = PropertiesUtils.getConfigAsStream(configurationFile)) {
            final BatchReportConfiguration configuration =
                PropertiesUtils.readYaml(yamlIS, BatchReportConfiguration.class);

            MongoClientOptions mongoClientOptions = VitamCollection.getMongoClientOptions();
            MongoClient mongoClient = MongoDbAccess.createMongoClient(configuration, mongoClientOptions);
            SimpleMongoDBAccess mongoDbAccess = new SimpleMongoDBAccess(mongoClient, configuration.getDbName());

            EliminationActionUnitRepository eliminationActionUnitRepository =
                new EliminationActionUnitRepository(mongoDbAccess);
            EliminationActionObjectGroupRepository eliminationActionObjectGroupRepository =
                new EliminationActionObjectGroupRepository(mongoDbAccess);
            WorkspaceClientFactory.changeMode(configuration.getWorkspaceUrl());
            WorkspaceClientFactory workspaceClientFactory = WorkspaceClientFactory.getInstance();
            BatchReportServiceImpl batchReportServiceImpl =
                new BatchReportServiceImpl(eliminationActionUnitRepository, eliminationActionObjectGroupRepository,
                    workspaceClientFactory);

            commonBusinessApplication = new CommonBusinessApplication();
            singletons.addAll(commonBusinessApplication.getResources());
            singletons.add(new BatchReportResource(batchReportServiceImpl));
            singletons.add(new HeaderIdContainerFilter());
            singletons.add(new JsonParseExceptionMapper());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<Class<?>> getClasses() {
        return commonBusinessApplication.getClasses();
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}
