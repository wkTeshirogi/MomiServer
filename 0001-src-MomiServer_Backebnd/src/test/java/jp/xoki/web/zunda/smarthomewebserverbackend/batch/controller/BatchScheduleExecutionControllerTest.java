package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionSummaryDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchScheduleService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.GlobalExceptionHandler;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class BatchScheduleExecutionControllerTest {

	@Configuration
	@EnableMethodSecurity
	static class MethodSecurityConfiguration {
	}

	private AnnotationConfigApplicationContext context;
	private BatchScheduleService service;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(BatchScheduleService.class);
		context = new AnnotationConfigApplicationContext();
		context.register(MethodSecurityConfiguration.class);
		context.registerBean(BatchScheduleService.class, () -> service);
		context.registerBean(BatchScheduleMasterController.class);
		context.refresh();
		mvc = MockMvcBuilders.standaloneSetup(context.getBean(BatchScheduleMasterController.class))
				.setControllerAdvice(new GlobalExceptionHandler(ApplicationClockFactory.systemClock())).build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		context.close();
	}

	@Test
	void adminReceivesExecutionSummary() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
		LocalDate date = LocalDate.of(2026, 9, 27);
		when(service.getExecutionSummaries(date, date)).thenReturn(List.of(
				new BatchScheduleExecutionSummaryDto(3L, date, 1, BatchExecutionStatus.SUCCESS, 2)));

		mvc.perform(get("/api/master/batch/schedule/executions")
				.param("from", "2026-09-27").param("to", "2026-09-27"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].schedule_id").value(3))
				.andExpect(jsonPath("$[0].scheduled_date").value("2026-09-27"))
				.andExpect(jsonPath("$[0].execution_status").value("success"))
				.andExpect(jsonPath("$[0].execution_count").value(2));
	}

	@Test
	void userCannotReadExecutionSummary() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("user", null, "ROLE_USER"));
		mvc.perform(get("/api/master/batch/schedule/executions")
				.param("from", "2026-09-27").param("to", "2026-09-27"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}

	@Test
	void adminReceivesCompletedLog() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
		LocalDate date = LocalDate.of(2026, 9, 27);
		UUID id = UUID.randomUUID();
		when(service.getExecutionLogs(3L, date, 1, 0, 20)).thenReturn(new BatchScheduleExecutionLogPageDto(
				List.of(new BatchScheduleExecutionLogDto(id, date.atTime(1, 30), BatchExecutionStatus.SUCCESS,
						null, date.atTime(1, 32), 0, "done", "output", null, false)), false, 1L));
		mvc.perform(get("/api/master/batch/schedule/executions/logs")
				.param("scheduleId", "3").param("date", "2026-09-27").param("hour", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.logs[0].execution_id").value(id.toString()))
				.andExpect(jsonPath("$.logs[0].scheduled_fire_time").value("2026-09-27T01:30:00"))
				.andExpect(jsonPath("$.logs[0].stdout").value("output"))
				.andExpect(jsonPath("$.has_more").value(false))
				.andExpect(jsonPath("$.total_count").value(1));
	}

	@Test
	void userCannotReadCompletedLogs() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("user", null, "ROLE_USER"));
		mvc.perform(get("/api/master/batch/schedule/executions/logs")
				.param("scheduleId", "3").param("date", "2026-09-27"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}
}
