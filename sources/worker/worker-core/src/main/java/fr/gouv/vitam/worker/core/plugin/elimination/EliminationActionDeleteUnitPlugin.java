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
package fr.gouv.vitam.worker.core.plugin.elimination;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamDBException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.worker.core.plugin.elimination.exception.EliminationException;
import fr.gouv.vitam.worker.core.plugin.elimination.model.EliminationActionUnitStatus;
import fr.gouv.vitam.worker.core.plugin.elimination.report.EliminationActionReportService;
import fr.gouv.vitam.worker.core.plugin.elimination.report.EliminationActionUnitReportEntry;
import org.apache.commons.collections4.SetUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static fr.gouv.vitam.worker.core.utils.PluginHelper.buildItemStatus;
import static java.util.Collections.singletonList;


/**
 * Elimination action delete unit plugin.
 */
public class EliminationActionDeleteUnitPlugin extends ActionHandler {

    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(EliminationActionDeleteUnitPlugin.class);

    private static final String ELIMINATION_ACTION_DELETE_UNIT = "ELIMINATION_ACTION_DELETE_UNIT";

    private final EliminationActionDeleteService eliminationActionDeleteService;
    private final MetaDataClientFactory metaDataClientFactory;
    private final EliminationActionReportService eliminationActionReportService;

    /**
     * Default constructor
     */
    public EliminationActionDeleteUnitPlugin() {
        this(
            new EliminationActionDeleteService(),
            MetaDataClientFactory.getInstance(),
            new EliminationActionReportService());
    }

    /***
     * Test only constructor
     */
    @VisibleForTesting
    EliminationActionDeleteUnitPlugin(
        EliminationActionDeleteService eliminationActionDeleteService,
        MetaDataClientFactory metaDataClientFactory,
        EliminationActionReportService eliminationActionReportService) {
        this.eliminationActionDeleteService = eliminationActionDeleteService;
        this.metaDataClientFactory = metaDataClientFactory;
        this.eliminationActionReportService = eliminationActionReportService;
    }

    @Override
    public ItemStatus execute(WorkerParameters param, HandlerIO handler)
        throws ProcessingException {
        throw new ProcessingException("No need to implements method");
    }

    @Override
    public List<ItemStatus> executeList(WorkerParameters param, HandlerIO handler) {

        try {
            Set<String> unitIds = new HashSet<>(param.getObjectNameList());
            List<ItemStatus> itemStatuses = processUnits(param.getContainerName(), unitIds);

            return itemStatuses;

        } catch (EliminationException e) {
            LOGGER.error("Elimination action delete unit failed with status " + e.getStatusCode(), e);
            return singletonList(
                buildItemStatus(ELIMINATION_ACTION_DELETE_UNIT, e.getStatusCode(), e.getEventDetails()));
        }
    }

    private List<ItemStatus> processUnits(String processId, Set<String> unitIds)
        throws EliminationException {

        Map<String, JsonNode> units = loadUnits(unitIds);

        Set<String> foundUnitIds = units.keySet();

        Set<String> notFoundUnitIds = SetUtils.difference(unitIds, foundUnitIds);

        List<ItemStatus> itemStatuses = new ArrayList<>();

        for (String unitId : notFoundUnitIds) {
            LOGGER.info("Unit " + unitId + " does not exist. Already deleted?");
            itemStatuses.add(buildItemStatus(ELIMINATION_ACTION_DELETE_UNIT, StatusCode.OK, null));
        }

        List<EliminationActionUnitReportEntry> eliminationUnitReportEntries = new ArrayList<>();

        Set<String> unitsToDelete = getUnitsToDelete(foundUnitIds);

        for (String unitId : foundUnitIds) {

            EliminationActionUnitStatus eliminationActionUnitStatus;
            if (unitsToDelete.contains(unitId)) {
                LOGGER.info("Unit " + unitId + " will be deleted");
                eliminationActionUnitStatus = EliminationActionUnitStatus.DELETED;
                itemStatuses.add(buildItemStatus(ELIMINATION_ACTION_DELETE_UNIT, StatusCode.OK, null));
            } else {
                LOGGER.info("Unit " + unitId + " cannot be deleted because it has child units attached to it.");
                eliminationActionUnitStatus = EliminationActionUnitStatus.NON_DESTROYABLE_HAS_CHILD_UNITS;
                itemStatuses.add(buildItemStatus(ELIMINATION_ACTION_DELETE_UNIT, StatusCode.WARNING, null));
            }

            JsonNode unit = units.get(unitId);
            String initialOperation = unit.get(VitamFieldsHelper.initialOperation()).asText();
            String objectGroupId =
                unit.has(VitamFieldsHelper.object()) ? unit.get(VitamFieldsHelper.object()).asText() : null;
            String originatingAgency = unit.has(VitamFieldsHelper.originatingAgency()) ?
                unit.get(VitamFieldsHelper.originatingAgency()).asText() : null;

            eliminationUnitReportEntries.add(new EliminationActionUnitReportEntry(
                unitId, originatingAgency, initialOperation, objectGroupId, eliminationActionUnitStatus));
        }

        eliminationActionReportService.appendUnitEntries(processId, eliminationUnitReportEntries);

        try {
            eliminationActionDeleteService.deleteUnits(unitsToDelete);
        } catch (MetaDataExecutionException | MetaDataClientServerException |
            LogbookClientBadRequestException | StorageServerClientException | LogbookClientServerException e) {
            throw new EliminationException(StatusCode.FATAL,
                "Could not delete units [" + String.join(", ", unitsToDelete) + "]", e);
        }

        return itemStatuses;
    }

