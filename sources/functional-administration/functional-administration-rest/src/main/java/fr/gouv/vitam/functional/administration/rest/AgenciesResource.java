/*
 * * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019) * <p> * contact.vitam@culture.gouv.fr
 * * <p> * This software is a computer program whose purpose is to implement a digital archiving back-office system
 * managing * high volumetry securely and efficiently. * <p> * This software is governed by the CeCILL 2.1 license under
 * French law and abiding by the rules of distribution of free * software. You can use, modify and/ or redistribute the
 * software under the terms of the CeCILL 2.1 license as * circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info". * <p> * As a counterpart to the access to the source code and rights to copy, modify and
 * redistribute granted by the license, * users are provided only with a limited warranty and the software's author, the
 * holder of the economic rights, and the * successive licensors have only limited liability. * <p> * In this respect,
 * the user's attention is drawn to the risks associated with loading, using, modifying and/or * developing or
 * reproducing the software by the user in light of its specific status of free software, that may mean * that it is
 * complicated to manipulate, and that also therefore means that it is reserved for developers and * experienced
 * professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the * software's
 * suitability as regards their requirements in conditions enabling the security of their systems and/or data * to be
 * ensured and, more generally, to use and operate it in the same conditions as regards security. * <p> * The fact that
 * you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you * accept its
 * terms.
 */
package fr.gouv.vitam.functional.administration.rest;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidGuidOperationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.administration.AgenciesModel;
import fr.gouv.vitam.common.security.SanityChecker;
import fr.gouv.vitam.common.stream.VitamAsyncInputStreamResponse;
import fr.gouv.vitam.functional.administration.common.Agencies;
import fr.gouv.vitam.functional.administration.common.ErrorReportAgencies;
import fr.gouv.vitam.functional.administration.common.FunctionalBackupService;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.functional.administration.agencies.api.AgenciesService;
import fr.gouv.vitam.functional.administration.common.counter.VitamCounterService;



/**
 * This resource manage Agencys create, update, find, ...
 */
@Path("/adminmanagement/v1")
@ApplicationPath("webresources")
public class AgenciesResource {

    private static final String FUNCTIONAL_ADMINISTRATION_MODULE = "FUNCTIONAL_ADMINISTRATION_MODULE";
    static final String AGENCIES = "/agencies";
    static final String AGENCIES_IMPORT = "/agencies/import";
    static final String AGENCIES_CHECK = "/agencies/check";
    private static final String ATTACHEMENT_FILENAME = "attachment; filename=ErrorReport.json";

