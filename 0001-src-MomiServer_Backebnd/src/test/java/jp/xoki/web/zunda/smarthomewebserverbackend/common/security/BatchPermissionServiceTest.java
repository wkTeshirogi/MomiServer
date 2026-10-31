package jp.xoki.web.zunda.smarthomewebserverbackend.common.security;

import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchPermissionServiceTest {

	@Mock
	private BatchDefinitionRepository batchDefinitionRepository;

	@InjectMocks
	private BatchPermissionService batchPermissionService;

	@Test
	void adminCanExecuteWithoutLoadingBatchDefinition() {
		boolean result = batchPermissionService.canExecute(authentication("ROLE_ADMIN"), 1L);

		assertThat(result).isTrue();
		verifyNoInteractions(batchDefinitionRepository);
	}

	@ParameterizedTest
	@CsvSource(value = {
			"USER, ROLE_USER, true",
			"USER, ROLE_OPERATOR, true",
			"OPERATOR, ROLE_OPERATOR, true",
			"ADMIN, ROLE_USER, false",
			"ADMIN, ROLE_OPERATOR, false",
			"UNKNOWN, ROLE_USER, false",
			"NULL, ROLE_USER, false"
	}, nullValues = "NULL")
	void executionPermissionFollowsRequiredRole(String requiredRole, String authority, boolean allowed) {
		when(batchDefinitionRepository.findById(1L))
				.thenReturn(Optional.of(batchDefinition(requiredRole)));

		boolean result = batchPermissionService.canExecute(authentication(authority), 1L);

		assertThat(result).isEqualTo(allowed);
	}

	@Test
	void operatorCanReadUserBatchExecution() {
		UUID executionId = UUID.randomUUID();
		when(batchDefinitionRepository.findRequiredRoleByExecutionId(executionId))
				.thenReturn(Optional.of("USER"));

		boolean result = batchPermissionService.canReadExecution(
				authentication("ROLE_OPERATOR"), executionId);

		assertThat(result).isTrue();
	}

	@Test
	void userCannotReadOperatorBatchExecution() {
		UUID executionId = UUID.randomUUID();
		when(batchDefinitionRepository.findRequiredRoleByExecutionId(executionId))
				.thenReturn(Optional.of("OPERATOR"));

		boolean result = batchPermissionService.canReadExecution(
				authentication("ROLE_USER"), executionId);

		assertThat(result).isFalse();
	}

	@Test
	void unauthenticatedUserCannotExecute() {
		boolean result = batchPermissionService.canExecute(null, 1L);

		assertThat(result).isFalse();
		verifyNoInteractions(batchDefinitionRepository);
	}

	private Authentication authentication(String authority) {
		return new UsernamePasswordAuthenticationToken(
				"test@example.com",
				"password",
				List.of(new SimpleGrantedAuthority(authority)));
	}

	private MBatchDefinitionsRecord batchDefinition(String requiredRole) {
		MBatchDefinitionsRecord databaseRecord = new MBatchDefinitionsRecord();
		databaseRecord.setRequiredRole(requiredRole);
		return databaseRecord;
	}
}
