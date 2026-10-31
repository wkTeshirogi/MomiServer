package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchMaintenanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BatchMaintenanceControllerTest {

	@TempDir
	Path directory;

	@Configuration
	@EnableMethodSecurity
	static class Security { }

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void onlyAdminMayPauseAndResumeAndUnsettledRequestsBlockStop() {
		BatchMaintenanceService maintenance = new BatchMaintenanceService(directory.resolve("state.flag").toString());
		BatchExecutionRequestService requests = mock(BatchExecutionRequestService.class);
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.register(Security.class);
			context.registerBean(BatchMaintenanceService.class, () -> maintenance);
			context.registerBean(BatchExecutionRequestService.class, () -> requests);
			context.registerBean(BatchMaintenanceController.class);
			context.refresh();
			BatchMaintenanceController controller = context.getBean(BatchMaintenanceController.class);
			BatchMaintenanceController.Change pause = new BatchMaintenanceController.Change(true);
			java.security.Principal actor = () -> "actor";
			for (String role : new String[]{"USER", "OPERATOR", "AGENT", "EXTERNAL"}) {
				SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("actor", null, "ROLE_" + role));
				assertThatThrownBy(controller::status).isInstanceOf(AccessDeniedException.class);
				assertThatThrownBy(() -> controller.change(pause, actor))
						.isInstanceOf(AccessDeniedException.class);
			}
			assertThat(maintenance.isPaused()).isFalse();
			SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
			when(requests.countUnsettledExecutions()).thenReturn(2L);
			BatchMaintenanceController.Status draining = controller.change(new BatchMaintenanceController.Change(true), () -> "admin");
			assertThat(draining.paused()).isTrue();
			assertThat(draining.unsettled_execution_count()).isEqualTo(2);
			assertThat(draining.safe_to_stop()).isFalse();
			when(requests.countUnsettledExecutions()).thenReturn(0L);
			assertThat(controller.status().safe_to_stop()).isTrue();
			assertThat(controller.change(new BatchMaintenanceController.Change(false), () -> "admin").safe_to_stop()).isFalse();
		}
	}

	@Test
	void missingPauseValueIsRejectedRatherThanResuming() throws Exception {
		BatchMaintenanceService maintenance = new BatchMaintenanceService(directory.resolve("state.flag").toString());
		maintenance.setPaused(true);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new BatchMaintenanceController(maintenance, mock(BatchExecutionRequestService.class))).build();
		mvc.perform(post("/api/master/common-settings/batch/maintenance").contentType("application/json").content("{}"))
				.andExpect(status().isBadRequest());
		assertThat(maintenance.isPaused()).isTrue();
	}
}
