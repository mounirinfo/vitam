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
package fr.gouv.vitam.worker.core.handler;

import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.UNITTYPE;
import static fr.gouv.vitam.common.model.IngestWorkflowConstants.SEDA_FILE;
import static fr.gouv.vitam.common.model.IngestWorkflowConstants.SEDA_FOLDER;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import de.odysseus.staxon.json.JsonXMLConfig;
import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLOutputFactory;
import fr.gouv.culture.archivesdefrance.seda.v2.ArchiveUnitType;
import fr.gouv.culture.archivesdefrance.seda.v2.DataObjectOrArchiveUnitReferenceType;
import fr.gouv.culture.archivesdefrance.seda.v2.DescriptiveMetadataContentType;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.SedaConstants;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.exception.CycleFoundException;
import fr.gouv.vitam.common.exception.InvalidGuidOperationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamDBException;
import fr.gouv.vitam.common.graph.DirectedCycle;
import fr.gouv.vitam.common.graph.DirectedGraph;
import fr.gouv.vitam.common.graph.Graph;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.guid.GUIDReader;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.IngestWorkflowConstants;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.UnitType;
import fr.gouv.vitam.common.model.administration.ActivationStatus;
import fr.gouv.vitam.common.model.administration.IngestContractModel;
import fr.gouv.vitam.common.model.unit.DescriptiveMetadataModel;
import fr.gouv.vitam.common.model.unit.GotObj;
import fr.gouv.vitam.common.model.unit.ManagementModel;
import fr.gouv.vitam.common.model.unit.RuleCategoryModel;
import fr.gouv.vitam.common.model.unit.RuleModel;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.performance.PerformanceLogger;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.model.LogbookLifeCycleObjectGroupModel;
import fr.gouv.vitam.logbook.common.model.LogbookLifeCycleUnitModel;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleObjectGroupParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleUnitParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCyclesClientHelper;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookMongoDbName;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ArchiveUnitContainDataObjectException;
import fr.gouv.vitam.processing.common.exception.ArchiveUnitContainSpecialCharactersException;
import fr.gouv.vitam.processing.common.exception.MissingFieldException;
import fr.gouv.vitam.processing.common.exception.ProcessingDuplicatedVersionException;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.exception.ProcessingMalformedDataException;
import fr.gouv.vitam.processing.common.exception.ProcessingManifestReferenceException;
import fr.gouv.vitam.processing.common.exception.ProcessingObjectGroupNotFoundException;
import fr.gouv.vitam.processing.common.exception.ProcessingUnauthorizeException;
import fr.gouv.vitam.processing.common.exception.ProcessingUnitLinkingException;
import fr.gouv.vitam.processing.common.exception.ProcessingUnitNotFoundException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.common.utils.DataObjectDetail;
import fr.gouv.vitam.worker.common.utils.DataObjectInfo;
import fr.gouv.vitam.worker.common.utils.RuleTypeName;
import fr.gouv.vitam.worker.common.utils.SedaUtils;
import fr.gouv.vitam.worker.core.exception.WorkerspaceQueueException;
import fr.gouv.vitam.worker.core.extractseda.ArchiveUnitListener;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

/**
 * Handler class used to extract metaData. </br>
 * Create and put a new file (metadata extracted) json.json into container GUID
 */
