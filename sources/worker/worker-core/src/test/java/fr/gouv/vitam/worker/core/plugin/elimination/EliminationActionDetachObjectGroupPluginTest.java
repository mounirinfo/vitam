package fr.gouv.vitam.worker.core.plugin.elimination;

import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.plugin.elimination.exception.EliminationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.HashSet;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

public class EliminationActionDetachObjectGroupPluginTest {

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private EliminationActionDeleteService eliminationActionDeleteService;

    @InjectMocks
    private EliminationActionDetachObjectGroupPlugin instance;

    @Mock
    private HandlerIO handler;

    private WorkerParameters params;

    @Before
    public void setUp() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(0);
        VitamThreadUtils.getVitamSession().setRequestId("opId");

        params = WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
            .newGUID()).setContainerName(VitamThreadUtils.getVitamSession().getRequestId())
            .setRequestId(VitamThreadUtils.getVitamSession().getRequestId())
            .setProcessId(VitamThreadUtils.getVitamSession().getRequestId())
            .setObjectName("id_got_1")
            .setObjectMetadata(JsonHandler.toJsonNode(Collections.singletonList("id_unit_1")))
            .setCurrentStep("StepName");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    @RunWithCustomExecutor
    public void testExecute_OK() throws Exception {

        ItemStatus itemStatus = instance.execute(params, handler);

        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.OK);

        verify(eliminationActionDeleteService)
            .detachObjectGroupFromDeleteParentUnits(eq(VitamThreadUtils.getVitamSession().getRequestId()),
                eq("id_got_1"), eq(new HashSet<>(singletonList("id_unit_1"))),
                eq(EliminationActionDetachObjectGroupPlugin.getId()));
    }

    @Test
    @RunWithCustomExecutor
    public void testExecute_WhenExceptionExpectFatal() throws Exception {

        doThrow(new EliminationException(StatusCode.FATAL, null)).when(eliminationActionDeleteService)
            .detachObjectGroupFromDeleteParentUnits(any(), any(), any(), any());

        ItemStatus itemStatus = instance.execute(params, handler);

        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.FATAL);
    }
}
