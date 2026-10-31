package jp.xoki.web.zunda.smarthomewebserverbackend.external.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.OptimisticLockConflictException;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalStockDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.repository.ExternalInventoryRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.repository.ExternalInventoryRepository.SavedOperation;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalInventoryServiceTest {

	@Mock
	private ExternalInventoryRepository repository;

	@Mock
	private ItemStockService stocks;

	private final ObjectMapper mapper = new ObjectMapper();
	private final UUID keyId = UUID.randomUUID();
	private final ExternalStockOperationRequest request =
			new ExternalStockOperationRequest(UUID.randomUUID(), "inbound", 2, "test");
	private ExternalInventoryService service;
	private String requestHash;

	@BeforeEach
	void setUp() throws Exception {
		service = new ExternalInventoryService(repository, stocks, mapper);
		requestHash = ExternalSecrets.hash("1:" + mapper.writeValueAsString(request));
		when(repository.reserve(eq(keyId), eq(request.operationId()), anyString())).thenReturn(false);
	}

	@Test
	void sameRequestReturnsStoredResponseWithoutUpdatingStock() throws Exception {
		ExternalStockDto saved = new ExternalStockDto(1L, 10L, "item", "maker", "model",
				"category", "shelf", 12, 2, "piece", "available");
		when(repository.previous(keyId, request.operationId()))
				.thenReturn(new SavedOperation(requestHash, mapper.writeValueAsString(saved)));

		assertThat(service.operate(keyId, 1L, request)).isEqualTo(saved);
		verify(repository).reserve(keyId, request.operationId(), requestHash);
		verifyNoInteractions(stocks);
		verify(repository, never()).lock(1L);
		verify(repository, never()).complete(eq(keyId), eq(request.operationId()), anyString());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 31, 63})
	void mismatchAtAnyDigestPositionConflictsWithoutUpdatingStock(int position) {
		char replacement = requestHash.charAt(position) == '0' ? '1' : '0';
		String changed = requestHash.substring(0, position) + replacement + requestHash.substring(position + 1);
		assertConflict(changed, "{}");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"invalid"})
	void missingOrMalformedStoredHashConflictsWithoutUpdatingStock(String storedHash) {
		assertConflict(storedHash, "{}");
	}

	@Test
	void matchingHashWithoutCompletedResponseStillConflicts() {
		assertConflict(requestHash, null);
	}

	@Test
	void storedHashIsNotCaseFoldedTrimmedOrHexNormalized() {
		assertConflict(requestHash.toUpperCase(java.util.Locale.ROOT), "{}");
		assertConflict(requestHash + " ", "{}");
		assertConflict("g".repeat(64), "{}");
	}

	@Test
	void matchingHashWithInvalidStoredResponseRetainsStorageError() {
		when(repository.previous(keyId, request.operationId())).thenReturn(new SavedOperation(requestHash, "invalid"));

		assertThatThrownBy(() -> service.operate(keyId, 1L, request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Stored inventory operation is invalid");
		verifyNoInteractions(stocks);
	}

	private void assertConflict(String storedHash, String response) {
		when(repository.previous(keyId, request.operationId())).thenReturn(new SavedOperation(storedHash, response));

		assertThatThrownBy(() -> service.operate(keyId, 1L, request))
				.isInstanceOf(OptimisticLockConflictException.class);
		verifyNoInteractions(stocks);
		verify(repository, never()).lock(1L);
		verify(repository, never()).complete(eq(keyId), eq(request.operationId()), anyString());
	}
}
