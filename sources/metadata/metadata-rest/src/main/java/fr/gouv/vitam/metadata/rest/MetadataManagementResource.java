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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.api.VitamRepositoryProvider;
import fr.gouv.vitam.common.database.offset.OffsetRepository;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.AuthenticationLevel;
import fr.gouv.vitam.common.model.GraphComputeResponse;
import fr.gouv.vitam.common.model.GraphComputeResponse.GraphComputeAction;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.security.rest.VitamAuthentication;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.metadata.api.MetaData;
import fr.gouv.vitam.metadata.api.model.ReclassificationChildNodeExportRequest;
import fr.gouv.vitam.metadata.core.database.collections.MetadataCollections;
import fr.gouv.vitam.metadata.core.graph.GraphComputeServiceImpl;
import fr.gouv.vitam.metadata.core.graph.ReclassificationDistributionService;
import fr.gouv.vitam.metadata.core.graph.StoreGraphService;
import fr.gouv.vitam.metadata.core.graph.api.GraphComputeService;
import fr.gouv.vitam.metadata.core.model.ReconstructionRequestItem;
import fr.gouv.vitam.metadata.core.model.ReconstructionResponseItem;
import fr.gouv.vitam.metadata.core.reconstruction.ReconstructionService;

/**
 * Metadata reconstruction resource.
 */
@Path("/metadata/v1")
public class MetadataManagementResource {

    /**
     * Vitam Logger.
     */
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(MetadataManagementResource.class);
    public static final String OBJECTGROUP = "OBJECTGROUP";
    public static final String UNIT = "UNIT";
    public static final String UNIT_OBJECTGROUP = UNIT + "_" + OBJECTGROUP;
    private final static String EXPORT_RECLASSIFICATION_CHILD_NODES = "exportReclassificationChildNodes";

    private final String RECONSTRUCTION_URI = "/reconstruction";
    private final String STORE_GRAPH_URI = "/storegraph";
    private final String COMPUTE_GRAPH_URI = "/computegraph";
    private final String STORE_GRAPH_PROGRESS_URI = "/storegraph/progress";
    private final String COMPUTE_GRAPH_PROGRESS_URI = "/computegraph/progress";
    /**
     * Error/Exceptions messages.
     */
    private static final String RECONSTRUCTION_JSON_MONDATORY_PARAMETERS_MSG =
        "the Json input of reconstruction's parameters is mondatory.";
    private static final String RECONSTRUCTION_EXCEPTION_MSG =
        "ERROR: Exception has been thrown when reconstructing Vitam collections: ";
    private static final String STORE_GRAPH_EXCEPTION_MSG =
        "ERROR: Exception has been thrown when sotre graph: ";

    private static final String COMPUTE_GRAPH_EXCEPTION_MSG =
        "ERROR: Exception has been thrown when compute graph: ";

    /**
     * Reconstruction service.
     */
    private final ReconstructionService reconstructionService;
    private final StoreGraphService storeGraphService;
    private final GraphComputeService graphComputeService;
    private final ReclassificationDistributionService reclassificationDistributionService;

    /**
     * Constructor
     *
     * @param vitamRepositoryProvider vitamRepositoryProvider
     * @param offsetRepository
     */
    public MetadataManagementResource(VitamRepositoryProvider vitamRepositoryProvider,
        OffsetRepository offsetRepository, MetaData metadata) {
        this(new ReconstructionService(vitamRepositoryProvider, offsetRepository),
            new StoreGraphService(vitamRepositoryProvider),
            GraphComputeServiceImpl.initialize(vitamRepositoryProvider, metadata),
            new ReclassificationDistributionService(vitamRepositoryProvider));
    }

    /**
     * Constructor for tests
     *
     * @param reconstructionService
     * @param storeGraphService
     */
    @VisibleForTesting
    public MetadataManagementResource(
        ReconstructionService reconstructionService,
        StoreGraphService storeGraphService,
        GraphComputeService graphComputeService,
        ReclassificationDistributionService reclassificationDistributionService) {
        this.reconstructionService = reconstructionService;
        this.storeGraphService = storeGraphService;
        this.graphComputeService = graphComputeService;
        this.reclassificationDistributionService = reclassificationDistributionService;
    }

    /**
     * API to access and launch the Vitam reconstruction service for metadatas.<br/>
     *
     * @param reconstructionItems list of reconstruction request items
     * @return the response
     */
    @Path(RECONSTRUCTION_URI)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @VitamAuthentication(authentLevel = AuthenticationLevel.BASIC_AUTHENT)
    public Response reconstructCollection(List<ReconstructionRequestItem> reconstructionItems) {
        ParametersChecker.checkParameter(RECONSTRUCTION_JSON_MONDATORY_PARAMETERS_MSG, reconstructionItems);

        List<ReconstructionResponseItem> responses = new ArrayList<>();
        if (!reconstructionItems.isEmpty()) {
            LOGGER.debug(String
                .format("Starting reconstruction Vitam service with the json parameters : (%s)", reconstructionItems));

            reconstructionItems.forEach(item -> {
                LOGGER.debug(String.format(
                    "Starting reconstruction for the collection {%s} on the tenant (%s) with (%s) elements",
                    item.getCollection(), item.getTenant(), item.getLimit()));
                try {
                    responses.add(reconstructionService.reconstruct(item));
                } catch (IllegalArgumentException e) {
                    LOGGER.error(RECONSTRUCTION_EXCEPTION_MSG, e);
                    responses.add(new ReconstructionResponseItem(item, StatusCode.KO));
                }
            });
        }

        return Response.ok().entity(responses).build();
    }