    private Map<String, JsonNode> loadUnits(Set<String> unitIds) throws EliminationException {
        try (MetaDataClient client = metaDataClientFactory.getClient()) {

            SelectMultiQuery selectMultiQuery = new SelectMultiQuery();
            selectMultiQuery.addRoots(unitIds.toArray(new String[0]));
            selectMultiQuery.addUsedProjection(
                VitamFieldsHelper.id(),
                VitamFieldsHelper.object(),
                VitamFieldsHelper.originatingAgency(),
                VitamFieldsHelper.initialOperation());

            JsonNode jsonNode = client.selectUnits(selectMultiQuery.getFinalSelect());
            RequestResponseOK<JsonNode> requestResponseOK = RequestResponseOK.getFromJsonNode(jsonNode);

            return requestResponseOK.getResults().stream()
                .collect(Collectors.toMap(
                    (unit) -> unit.get(VitamFieldsHelper.id()).asText(), unit -> unit));

        } catch (MetaDataExecutionException | MetaDataDocumentSizeException | MetaDataClientServerException | InvalidParseOperationException e) {
            throw new EliminationException(StatusCode.FATAL, "Could not load units", e);
        }
    }

    private Set<String> getUnitsToDelete(Set<String> unitIds) throws EliminationException {
        Set<String> unitsWithChildren = getUnitsWithChildren(unitIds);
        return SetUtils.difference(unitIds, unitsWithChildren);
    }

    private Set<String> getUnitsWithChildren(Set<String> unitIds) throws EliminationException {

        try (MetaDataClient metaDataClient = metaDataClientFactory.getClient()) {

            Set<String> unitsToFetch = new HashSet<>(unitIds);
            Set<String> result = new HashSet<>();

            while (!unitsToFetch.isEmpty()) {

                RequestResponseOK<JsonNode> responseOK = selectChildUnits(metaDataClient, unitsToFetch);

                Set<String> unitsWithChildren = parseUnitsWithChildren(responseOK.getResults(), unitsToFetch);

                result.addAll(unitsWithChildren);
                unitsToFetch.removeAll(unitsWithChildren);

                if (noMoreResults(responseOK)) {
                    break;
                }
            }

            return result;

        } catch (InvalidParseOperationException | InvalidCreateOperationException | VitamDBException | MetaDataExecutionException | MetaDataDocumentSizeException | MetaDataClientServerException e) {
            throw new EliminationException(StatusCode.FATAL, "Could not check child units", e);
        }
    }

    private RequestResponseOK<JsonNode> selectChildUnits(MetaDataClient metaDataClient, Set<String> unitsToFetch)
        throws InvalidCreateOperationException, InvalidParseOperationException, MetaDataExecutionException,
        MetaDataDocumentSizeException, MetaDataClientServerException, VitamDBException {
        SelectMultiQuery selectAllUnitsUp = new SelectMultiQuery();
        selectAllUnitsUp.addQueries(QueryHelper.in(VitamFieldsHelper.unitups(), unitsToFetch.toArray(new String[0])));
        selectAllUnitsUp.setLimitFilter(0, VitamConfiguration.getBatchSize());
        selectAllUnitsUp.addUsedProjection(VitamFieldsHelper.unitups());
        JsonNode response = metaDataClient.selectUnits(selectAllUnitsUp.getFinalSelect());
        return RequestResponseOK.getFromJsonNode(response);
    }

    private Set<String> parseUnitsWithChildren(List<JsonNode> results, Set<String> unitsToFetch) {
        Set<String> foundUnitIds = new HashSet<>();

        for (JsonNode childUnit : results) {
            childUnit.get(VitamFieldsHelper.unitups()).elements()
                .forEachRemaining(jsonNode -> {
                    String unitId = jsonNode.asText();
                    if (unitsToFetch.contains(unitId)) {
                        foundUnitIds.add(unitId);
                    }
                });
        }

        return foundUnitIds;
    }

    private boolean noMoreResults(RequestResponseOK<JsonNode> responseOK) {
        return responseOK.getHits().getTotal() < VitamConfiguration.getBatchSize();
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // NOP.
    }

    public static String getId() {
        return ELIMINATION_ACTION_DELETE_UNIT;
    }
}