public class ExtractSedaActionHandler extends ActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ExtractSedaActionHandler.class);

    private static final PerformanceLogger PERFORMANCE_LOGGER = PerformanceLogger.getInstance();

    // OUT RANK
    private static final int GRAPH_WITH_LONGEST_PATH_IO_RANK = 0;
    private static final int DO_ID_TO_OG_ID_IO_RANK = 1;
    private static final int DO_ID_TO_GUID_IO_RANK = 2;
    private static final int OG_ID_TO_GUID_IO_RANK = 3;
    public static final int OG_ID_TO_UNID_ID_IO_RANK = 4;
    private static final int BDO_ID_TO_VERSION_DO_IO_RANK = 5;
    private static final int UNIT_ID_TO_GUID_IO_RANK = 6;
    private static final int GLOBAL_SEDA_PARAMETERS_FILE_IO_RANK = 7;
    public static final int OG_ID_TO_GUID_IO_MEMORY_RANK = 8;
    private static final int GUID_TO_UNIT_ID_IO_RANK = 10;
    private static final int HANDLER_IO_OUT_PARAMETER_NUMBER = 11;

    // IN RANK
    private static final int UNIT_TYPE_INPUT_RANK = 1;
    private static final int STORAGE_INFO_INPUT_RANK = 2;
    private static final int EXISTING_GOT_RANK = 9;

    private static final String HANDLER_ID = "CHECK_MANIFEST";
    private static final String SUBTASK_LOOP = "CHECK_MANIFEST_LOOP";
    private static final String SUBTASK_ATTACHEMENT = "CHECK_MANIFEST_WRONG_ATTACHMENT";
    private static final String SUBTASK_ATTACHEMENT_LINK = "CHECK_MANIFEST_WRONG_ATTACHMENT_LINK";
    private static final String SUBTASK_MALFORMED = "CHECK_MANIFEST_MALFORMED_DATA";
    private static final String EXISTING_OG_NOT_DECLARED = "EXISTING_OG_NOT_DECLARED";
    private static final String LFC_INITIAL_CREATION_EVENT_TYPE = "LFC_CREATION";
    private static final String LFC_CREATION_SUB_TASK_ID = "LFC_CREATION";
    private static final String ATTACHMENT_IDS = "_up";
    private static final String OBJECT_GROUP_ID = "_og";
    private static final String TRANSFER_AGENCY = "TransferringAgency";
    private static final String ARCHIVAL_AGENCY = "ArchivalAgency";
    public static final int BATCH_SIZE = 50;
    public static final String RULES = "Rules";

    private static String ORIGIN_ANGENCY_NAME = "originatingAgency";
    private static final String ORIGIN_ANGENCY_SUBMISSION = "submissionAgency";
    private static final String ARCHIVAl_AGREEMENT = "ArchivalAgreement";
    private static final String ARCHIVAl_PROFIL = "ArchivalProfile";

    private HandlerIO handlerIO;

    private static final String EV_DETAIL_REQ = "EvDetailReq";
    private static final String JSON_EXTENSION = ".json";
    private static final String DATA_OBJECT_GROUP = "DataObjectGroup";
    private static final String BINARY_DATA_OBJECT = "BinaryDataObject";
    private static final String PHYSICAL_DATA_OBJECT = "PhysicalDataObject";
    private static final String DATA_OBJECT_GROUPID = "DataObjectGroupId";
    private static final String ARCHIVE_UNIT = "ArchiveUnit";
    private static final String BINARY_MASTER = "BinaryMaster";
    private static final String PHYSICAL_MASTER = "PhysicalMaster";
    private static final String DATAOBJECT_PACKAGE = "DataObjectPackage";
    private static final String FILE_INFO = "FileInfo";
    private static final String METADATA = "Metadata";
    // TODO P0 WORKFLOW will be in vitam-logbook file
    private static final String LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG = "LogbookClient Unsupported request";
    private static final String LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG = "LifeCycle Object already exists";
    private static final String LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG = "Logbook LifeCycle resource not found";
    private static final String LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG = "Logbook Server internal error";
    private static final String DATA_OBJECT_VERSION_MUST_BE_UNIQUE = "ERROR: DataObject version must be unique";
    private static final String LEVEL = "level_";

    private static final String GRAPH_CYCLE_MSG =
        "The Archive Unit graph in the SEDA file has a cycle";
    private static final String CYCLE_FOUND_EXCEPTION = "Seda has an archive unit cycle ";
    private static final String SAVE_ARCHIVE_ID_TO_GUID_IOEXCEPTION_MSG =
        "Can not save unitToGuidMap to temporary file";
    private static final String FILE_COULD_NOT_BE_DELETED_MSG = "File could not be deleted";
    private static final String CANNOT_READ_SEDA = "Can not read SEDA";
    private static final String MANIFEST_NOT_FOUND = "Manifest.xml Not Found";
    private static final String ARCHIVE_UNIT_TMP_FILE_PREFIX = "AU_TMP_";
    private static final String GLOBAL_MGT_RULE_TAG = "GLOBAL_MGT_RULE";
    private static final String DEFAULT_STRATEGY = "default";

    private final Map<String, String> dataObjectIdToGuid;
    private final Map<String, String> objectGroupIdToGuid;
    private final Map<String, String> objectGroupIdToGuidTmp;
    private final Map<String, String> unitIdToGuid;
    private final Map<String, String> guidToUnitId;
    private final Set<String> existingUnitGuids;
    private final Map<String, String> existingUnitIdWithExistingObjectGroup;
    private final Set<String> physicalDataObjetsGuids;

    private final Map<String, String> dataObjectIdToObjectGroupId;
    private final Map<String, List<String>> objectGroupIdToDataObjectId;
    private final Map<String, String> unitIdToGroupId;
    // this map contains DataObject that not have DataObjectGroupId
    private final Map<String, GotObj> dataObjectIdWithoutObjectGroupId;
    private final Map<String, List<String>> objectGroupIdToUnitId;
    private final Map<String, DataObjectInfo> objectGuidToDataObject;
    private final Map<String, DataObjectDetail> dataObjectIdToDetailDataObject;
    private final Map<String, LogbookLifeCycleParameters> guidToLifeCycleParameters;

    private final List<Class<?>> handlerInputIOList = Arrays.asList(String.class, String.class, File.class);
    private File globalSedaParametersFile;
    private final Map<String, Set<String>> unitIdToSetOfRuleId;
    private final Map<String, StringWriter> mngtMdRuleIdToRulesXml;
    private int nbAUExisting = 0;

    private final List<String> originatingAgencies;

    private String originatingAgency = null;
    private String submissionAgencyIdentifier = null;
    private String transferringAgency = null;
    private String archivalAgency = null;
    private String contractName = null;
    private IngestContractModel ingestContract = null;
    private String archivalProfile = null;

    private String linkParentId = null;

    private  Map<String, Boolean>  isThereManifestRelatedReferenceRemained;

    private static JAXBContext jaxbContext;

    static {
        try {
            jaxbContext =
                JAXBContext
                    .newInstance(ArchiveUnitType.class.getPackage().getName() + ":fr.gouv.vitam.common.model.unit");
        } catch (JAXBException e) {
            LOGGER.error("unable to create jaxb context", e);
        }
    }

    private Unmarshaller unmarshaller;
    private ArchiveUnitListener listener;

    private MetaDataClientFactory metaDataClientFactory;
    private LogbookLifeCyclesClientFactory logbookLifeCyclesClientFactory;

    private ObjectNode archiveUnitTree;
    private Map<String, JsonNode> existingGOTs;
    private boolean asyncIO = true;

    /**
     * Constructor with parameter SedaUtilsFactory
     */
    public ExtractSedaActionHandler() {
        this(MetaDataClientFactory.getInstance(), LogbookLifeCyclesClientFactory.getInstance());
    }

    @VisibleForTesting
    ExtractSedaActionHandler(MetaDataClientFactory metaDataClientFactory, LogbookLifeCyclesClientFactory logbookLifeCyclesClientFactory) {
        dataObjectIdToGuid = new HashMap<>();
        dataObjectIdWithoutObjectGroupId = new HashMap<>();
        objectGroupIdToGuid = new HashMap<>();
        objectGroupIdToGuidTmp = new HashMap<>();
        unitIdToGuid = new HashMap<>();
        guidToUnitId = new HashMap<>();
        dataObjectIdToObjectGroupId = new HashMap<>();
        objectGroupIdToDataObjectId = new HashMap<>();
        unitIdToGroupId = new HashMap<>();
        objectGroupIdToUnitId = new HashMap<>();
        guidToLifeCycleParameters = new HashMap<>();
        dataObjectIdToDetailDataObject = new HashMap<>();
        objectGuidToDataObject = new HashMap<>();
        mngtMdRuleIdToRulesXml = new HashMap<>();
        unitIdToSetOfRuleId = new HashMap<>();
        existingUnitGuids = new HashSet<>();
        physicalDataObjetsGuids = new HashSet<>();
        originatingAgencies = new ArrayList<>();
        existingGOTs = new HashMap<>();
        existingUnitIdWithExistingObjectGroup = new HashMap<>();
        isThereManifestRelatedReferenceRemained = new HashMap<>();

        archiveUnitTree = JsonHandler.createObjectNode();
        this.metaDataClientFactory = metaDataClientFactory;
        this.logbookLifeCyclesClientFactory = logbookLifeCyclesClientFactory;
    }

    /**
     * @return HANDLER_ID
     */
    public static String getId() {
        return HANDLER_ID;
    }

    /**
     * @return HandlerIO
     */
    public HandlerIO getHandlerIO() {
        return handlerIO;
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO ioParam) {
        checkMandatoryParameters(params);
        handlerIO = ioParam;
        final ItemStatus globalCompositeItemStatus = new ItemStatus(HANDLER_ID);

        UnitType workflowUnitType = getUnitType();

        try (LogbookLifeCyclesClient lifeCycleClient = logbookLifeCyclesClientFactory.getClient()) {

            if (asyncIO) {
                handlerIO.enableAsync(true);
            }

            checkMandatoryIOParameter(ioParam);
            globalSedaParametersFile =
                handlerIO.getNewLocalFile(handlerIO.getOutput(GLOBAL_SEDA_PARAMETERS_FILE_IO_RANK).getPath());

            unmarshaller = jaxbContext.createUnmarshaller();
            listener = new ArchiveUnitListener(handlerIO, archiveUnitTree, unitIdToGuid, guidToUnitId, unitIdToGroupId,
                objectGroupIdToUnitId, dataObjectIdToObjectGroupId, dataObjectIdWithoutObjectGroupId,
                guidToLifeCycleParameters, existingUnitGuids, params.getLogbookTypeProcess(),
                params.getContainerName(), metaDataClientFactory, objectGroupIdToGuid,
                dataObjectIdToGuid, unitIdToSetOfRuleId,
                workflowUnitType, originatingAgencies, existingGOTs, existingUnitIdWithExistingObjectGroup, isThereManifestRelatedReferenceRemained);
            unmarshaller.setListener(listener);

            ObjectNode evDetData = extractSEDA(lifeCycleClient, params, globalCompositeItemStatus, workflowUnitType);

            if (existingUnitGuids.size() > 0) {
                ArrayNode attachmentNode = JsonHandler.createArrayNode();
                if (ingestContract != null && contractName != null) {
                    // if ingestContract is not null that means we need
                    // to check that existing unit guids are sons of the ingest value
                    checkIngestContractWithAttachmentGuid(attachmentNode);
                }
                evDetData.set(ATTACHMENT_IDS, attachmentNode);
            }

            globalCompositeItemStatus.setEvDetailData(JsonHandler.unprettyPrint(evDetData));
            globalCompositeItemStatus.setMasterData(LogbookParameterName.eventDetailData.name(),
                JsonHandler.unprettyPrint(evDetData));
            globalCompositeItemStatus.increment(StatusCode.OK);

            if (asyncIO) {
                handlerIO.enableAsync(false);
            }
            ObjectNode agIdExt = JsonHandler.createObjectNode();

            if (originatingAgency != null) {
                LOGGER.debug("supplier service is: " + originatingAgency);
                agIdExt.put(ORIGIN_ANGENCY_NAME, originatingAgency);
            }
            if (transferringAgency != null) {
                LOGGER.debug("Find a transfAgency: " + transferringAgency);
                agIdExt.put(TRANSFER_AGENCY, transferringAgency);
            }
            if (archivalAgency != null) {
                LOGGER.debug("Find a archivalAgency: " + archivalAgency);
                agIdExt.put(ARCHIVAL_AGENCY, archivalAgency);
            }
            if (submissionAgencyIdentifier != null) {
                LOGGER.debug("Find a submissionAgencyIdentifier: " + submissionAgencyIdentifier);
                agIdExt.put(ORIGIN_ANGENCY_SUBMISSION, submissionAgencyIdentifier);
            }
            /*
             * setting agIdExt information
             */
            if (agIdExt.size() > 0) {
                globalCompositeItemStatus.setMasterData(LogbookMongoDbName.agIdExt.getDbname(), agIdExt.toString());
                globalCompositeItemStatus.setData(LogbookMongoDbName.agIdExt.getDbname(), agIdExt.toString());
            }


            ObjectNode rightsStatementIdentifier = JsonHandler.createObjectNode();
            if (contractName != null) {
                LOGGER.debug("contract name  is: " + contractName);
                rightsStatementIdentifier.put(ARCHIVAl_AGREEMENT, contractName);
            }
            if (archivalProfile != null) {
                LOGGER.debug("archivalProfile  is: " + archivalProfile);
                rightsStatementIdentifier.put(ARCHIVAl_PROFIL, archivalProfile);
            }

            /*
             * setting rightsStatementIdentifier information
             */
            if (rightsStatementIdentifier.size() > 0) {
                globalCompositeItemStatus.setData(LogbookMongoDbName.rightsStatementIdentifier.getDbname(),
                    rightsStatementIdentifier.toString());
                globalCompositeItemStatus
                    .setMasterData(LogbookMongoDbName.rightsStatementIdentifier.getDbname(),
                        rightsStatementIdentifier.toString());
                ObjectNode data = null;
                try {
                    data = (ObjectNode) JsonHandler.getFromString(globalCompositeItemStatus.getEvDetailData());
                    data.set(LogbookMongoDbName.rightsStatementIdentifier.getDbname(), rightsStatementIdentifier);
                    globalCompositeItemStatus.setEvDetailData(data.toString());
                    globalCompositeItemStatus.setData(LogbookMongoDbName.rightsStatementIdentifier.getDbname(),
                        rightsStatementIdentifier.toString());

                } catch (InvalidParseOperationException e) {
                    LOGGER.debug("Invalid Json", e);
                }
            }

        } catch (final ProcessingDuplicatedVersionException e) {
            LOGGER.debug("ProcessingException: duplicated version", e);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ProcessingUnitNotFoundException e) {
            LOGGER.debug("ProcessingException : unit not found", e);
            updateDetailItemStatus(globalCompositeItemStatus,
                getMessageItemStatusAUNotFound(e.getUnitId(), e.getUnitGuid()), SUBTASK_ATTACHEMENT);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ProcessingMalformedDataException e) {
            LOGGER.debug("ProcessingException : Missing or malformed data in the manifest", e);
            ObjectNode error = JsonHandler.createObjectNode();
            error.put("error", e.getMessage());
            updateDetailItemStatus(globalCompositeItemStatus, JsonHandler.unprettyPrint(error), SUBTASK_MALFORMED);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ProcessingObjectGroupNotFoundException e) {
            LOGGER.debug("ProcessingException : ObjectGroup not found", e);
            updateDetailItemStatus(globalCompositeItemStatus,
                getMessageItemStatusOGNotFound(e.getUnitId(), e.getObjectGroupGuid()), SUBTASK_ATTACHEMENT);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ProcessingUnauthorizeException e) {
            LOGGER.debug("ProcessingException : unit not found", e);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ProcessingManifestReferenceException e) {
            LOGGER.debug("ProcessingException : reference incorrect in Manifest", e);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final MissingFieldException e) {
            LOGGER.debug("MissingFieldException", e);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ArchiveUnitContainDataObjectException e) {
            LOGGER.debug("ProcessingException: archive unit contain an data object declared object group.", e);
            globalCompositeItemStatus.setEvDetailData(e.getEventDetailData());
            updateDetailItemStatus(globalCompositeItemStatus,
                getMessageItemStatusAUDeclaringObject(e.getUnitId(), e.getBdoId(), e.getGotId()),
                EXISTING_OG_NOT_DECLARED);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ArchiveUnitContainSpecialCharactersException e) {
            LOGGER.debug("ProcessingException: archive unit contains special characters.", e);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ProcessingUnitLinkingException e) {
            LOGGER.debug("ProcessingException: Linking FILING_UNIT or HOLDING_UNIT to INGEST Unauthorized", e);
            if (e.getUnitGuid() != null && e.getUnitIngestContractGuid() != null) {
                updateDetailItemStatus(globalCompositeItemStatus,
                    getMessageItemStatusAULinkingException(e.getUnitGuid(), e.getUnitIngestContractGuid()),
                    SUBTASK_ATTACHEMENT_LINK);
            }
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (final ProcessingException | WorkerspaceQueueException e) {
            e.printStackTrace();
            LOGGER.debug("ProcessingException", e);
            globalCompositeItemStatus.increment(StatusCode.FATAL);
        } catch (final CycleFoundException e) {
            LOGGER.debug("ProcessingException: cycle found", e);
            globalCompositeItemStatus.setEvDetailData(e.getEventDetailData());
            updateDetailItemStatus(globalCompositeItemStatus, e.getCycle(), SUBTASK_LOOP);
            globalCompositeItemStatus.increment(StatusCode.KO);
        } catch (JAXBException e) {
            LOGGER.error("unable to create ExtractSeda handler, unmarshaller failed", e);
            globalCompositeItemStatus.increment(StatusCode.FATAL);
        } finally {
            // Empty all maps
            objectGroupIdToGuidTmp.clear();
            unitIdToGuid.clear();
            guidToUnitId.clear();
            dataObjectIdWithoutObjectGroupId.clear();
            objectGroupIdToDataObjectId.clear();
            guidToLifeCycleParameters.clear();
            objectGuidToDataObject.clear();
            dataObjectIdToDetailDataObject.clear();
            existingGOTs.clear();
            // Except if they are to be used in MEMORY just after in the same STEP
            // objectGroupIdToGuid
            // objectGroupIdToUnitId
        }



        return new ItemStatus(HANDLER_ID).setItemsStatus(HANDLER_ID, globalCompositeItemStatus);
    }

    private UnitType getUnitType() {
        UnitType unitType =
            UnitType.valueOf(UnitType.getUnitTypeString((String) handlerIO.getInput(UNIT_TYPE_INPUT_RANK)));
        return unitType;
    }

    private String getMessageItemStatusAUDeclaringObject(final String unitId, final String bdoId, final String gotId) {
        ObjectNode error = JsonHandler.createObjectNode();
        error.put(SedaConstants.TAG_ARCHIVE_UNIT, unitId);
        error.put(SedaConstants.TAG_BINARY_DATA_OBJECT_ID, bdoId);
        error.put(SedaConstants.TAG_DATA_OBJECT_GROUPE_ID, gotId);
        return JsonHandler.unprettyPrint(error);
    }

    private String getMessageItemStatusAUNotFound(final String unitId, final String unitGuid) {
        ObjectNode error = JsonHandler.createObjectNode();
        ObjectNode errorDetail = JsonHandler.createObjectNode();
        errorDetail.put(SedaConstants.TAG_ARCHIVE_UNIT, unitGuid);
        error.set(unitId, errorDetail);
        return JsonHandler.unprettyPrint(error);
    }

    private String getMessageItemStatusAULinkingException(final String unitGuid, final String unitIngestContractGuid) {
        ObjectNode error = JsonHandler.createObjectNode();
        error.put(SedaConstants.TAG_ARCHIVE_UNIT, unitGuid);
        error.put(SedaConstants.TAG_ARCHIVE_SYSTEM_ID, unitIngestContractGuid);
        return JsonHandler.unprettyPrint(error);
    }

    private String getMessageItemStatusOGNotFound(final String unitId, final String objectGroupGuid) {
        ObjectNode error = JsonHandler.createObjectNode();
        ObjectNode errorDetail = JsonHandler.createObjectNode();
        errorDetail.put(SedaConstants.TAG_DATA_OBJECT_GROUP, objectGroupGuid);
        error.set(unitId, errorDetail);
        return JsonHandler.unprettyPrint(error);
    }


    /**
     * Split Element from InputStream and write it to workspace
     *
     * @param logbookLifeCycleClient
     * @param params parameters of workspace server
     * @param globalCompositeItemStatus the global status
     * @param workflowUnitType
     * @throws ProcessingException throw when can't read or extract element from SEDA
     * @throws CycleFoundException when a cycle is found in data extract
     */
    public ObjectNode extractSEDA(LogbookLifeCyclesClient logbookLifeCycleClient, WorkerParameters params,
        ItemStatus globalCompositeItemStatus, UnitType workflowUnitType)
        throws ProcessingException, CycleFoundException {
        ParameterHelper.checkNullOrEmptyParameters(params);
        final String containerId = params.getContainerName();
        return extractSEDAWithWorkspaceClient(containerId, globalCompositeItemStatus,
            logbookLifeCycleClient, params.getLogbookTypeProcess(), workflowUnitType);
    }

    private ObjectNode extractSEDAWithWorkspaceClient(String containerId, ItemStatus globalCompositeItemStatus,
        LogbookLifeCyclesClient logbookLifeCycleClient, LogbookTypeProcess typeProcess,
        UnitType workflowUnitType)
        throws ProcessingException, CycleFoundException {
        ParametersChecker.checkParameter("ContainerId is a mandatory parameter", containerId);
        ParametersChecker.checkParameter("itemStatus is a mandatory parameter", globalCompositeItemStatus);

        /**
         * Retrieves SEDA
         **/

        final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLEventReader reader = null;

        final QName dataObjectGroupName = new QName(SedaConstants.NAMESPACE_URI, DATA_OBJECT_GROUP);
        final QName dataObjectName = new QName(SedaConstants.NAMESPACE_URI, BINARY_DATA_OBJECT);
        final QName physicalDataObjectName = new QName(SedaConstants.NAMESPACE_URI, PHYSICAL_DATA_OBJECT);
        final QName unitName = new QName(SedaConstants.NAMESPACE_URI, ARCHIVE_UNIT);
        final QName idQName = new QName(SedaConstants.ATTRIBUTE_ID);

        try (InputStream xmlFile = handlerIO.getInputStreamFromWorkspace(SEDA_FOLDER + "/" + SEDA_FILE)) {
            XMLStreamReader rawReader = xmlInputFactory.createXMLStreamReader(xmlFile);
            XMLStreamReader filteredReader = xmlInputFactory.createFilteredReader(rawReader,
                r -> !r.isWhiteSpace());

            reader = xmlInputFactory.createXMLEventReader(filteredReader);
            final JsonXMLConfig config =
                new JsonXMLConfigBuilder().autoArray(true).autoPrimitive(true).prettyPrint(true)
                    .namespaceDeclarations(false).build();
            // This file will be a JSON representation of the SEDA manifest with an empty DataObjectPackage structure
            final FileWriter tmpFileWriter = new FileWriter(globalSedaParametersFile);
            final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
            final XMLEventWriter writer = new JsonXMLOutputFactory(config).createXMLEventWriter(tmpFileWriter);
            writer.add(eventFactory.createStartDocument());
            boolean globalMetadata = true;

            // Retrieve storage info
            final JsonNode storageInfo = JsonHandler.getFromFile((File) handlerIO.getInput(STORAGE_INFO_INPUT_RANK));

            ObjectNode evDetData = JsonHandler.createObjectNode();

            Stopwatch xmlParserStopwatch = Stopwatch.createStarted();
            String currentGroupId = null;

            while (true) {
                final XMLEvent event = reader.peek();

                if (event.isStartElement() && event.asStartElement().getName().equals(unitName)) {

                    try {
                        unmarshaller.unmarshal(reader, ArchiveUnitType.class);
                    } catch (RuntimeException e) {
                        LOGGER.error(e);
                        if (e.getCause() instanceof ProcessingUnitNotFoundException) {
                            ProcessingUnitNotFoundException exception = (ProcessingUnitNotFoundException) e.getCause();
                            unitIdToGuid.put(exception.getUnitId(), exception.getUnitGuid());
                            guidToUnitId.put(exception.getUnitGuid(), exception.getUnitId());
                            saveGuidsMaps();
                            if (exception.isValidGuid()) {
                                createLifeCycleForError(SUBTASK_ATTACHEMENT,
                                    getMessageItemStatusAUNotFound(exception.getUnitId(), exception.getUnitGuid()),
                                    exception.getUnitGuid(), true, false, containerId, logbookLifeCycleClient,
                                    typeProcess);
                            }
                        }
                        if (e.getCause() instanceof ProcessingObjectGroupNotFoundException) {
                            ProcessingObjectGroupNotFoundException exception =
                                (ProcessingObjectGroupNotFoundException) e.getCause();
                            dataObjectIdToGuid.put(exception.getObjectGroupGuid(), exception.getObjectGroupGuid());
                            saveGuidsMaps();
                            createLifeCycleForError(SUBTASK_ATTACHEMENT,
                                getMessageItemStatusOGNotFound(exception.getUnitId(), exception.getObjectGroupGuid()),
                                exception.getObjectGroupGuid(), false, true, containerId, logbookLifeCycleClient,
                                typeProcess);
                        }
                        if (e.getCause() instanceof ProcessingException) {
                            throw (ProcessingException) e.getCause();
                        }
                        throw e;
                    } catch (JAXBException e) {
                        LOGGER.error("unable to parse archiveUnit", e);
                        throw new InvalidParseOperationException(e);
                    }
                    continue;
                }

                reader.nextEvent();

                if (event.isStartElement() && event.asStartElement().getName().equals(dataObjectGroupName)) {
                    final StartElement element = event.asStartElement();
                    // if(!element.getAttributes().hasNext())
                    currentGroupId = element.getAttributeByName(idQName).getValue();
                    continue;
                }

                if (event.isStartElement() && (event.asStartElement().getName().equals(dataObjectName) ||
                    event.asStartElement().getName().equals(physicalDataObjectName))) {
                    final StartElement element = event.asStartElement();
                    writeDataObjectInLocal(reader, element, currentGroupId);
                    continue;
                }

                if (event.isEndElement() && event.asEndElement().getName().equals(dataObjectGroupName)) {
                    currentGroupId = null;
                }


                // extract info for ATR
                // The DataObjectPackage EndElement is tested before the add condition as we need to add a empty
                // DataObjectPackage endElement event
                if (event.isEndElement() && event.asEndElement().getName().getLocalPart().equals(DATAOBJECT_PACKAGE)) {
                    globalMetadata = true;
                }

                if (event.isStartElement() && event.asStartElement().getName().getLocalPart()
                    .equals(SedaConstants.TAG_ARCHIVAL_AGREEMENT)) {
                    contractName = reader.getElementText();
                    // FIXME : this should be done in a proper way (check ingest is done before, so we should use the
                    // result)
                    listener.setIngestContract(contractName);
                    writer.add(eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_ARCHIVAL_AGREEMENT));
                    writer.add(eventFactory.createCharacters(contractName));
                    writer.add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_ARCHIVAL_AGREEMENT));
                    continue;
                }

                if (event.isStartElement() && event.asStartElement().getName().getLocalPart()
                    .equals(SedaConstants.TAG_ARCHIVE_PROFILE)) {
                    writer.add(eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_ARCHIVE_PROFILE));
                    writer.add(eventFactory.createCharacters(reader.getElementText()));
                    writer.add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_ARCHIVE_PROFILE));
                    continue;
                }

                if (event.isStartElement() && event.asStartElement().getName().getLocalPart()
                    .equals(SedaConstants.TAG_ORIGINATINGAGENCYIDENTIFIER)) {

                    if (!UnitType.HOLDING_UNIT.equals(workflowUnitType)) {
                        originatingAgency = reader.getElementText();
                        originatingAgencies.add(originatingAgency);
                        writer.add(eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI,
                            SedaConstants.TAG_ORIGINATINGAGENCYIDENTIFIER));
                        writer.add(eventFactory.createCharacters(originatingAgency));
                        writer.add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI,
                            SedaConstants.TAG_ORIGINATINGAGENCYIDENTIFIER));
                        for (String currentAgency : originatingAgencies) {
                            writer.add(eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI,
                                SedaConstants.TAG_ORIGINATINGAGENCYIDENTIFIERS));
                            writer.add(eventFactory.createCharacters(currentAgency));
                            writer.add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI,
                                SedaConstants.TAG_ORIGINATINGAGENCYIDENTIFIERS));
                        }
                    }
                    writer.add(
                        eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI, SedaUtils.NB_AU_EXISTING));
                    writer.add(eventFactory.createCharacters(String.valueOf(nbAUExisting)));
                    writer
                        .add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI, SedaUtils.NB_AU_EXISTING));

                    globalMetadata = false;
                }

                // Bug #2324 - lets check the serviceLevel value
                if (event.isStartElement() && event.asStartElement().getName().getLocalPart()
                    .equals(SedaConstants.TAG_SERVICE_LEVEL)) {
                    final String serviceLevel = reader.getElementText();
                    writer.add(eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_SERVICE_LEVEL));
                    writer.add(eventFactory.createCharacters(serviceLevel));
                    writer.add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_SERVICE_LEVEL));
                    globalMetadata = false;
                }

                if (event.isStartElement() && event.asStartElement().getName().getLocalPart()
                    .equals(SedaConstants.TAG_ACQUISITIONINFORMATION)) {
                    final String acquisitionInformation = reader.getElementText();
                    writer.add(eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_ACQUISITIONINFORMATION));
                    writer.add(eventFactory.createCharacters(acquisitionInformation));
                    writer.add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_ACQUISITIONINFORMATION));
                    globalMetadata = false;
                }

                if (event.isStartElement() && event.asStartElement().getName().getLocalPart()
                    .equals(SedaConstants.TAG_LEGALSTATUS)) {
                    final String legalStatus = reader.getElementText();
                    writer.add(eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_LEGALSTATUS));
                    writer.add(eventFactory.createCharacters(legalStatus));
                    writer.add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_LEGALSTATUS));
                    globalMetadata = false;
                }

                if (event.isStartElement() && event.asStartElement().getName().getLocalPart()
                    .equals(SedaConstants.TAG_SUBMISSIONAGENCYIDENTIFIER)) {

                    submissionAgencyIdentifier = reader.getElementText();
                    writer.add(eventFactory.createStartElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_SUBMISSIONAGENCYIDENTIFIER));
                    writer.add(eventFactory.createCharacters(submissionAgencyIdentifier));
                    writer.add(eventFactory.createEndElement("", SedaConstants.NAMESPACE_URI,
                        SedaConstants.TAG_SUBMISSIONAGENCYIDENTIFIER));
                    globalMetadata = false;
                }
                // Process rules : build mgtRulesMap
                if (event.isStartElement() &&
                    SedaConstants.getSupportedRules().contains(event.asStartElement().getName().getLocalPart())) {
                    final StartElement element = event.asStartElement();
                    parseMetadataManagementRules(reader, element, event.asStartElement().getName().getLocalPart());
                }

                // We add all the end but the start and end document and the event in the DataObjectPackage structure
                if (globalMetadata && event.getEventType() != XMLStreamConstants.START_DOCUMENT &&
                    event.getEventType() != XMLStreamConstants.END_DOCUMENT) {
                    writer.add(event);
                }
                // The DataObjectPackage StartElement is tested after the add event condition as we need to add an empty
                // DataObjectPackage startElement event
                if (event.isStartElement() &&
                    event.asStartElement().getName().getLocalPart().equals(DATAOBJECT_PACKAGE)) {
                    globalMetadata = false;
                }



                if (event.isEndDocument()) {
                    break;
                }
            }

            long elapsed = xmlParserStopwatch.elapsed(TimeUnit.MILLISECONDS);

            PERFORMANCE_LOGGER.log("CHECK_DATAOBJECTPACKAGE", getId(), "extractSeda.xml.parse", elapsed);

            writer.add(eventFactory.createEndDocument());
            writer.close();

            // save maps
            saveGuidsMaps();

            // Fill evDetData EvDetailReq, ArchivalAgreement, ArchivalProfile and ServiceLevel properties
            try {
                JsonNode metadataAsJson =
                    JsonHandler.getFromFile(globalSedaParametersFile).get(SedaConstants.TAG_ARCHIVE_TRANSFER);

                JsonNode comments = metadataAsJson.get(SedaConstants.TAG_COMMENT);

                if (comments != null && comments.isArray()) {
                    ArrayNode commentsArray = (ArrayNode) comments;
                    for (JsonNode node : commentsArray) {
                        String comment = null, lang = null;
                        if (node.isTextual()) {
                            comment = node.asText();
                        } else {
                            lang = node.get('@' + SedaConstants.TAG_ATTRIBUTE_LANG).asText();
                            comment = node.get("$").asText();
                        }

                        JsonNode oldComment = evDetData.get(EV_DETAIL_REQ);
                        String evDetReq = null;
                        if (oldComment != null) {
                            evDetReq = oldComment.asText();
                        }

                        JsonNode oldCommentCurrentLang =
                            evDetData.get(EV_DETAIL_REQ + (ParametersChecker.isNotEmpty(lang) ? "_" + lang : ""));
                        if (oldCommentCurrentLang != null) {
                            comment = oldCommentCurrentLang.asText() + "_" + comment;
                        }

                        if (evDetReq == null || "fr".equalsIgnoreCase(lang)) {
                            evDetData.put(EV_DETAIL_REQ, comment);
                        }

                        evDetData.put(EV_DETAIL_REQ + (ParametersChecker.isNotEmpty(lang) ? "_" + lang : ""), comment);
                        LOGGER.debug("evDetData after comment: " + evDetData);
                    }

                } else if (comments != null && comments.isTextual()) {
                    evDetData.put(EV_DETAIL_REQ, comments.asText());
                }

                JsonNode date = metadataAsJson.get(SedaConstants.TAG_DATE);
                if (date != null) {
                    LOGGER.debug("Find a date: " + date);
                    evDetData.put("EvDateTimeReq", date.asText().trim());
                }

                JsonNode archAgreement = metadataAsJson.get(SedaConstants.TAG_ARCHIVAL_AGREEMENT);
                if (archAgreement != null) {
                    LOGGER.debug("Find an archival agreement: " + archAgreement);
                    evDetData.put("ArchivalAgreement", archAgreement.asText());
                }

                JsonNode transfAgency = metadataAsJson.get(SedaConstants.TAG_TRANSFERRING_AGENCY);
                if (transfAgency != null) {
                    JsonNode identifier = transfAgency.get(SedaConstants.TAG_IDENTIFIER);
                    if (identifier != null) {
                        transferringAgency = identifier.asText();
                    }
                }
                JsonNode archivalAgencyContent = metadataAsJson.get(SedaConstants.TAG_ARCHIVAL_AGENCY);
                if (archivalAgencyContent != null) {
                    JsonNode identifier = archivalAgencyContent.get(SedaConstants.TAG_IDENTIFIER);
                    if (identifier != null) {
                        archivalAgency = identifier.asText();
                    }
                }
                JsonNode dataObjPack = metadataAsJson.get(SedaConstants.TAG_DATA_OBJECT_PACKAGE);
                if (dataObjPack != null) {
                    JsonNode serviceLevel = dataObjPack.get(SedaConstants.TAG_SERVICE_LEVEL);

                    JsonNode archivalProfileElement = dataObjPack.get(SedaConstants.TAG_ARCHIVE_PROFILE);
                    if (archivalProfileElement != null) {
                        LOGGER.debug("Find an archival profile: " + archivalProfileElement.asText());
                        evDetData.put(SedaConstants.TAG_ARCHIVE_PROFILE, archivalProfileElement.asText());
                        archivalProfile = archivalProfileElement.asText();
                    }
                    if (serviceLevel != null) {
                        LOGGER.debug("Find a service Level: " + serviceLevel);
                        evDetData.put("ServiceLevel", serviceLevel.asText());
                    } else {
                        LOGGER.debug("Put a null ServiceLevel (No service Level)");
                        evDetData.set("ServiceLevel", (ObjectNode) null);
                    }
                    JsonNode acquisitionInformation = dataObjPack.get(SedaConstants.TAG_ACQUISITIONINFORMATION);
                    if (acquisitionInformation != null) {
                        LOGGER.debug("Find AcquisitionInformation : " + acquisitionInformation);
                        evDetData.put(SedaConstants.TAG_ACQUISITIONINFORMATION, acquisitionInformation.asText());
                    }

                    JsonNode legalStatus = dataObjPack.get(SedaConstants.TAG_LEGALSTATUS);
                    if (legalStatus != null) {
                        LOGGER.debug("Find legalStatus : " + legalStatus);
                        evDetData.put(SedaConstants.TAG_LEGALSTATUS, legalStatus.asText());
                    }


                } else {
                    LOGGER.debug("Put a null ServiceLevel (No Data Object Package)");
                    evDetData.set("ServiceLevel", (ObjectNode) null);
                }

            } catch (InvalidParseOperationException e) {
                LOGGER.error("Can't parse globalSedaPareters", e);
                throw new ProcessingException(e);
            }

            String evDetDataJson = JsonHandler.unprettyPrint(evDetData);

            // 2-detect cycle : if graph has a cycle throw CycleFoundException
            // Define Treatment DirectedCycle detection

            Stopwatch checkCycle = Stopwatch.createStarted();

            checkCycle(logbookLifeCycleClient, containerId, evDetDataJson);

            PERFORMANCE_LOGGER.log("CHECK_DATAOBJECTPACKAGE", getId(), "extractSeda.checkCycle",
                checkCycle.elapsed(TimeUnit.MILLISECONDS));

            // 2- create graph and create level
            // Define Treatment Graph and Level Creation
            createIngestLevelStackFile(new Graph(archiveUnitTree).getGraphWithLongestPaths(),
                GRAPH_WITH_LONGEST_PATH_IO_RANK);

            checkArchiveUnitIdReference(evDetDataJson);

            Stopwatch saveObjectGroupToWorkspaceStopWatch = Stopwatch.createStarted();

            saveObjectGroupsToWorkspace(containerId, logbookLifeCycleClient, typeProcess, originatingAgency,
                storageInfo);

            PERFORMANCE_LOGGER.log("CHECK_DATAOBJECTPACKAGE", getId(), "extractSeda.saveObjectGroup",
                saveObjectGroupToWorkspaceStopWatch.elapsed(TimeUnit.MILLISECONDS));


            // Add parents to archive units and save them into workspace

            Stopwatch saveArchiveUnitStopWatch = Stopwatch.createStarted();

            finalizeAndSaveArchiveUnitToWorkspace(archiveUnitTree, containerId,
                IngestWorkflowConstants.ARCHIVE_UNIT_FOLDER, logbookLifeCycleClient, storageInfo);

            PERFORMANCE_LOGGER.log("CHECK_DATAOBJECTPACKAGE", getId(), "extractSeda.saveArchiveUnit",
                saveArchiveUnitStopWatch.elapsed(TimeUnit.MILLISECONDS));

            handlerIO.addOuputResult(GLOBAL_SEDA_PARAMETERS_FILE_IO_RANK, globalSedaParametersFile, false, asyncIO);

            return evDetData;
        } catch (final XMLStreamException | InvalidParseOperationException e) {
            LOGGER.error(CANNOT_READ_SEDA, e);
            throw new ProcessingException(e);
        } catch (final LogbookClientBadRequestException e) {
            LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (final LogbookClientNotFoundException e) {
            LOGGER.error(LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (final LogbookClientAlreadyExistsException e) {
            LOGGER.error(LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (final LogbookClientServerException e) {
            LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (final CycleFoundException e) {
            LOGGER.error(CYCLE_FOUND_EXCEPTION, e);
            throw e;
        } catch (final IOException e) {
            LOGGER.error(SAVE_ARCHIVE_ID_TO_GUID_IOEXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (final ArchiveUnitContainDataObjectException e) {
            LOGGER.error("Archive Unit Reference to BDO", e);
            throw e;
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.error(MANIFEST_NOT_FOUND);
            throw new ProcessingException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (final XMLStreamException e) {
                    SysErrLogger.FAKE_LOGGER.ignoreLog(e);
                }
            }
        }
    }

    private void saveGuidsMaps() throws IOException, ProcessingException {
        // Save DataObjectIdToGuid Map
        HandlerUtils.saveMap(handlerIO, dataObjectIdToGuid, DO_ID_TO_GUID_IO_RANK, true, asyncIO);
        // Save objectGroupIdToUnitId Map
        handlerIO.addOuputResult(OG_ID_TO_UNID_ID_IO_RANK, objectGroupIdToUnitId, asyncIO);
        // Save dataObjectIdToDetailDataObject Map
        HandlerUtils.saveMap(handlerIO, dataObjectIdToDetailDataObject, BDO_ID_TO_VERSION_DO_IO_RANK, true,
            asyncIO);
        // Save unitIdToGuid Map post unmarshalling
        HandlerUtils.saveMap(handlerIO, unitIdToGuid, UNIT_ID_TO_GUID_IO_RANK, true, asyncIO);
        // Save guidToUnitId Map post unmarshalling
        HandlerUtils.saveMap(handlerIO, guidToUnitId, GUID_TO_UNIT_ID_IO_RANK, true, asyncIO);
    }

    /**
     *
     * @param logbookLifeCycleClient
     * @param containerId
     * @param evDetData
     * @throws CycleFoundException
     * @throws LogbookClientNotFoundException
     * @throws InvalidParseOperationException
     * @throws LogbookClientBadRequestException
     * @throws LogbookClientServerException
     */
    private void checkCycle(LogbookLifeCyclesClient logbookLifeCycleClient, String containerId, String evDetData)
        throws CycleFoundException, LogbookClientNotFoundException, InvalidParseOperationException,
        LogbookClientBadRequestException, LogbookClientServerException {
        final DirectedGraph directedGraph = new DirectedGraph(archiveUnitTree);
        final DirectedCycle directedCycle = new DirectedCycle(directedGraph);
        if (!directedCycle.isCyclic()) {
            return;
        }

        String cycleMessage = null;
        if (directedCycle.getCycle() != null && !directedCycle.getCycle().isEmpty()) {
            if (directedCycle.getCycle().size() <= 20) {
                List<String> cycle = new ArrayList<>();
                for (Integer index : directedCycle.getCycle()) {
                    cycle.add(directedGraph.getId(index));
                }
                cycleMessage = "Cycle : " + Arrays.toString(cycle.toArray());
            }

            // update lifecycle of the first node
            String unitGuid = unitIdToGuid.get(directedGraph.getId(directedCycle.getCycle().get(0)));
            final LogbookLifeCycleParameters llcp = guidToLifeCycleParameters.get(unitGuid);
            llcp.setFinalStatus(SUBTASK_LOOP, null, StatusCode.KO, null, null);
            ObjectNode llcEvDetData = JsonHandler.createObjectNode();
            llcEvDetData.put(SedaConstants.EV_DET_TECH_DATA, cycleMessage);
            String wellFormedJson = JsonHandler.writeAsString(llcEvDetData);
            llcp.putParameterValue(LogbookParameterName.eventDetailData, wellFormedJson);
            handlerIO.getHelper().updateDelegate(llcp);
            bulkLifeCycleUnit(containerId, logbookLifeCycleClient, Lists.newArrayList(unitGuid));
        }
        throw new CycleFoundException(GRAPH_CYCLE_MSG, cycleMessage, evDetData);

    }

    private void parseMetadataManagementRules(XMLEventReader reader, StartElement element, String currentRuleInProcess)
        throws ProcessingException {
        try {
            StringWriter stringWriterRule = new StringWriter();
            final JsonXMLConfig config =
                new JsonXMLConfigBuilder().autoArray(true).autoPrimitive(true).prettyPrint(true)
                    .namespaceDeclarations(false).build();
            final XMLOutputFactory xmlOutputFactory = new JsonXMLOutputFactory(config);
            XMLEventWriter xw =
                xmlOutputFactory.createXMLEventWriter(stringWriterRule);
            final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
            String currentRuleId = null;

            xw.add(eventFactory.createStartDocument());
            // Add start element
            xw.add(eventFactory.createStartElement("", "", GLOBAL_MGT_RULE_TAG));
            xw.add(eventFactory.createStartElement("", "", element.getName().getLocalPart()));
            while (true) {

                XMLEvent event = reader.nextEvent();

                if (event.isEndElement() &&
                    currentRuleInProcess.equalsIgnoreCase(((EndElement) event).getName().getLocalPart())) {
                    xw.add(eventFactory.createEndElement("", "", event.asEndElement().getName().getLocalPart()));
                    // Add to map
                    mngtMdRuleIdToRulesXml.put(currentRuleId, stringWriterRule);
                    stringWriterRule.close();
                    break;
                }

                if (event.isStartElement() &&
                    SedaConstants.TAG_RULE_RULE.equals(event.asStartElement().getName().getLocalPart())) {

                    // A new rule was found => close the current stringWriterRule and add it to map
                    if (currentRuleId != null) {
                        xw.add(eventFactory.createEndElement("", "", GLOBAL_MGT_RULE_TAG));
                        xw.add(eventFactory.createEndDocument());
                        stringWriterRule.close();
                        mngtMdRuleIdToRulesXml.put(currentRuleId, stringWriterRule);

                        // Start a new build of a stringWriterRule
                        stringWriterRule = new StringWriter();
                        xw = xmlOutputFactory.createXMLEventWriter(stringWriterRule);
                        xw.add(eventFactory.createStartDocument());
                        xw.add(eventFactory.createStartElement("", "", GLOBAL_MGT_RULE_TAG));
                        xw.add(eventFactory.createStartElement("", "", element.getName().getLocalPart()));
                    }
                    xw.add(eventFactory.createStartElement("", "", SedaConstants.TAG_RULE_RULE));
                    event = (XMLEvent) reader.next();
                    xw.add(event);
                    if (event.isCharacters()) {
                        currentRuleId = event.asCharacters().getData();
                    }
                    event = (XMLEvent) reader.next();
                    if (event.isStartElement()) {
                        xw.add(
                            eventFactory.createStartElement("", "", event.asStartElement().getName().getLocalPart()));
                    } else if (event.isCharacters()) {
                        xw.add(event.asCharacters());
                    } else if (event.isEndElement()) {
                        xw.add(eventFactory.createEndElement("", "", event.asEndElement().getName().getLocalPart()));
                    }

                    continue;
                }

                if (event.isStartElement()) {
                    xw.add(eventFactory.createStartElement("", "", event.asStartElement().getName().getLocalPart()));
                } else if (event.isCharacters()) {
                    xw.add(event.asCharacters());
                } else if (event.isEndElement()) {
                    xw.add(eventFactory.createEndElement("", "", event.asEndElement().getName().getLocalPart()));
                }
            }
            xw.add(eventFactory.createEndDocument());
        } catch (XMLStreamException | IOException e) {
            LOGGER.error(CANNOT_READ_SEDA, e);
            throw new ProcessingException(e);
        }
    }

    private void finalizeAndSaveArchiveUnitToWorkspace(ObjectNode archiveUnitTree,
        String containerId, String path, LogbookLifeCyclesClient logbookLifeCycleClient,
        JsonNode storageInfo)
        throws LogbookClientBadRequestException, LogbookClientNotFoundException, LogbookClientServerException,
        XMLStreamException, ProcessingException, InvalidParseOperationException {

        // Finalize Archive units extraction process
        if (unitIdToGuid == null) {
            return;
        }

        List<String> uuids = new ArrayList<>();

        for (final Entry<String, String> element : unitIdToGuid.entrySet()) {

            final String unitGuid = element.getValue();
            final String unitId = element.getKey();
            boolean isRootArchive = true;

            // 1- create Unit life cycles
            createUnitLifeCycle(unitGuid, containerId);

            // 2- Update temporary files
            final File unitTmpFileForRead = handlerIO.getNewLocalFile(ARCHIVE_UNIT_TMP_FILE_PREFIX + unitGuid);
            final File unitCompleteTmpFile = handlerIO.getNewLocalFile(unitGuid);

            if (unitTmpFileForRead.exists()) {
                // Get the archiveUnit
                ObjectNode archiveUnit = (ObjectNode) JsonHandler.getFromFile(unitTmpFileForRead);

                // Management rules id to add
                Set<String> globalMgtIdExtra = new HashSet<>();

                // Add storage information to archive unit
                addStorageInformation(archiveUnit, storageInfo);

                isRootArchive = addWorkInformation(archiveUnit, unitId, unitGuid, archiveUnitTree, globalMgtIdExtra);

                updateManagementAndAppendGlobalMgtRule(archiveUnit, globalMgtIdExtra);

                if(isThereManifestRelatedReferenceRemained.get(unitId) != null && isThereManifestRelatedReferenceRemained.get(unitId)) {
                    postReplaceInternalReferenceForRelatedObjectReference(archiveUnit);
                }
                // Write to new File
                JsonHandler.writeAsFile(archiveUnit, unitCompleteTmpFile);
                // Write to workspace
                try {
                    handlerIO
                        .transferFileToWorkspace(path + File.separator + unitGuid + JSON_EXTENSION,
                            unitCompleteTmpFile, true, asyncIO);
                } finally {
                    if (!unitTmpFileForRead.delete()) {
                        LOGGER.warn(FILE_COULD_NOT_BE_DELETED_MSG);
                    }
                }
            }

            // 3- Update created Unit life cycles
            addFinalStatusToUnitLifeCycle(unitGuid, unitId, isRootArchive);

            uuids.add(unitGuid);

            if (uuids.size() == BATCH_SIZE) {
                bulkLifeCycleUnit(containerId, logbookLifeCycleClient, uuids);
                uuids.clear();
            }
        }

        // finish missing AU
        if (!uuids.isEmpty()) {
            bulkLifeCycleUnit(containerId, logbookLifeCycleClient, uuids);
            uuids.clear();
        }
    }

    private void addStorageInformation(ObjectNode archiveUnit, JsonNode storageInfo) {
        ObjectNode archiveUnitNode = (ObjectNode) archiveUnit.get(SedaConstants.TAG_ARCHIVE_UNIT);
        archiveUnitNode.set(SedaConstants.STORAGE, storageInfo);
    }
    /**
     * <p>Finalize filling of sytemGUID for all reference items of RelatedObjectReference (RelationGroup) instead of internal seda id (defined in manifest).<br>
     *     not set yet by first pass call (one parsing) in method
     *  {@link fr.gouv.vitam.worker.core.extractseda.ArchiveUnitListener#replaceInternalReferenceForRelatedObjectReference(String, DescriptiveMetadataModel)}
     * </p>
     * @param archiveUnit
     * @throws InvalidParseOperationException if json serialization fail
     */
    private void postReplaceInternalReferenceForRelatedObjectReference(ObjectNode archiveUnit)
        throws InvalidParseOperationException {

        ObjectNode archiveUnitNode = (ObjectNode) archiveUnit.get(SedaConstants.TAG_ARCHIVE_UNIT);
        DescriptiveMetadataContentType.RelatedObjectReference archiveUnitRelatedObjectReference;

        if (archiveUnitNode.has(SedaConstants.TAG_RELATED_OBJECT_REFERENCE) &&
            archiveUnitNode.get(SedaConstants.TAG_RELATED_OBJECT_REFERENCE) instanceof ObjectNode) {
            archiveUnitRelatedObjectReference =
                JsonHandler.getFromJsonNode(archiveUnitNode.get(SedaConstants.TAG_RELATED_OBJECT_REFERENCE), DescriptiveMetadataContentType.RelatedObjectReference.class);

            fillArchiveUnitReference(archiveUnitRelatedObjectReference.getIsVersionOf());
            fillArchiveUnitReference(archiveUnitRelatedObjectReference.getReplaces());
            fillArchiveUnitReference(archiveUnitRelatedObjectReference.getRequires());
            fillArchiveUnitReference(archiveUnitRelatedObjectReference.getIsPartOf());
            fillArchiveUnitReference(archiveUnitRelatedObjectReference.getReferences());

            ObjectNode archiveUnitRelatedObjectReferenceNode = (ObjectNode) JsonHandler.toJsonNode(archiveUnitRelatedObjectReference);
            archiveUnitNode.set(SedaConstants.TAG_RELATED_OBJECT_REFERENCE, archiveUnitRelatedObjectReferenceNode);
        }

    }

    private void fillArchiveUnitReference(List<DataObjectOrArchiveUnitReferenceType> dataObjectOrArchiveUnitReference) {

        for (DataObjectOrArchiveUnitReferenceType relatedObjectReferenceItem : dataObjectOrArchiveUnitReference) {

            String archiveUnitRefId = relatedObjectReferenceItem.getArchiveUnitRefId();

            if (archiveUnitRefId != null) {
                if (unitIdToGuid.containsKey(archiveUnitRefId)) {
                    relatedObjectReferenceItem.setArchiveUnitRefId(unitIdToGuid.get(archiveUnitRefId));
                }
            }
        }
    }

    private void bulkLifeCycleUnit(String containerId, LogbookLifeCyclesClient logbookLifeCycleClient,
        List<String> uuids)
        throws LogbookClientBadRequestException, LogbookClientServerException {
        List<LogbookLifeCycleUnitModel> collect =
            uuids.stream()
                .filter(value -> handlerIO.getHelper().containsUpdate(value))
                .map(value -> {
                    Queue<? extends LogbookLifeCycleParameters> logbookLifeCycleParameters1 =
                        handlerIO.getHelper().removeUpdateDelegate(value);
                    Collection<LogbookLifeCycleUnitParameters> logbookLifeCycleParameters =
                        (Collection<LogbookLifeCycleUnitParameters>) logbookLifeCycleParameters1;

                    return new LogbookLifeCycleUnitModel(value, logbookLifeCycleParameters);
                }).collect(Collectors.toList());
        try {
            logbookLifeCycleClient.bulkUnit(containerId, collect);
        } catch (LogbookClientAlreadyExistsException e) {
            throw new RuntimeException(e);
        }
    }

    private void bulkLifeCycleObjectGroup(String containerId, LogbookLifeCyclesClient logbookLifeCycleClient,
        List<String> uuids)
        throws LogbookClientBadRequestException, LogbookClientServerException {
        List<LogbookLifeCycleObjectGroupModel> collect =
            uuids.stream()
                .filter(value -> handlerIO.getHelper().containsCreate(value))
                .map(value -> {
                    Queue<? extends LogbookLifeCycleParameters> logbookLifeCycleParameters1 =
                        handlerIO.getHelper().removeCreateDelegate(value);
                    Collection<LogbookLifeCycleObjectGroupParameters> logbookLifeCycleParameters =
                        (Collection<LogbookLifeCycleObjectGroupParameters>) logbookLifeCycleParameters1;

                    return new LogbookLifeCycleObjectGroupModel(value, logbookLifeCycleParameters);
                }).collect(Collectors.toList());
        try {
            logbookLifeCycleClient.bulkObjectGroup(containerId, collect);
        } catch (LogbookClientAlreadyExistsException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Merge global rules to specific archive rules and clean management node
     *
     * @param archiveUnit archiveUnit
     * @param globalMgtIdExtra list of global management rule ids
     * @throws InvalidParseOperationException
     */
    private void updateManagementAndAppendGlobalMgtRule(ObjectNode archiveUnit, Set<String> globalMgtIdExtra)
        throws InvalidParseOperationException {

        ObjectNode archiveUnitNode = (ObjectNode) archiveUnit.get(SedaConstants.TAG_ARCHIVE_UNIT);
        ManagementModel archiveUnitManagementModel;

        if (archiveUnitNode.has(SedaConstants.TAG_MANAGEMENT) &&
            archiveUnitNode.get(SedaConstants.TAG_MANAGEMENT) instanceof ObjectNode) {
            archiveUnitManagementModel =
                JsonHandler.getFromJsonNode(archiveUnitNode.get(SedaConstants.TAG_MANAGEMENT), ManagementModel.class);
        } else {
            archiveUnitManagementModel = new ManagementModel();
        }
        for (final String ruleId : globalMgtIdExtra) {
            final StringWriter stringWriter = mngtMdRuleIdToRulesXml.get(ruleId);
            JsonNode stringWriterNode = JsonHandler.getFromString(stringWriter.toString());
            JsonNode globalMgtRuleNode = stringWriterNode.get(GLOBAL_MGT_RULE_TAG);
            Iterator<String> ruleTypes = globalMgtRuleNode.fieldNames();

            while (ruleTypes.hasNext()) {
                String ruleType = ruleTypes.next();
                JsonNode globalMgtRuleTypeNode = globalMgtRuleNode.get(ruleType);
                if (globalMgtRuleTypeNode.isArray()) {
                    for (JsonNode globalMgtRuleTypeItemNode : globalMgtRuleTypeNode) {
                        mergeRule(globalMgtRuleTypeItemNode, archiveUnitManagementModel, ruleType);
                    }
                } else {
                    mergeRule(globalMgtRuleTypeNode, archiveUnitManagementModel, ruleType);
                }
            }
        }
        ObjectNode archiveUnitMgtNode = (ObjectNode) JsonHandler.toJsonNode(archiveUnitManagementModel);
        if (archiveUnitMgtNode != null) {
            for (RuleTypeName ruleType : RuleTypeName.values()) {
                String name = ruleType.getType();
                if (archiveUnitMgtNode.get(name) != null && archiveUnitMgtNode.get(name).get(RULES) != null &&
                    archiveUnitMgtNode.get(name).get(RULES).size() == 0) {
                    ObjectNode ruleNode = (ObjectNode) archiveUnitMgtNode.get(name);
                    ruleNode.remove(RULES);
                }
            }
        }
        archiveUnitNode.set(SedaConstants.TAG_MANAGEMENT, archiveUnitMgtNode);
    }

    /**
     * Merge global management rule in root units management rules.
     *
     * @param globalMgtRuleNode global management node
     * @param archiveUnitManagementModel rule management model
     * @param ruleType category of rule
     * @throws InvalidParseOperationException
     */
    private void mergeRule(JsonNode globalMgtRuleNode, ManagementModel archiveUnitManagementModel, String ruleType)
        throws InvalidParseOperationException {
        RuleCategoryModel ruleCategoryModel = archiveUnitManagementModel.getRuleCategoryModel(ruleType);
        if (ruleCategoryModel == null) {
            ruleCategoryModel = new RuleCategoryModel();
        }
        if (ruleCategoryModel.isPreventInheritance()) {
            return;
        }

        RuleModel ruleModel = JsonHandler.getFromJsonNode(globalMgtRuleNode, RuleModel.class);
        if (ruleModel.getRule() != null) {
            if (ruleCategoryModel.getInheritance() != null &&
                ruleCategoryModel.getInheritance().getPreventRulesId() != null &&
                ruleCategoryModel.getInheritance().getPreventRulesId().contains(ruleModel.getRule())) {
                return;
            } else {
                ruleCategoryModel.getRules().add(ruleModel);
            }
        }
        if (globalMgtRuleNode.has(SedaConstants.TAG_RULE_PREVENT_INHERITANCE)) {
            ruleCategoryModel
                .setPreventInheritance(globalMgtRuleNode.get(SedaConstants.TAG_RULE_PREVENT_INHERITANCE).asBoolean());
        }
        if (globalMgtRuleNode.has(SedaConstants.TAG_RULE_REF_NON_RULE_ID)) {
            if (globalMgtRuleNode.get(SedaConstants.TAG_RULE_REF_NON_RULE_ID).isArray()) {
                for (JsonNode refNonRuleId : globalMgtRuleNode.get(SedaConstants.TAG_RULE_REF_NON_RULE_ID)) {
                    ruleCategoryModel.addToPreventRulesId(refNonRuleId.asText());
                }
            } else {
                ruleCategoryModel
                    .addToPreventRulesId(globalMgtRuleNode.get(SedaConstants.TAG_RULE_REF_NON_RULE_ID).asText());
            }
        }

        JsonNode finalAction = globalMgtRuleNode.get(SedaConstants.TAG_RULE_FINAL_ACTION);
        if (finalAction != null && ruleCategoryModel.getFinalAction() == null) {
            ruleCategoryModel.setFinalAction(finalAction.asText());
        }

        archiveUnitManagementModel.setRuleCategoryModel(ruleCategoryModel, ruleType);

    }

    private boolean addWorkInformation(ObjectNode archiveUnit, String unitId, String unitGuid,
        ObjectNode archiveUnitTree, Set<String> globalMgtIdExtra)
        throws XMLStreamException {

        ObjectNode workNode = JsonHandler.createObjectNode();

        // Get parents list
        ArrayNode upNode = JsonHandler.createArrayNode();
        boolean isRootArchive = addParentsToTmpFile(upNode, unitId, archiveUnitTree);

        if (upNode.isEmpty(null)) {
            linkToArchiveUnitDeclaredInTheIngestContract(upNode);
        } else {
            isRootArchive = true;
            for (JsonNode parent : upNode) {
                if (!existingUnitGuids.contains(parent.asText())) {
                    isRootArchive = false;
                }
            }

            if (isRootArchive) {
                linkToArchiveUnitDeclaredInTheIngestContract(upNode);
            }
        }
        workNode.set(IngestWorkflowConstants.UP_FIELD, upNode);

        // Determine rules to apply
        ArrayNode rulesNode = JsonHandler.createArrayNode();
        globalMgtIdExtra.addAll(getMgtRulesToApplyByUnit(rulesNode, unitId, isRootArchive));
        workNode.set(IngestWorkflowConstants.RULES, rulesNode);

        // Add existing guid
        if (existingUnitGuids.contains(unitGuid)) {
            workNode.put(IngestWorkflowConstants.EXISTING_TAG, Boolean.TRUE);
        }

        archiveUnit.set(SedaConstants.PREFIX_WORK, workNode);

        return isRootArchive;
    }

    private void createUnitLifeCycle(String unitGuid, String containerId)
        throws LogbookClientNotFoundException, LogbookClientBadRequestException, LogbookClientServerException {

        if (guidToLifeCycleParameters.get(unitGuid) != null) {
            if (!existingUnitGuids.contains(unitGuid)) {
                LogbookLifeCycleUnitParameters unitLifeCycle =
                    createUnitLifeCycle(unitGuid, containerId, LogbookTypeProcess.INGEST);

                handlerIO.getHelper().updateDelegate(unitLifeCycle);
            }
        }
    }

    private void addFinalStatusToUnitLifeCycle(String unitGuid, String unitId, boolean isRootArchive)
        throws LogbookClientNotFoundException, LogbookClientBadRequestException, LogbookClientServerException {

        if (guidToLifeCycleParameters.get(unitGuid) != null) {
            final LogbookLifeCycleParameters llcp = guidToLifeCycleParameters.get(unitGuid);
            String eventId = GUIDFactory.newEventGUID(ParameterHelper.getTenantParameter()).toString();
            LogbookLifeCycleParameters subLlcp = null;
            // TODO : add else case
            if (!existingUnitGuids.contains(unitGuid)) {
                subLlcp = LogbookLifeCyclesClientHelper.copy(llcp);
                // generate new eventId for task
                subLlcp.putParameterValue(LogbookParameterName.eventIdentifier,
                    GUIDFactory.newEventGUID(ParameterHelper.getTenantParameter()).toString());
                // set parentEventId
                subLlcp.putParameterValue(LogbookParameterName.parentEventIdentifier, eventId);
                // set status for sub task
                subLlcp.setFinalStatus(HANDLER_ID, LFC_CREATION_SUB_TASK_ID, StatusCode.OK, null);
            }
            // generate new eventId for task
            llcp.putParameterValue(LogbookParameterName.eventIdentifier, eventId);
            // set status for task
            llcp.setFinalStatus(HANDLER_ID, null, StatusCode.OK, null);

            Set<String> parentAttachments = existAttachmentUnitAsParentOnTree(unitId);

            if (isRootArchive && linkParentId != null) {
                parentAttachments.add(linkParentId);
            }

            ObjectNode evDetData = JsonHandler.createObjectNode();

            if (parentAttachments.size() > 0) {
                ArrayNode arrayNode = JsonHandler.createArrayNode();
                parentAttachments.forEach(arrayNode::add);
                evDetData.set(ATTACHMENT_IDS, arrayNode);
            }

            if (unitIdToGroupId.containsKey(unitId) && existingGOTs.containsKey(unitIdToGroupId.get(unitId))) {
                evDetData.put(OBJECT_GROUP_ID, unitIdToGroupId.get(unitId));
            }

            try {
                String wellFormedJson = JsonHandler.writeAsString(evDetData);
                llcp.putParameterValue(LogbookParameterName.eventDetailData, wellFormedJson);
            } catch (InvalidParseOperationException e) {
                LOGGER.error("unable to generate evDetData, incomplete journal generation", e);
            }

            // update delegate
            handlerIO.getHelper().updateDelegate(llcp);
            if (!existingUnitGuids.contains(unitGuid)) {
                handlerIO.getHelper().updateDelegate(subLlcp);
            }

            // FIXME: use bulk
            // logbookLifeCycleClient.bulkUpdateUnit(containerId, handlerIO.getHelper().removeUpdateDelegate(unitGuid));
        }
    }

    private LogbookLifeCycleUnitParameters createUnitLifeCycle(String unitGuid, String containerId,
        LogbookTypeProcess logbookTypeProcess) {
        final LogbookLifeCycleUnitParameters logbookLifecycleUnitParameters =
            (LogbookLifeCycleUnitParameters) initLogbookLifeCycleParameters(unitGuid, true, false);

        logbookLifecycleUnitParameters.setFinalStatus(LFC_INITIAL_CREATION_EVENT_TYPE, null, StatusCode.OK, null);

        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.eventIdentifierProcess, containerId);
        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.eventIdentifier,
            GUIDFactory.newEventGUID(ParameterHelper.getTenantParameter()).toString());
        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.eventTypeProcess,
            logbookTypeProcess.name());

        return logbookLifecycleUnitParameters;
    }


    private Set<String> existAttachmentUnitAsParentOnTree(String unitId) {
        Set<String> parents = new HashSet<>();
        if (archiveUnitTree.has(unitId)) {
            JsonNode archiveNode = archiveUnitTree.get(unitId);
            if (archiveNode.has(IngestWorkflowConstants.UP_FIELD)) {
                final JsonNode archiveUps = archiveNode.get(IngestWorkflowConstants.UP_FIELD);
                if (archiveUps.isArray() && archiveUps.size() > 0) {
                    ArrayNode archiveUpsArray = (ArrayNode) archiveUps;
                    for (JsonNode jsonNode : archiveUpsArray) {
                        String archiveUnitId = jsonNode.textValue();
                        String guid = unitIdToGuid.get(archiveUnitId);

                        if (existingUnitGuids.contains(guid)) {
                            parents.add(guid);
                        }
                    }
                }
            }
        }
        return parents;
    }

    private boolean addParentsToTmpFile(ArrayNode upNode, String unitId, ObjectNode archiveUnitTree)
        throws XMLStreamException {

        boolean isRootArchive = true;
        if (archiveUnitTree.has(unitId)) {
            final JsonNode archiveNode = archiveUnitTree.get(unitId);
            if (archiveNode.has(IngestWorkflowConstants.UP_FIELD)) {
                // add archive units parents and originating agency
                final JsonNode archiveUps = archiveNode.get(IngestWorkflowConstants.UP_FIELD);
                if (archiveUps.isArray() && archiveUps.size() > 0) {
                    ArrayNode ups = (ArrayNode) archiveUps;
                    upNode.addAll(getUnitParents(ups));
                    isRootArchive = false;
                }
            }
        }

        return isRootArchive;
    }

    private Set<String> getMgtRulesToApplyByUnit(ArrayNode rulesNode, String unitId, boolean isRootArchive)
        throws XMLStreamException {

        String listRulesForCurrentUnit = "";
        if (unitIdToSetOfRuleId != null && unitIdToSetOfRuleId.containsKey(unitId)) {
            listRulesForCurrentUnit = getListOfRulesFormater(unitIdToSetOfRuleId.get(unitId));
        }

        String listRulesForAuRoot = "";
        Set<String> globalMgtIdExtra = new HashSet<>();

        if (isRootArchive) {
            // Add rules from global Management Data (only new
            // ones)
            if (mngtMdRuleIdToRulesXml != null && !mngtMdRuleIdToRulesXml.isEmpty()) {
                globalMgtIdExtra.clear();
                globalMgtIdExtra.addAll(mngtMdRuleIdToRulesXml.keySet());
            }

            if (!globalMgtIdExtra.isEmpty() && unitIdToSetOfRuleId != null && unitIdToSetOfRuleId.get(unitId) != null &&
                !unitIdToSetOfRuleId.get(unitId).isEmpty()) {
                globalMgtIdExtra.removeAll(unitIdToSetOfRuleId.get(unitId));
            }

            if (!globalMgtIdExtra.isEmpty()) {
                listRulesForAuRoot = getListOfRulesFormater(globalMgtIdExtra);
            }
        }

        final StringBuilder rules = new StringBuilder();
        if (!Strings.isNullOrEmpty(listRulesForCurrentUnit)) {
            rules.append(listRulesForCurrentUnit);
        }
        if (!Strings.isNullOrEmpty(listRulesForAuRoot)) {
            rules.append(listRulesForAuRoot);
        }

        if (ParametersChecker.isNotEmpty(rules.toString())) {
            rulesNode.add(rules.toString());
        }

        return globalMgtIdExtra;
    }

    private String getListOfRulesFormater(Set<String> rulesId) {
        final StringBuilder sbRules = new StringBuilder();
        if (rulesId != null) {
            for (final String ruleId : rulesId) {
                sbRules.append(ruleId).append(SedaConstants.RULE_SEPARATOR);
            }
        }
        return sbRules.toString();
    }

    private ArrayNode getUnitParents(ArrayNode parents) {
        final ArrayNode parentsList = JsonHandler.createArrayNode();
        for (final JsonNode currentParentNode : parents) {
            final String currentParentId = currentParentNode.asText();
            if (unitIdToGuid.containsKey(currentParentId)) {
                parentsList.add(unitIdToGuid.get(currentParentId));
            }
        }
        return parentsList;
    }

    private void checkArchiveUnitIdReference(String llcEvDetData) throws ProcessingException {

        if (unitIdToGroupId != null && !unitIdToGroupId.isEmpty()) {
            for (final Entry<String, String> entry : unitIdToGroupId.entrySet()) {
                if (objectGroupIdToGuid.get(entry.getValue()) == null) {
                    final String groupId = dataObjectIdToObjectGroupId.get(entry.getValue()); // the AU reference
                    // an BDO
                    if (Strings.isNullOrEmpty(groupId)) {
                        throw new ProcessingException("Archive Unit references a BDO Id but is not correct");
                    } else {
                        if (!groupId.equals(entry.getValue())) {
                            throw new ArchiveUnitContainDataObjectException(
                                "The archive unit " + entry.getKey() + " references one BDO Id " + entry.getValue() +
                                    " while this BDO has a GOT id " + groupId,
                                entry.getKey(), entry.getValue(), groupId, llcEvDetData);
                        }
                    }
                }
            }
        }
    }

    private String writeDataObjectInLocal(XMLEventReader reader, StartElement startElement, String currentGroupId)
        throws ProcessingException {
        final String elementGuid = GUIDFactory.newGUID().toString();
        final File tmpFile = handlerIO.getNewLocalFile(elementGuid + JSON_EXTENSION);
        final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        final JsonXMLConfig config = new JsonXMLConfigBuilder().autoArray(true).autoPrimitive(true).prettyPrint(true)
            .namespaceDeclarations(false).build();
        String groupGuid = null;
        boolean isTraversingNestedGroupTags = false;
        try {

            final FileWriter tmpFileWriter = new FileWriter(tmpFile);

            final XMLEventWriter jsonWriter = new JsonXMLOutputFactory(config).createXMLEventWriter(tmpFileWriter);

            final Iterator<?> it = startElement.getAttributes();
            String dataObjectId = "";
            final DataObjectInfo bo = new DataObjectInfo();
            final DataObjectDetail detail = new DataObjectDetail();

            if (it.hasNext()) { // id is always required
                dataObjectId = ((Attribute) it.next()).getValue();
                dataObjectIdToGuid.put(dataObjectId, elementGuid);
                dataObjectIdToObjectGroupId.put(dataObjectId, currentGroupId != null ? currentGroupId : "");

                jsonWriter.add(eventFactory.createStartDocument());
                jsonWriter.add(eventFactory.createStartElement("", "", startElement.getName().getLocalPart()));
                jsonWriter.add(eventFactory.createStartElement("", "", SedaConstants.PREFIX_ID));
                jsonWriter.add(eventFactory.createCharacters(dataObjectId));
                jsonWriter.add(eventFactory.createEndElement("", "", SedaConstants.PREFIX_ID));

                // ObjectGroup Wrapping mode (seda version >= 2.1)
                if (currentGroupId != null) {
                    if (objectGroupIdToDataObjectId.get(currentGroupId) == null) {
                        final List<String> dataOjectList = new ArrayList<>();
                        dataOjectList.add(dataObjectId);
                        objectGroupIdToDataObjectId.put(currentGroupId, dataOjectList);
                    } else {
                        objectGroupIdToDataObjectId.get(currentGroupId).add(dataObjectId);
                    }

                    groupGuid = GUIDFactory.newObjectGroupGUID(ParameterHelper.getTenantParameter())
                        .toString();

                    objectGroupIdToGuid.put(currentGroupId, groupGuid);

                    // Create new startElement for group with new guid
                    jsonWriter.add(eventFactory.createStartElement("", "", DATA_OBJECT_GROUPID));
                    jsonWriter.add(eventFactory.createCharacters(currentGroupId));
                    jsonWriter.add(eventFactory.createEndElement("", "", DATA_OBJECT_GROUPID));

                    isTraversingNestedGroupTags = true;
                }
            }

            while (true) {
                boolean writable = true;
                final XMLEvent event = reader.nextEvent();
                if (event.isEndElement()) {
                    final EndElement end = event.asEndElement();
                    if (BINARY_DATA_OBJECT.equals(end.getName().getLocalPart()) ||
                        PHYSICAL_DATA_OBJECT.equals(end.getName().getLocalPart())) {
                        jsonWriter.add(event);
                        jsonWriter.add(eventFactory.createEndDocument());
                        objectGuidToDataObject.put(elementGuid, bo);
                        if (PHYSICAL_DATA_OBJECT.equals(end.getName().getLocalPart())) {
                            physicalDataObjetsGuids.add(elementGuid);
                            detail.setPhysical(true);
                        }
                        dataObjectIdToDetailDataObject.put(dataObjectId, detail);
                        break;
                    }

                }

                if (event.isStartElement()) {
                    final String localPart = event.asStartElement().getName().getLocalPart();

                    // extract info for version DBO
                    switch (localPart) {
                        case SedaConstants.TAG_DO_VERSION: {
                            final String version = reader.getElementText();
                            detail.setVersion(version);
                            bo.setVersion(version);
                            jsonWriter.add(eventFactory.createStartElement("", "", localPart));
                            jsonWriter.add(eventFactory.createCharacters(version));
                            jsonWriter.add(eventFactory.createEndElement("", "", localPart));
                            break;
                        }
                        case DATA_OBJECT_GROUPID: {
                            if (currentGroupId == null) { // ignore if ObjectGroup wrapping mode

                                groupGuid = GUIDFactory.newObjectGroupGUID(ParameterHelper.getTenantParameter())
                                    .toString();
                                final String groupId = reader.getElementText();
                                // Having DataObjectGroupID after a DataObjectGroupReferenceID in the XML flow.
                                // We get the GUID defined earlier during the DataObjectGroupReferenceID analysis
                                if (objectGroupIdToGuidTmp.get(groupId) != null) {
                                    groupGuid = objectGroupIdToGuidTmp.get(groupId);
                                    objectGroupIdToGuidTmp.remove(groupId);
                                }
                                dataObjectIdToObjectGroupId.put(dataObjectId, groupId);
                                objectGroupIdToGuid.put(groupId, groupGuid);

                                // Create OG lifeCycle

                                // FIXME : probably a bad idea
                                // createObjectGroupLifeCycle(groupGuid, containerId, typeProcess);
                                if (objectGroupIdToDataObjectId.get(groupId) == null) {
                                    final List<String> dataOjectList = new ArrayList<>();
                                    dataOjectList.add(dataObjectId);
                                    objectGroupIdToDataObjectId.put(groupId, dataOjectList);
                                } else {
                                    objectGroupIdToDataObjectId.get(groupId).add(dataObjectId);
                                }

                                // Create new startElement for group with new guid
                                jsonWriter.add(eventFactory.createStartElement("", "", DATA_OBJECT_GROUPID));
                                jsonWriter.add(eventFactory.createCharacters(groupGuid));
                                jsonWriter.add(eventFactory.createEndElement("", "", DATA_OBJECT_GROUPID));
                            } else {
                                isTraversingNestedGroupTags = true;
                            }

                            break;
                        }
                        case SedaConstants.TAG_DATA_OBJECT_GROUP_REFERENCEID: {
                            if (currentGroupId == null) { // ignore if ObjectGroup wrapping mode
                                final String groupId = reader.getElementText();
                                String groupGuidTmp = GUIDFactory.newGUID().toString();
                                dataObjectIdToObjectGroupId.put(dataObjectId, groupId);
                                // The DataObjectGroupReferenceID is after
                                // DataObjectGroupID in the XML flow
                                if (objectGroupIdToDataObjectId.get(groupId) != null) {
                                    objectGroupIdToDataObjectId.get(groupId).add(dataObjectId);
                                    groupGuidTmp = objectGroupIdToGuid.get(groupId);
                                } else {
                                    // The DataObjectGroupReferenceID is before DataObjectGroupID in the XML flow
                                    final List<String> dataOjectList = new ArrayList<>();
                                    dataOjectList.add(dataObjectId);
                                    objectGroupIdToDataObjectId.put(groupId, dataOjectList);
                                    objectGroupIdToGuidTmp.put(groupId, groupGuidTmp);
                                }

                                // Create new startElement for group with new guid
                                jsonWriter.add(eventFactory.createStartElement("", "", DATA_OBJECT_GROUPID));
                                jsonWriter.add(eventFactory.createCharacters(groupGuidTmp));
                                jsonWriter.add(eventFactory.createEndElement("", "", DATA_OBJECT_GROUPID));
                            } else {
                                isTraversingNestedGroupTags = true;
                            }
                            break;
                        }
                        case SedaConstants.TAG_URI: {
                            final String uri = reader.getElementText();
                            bo.setUri(uri);
                            break;
                        }
                        case SedaConstants.TAG_SIZE: {
                            final long size = Long.parseLong(reader.getElementText());
                            bo.setSize(size);
                            break;
                        }
                        case SedaConstants.TAG_DIGEST: {
                            final String messageDigest = reader.getElementText();
                            bo.setMessageDigest(messageDigest);
                            final Iterator<?> it1 = event.asStartElement().getAttributes();

                            if (it1.hasNext()) {
                                final String al = ((Attribute) it1.next()).getValue();
                                final DigestType d = DigestType.fromValue(al);
                                bo.setAlgo(d);
                            }
                            break;
                        }
                        case "PhysicalDimensions": {
                            extractPhysicalDimensions(reader, jsonWriter, eventFactory, event.asStartElement());
                            break;
                        }

                        default: {
                            jsonWriter.add(eventFactory.createStartElement("", "", localPart));
                            isTraversingNestedGroupTags = false;
                        }

                    }

                    writable = false;
                }

                if (writable && !isTraversingNestedGroupTags) {
                    jsonWriter.add(event);
                }
            }
            reader.close();
            jsonWriter.close();
            tmpFileWriter.close();

            if (Strings.isNullOrEmpty(dataObjectIdToObjectGroupId.get(dataObjectId))) {
                // not have object group, must creat an technical object group
                LOGGER.debug("DO {} not have an GDO", dataObjectId);
                dataObjectIdToObjectGroupId.remove(dataObjectId);
                postDataObjectActions(elementGuid + JSON_EXTENSION, dataObjectId);
            }


        } catch (final XMLStreamException e) {
            LOGGER.debug("Can not read input stream");
            throw new ProcessingException(e);
        } catch (final InvalidParseOperationException e) {
            LOGGER.debug("Can not parse data object json");
            throw new ProcessingException(e);
        } catch (final IOException e) {
            LOGGER.debug("Closing stream error");
            throw new ProcessingException(e);
        }

        return groupGuid;

    }

    /**
     * Extract all physical dimensions and manage unit attributes : replace dimensions containing a unit attribute by an
     * object with format: <myDimension> <unit>unitValue</unit> <value>dimensionValue</value> </myDimension>
     *
     * @param reader
     * @param writer
     * @param eventFactory
     * @param startElement
     * @throws XMLStreamException
     */
    private void extractPhysicalDimensions(XMLEventReader reader, XMLEventWriter writer, XMLEventFactory eventFactory,
        StartElement startElement)
        throws XMLStreamException {
        final QName physicalDimensionsName = startElement.getName();
        writer.add(eventFactory.createStartElement("", "", physicalDimensionsName.getLocalPart()));
        while (true) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                writer.add(eventFactory.createStartElement("", "",
                    event.asStartElement().getName().getLocalPart()));
                Iterator<Attribute> attributes = (Iterator<Attribute>) event.asStartElement().getAttributes();
                while (attributes.hasNext()) {
                    Attribute attribute = attributes.next();
                    if (SedaConstants.TAG_ATTRIBUTE_UNIT.equals(attribute.getName().getLocalPart())) {
                        writer.add(eventFactory.createStartElement("", "", SedaConstants.TAG_ATTRIBUTE_UNIT));
                        writer.add(eventFactory.createCharacters(attribute.getValue()));
                        writer.add(eventFactory.createEndElement("", "", SedaConstants.TAG_ATTRIBUTE_UNIT));
                        event = reader.nextEvent();
                        writer.add(eventFactory.createStartElement("", "", SedaConstants.TAG_VALUE));
                        writer.add(event.asCharacters());
                        writer.add(eventFactory.createEndElement("", "", SedaConstants.TAG_VALUE));
                    }
                }
            } else if (event.isCharacters()) {
                writer.add(event.asCharacters());
            } else if (event.isEndElement()) {
                String endElementName = event.asEndElement().getName().getLocalPart();
                writer.add(eventFactory.createEndElement("", "", event.asEndElement().getName().getLocalPart()));
                if (physicalDimensionsName.getLocalPart().equals(endElementName)) {
                    return;
                }
            } else if (event.isEndDocument()) {
                writer.add(eventFactory.createEndDocument());
            }
        }
    }

    /**
     * Post actions when reading data object in manifest
     *
     * @param jsonFileName
     * @param dataOjectId
     * @return
     * @throws InvalidParseOperationException
     */
    private JsonNode postDataObjectActions(final String jsonFileName, final String dataOjectId)
        throws InvalidParseOperationException {
        final File tmpJsonFile = handlerIO.getNewLocalFile(jsonFileName);
        final JsonNode jsonBDO = JsonHandler.getFromFile(tmpJsonFile);
        JsonNode objectNode = mapNewTechnicalDataObjectGroupToDO(jsonBDO, dataOjectId);
        objectNode = addExtraField(objectNode);
        if (objectNode.get(BINARY_DATA_OBJECT) != null) {
            DataObjectDetail detail = new DataObjectDetail();
            detail.setVersion(objectNode.get(BINARY_DATA_OBJECT).get(SedaConstants.TAG_DO_VERSION).textValue());
            dataObjectIdToDetailDataObject.put(dataOjectId, detail);
        } else if (objectNode.get(PHYSICAL_DATA_OBJECT) != null) {
            DataObjectDetail detail = new DataObjectDetail();
            detail.setVersion(objectNode.get(PHYSICAL_DATA_OBJECT).get(SedaConstants.TAG_DO_VERSION).textValue());
            dataObjectIdToDetailDataObject.put(dataOjectId, detail);
        } else {
            throw new InvalidParseOperationException("Object should have a Binary or a Physical subnode");
        }
        JsonHandler.writeAsFile(objectNode,
            handlerIO.getNewLocalFile(jsonFileName)); // write the new DataObject
        return objectNode;
    }

    /**
     * add fields that is missing in manifest
     *
     * @param objectNode
     * @return
     */
    private JsonNode addExtraField(JsonNode objectNode) throws InvalidParseOperationException {
        if (objectNode.get(BINARY_DATA_OBJECT) != null) {
            final ObjectNode bdoObjNode = (ObjectNode) objectNode.get(BINARY_DATA_OBJECT);
            if (bdoObjNode.get(SedaConstants.TAG_DO_VERSION) == null ||
                Strings.isNullOrEmpty(bdoObjNode.get(SedaConstants.TAG_DO_VERSION).textValue())) {
                bdoObjNode.put(SedaConstants.TAG_DO_VERSION, BINARY_MASTER);
            }
            return JsonHandler.createObjectNode().set(BINARY_DATA_OBJECT, bdoObjNode);
        } else if (objectNode.get(PHYSICAL_DATA_OBJECT) != null) {
            final ObjectNode pdoObjNode = (ObjectNode) objectNode.get(PHYSICAL_DATA_OBJECT);
            if (pdoObjNode.get(SedaConstants.TAG_DO_VERSION) == null ||
                Strings.isNullOrEmpty(pdoObjNode.get(SedaConstants.TAG_DO_VERSION).textValue())) {
                pdoObjNode.put(SedaConstants.TAG_DO_VERSION, PHYSICAL_MASTER);
            }
            return JsonHandler.createObjectNode().set(PHYSICAL_DATA_OBJECT, pdoObjNode);
        }
        throw new InvalidParseOperationException("Data Object is neither Binary nor Physical");
    }

    /**
     * Creation of the technical new object group and update the maps
     *
     * @param jsonDataObject
     * @param dataOjectId
     * @return
     */
    private JsonNode mapNewTechnicalDataObjectGroupToDO(JsonNode jsonDataObject, String dataOjectId) {
        JsonNode dataObject = jsonDataObject.get(BINARY_DATA_OBJECT);
        if (dataObject == null) {
            dataObject = jsonDataObject.get(PHYSICAL_DATA_OBJECT);
        }
        final ObjectNode dataObjectNode = (ObjectNode) dataObject;

        final String technicalGotGuid =
            GUIDFactory.newObjectGroupGUID(ParameterHelper.getTenantParameter()).toString();
        objectGroupIdToGuid.put(technicalGotGuid, technicalGotGuid); // update object group id guid
        dataObjectNode.put(DATA_OBJECT_GROUPID, technicalGotGuid);

        if (Strings.isNullOrEmpty(dataObjectIdToObjectGroupId.get(dataOjectId))) {
            dataObjectIdToObjectGroupId.put(dataOjectId, technicalGotGuid);
        } else {
            LOGGER.warn("unexpected state - dataObjectIdToObjectGroupId contains the GOT and should not");
        }

        final String gotGuid = dataObjectIdWithoutObjectGroupId.get(dataOjectId) != null
            ? dataObjectIdWithoutObjectGroupId.get(dataOjectId).getGotGuid()
            : "";
        if (Strings.isNullOrEmpty(gotGuid)) {
            final GotObj gotObj = new GotObj(technicalGotGuid, false);
            dataObjectIdWithoutObjectGroupId.put(dataOjectId, gotObj);
            dataObjectIdToObjectGroupId
                .put(dataOjectId, technicalGotGuid); // update the list of bdo in the map
        } else {
            LOGGER.warn("unexpected state - dataObjectIdWithoutObjectGroupId contains the GOT and should not");
        }

        List<String> listDO = objectGroupIdToDataObjectId.get(technicalGotGuid);
        if (listDO != null && !listDO.contains(technicalGotGuid)) {
            listDO.add(dataOjectId);
            objectGroupIdToDataObjectId.put(technicalGotGuid, listDO);
        } else {
            listDO = new ArrayList<>();
            listDO.add(dataOjectId);
            objectGroupIdToDataObjectId.put(technicalGotGuid, listDO);
        }

        return jsonDataObject;
    }

    private void createObjectGroupLifeCycle(String groupGuid, String containerId, LogbookTypeProcess typeProcess)
        throws LogbookClientAlreadyExistsException {
        final LogbookLifeCycleObjectGroupParameters logbookLifecycleObjectGroupParameters =
            (LogbookLifeCycleObjectGroupParameters) initLogbookLifeCycleParameters(
                groupGuid, false, true);
        logbookLifecycleObjectGroupParameters.setFinalStatus(LFC_INITIAL_CREATION_EVENT_TYPE, null, StatusCode.OK,
            null);

        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventIdentifierProcess,
            containerId);
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventIdentifier,
            GUIDFactory.newEventGUID(ParameterHelper.getTenantParameter()).toString());
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventTypeProcess,
            typeProcess.name());

        handlerIO.getHelper().createDelegate(logbookLifecycleObjectGroupParameters);

        // Update guidToLifeCycleParameters
        guidToLifeCycleParameters.put(groupGuid, logbookLifecycleObjectGroupParameters);
    }

    /**
     * create level stack on Json file
     *
     * @param levelStackMap
     * @param rank
     * @throws ProcessingException
     */
    private void createIngestLevelStackFile(Map<Integer, Set<String>> levelStackMap, int rank)
        throws ProcessingException {
        LOGGER.debug("Begin createIngestLevelStackFile/containerId: ", handlerIO.getContainerName());
        ParametersChecker.checkParameter("levelStackMap is a mandatory parameter", levelStackMap);
        ParametersChecker.checkParameter("unitIdToGuid is a mandatory parameter", unitIdToGuid);

        File tempFile = null;
        try {
            tempFile = handlerIO.getNewLocalFile(handlerIO.getOutput(rank).getPath());
            // create level json object node
            final ObjectNode ingestLevelStack = JsonHandler.createObjectNode();
            for (final Entry<Integer, Set<String>> entry : levelStackMap.entrySet()) {
                final ArrayNode unitList = ingestLevelStack.withArray(LEVEL + entry.getKey());
                final Set<String> unitGuidList = entry.getValue();
                Set<String> alreadyAdded = new HashSet<>();
                for (final String idXml : unitGuidList) {

                    final String unitGuid = unitIdToGuid.get(idXml);
                    if (unitGuid == null) {
                        throw new IllegalArgumentException("Unit guid not found in map");
                    }

                    if (!alreadyAdded.contains(unitGuid)) {
                        alreadyAdded.add(unitGuid);
                        unitList.add(unitGuid);
                    }
                }
                ingestLevelStack.set(LEVEL + entry.getKey(), unitList);
            }
            LOGGER.debug("IngestLevelStack: {}", ingestLevelStack);
            // create json file
            JsonHandler.writeAsFile(ingestLevelStack, tempFile);
            // put file in workspace
            handlerIO.addOuputResult(rank, tempFile, true, asyncIO);
        } catch (final IllegalArgumentException | InvalidParseOperationException e) {
            LOGGER.error(e);
            throw new ProcessingException(e);
        }
        LOGGER.info("End createIngestLevelStackFile/containerId:" + handlerIO.getContainerName());

    }


    private LogbookParameters initLogbookLifeCycleParameters(String guid, boolean isArchive, boolean isObjectGroup) {
        LogbookParameters logbookLifeCycleParameters = guidToLifeCycleParameters.get(guid);
        if (logbookLifeCycleParameters == null) {
            logbookLifeCycleParameters = isArchive ? LogbookParametersFactory.newLogbookLifeCycleUnitParameters()
                : isObjectGroup ? LogbookParametersFactory.newLogbookLifeCycleObjectGroupParameters()
                    : LogbookParametersFactory.newLogbookOperationParameters();


            logbookLifeCycleParameters.putParameterValue(LogbookParameterName.objectIdentifier, guid);
        }
        return logbookLifeCycleParameters;
    }

    private void saveObjectGroupsToWorkspace(
        String containerId,
        LogbookLifeCyclesClient logbookLifeCycleClient, LogbookTypeProcess typeProcess, String originatingAgency,
        JsonNode storageInfo)
        throws ProcessingException {
        boolean existingGot = false;
        completeDataObjectToObjectGroupMap();

        // Save maps
        try {
            // Save dataObjectIdToObjectGroupId
            HandlerUtils.saveMap(handlerIO, dataObjectIdToObjectGroupId, DO_ID_TO_OG_ID_IO_RANK, true, asyncIO);
            // Save objectGroupIdToGuid
            HandlerUtils.saveMap(handlerIO, objectGroupIdToGuid, OG_ID_TO_GUID_IO_RANK, true, asyncIO);
            handlerIO.addOuputResult(OG_ID_TO_GUID_IO_MEMORY_RANK, objectGroupIdToGuid, asyncIO);
        } catch (final IOException e1) {
            LOGGER.error("Can not write to tmp folder ", e1);
            throw new ProcessingException(e1);
        }

        // check if folder OBJECT_GROUP_FOLDER is not empty, if it is not, that means we 're in the replay mode, so lets
        // purge it
        try {
            if (handlerIO.removeFolder(IngestWorkflowConstants.OBJECT_GROUP_FOLDER)) {
                LOGGER.warn("Folder has been deleted, it's a replay for this operation : " + containerId);
            }
        } catch (ContentAddressableStorageException e1) {
            LOGGER.warn("Couldnt delete folder", e1);
        }

        List<String> uuids = new ArrayList<>();


        for (final Entry<String, List<String>> entry : objectGroupIdToDataObjectId.entrySet()) {
            final ObjectNode objectGroup = JsonHandler.createObjectNode();
            ObjectNode fileInfo = JsonHandler.createObjectNode();
            final ArrayNode unitParent = JsonHandler.createArrayNode();
            String objectGroupType = "";

            String unitParentGUID = null;
            if (objectGroupIdToUnitId != null && objectGroupIdToUnitId.size() != 0) {
                if (objectGroupIdToUnitId.get(entry.getKey()) != null) {
                    for (final String objectGroupId : objectGroupIdToUnitId.get(entry.getKey())) {
                        if (unitIdToGuid.get(objectGroupId) != null) {
                            unitParentGUID = unitIdToGuid.get(objectGroupId);
                            unitParent.add(unitParentGUID);
                        }
                    }
                }
            }

            String objectGroupGuid = objectGroupIdToGuid.get(entry.getKey());
            if (existingUnitIdWithExistingObjectGroup.containsKey(unitParentGUID)) {
                existingGot = true;
                objectGroupGuid = existingUnitIdWithExistingObjectGroup.get(unitParentGUID);
            }
            final File tmpFile = handlerIO.getNewLocalFile(objectGroupGuid + JSON_EXTENSION);

            uuids.add(objectGroupGuid);

            try {

                final Map<String, ArrayList<JsonNode>> categoryMap = new HashMap<>();
                objectGroup.put(SedaConstants.PREFIX_ID, objectGroupGuid);
                objectGroup.put(SedaConstants.PREFIX_TENANT_ID, ParameterHelper.getTenantParameter());
                final List<String> versionList = new ArrayList<>();
                for (int index = 0; index < entry.getValue().size(); index++) {
                    final String id = entry.getValue().get(index);
                    final File dataObjectFile =
                        handlerIO.getNewLocalFile(dataObjectIdToGuid.get(id) + JSON_EXTENSION);
                    JsonNode dataObjectNode = JsonHandler.getFromFile(dataObjectFile).get(BINARY_DATA_OBJECT);

                    if (dataObjectNode == null) {
                        dataObjectNode = JsonHandler.getFromFile(dataObjectFile).get(PHYSICAL_DATA_OBJECT);
                    }
                    String nodeCategory = "";
                    if (dataObjectNode.get(SedaConstants.TAG_DO_VERSION) != null) {
                        nodeCategory = dataObjectNode.get(SedaConstants.TAG_DO_VERSION).asText();
                        if (versionList.contains(nodeCategory)) {
                            LOGGER.error(DATA_OBJECT_VERSION_MUST_BE_UNIQUE);
                            throw new ProcessingDuplicatedVersionException(DATA_OBJECT_VERSION_MUST_BE_UNIQUE);
                        }
                        versionList.add(nodeCategory);
                    }
                    ArrayList<JsonNode> nodeCategoryArray = categoryMap.get(nodeCategory);
                    if (nodeCategory.split("_").length == 1) {
                        final String nodeCategoryNumbered = nodeCategory + "_1";
                        ((ObjectNode) dataObjectNode).put(SedaConstants.TAG_DO_VERSION, nodeCategoryNumbered);
                    }
                    if (nodeCategoryArray == null) {
                        nodeCategoryArray = new ArrayList<>();
                        nodeCategoryArray.add(dataObjectNode);
                    } else {
                        final int dataObjectNodePosition = Integer.parseInt(nodeCategory.split("_")[1]) - 1;
                        nodeCategoryArray.add(dataObjectNodePosition, dataObjectNode);
                    }
                    categoryMap.put(nodeCategory, nodeCategoryArray);
                    if (BINARY_MASTER.equals(nodeCategory)) {

                        fileInfo = (ObjectNode) dataObjectNode.get(FILE_INFO);
                        if (dataObjectNode.get(METADATA) != null) {
                            objectGroupType = dataObjectNode.get(METADATA).fieldNames().next();
                        }
                    }
                    if (!dataObjectFile.delete()) {
                        LOGGER.warn(FILE_COULD_NOT_BE_DELETED_MSG);
                    }
                }

                objectGroup.put(SedaConstants.PREFIX_TYPE, objectGroupType);
                objectGroup.set(SedaConstants.TAG_FILE_INFO, fileInfo);
                final ArrayNode qualifiersNode = getObjectGroupQualifiers(categoryMap, containerId);
                objectGroup.set(SedaConstants.PREFIX_QUALIFIERS, qualifiersNode);
                final ObjectNode workNode = getObjectGroupWork(categoryMap, containerId);
                // in case of attachement, this will be true, we will then add information about existing og in work
                // part
                if (existingUnitIdWithExistingObjectGroup.containsKey(unitParentGUID)) {
                    workNode.put(SedaConstants.TAG_DATA_OBJECT_GROUP_EXISTING_REFERENCEID,
                        existingUnitIdWithExistingObjectGroup.get(unitParentGUID));
                }
                objectGroup.set(SedaConstants.PREFIX_WORK, workNode);
                objectGroup.set(SedaConstants.PREFIX_UP, unitParent);

                objectGroup.put(SedaConstants.PREFIX_NB, entry.getValue().size());
                // Add operation to OPS
                objectGroup.putArray(SedaConstants.PREFIX_OPS).add(containerId);
                objectGroup.put(SedaConstants.PREFIX_OPI, containerId);
                objectGroup.put(SedaConstants.PREFIX_ORIGINATING_AGENCY, originatingAgency);
                objectGroup.set(SedaConstants.PREFIX_ORIGINATING_AGENCIES,
                    JsonHandler.createArrayNode().add(originatingAgency));
                objectGroup.set(SedaConstants.STORAGE, storageInfo);

                JsonHandler.writeAsFile(objectGroup, tmpFile);

                handlerIO.transferFileToWorkspace(
                    IngestWorkflowConstants.OBJECT_GROUP_FOLDER + "/" + objectGroupGuid + JSON_EXTENSION,
                    tmpFile, true, asyncIO);
                if (!existingGot) {
                    // Create unreferenced object group
                    createObjectGroupLifeCycle(objectGroupGuid, containerId, typeProcess);

                    // Update Object Group lifeCycle creation event
                    // Set new eventId for task and set status then update delegate
                    String eventId = GUIDFactory.newEventGUID(ParameterHelper.getTenantParameter()).toString();
                    handlerIO.getHelper()
                        .updateDelegate((LogbookLifeCycleObjectGroupParameters) guidToLifeCycleParameters
                            .get(objectGroupGuid).setFinalStatus(HANDLER_ID, null, StatusCode.OK,
                                null)
                            .putParameterValue(LogbookParameterName.eventIdentifier, eventId));
                    // Add creation sub task event (add new eventId and set status for subtask before update delegate)
                    handlerIO.getHelper()
                        .updateDelegate((LogbookLifeCycleObjectGroupParameters) guidToLifeCycleParameters
                            .get(objectGroupGuid).setFinalStatus(HANDLER_ID, LFC_CREATION_SUB_TASK_ID, StatusCode.OK,
                                null)
                            .putParameterValue(LogbookParameterName.eventIdentifier,
                                GUIDFactory.newEventGUID(ParameterHelper.getTenantParameter()).toString())
                            .putParameterValue(LogbookParameterName.parentEventIdentifier, eventId));

                }

                if (uuids.size() == BATCH_SIZE) {
                    bulkLifeCycleObjectGroup(containerId, logbookLifeCycleClient, uuids);
                    uuids.clear();
                }

            } catch (final InvalidParseOperationException e) {
                LOGGER.error("Can not parse ObjectGroup", e);
                throw new ProcessingException(e);
            } catch (final LogbookClientBadRequestException e) {
                LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            } catch (final LogbookClientAlreadyExistsException e) {
                LOGGER.error(LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            } catch (final LogbookClientServerException e) {
                LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            } catch (final LogbookClientNotFoundException e) {
                LOGGER.error(LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            }
        }

        if (!uuids.isEmpty()) {
            try {
                bulkLifeCycleObjectGroup(containerId, logbookLifeCycleClient, uuids);
                uuids.clear();
            } catch (LogbookClientBadRequestException | LogbookClientServerException e) {
                throw new RuntimeException(e);
            }
        }

        manageExistingObjectGroups(containerId, logbookLifeCycleClient, typeProcess, uuids);
    }

    /**
     * Particular treatment for existing object group
     *
     * @param containerId
     * @param logbookLifeCycleClient
     * @param typeProcess
     * @param uuids
     * @throws ProcessingException
     */
    private void manageExistingObjectGroups(String containerId, LogbookLifeCyclesClient logbookLifeCycleClient,
        LogbookTypeProcess typeProcess, List<String> uuids)
        throws ProcessingException {
        final Set<String> collect =
            existingGOTs.entrySet().stream().filter(e -> e.getValue() != null).map(entry -> entry.getKey() + ".json")
                .collect(Collectors.toSet());

        File existingGotsFile = handlerIO.getNewLocalFile(handlerIO.getOutput(EXISTING_GOT_RANK).getPath());

        try {
            JsonHandler.writeAsFile(collect, existingGotsFile);
        } catch (InvalidParseOperationException e) {
            throw new ProcessingException(e);
        }
        handlerIO.addOuputResult(EXISTING_GOT_RANK, existingGotsFile, true, false);

        // Update LFC of exiting object group
        for (String gotGuid : existingGOTs.keySet()) {
            try {

                // Get original information from got and save them in LFC in order to keep possibility to rollback if
                // ingest >= KO
                JsonNode existingGot = existingGOTs.get(gotGuid);
                if (existingGot == null) {
                    // Idempotence, GOT already treated. @see ArchiveUnitListener for more information
                    continue;
                }

                uuids.add(gotGuid);

                createObjectGroupLifeCycle(gotGuid, containerId, typeProcess);

                if (uuids.size() == BATCH_SIZE) {
                    bulkLifeCycleObjectGroup(containerId, logbookLifeCycleClient, uuids);
                    uuids.clear();
                }

            } catch (final LogbookClientBadRequestException e) {
                LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            } catch (final LogbookClientServerException e) {
                LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            } catch (final LogbookClientAlreadyExistsException e) {
                LOGGER.error(LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            }
        }

        if (!uuids.isEmpty()) {
            try {
                bulkLifeCycleObjectGroup(containerId, logbookLifeCycleClient, uuids);
                uuids.clear();
            } catch (LogbookClientBadRequestException | LogbookClientServerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ArrayNode getObjectGroupQualifiers(Map<String, ArrayList<JsonNode>> categoryMap, String containerId) {
        final ArrayNode qualifiersArray = JsonHandler.createArrayNode();
        for (final Entry<String, ArrayList<JsonNode>> entry : categoryMap.entrySet()) {
            final ObjectNode objectNode = JsonHandler.createObjectNode();
            // fix qualifier_version in qualifier field
            String qualifier = null;
            if (entry.getKey().contains("_")) {
                qualifier = entry.getKey().split("_")[0];
                objectNode.put(SedaConstants.PREFIX_QUALIFIER, qualifier);
            } else {
                qualifier = entry.getKey();
                objectNode.put(SedaConstants.PREFIX_QUALIFIER, qualifier);
            }
            objectNode.put(SedaConstants.TAG_NB, entry.getValue().size());
            final ArrayNode arrayNode = JsonHandler.createArrayNode();
            for (final JsonNode node : entry.getValue()) {
                final String id = node.findValue(SedaConstants.PREFIX_ID).textValue();
                final String guid = dataObjectIdToGuid.get(id);
                updateObjectNode((ObjectNode) node, guid, SedaConstants.TAG_PHYSICAL_DATA_OBJECT.equals(qualifier),
                    containerId);
                arrayNode.add(node);
            }
            objectNode.set(SedaConstants.TAG_VERSIONS, arrayNode);
            qualifiersArray.add(objectNode);
        }
        return qualifiersArray;
    }

    private ObjectNode getObjectGroupWork(Map<String, ArrayList<JsonNode>> categoryMap, String containerId) {
        final ObjectNode workObject = JsonHandler.createObjectNode();
        final ObjectNode qualifierObject = JsonHandler.createObjectNode();
        for (final Entry<String, ArrayList<JsonNode>> entry : categoryMap.entrySet()) {
            final ObjectNode dataObjectNode = JsonHandler.createObjectNode();
            dataObjectNode.put(SedaConstants.TAG_NB, entry.getValue().size());
            final ArrayNode arrayNode = JsonHandler.createArrayNode();
            for (final JsonNode node : entry.getValue()) {
                final ObjectNode objectNode = JsonHandler.createObjectNode();
                final String id = node.findValue(SedaConstants.PREFIX_ID).textValue();
                boolean phsyical = physicalDataObjetsGuids.contains(id);
                updateObjectNode(objectNode, id, phsyical, containerId);
                if (phsyical) {
                    objectNode.set(SedaConstants.TAG_PHYSICAL_ID, node.get(SedaConstants.TAG_PHYSICAL_ID));
                }

                arrayNode.add(objectNode);
            }
            dataObjectNode.set(SedaConstants.TAG_VERSIONS, arrayNode);
            qualifierObject.set(entry.getKey(), dataObjectNode);
        }
        workObject.set(SedaConstants.PREFIX_QUALIFIERS, qualifierObject);
        return workObject;
    }

    /**
     * Update data object json node with data from maps
     *
     * @param objectNode data object json node
     * @param guid guid of data object
     * @param isPhysical is this object a physical object
     */

    private void updateObjectNode(final ObjectNode objectNode, String guid, boolean isPhysical, String containerId) {
        objectNode.put(SedaConstants.PREFIX_ID, guid);
        objectNode.put(SedaConstants.PREFIX_OPI, containerId);
        if (!isPhysical) {
            if (objectGuidToDataObject.get(guid).getSize() != null) {
                objectNode.put(SedaConstants.TAG_SIZE, objectGuidToDataObject.get(guid).getSize());
            }
            if (objectGuidToDataObject.get(guid).getUri() != null) {
                objectNode.put(SedaConstants.TAG_URI, objectGuidToDataObject.get(guid).getUri());
            }
            if (objectGuidToDataObject.get(guid).getMessageDigest() != null) {
                objectNode.put(SedaConstants.TAG_DIGEST,
                    objectGuidToDataObject.get(guid).getMessageDigest());
            }
            if (objectGuidToDataObject.get(guid).getAlgo() != null) {
                objectNode.put(SedaConstants.ALGORITHM,
                    objectGuidToDataObject.get(guid).getAlgo().getName());
            }
        }
    }

    private void completeDataObjectToObjectGroupMap() {
        for (final String key : dataObjectIdToObjectGroupId.keySet()) {
            if ("".equals(dataObjectIdToObjectGroupId.get(key))) {
                final List<String> dataOjectList = new ArrayList<>();
                dataOjectList.add(key);
                objectGroupIdToDataObjectId.put(GUIDFactory.newGUID().toString(), dataOjectList);
                // TODO P0 Create OG / OG lifeCycle
            }
        }
    }

    private void linkToArchiveUnitDeclaredInTheIngestContract(ArrayNode upNode) {
        findArchiveUnitDeclaredInTheIngestContract();
        // if ingest contract is not null, that means control is activated
        // if control is activated, then no need to add linkParentId as a up
        if ((ingestContract != null && ingestContract.getLinkParentId() != null && linkParentId != null &&
            !ingestContract.getLinkParentId().equals(linkParentId)) ||
            (linkParentId != null && ingestContract == null)) {
            if (!upNode.toString().contains(linkParentId)) {
                upNode.add(linkParentId);
            }
        }
    }

    private boolean findArchiveUnitDeclaredInTheIngestContract() {
        try (final AdminManagementClient adminClient = AdminManagementClientFactory.getInstance().getClient();
            final MetaDataClient metaDataClient = metaDataClientFactory.getClient()) {

            if (contractName != null) {
                RequestResponse<IngestContractModel> referenceContracts =
                    adminClient.findIngestContractsByID(contractName);
                if (referenceContracts.isOk()) {
                    List<IngestContractModel> results = ((RequestResponseOK) referenceContracts).getResults();
                    if (!results.isEmpty()) {
                        for (IngestContractModel result : results) {
                            if (ActivationStatus.ACTIVE.equals(result.getCheckParentLink())) {
                                ingestContract = result;
                            }
                            linkParentId = result.getLinkParentId();
                        }
                    }

                    if (linkParentId != null && !linkParentId.isEmpty()) {
                        final Select select = new Select();
                        String[] schemaArray = new String[] {UnitType.FILING_UNIT.name(), UnitType.HOLDING_UNIT.name()};
                        select.setQuery(QueryHelper.in(UNITTYPE.exactToken(), schemaArray).setDepthLimit(0));
                        final ObjectNode queryDsl = select.getFinalSelect();
                        JsonNode res = metaDataClient.selectUnitbyId(queryDsl, linkParentId).get("$results").get(0);

                        ObjectNode archiveUnit = JsonHandler.createObjectNode();
                        createArchiveUnitDeclaredInTheIngestContract(archiveUnit, res);
                        saveArchiveUnitDeclaredInTheIngestContract(archiveUnit, linkParentId);
                        return true;
                    }

                }
            }


        } catch (AdminManagementClientServerException | InvalidParseOperationException e) {
            LOGGER.error("Contract found but inactive: ", e);
        } catch (ReferentialNotFoundException | InvalidCreateOperationException e) {
            LOGGER.error("Contract not found :", e);
        } catch (MetaDataExecutionException | MetaDataDocumentSizeException | MetaDataClientServerException e) {
            LOGGER.error("Metadata does not work :", e);
        } catch (ProcessingException e) {
            LOGGER.error("Cannot store the archive unit declared in the ingest contract :", e);
        }
        return false;
    }

    private void createArchiveUnitDeclaredInTheIngestContract(ObjectNode archiveUnit, JsonNode res) {
        archiveUnit.set(SedaConstants.TAG_ARCHIVE_UNIT, res);

        // Add _work information
        ObjectNode workNode = JsonHandler.createObjectNode();
        workNode.put(IngestWorkflowConstants.EXISTING_TAG, Boolean.TRUE);

        archiveUnit.set(SedaConstants.PREFIX_WORK, workNode);
    }

    private void saveArchiveUnitDeclaredInTheIngestContract(ObjectNode archiveUnit,
        String linkParentId)
        throws InvalidParseOperationException, ProcessingException {

        final File unitCompleteTmpFile = handlerIO.getNewLocalFile(linkParentId);

        // Write to new File
        JsonHandler.writeAsFile(archiveUnit, unitCompleteTmpFile);

        // Write to workspace
        handlerIO.transferFileToWorkspace(
            IngestWorkflowConstants.ARCHIVE_UNIT_FOLDER + File.separator + linkParentId + JSON_EXTENSION,
            unitCompleteTmpFile, true, asyncIO);
    }

    private void createLifeCycleForError(String subTask, String message, String guid, boolean isArchive,
        boolean isObjectGroup, String containerId,
        LogbookLifeCyclesClient logbookLifeCycleClient, LogbookTypeProcess logbookTypeProcess)
        throws InvalidParseOperationException, LogbookClientNotFoundException, LogbookClientBadRequestException,
        LogbookClientAlreadyExistsException, LogbookClientServerException {

        final LogbookLifeCycleParameters lfcParameters =
            (LogbookLifeCycleParameters) initLogbookLifeCycleParameters(guid, isArchive, isObjectGroup);
        lfcParameters.setFinalStatus(LFC_INITIAL_CREATION_EVENT_TYPE, null, StatusCode.KO, null);
        lfcParameters.putParameterValue(LogbookParameterName.eventIdentifierProcess, containerId);
        lfcParameters.putParameterValue(LogbookParameterName.eventIdentifier,
            GUIDFactory.newEventGUID(ParameterHelper.getTenantParameter()).toString());
        lfcParameters.putParameterValue(LogbookParameterName.eventTypeProcess, logbookTypeProcess.name());

        // do not create a lifecycle if guid is incorrect.
        try {
            GUIDReader.getGUID(guid);
            logbookLifeCycleClient.create(lfcParameters);
            guidToLifeCycleParameters.put(guid, lfcParameters);

            lfcParameters.setFinalStatus(HANDLER_ID, subTask, StatusCode.KO, null, null);
            ObjectNode llcEvDetData = JsonHandler.createObjectNode();
            llcEvDetData.put(SedaConstants.EV_DET_TECH_DATA, message);
            lfcParameters
                .putParameterValue(LogbookParameterName.eventDetailData, JsonHandler.writeAsString(llcEvDetData));
            logbookLifeCycleClient.update(lfcParameters);

        } catch (final InvalidGuidOperationException e) {
            LOGGER.error("ID is not a GUID: " + guid, e);
        }
    }

    /**
     * Method that will check au meant to be attached to in the manifest are sons of the au declared in the ingest
     * contract
     * 
     * @throws ProcessingUnitLinkingException in case the sip declares an attachment to a unit that is not a children of
     *         the unit declared in ingest contract
     */
    private void checkIngestContractWithAttachmentGuid(ArrayNode attachmentNode)
        throws ProcessingUnitLinkingException {

        try (final MetaDataClient metaDataClient = metaDataClientFactory.getClient()) {
            for (String auToBeChecked : existingUnitGuids) {
                if (ingestContract != null && ingestContract.getLinkParentId() != null &&
                    !ingestContract.getLinkParentId().equals(auToBeChecked)) {
                    final SelectMultiQuery request = new SelectMultiQuery();
                    request.addRoots(ingestContract.getLinkParentId());
                    request.setQuery(QueryHelper.eq(PROJECTIONARGS.ID.exactToken(), auToBeChecked).setDepthLimit(50));
                    final ObjectNode queryDsl = request.getFinalSelect();
                    JsonNode existingData = metaDataClient.selectUnits(queryDsl);
                    JsonNode result = (existingData == null) ? null : existingData.get("$results");
                    if (result == null || result.size() == 0) {
                        LOGGER.error("Unit was not found {}", auToBeChecked);
                        throw new ProcessingUnitLinkingException("Linking Unauthorized - unit " + auToBeChecked +
                            "is not linked to the unit declared in ingest contract : " +
                            ingestContract.getLinkParentId(), auToBeChecked, ingestContract.getLinkParentId());
                    } else if (result.size() > 1) {
                        LOGGER.error("Multiple Unit was found {}", auToBeChecked);
                        throw new ProcessingUnitLinkingException("Multiple unit was found for " + auToBeChecked,
                            auToBeChecked, ingestContract.getLinkParentId());
                    }
                }
                attachmentNode.add(auToBeChecked);
            }
        } catch (InvalidCreateOperationException | MetaDataExecutionException | MetaDataDocumentSizeException |
            MetaDataClientServerException | InvalidParseOperationException | VitamDBException e) {
            LOGGER.error("error while checking unit", e);
            throw new ProcessingUnitLinkingException("Unit could not be attached", e);
        }
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        if (!handler.checkHandlerIO(HANDLER_IO_OUT_PARAMETER_NUMBER, handlerInputIOList)) {
            throw new ProcessingException(HandlerIOImpl.NOT_CONFORM_PARAM);
        }
    }

    @VisibleForTesting
    Map<String, String> getUnitIdToGroupId() {
        return unitIdToGroupId;
    }

    @VisibleForTesting
    Map<String, String> getDataObjectIdToGuid() {
        return dataObjectIdToGuid;
    }

    @VisibleForTesting
    Map<String, String> getDataObjectIdToObjectGroupId() {
        return dataObjectIdToObjectGroupId;
    }

    @VisibleForTesting
    ObjectNode getArchiveUnitTree() {
        return archiveUnitTree;
    }

    @VisibleForTesting
    Map<String, Set<String>> getUnitIdToSetOfRuleId() {
        return unitIdToSetOfRuleId;
    }

}