    /**
     * API to access and launch the Vitam store graph service for metadatas.<br/>
     *
     * @return the response
     */
    @Path(STORE_GRAPH_URI)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @VitamAuthentication(authentLevel = AuthenticationLevel.BASIC_AUTHENT)
    public Response storeGraph() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(VitamConfiguration.getAdminTenant());
            Map<MetadataCollections, Integer> map = this.storeGraphService.tryStoreGraph();
            return Response.ok().entity(map).build();
        } catch (Exception e) {
            LOGGER.error(STORE_GRAPH_EXCEPTION_MSG, e);
            return Response.serverError().entity("{\"ErrorMsg\":\"" + e.getMessage() + "\"}").build();
        }
    }


    /**
     * Check if store graph is in progress.<br/>
     *
     * @return the response
     */
    @Path(STORE_GRAPH_PROGRESS_URI)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @VitamAuthentication(authentLevel = AuthenticationLevel.BASIC_AUTHENT)
    public Response storeGraphInProgress() {

        boolean inProgress = this.storeGraphService.isInProgress();
        if (inProgress) {
            LOGGER.info("Store graph in progress ...");
            return Response.ok("{\"msg\": \"Store graph in progress ...\"}").build();
        } else {
            LOGGER.info("No active store graph");
            return Response.status(Response.Status.NOT_FOUND).entity("{\"msg\": \"No active store graph\"}")
                .build();
        }
    }

    /**
     * API to access and launch the Vitam graph builder service for metadatas.<br/>
     *
     * @return the response
     */
    @Path(COMPUTE_GRAPH_URI)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @VitamAuthentication(authentLevel = AuthenticationLevel.BASIC_AUTHENT)
    public Response computeGraphByDSL(@HeaderParam(GlobalDataRest.X_TENANT_ID) Integer xTenantId, JsonNode queryDsl) {

        try {
            ParametersChecker.checkParameter("X_TENANT_ID header is required and mustn't be null", xTenantId);
            VitamThreadUtils.getVitamSession().setTenantId(xTenantId);

            GraphComputeResponse response = this.graphComputeService.computeGraph(queryDsl);
            return Response.ok().entity(response).build();
        } catch (Exception e) {
            LOGGER.error(COMPUTE_GRAPH_EXCEPTION_MSG, e);
            return Response.serverError().entity("{\"ErrorMsg\":\"" + e.getMessage() + "\"}").build();
        }
    }



    /**
     * Check if graph builder is in progress.<br/>
     *
     * @return the response
     */
    @Path(COMPUTE_GRAPH_PROGRESS_URI)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @VitamAuthentication(authentLevel = AuthenticationLevel.BASIC_AUTHENT)
    public Response computeGraphByDSLInProgress() {

        boolean inProgress = this.graphComputeService.isInProgress();
        if (inProgress) {
            LOGGER.info("Graph compute in progress ...");
            return Response.ok("{\"msg\": \"Graph compute in progress ...\"}").build();
        } else {
            LOGGER.info("No active graph builder");
            return Response.status(Response.Status.NOT_FOUND).entity("{\"msg\": \"No active graph compute service\"}")
                .build();
        }
    }


    /**
     * API to access and launch the Vitam graph builder service for metadatas.<br/>
     *
     * @return the response
     */
    @Path(COMPUTE_GRAPH_URI + "/{collection:" + UNIT + "|" + OBJECTGROUP + "|" + UNIT_OBJECTGROUP + "}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response computeGraph(@PathParam("collection") GraphComputeAction action, Set<String> documentsId) {
        try {

            MetadataCollections metadataCollections = MetadataCollections.UNIT;
            boolean computeObjectGroupGraph = GraphComputeAction.UNIT_OBJECTGROUP.equals(action);

            if (GraphComputeAction.OBJECTGROUP.equals(action)) {
                metadataCollections = MetadataCollections.OBJECTGROUP;
            }

            GraphComputeResponse response =
                this.graphComputeService.computeGraph(metadataCollections, documentsId, computeObjectGroupGraph);
            return Response.ok().entity(response).build();
        } catch (Exception e) {
            LOGGER.error(COMPUTE_GRAPH_EXCEPTION_MSG, e);
            return Response.serverError().entity("{\"ErrorMsg\":\"" + e.getMessage() + "\"}").build();
        }
    }

    /**
     * Export child nodes of units to reclassify for graph update into workspaces.
     *
     * @return the response (200 or KO)
     */
    @Path(EXPORT_RECLASSIFICATION_CHILD_NODES)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportReclassificationChildNodes(ReclassificationChildNodeExportRequest request) {
        try {

            this.reclassificationDistributionService.exportReclassificationChildNodes(
                request.getUnitIds(),
                request.getUnitsToUpdateChainedFileName(),
                request.getObjectGroupsToUpdateChainedFileName());

            return Response.ok().build();
        } catch (Exception e) {
            LOGGER.error("Could not export child nodes for reclassification graph update", e);
            return Response.serverError().entity("{\"ErrorMsg\":\"" + e.getMessage() + "\"}").build();
        }
    }
}
