package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.ApiAccessLogProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogSearchRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.ApiAccessLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogPageDto;

@ExtendWith(MockitoExtension.class)
class ApiAccessLogServiceTest {

	@Mock
	private ApiAccessLogRepository repository;

	private ApiAccessLogProperties properties;
	private ApiAccessLogService service;

	@BeforeEach
	void setUp() {
		properties = new ApiAccessLogProperties();
		service = new ApiAccessLogService(repository, properties, ApplicationClockFactory.systemClock());
	}

	@Test
	void searchNormalizesFiltersAndReportsNextPage() {
		OffsetDateTime to = OffsetDateTime.parse("2026-10-12T12:00:00+09:00");
		OffsetDateTime from = to.minusHours(2);
		ApiAccessLogDetailDto first = detail(1L);
		ApiAccessLogDetailDto second = detail(2L);
		when(repository.search(org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of(first, second));
		when(repository.count(org.mockito.ArgumentMatchers.any())).thenReturn(25L);

		ApiAccessLogPageDto page = service.search(new ApiAccessLogSearchRequest(
				from, to, " get ", " /api/status ", " CLIENT_ERROR ", null,
				" WEB ", " operator@example.com ", "127.0.0.1", 10L, 0, 1));

		assertThat(page.logs()).containsExactly(first);
		assertThat(page.has_more()).isTrue();
		assertThat(page.total_count()).isEqualTo(25L);
		ArgumentCaptor<ApiAccessLogSearchRequest> captor =
				ArgumentCaptor.forClass(ApiAccessLogSearchRequest.class);
		verify(repository).search(captor.capture());
		verify(repository).count(captor.getValue());
		assertThat(captor.getValue().http_method()).isEqualTo("GET");
		assertThat(captor.getValue().status_category()).isEqualTo("client_error");
		assertThat(captor.getValue().source_type()).isEqualTo("web");
		assertThat(captor.getValue().request_path()).isEqualTo("/api/status");
	}

	@Test
	void searchRejectsRangesLongerThanThirtyOneDays() {
		OffsetDateTime to = OffsetDateTime.parse("2026-10-12T12:00:00+09:00");
		ApiAccessLogSearchRequest request = new ApiAccessLogSearchRequest(
				to.minusDays(31).minusSeconds(1), to, null, null, null,
				null, null, null, null, null, 0, 50);
		assertThatThrownBy(() -> service.search(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("31日以内");
	}

	@Test
	void searchRejectsPageSizeOutsideAllowedRange() {
		OffsetDateTime to = OffsetDateTime.parse("2026-10-12T12:00:00+09:00");
		ApiAccessLogSearchRequest tooSmall = new ApiAccessLogSearchRequest(
				to.minusHours(1), to, null, null, null,
				null, null, null, null, null, 0, 0);
		ApiAccessLogSearchRequest tooLarge = new ApiAccessLogSearchRequest(
				to.minusHours(1), to, null, null, null,
				null, null, null, null, null, 0, 201);
		assertThatThrownBy(() -> service.search(tooSmall))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("1から200");
		assertThatThrownBy(() -> service.search(tooLarge))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("1から200");
	}

	@Test
	void cleanupUsesConfiguredRetentionDays() {
		properties.setRetentionDays(30);
		OffsetDateTime now = OffsetDateTime.parse("2026-10-12T12:00:00+09:00");

		service.cleanupExpired(now);

		verify(repository).deleteOccurredBefore(now.minusDays(30));
	}

	private ApiAccessLogDetailDto detail(long id) {
		return new ApiAccessLogDetailDto(id, java.util.UUID.randomUUID(), OffsetDateTime.now(ApplicationClockFactory.systemClock()),
				"GET", "/api/status", "/api/status", 200, 5L, "web",
				"operator@example.com", "127.0.0.1", "test", null);
	}
}
