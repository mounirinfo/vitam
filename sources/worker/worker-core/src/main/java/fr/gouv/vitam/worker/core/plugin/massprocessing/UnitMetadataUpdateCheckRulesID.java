/**
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 * <p>
 * contact.vitam@culture.gouv.fr
 * <p>
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 * <p>
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 * <p>
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 * <p>
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 * <p>
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.worker.core.plugin.massprocessing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.database.model.DataType;
import fr.gouv.vitam.common.database.parser.query.helper.CheckSpecifiedFieldHelper;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.massupdate.RuleAction;
import fr.gouv.vitam.common.model.massupdate.RuleActions;
import fr.gouv.vitam.common.model.massupdate.RuleCategoryAction;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesException;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Check update permissions.
 */
public class UnitMetadataUpdateCheckRulesID extends ActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ActionHandler.class);

    /**
     * CHECK_RULES_ID
     */
    private static final String CHECK_RULES_ID = "CHECK_RULES_ID";

    /**
     * AdminManagementClientFactory
     */
    private AdminManagementClientFactory adminManagementClientFactory;

    /**
     * Constructor.
     */
    public UnitMetadataUpdateCheckRulesID() {
        this(AdminManagementClientFactory.getInstance());
    }

    /**
     * Constructor.
     * @param adminManagementClientFactory admin management client
     */
    @VisibleForTesting
    public UnitMetadataUpdateCheckRulesID(
        AdminManagementClientFactory adminManagementClientFactory) {
        this.adminManagementClientFactory = adminManagementClientFactory;
    }

    /**
     * Execute an action
     * @param param {@link WorkerParameters}
     * @param handler the handlerIo
     * @return CompositeItemStatus:response contains a list of functional message and status code
     * @throws ProcessingException if an error is encountered when executing the action
     */
    @Override
    public ItemStatus execute(WorkerParameters param, HandlerIO handler)
        throws ProcessingException {

        checkMandatoryParameters(param);
        final ItemStatus itemStatus = new ItemStatus(CHECK_RULES_ID);

        // FIXME: Use in/out in order to transfer json from a step to another ?
        JsonNode queryActions = handler.getJsonFromWorkspace("actions.json");
        if (JsonHandler.isNullOrEmpty(queryActions)) {
            itemStatus.increment(StatusCode.OK);
            return new ItemStatus(CHECK_RULES_ID)
                .setItemsStatus(CHECK_RULES_ID, itemStatus);
        }

        try {
            JsonNode errorEvDetData = getErrorFromActionQuery(queryActions);
            if (errorEvDetData != null) {
                itemStatus.increment(StatusCode.KO);
                itemStatus.setMessage(VitamCode.UPDATE_UNIT_RULES_CONSISTENCY.name());
                itemStatus.setEvDetailData(JsonHandler.unprettyPrint(errorEvDetData));
                return new ItemStatus(CHECK_RULES_ID)
                    .setItemsStatus(CHECK_RULES_ID, itemStatus);
            };
        } catch (final VitamException | IllegalStateException e) {
            throw new ProcessingException(e);
        }

        itemStatus.increment(StatusCode.OK);
        return new ItemStatus(CHECK_RULES_ID)
            .setItemsStatus(CHECK_RULES_ID, itemStatus);
    }

    private JsonNode getErrorFromActionQuery(JsonNode queryActions) throws InvalidParseOperationException {
        RuleActions ruleActions = JsonHandler.getFromJsonNode(queryActions, RuleActions.class);

        Optional<JsonNode> checkError = ruleActions.getUpdate().stream()
            .flatMap(x -> x.entrySet().stream())
            .map(this::computeErrorsInRuleForCategory)
            .filter(Objects::nonNull)
            .findFirst();
        if(checkError.isPresent()) return checkError.get();

        checkError = ruleActions.getAdd().stream()
            .flatMap(x -> x.entrySet().stream())
            .map(this::computeErrorsInRuleForCategory)
            .filter(Objects::nonNull)
            .findFirst();
        if(checkError.isPresent()) return checkError.get();

        checkError = ruleActions.getDelete().stream()
            .flatMap(x -> x.entrySet().stream())
            .map(this::computeErrorsInRuleForCategory)
            .filter(Objects::nonNull)
            .findFirst();
        return checkError.orElse(null);
    }

    private JsonNode computeErrorsInRuleForCategory(Map.Entry<String, RuleCategoryAction> entry) {
        String categoryName = entry.getKey();
        RuleCategoryAction category = entry.getValue();

        Set<String> rulesToCheck = new HashSet<>();
        if (category.getRules() != null) {
            rulesToCheck.addAll(
                category.getRules().stream()
                .map(RuleAction::getRule)
                .collect(Collectors.toSet())
            );
        }
        if (category.getPreventRulesId() != null) {
            rulesToCheck.addAll(category.getPreventRulesId());
        }

        for (String ruleID : rulesToCheck) {
            JsonNode ruleResponseInReferential;

            try {
                ruleResponseInReferential = adminManagementClientFactory.getClient().getRuleByID(ruleID);
            } catch (FileRulesException e) {
                ObjectNode errorInfo = JsonHandler.createObjectNode();
                errorInfo.put("Error", VitamCode.UPDATE_UNIT_RULES_CONSISTENCY.name());
                errorInfo.put("Message", VitamCode.UPDATE_UNIT_RULES_CONSISTENCY.getMessage());
                errorInfo.put("Info ", "Rule " + ruleID + " is not in database");
                return errorInfo;
            } catch (AdminManagementClientServerException | InvalidParseOperationException e) {
                throw new IllegalStateException("Error while get the rule in Referential");
            }

            JsonNode ruleInReferential = ruleResponseInReferential.get("$results").get(0);
            if (!categoryName.equals(ruleInReferential.get("RuleType").asText())) {
                ObjectNode errorInfo = JsonHandler.createObjectNode();
                errorInfo.put("Error", VitamCode.UPDATE_UNIT_RULES_CONSISTENCY.name());
                errorInfo.put("Message", VitamCode.UPDATE_UNIT_RULES_CONSISTENCY.getMessage());
                errorInfo.put("Info ", "Rule " + ruleID + " is not in category " + categoryName + " but " +
                    ruleInReferential.get("RuleType").asText());
                return errorInfo;
            }
        }

        return null;
    }


    /**
     * Check mandatory parameter
     * @param handler input output list
     * @throws ProcessingException when handler io is not complete
     */
    @Override public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // Nothing
    }
}
