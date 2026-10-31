package jp.xoki.web.zunda.smarthomewebserverbackend.common.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.GlobalExceptionHandler;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class BatchGlobalSettingControllerTest {

	private static final String PATH = "/api/master/common-settings/batch";
	private final ObjectMapper mapper = new ObjectMapper();
	private AnnotationConfigApplicationContext context;
	private BatchGlobalSettingService service;
	private MockMvc mvc;

	@Configuration
	@EnableMethodSecurity
	static class MethodSecurityConfiguration {
	}

	@BeforeEach
	void setUp() {
		service = mock(BatchGlobalSettingService.class);
		context = new AnnotationConfigApplicationContext();
		context.register(MethodSecurityConfiguration.class);
		context.registerBean(BatchGlobalSettingService.class, () -> service);
		context.registerBean(BatchGlobalSettingController.class);
		context.refresh();
		mvc = MockMvcBuilders.standaloneSetup(context.getBean(BatchGlobalSettingController.class))
				.setControllerAdvice(new GlobalExceptionHandler(ApplicationClockFactory.systemClock())).build();
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		context.close();
	}

	@Test
	void returnsPersistedVersionAndValues() throws Exception {
		when(service.detail()).thenReturn(BatchGlobalSettingDetailDto.builder()
				.settingVersion(4L).maxConcurrentExecutions(3).build());
		mvc.perform(get(PATH)).andExpect(status().isOk())
				.andExpect(jsonPath("$.setting_version").value(4))
				.andExpect(jsonPath("$.max_concurrent_executions").value(3));
	}

	@Test
	void acceptsZeroRetriesAndReturnsSavedVersion() throws Exception {
		Map<String, Object> input = validInput();
		input.put("result_retry_count", 0);
		when(service.save(any())).thenReturn(BatchGlobalSettingDetailDto.builder()
				.settingVersion(5L).resultRetryCount(0).build());
		mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(input)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.setting_version").value(5))
				.andExpect(jsonPath("$.result_retry_count").value(0));
		verify(service).save(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"max_concurrent_executions", "default_timeout_seconds",
			"result_retry_interval_seconds", "result_retry_count", "unsent_result_retention_days",
			"result_wait_timeout_seconds", "start_wait_timeout_seconds", "cancel_grace_seconds"})
	void rejectsMissingOrNegativeValues(String field) throws Exception {
		Map<String, Object> input = validInput();
		input.remove(field);
		mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
		input.put(field, -1);
		mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@ParameterizedTest
	@ValueSource(ints = {1799, 1800})
	void rejectsResultWaitNotLongerThanExecutionTimeout(int seconds) throws Exception {
		Map<String, Object> input = validInput();
		input.put("result_wait_timeout_seconds", seconds);
		mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void rejectsNonAdminReadAndWrite() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("viewer", null, "ROLE_VIEWER"));
		mvc.perform(get(PATH)).andExpect(status().isForbidden());
		mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(validInput()))).andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}

	private Map<String, Object> validInput() {
		return new LinkedHashMap<>(Map.of(
				"max_concurrent_executions", 3,
				"default_timeout_seconds", 1800,
				"result_retry_interval_seconds", 300,
				"result_retry_count", 5,
				"unsent_result_retention_days", 7,
				"result_wait_timeout_seconds", 3600,
				"start_wait_timeout_seconds", 3600,
				"cancel_grace_seconds", 30));
	}
}