    static final String UPDATE_AGENCIES_URI = "/agencies";

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AgenciesResource.class);
    private static final String AGENCIES_FILES_IS_MANDATORY_PATAMETER =
        "The json input of agency is mandatory";

    private final MongoDbAccessAdminImpl mongoAccess;
    private final FunctionalBackupService functionalBackupService;
    private VitamCounterService vitamCounterService;
    /**
     * @param mongoAccess
     * @param vitamCounterService
     */
    public AgenciesResource(MongoDbAccessAdminImpl mongoAccess,
        VitamCounterService vitamCounterService) {
        this.mongoAccess = mongoAccess;
        this.vitamCounterService = vitamCounterService;
        this.functionalBackupService = new FunctionalBackupService(vitamCounterService);
        LOGGER.debug("init Admin Management Resource server");
    }

    /**
     * @param mongoAccess
     * @param vitamCounterService
     */
    @VisibleForTesting
    public AgenciesResource(MongoDbAccessAdminImpl mongoAccess,
        VitamCounterService vitamCounterService,
        FunctionalBackupService functionalBackupService) {
        this.mongoAccess = mongoAccess;
        this.vitamCounterService = vitamCounterService;
        this.functionalBackupService = functionalBackupService;
        LOGGER.debug("init Admin Management Resource server");
    }

    @Path(AGENCIES_IMPORT)
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importAgencies(@Context HttpHeaders headers,InputStream inputStream, @Context UriInfo uri) {
        ParametersChecker.checkParameter(AGENCIES_FILES_IS_MANDATORY_PATAMETER, inputStream);

        try (AgenciesService agencies = new AgenciesService(mongoAccess, vitamCounterService,
            functionalBackupService)) {

            String filename = headers.getHeaderString(GlobalDataRest.X_FILENAME);

            RequestResponse requestResponse = agencies.importAgencies(inputStream,filename);

            if (!requestResponse.isOk()) {
                ((VitamError) requestResponse).setHttpCode(Status.BAD_REQUEST.getStatusCode());
                return Response.status(Status.BAD_REQUEST).entity(requestResponse).build();
            } else {
                return Response.created(uri.getRequestUri().normalize()).entity(requestResponse).build();
            }

        } catch (VitamException exp) {
            LOGGER.error(exp);
            return Response.status(Status.BAD_REQUEST)
                .entity(getErrorEntity(Status.BAD_REQUEST, exp.getMessage(), null)).build();

        } catch (Exception exp) {
            LOGGER.error("Unexpected server error {}", exp);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                .entity(getErrorEntity(Status.INTERNAL_SERVER_ERROR, exp.getMessage(), null)).build();
        }

    }

    /**
     * * Construct the error following input * * @param status Http error status * @param message The functional error
     * message, if absent the http reason phrase will be used instead * @param code The functional error code, if absent
     * the http code will be used instead * @return
     */
    private VitamError getErrorEntity(Status status, String message, String code) {
        String aMessage =
            (message != null && !message.trim().isEmpty()) ? message
                : (status.getReasonPhrase() != null ? status.getReasonPhrase() : status.name());
        String aCode = (code != null) ? code : String.valueOf(status.getStatusCode());
        return new VitamError(aCode).setHttpCode(status.getStatusCode())
            .setContext(FUNCTIONAL_ADMINISTRATION_MODULE)
            .setState("ko").setMessage(status.getReasonPhrase()).setDescription(aMessage);
    }

    /**
     * find access contracts by queryDsl
     *
     * @param queryDsl
     * @return Response
     */
    @Path(AGENCIES)
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAgencies(JsonNode queryDsl) {
        try (AgenciesService agencyService = new AgenciesService(mongoAccess, vitamCounterService,
            functionalBackupService)) {
            SanityChecker.checkJsonAll(queryDsl);
            final DbRequestResult agenciesModelList = agencyService.findAgencies(queryDsl);
            RequestResponseOK reponse =
                agenciesModelList.getRequestResponseOK(queryDsl, Agencies.class, AgenciesModel.class);
            return Response.status(Status.OK).entity(reponse).build();
        } catch (Exception  e) {
            LOGGER.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                .entity(getErrorEntity(Status.INTERNAL_SERVER_ERROR, e.getMessage(), null)).build();
        }
    }

    /**
     * check the agencie file
     * @param agencyStream the stream containing agencies to be checked
     *
     * @return Response response jersey
     * @throws IOException convert inputstream agency to File exception occurred
     * @throws InvalidCreateOperationException if exception occurred when create query
     * @throws InvalidParseOperationException if parsing json data exception occurred
     * @throws ReferentialException if exception occurred when create agency file manager
     */
    @Path(AGENCIES_CHECK)
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response checkAgenciesFile(InputStream agencyStream) {
        ParametersChecker.checkParameter("agenciessStream is a mandatory parameter", agencyStream);
        return downloadErrorReport(agencyStream);
    }

    /**
     * async Download Report
     *
     * @param document the document to check
     */
    private Response downloadErrorReport(InputStream document) {
        Map<Integer, List<ErrorReportAgencies>> errors = new HashMap<>();
        try (AgenciesService agenciesService = new AgenciesService(mongoAccess, vitamCounterService,
            functionalBackupService)) {
            agenciesService.checkFile(document);
            InputStream errorReportInputStream =
                agenciesService.generateErrorReport();
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
            headers.put(HttpHeaders.CONTENT_DISPOSITION, ATTACHEMENT_FILENAME);
            return new VitamAsyncInputStreamResponse(errorReportInputStream,
                Status.OK, headers);
        } catch (Exception e) {
            return handleGenerateReport(errors);
        }
    }

    /**
     * Handle Generation of the report in case of exception
     *
     * @param errors
     * @return response
     */
    private Response handleGenerateReport(
        Map<Integer, List<ErrorReportAgencies>> errors) {
        InputStream errorReportInputStream = null;
        try (AgenciesService agenciesService = new AgenciesService(mongoAccess, vitamCounterService,
            functionalBackupService)) {
            errorReportInputStream =
                agenciesService.generateErrorReport();
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
            headers.put(HttpHeaders.CONTENT_DISPOSITION, ATTACHEMENT_FILENAME);
            return new VitamAsyncInputStreamResponse(errorReportInputStream,
                Status.BAD_REQUEST, headers);
        } catch (Exception e1) {
            LOGGER.error(e1);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * Update agencys
     *
     * @param agencyId
     * @param queryDsl
     * @return Response
     */
    @Path(UPDATE_AGENCIES_URI + "/{id}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAgencies(@PathParam("id") String agencyId, JsonNode queryDsl) {
        return Response.status(Status.NOT_IMPLEMENTED).entity(null).build();

    }

}

