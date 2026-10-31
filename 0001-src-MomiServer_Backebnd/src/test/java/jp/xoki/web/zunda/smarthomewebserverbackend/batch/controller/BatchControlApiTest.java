package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthorization;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.GlobalExceptionHandler;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.BatchPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class BatchControlApiTest {
	private AnnotationConfigApplicationContext context;
	private BatchExecutionRequestService requests;
	private BatchGlobalSettingService settings;
	private BatchPermissionService permissions;
	private MockMvc mvc;
	private final UUID executionId = UUID.randomUUID();

	@Configuration
	@EnableMethodSecurity
	static class Security {
	}

	@BeforeEach
	void setUp() {
		requests = mock(BatchExecutionRequestService.class);
		settings = mock(BatchGlobalSettingService.class);
		permissions = mock(BatchPermissionService.class);
		context = new AnnotationConfigApplicationContext();
		context.register(Security.class);
		context.registerBean(BatchExecutionRequestService.class, () -> requests);
		context.registerBean(BatchGlobalSettingService.class, () -> settings);
		context.registerBean(BatchPermissionService.class, () -> permissions);
		context.registerBean(BatchExecutionController.class);
		context.registerBean(AgentAuthorization.class,
                () -> mock(AgentAuthorization.class));
        context.registerBean(AgentBatchExecutionController.class);
		context.registerBean(AgentBatchSettingController.class);
		context.refresh();
		mvc = MockMvcBuilders.standaloneSetup(context.getBean(BatchExecutionController.class),
				context.getBean(AgentBatchExecutionController.class), context.getBean(AgentBatchSettingController.class))
				.setControllerAdvice(new GlobalExceptionHandler(ApplicationClockFactory.systemClock())).build();
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("operator", null, "ROLE_USER"));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		context.close();
	}

	@Test
	void nonAdminCannotCancel() throws Exception {
		mvc.perform(post("/api/batch/executions/{id}/cancel", executionId)).andExpect(status().isForbidden());
		verifyNoInteractions(requests);
	}

	@Test
	void adminCancellationReturnsCurrentState() throws Exception {
		TestingAuthenticationToken auth = new TestingAuthenticationToken("admin", null, "ROLE_ADMIN");
		SecurityContextHolder.getContext().setAuthentication(auth);
		when(requests.cancel(executionId, "admin")).thenReturn(BatchExecutionRequestDetailDto.builder()
				.executionId(executionId).executionStatus(BatchExecutionStatus.RUNNING).build());
		mvc.perform(post("/api/batch/executions/{id}/cancel", executionId).principal(auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.execution_status").value("running"));
	}

	@Test
	void permittedRoleCanReadBatchExecutionDetail() throws Exception {
		when(permissions.canReadExecution(any(), eq(executionId))).thenReturn(true);
		when(requests.getDetail(executionId)).thenReturn(BatchExecutionRequestDetailDto.builder()
				.executionId(executionId).executionStatus(BatchExecutionStatus.RUNNING).build());

		mvc.perform(get("/api/batch/executions/{id}", executionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.execution_status").value("running"));
	}

	@Test
	void insufficientRoleCannotReadBatchExecutionDetail() throws Exception {
		when(permissions.canReadExecution(any(), eq(executionId))).thenReturn(false);

		mvc.perform(get("/api/batch/executions/{id}", executionId))
				.andExpect(status().isForbidden());
		verify(requests, never()).getDetail(executionId);
	}

	@Test
	void authenticatedAgentCanReadSettings() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("agent", null, "ROLE_AGENT"));
		when(settings.detail()).thenReturn(BatchGlobalSettingDetailDto.builder()
				.settingVersion(3L).maxConcurrentExecutions(4).build());
		mvc.perform(get("/api/v1/agent/batch/settings")).andExpect(status().isOk())
				.andExpect(jsonPath("$.setting_version").value(3))
				.andExpect(jsonPath("$.max_concurrent_executions").value(4));
	}

	@Test
	void stateReconciliationDoesNotExposeCommand() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("agent", null, "ROLE_AGENT"));
		when(requests.getDetail(executionId)).thenReturn(BatchExecutionRequestDetailDto.builder()
				.executionId(executionId).executionStatus(BatchExecutionStatus.RUNNING)
				.confirmationStatus("result_unconfirmed").executionCommand("private-command").build());
		mvc.perform(get("/api/v1/agent/batch/executions/{id}/state", executionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.confirmation_status").value("result_unconfirmed"))
				.andExpect(jsonPath("$.execution_command").doesNotExist());
	}
}
