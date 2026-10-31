package jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

	@Test
	void notificationClampsWhileStrictApisRejectInvalidLimits() {
		assertThat(Pagination.clampLimit(null, 20, 100)).isEqualTo(20);
		assertThat(Pagination.clampLimit(Integer.MIN_VALUE, 20, 100)).isEqualTo(1);
		assertThat(Pagination.clampLimit(Integer.MAX_VALUE, 20, 100)).isEqualTo(100);
		assertThat(Pagination.requireLimit(200, 200, "logs")).isEqualTo(200);
		assertThatThrownBy(() -> Pagination.requireLimit(200, 100, "external"))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("external");
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 201, Integer.MAX_VALUE})
	void strictLimitPreservesTheCallerMessage(int limit) {
		assertThatThrownBy(() -> Pagination.requireLimit(limit, 200, "limitは1から200で指定してください。"))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("limitは1から200で指定してください。");
	}

	@ParameterizedTest
	@ValueSource(ints = {20, 50, 100})
	void allowedSizesPreserveTheBatchContract(int limit) {
		assertThat(Pagination.requireAllowedLimit(limit, Set.of(20, 50, 100), "batch")).isEqualTo(limit);
		assertThat(Pagination.lookAheadLimit(limit)).isEqualTo(limit + 1);
	}

	@Test
	void invalidOffsetsAndUnlistedSizesAreNotSilentlyCorrected() {
		Set<Integer> allowedSizes = Set.of(20, 50, 100);
		assertThat(Pagination.requireOffset(Integer.MAX_VALUE, "offset")).isEqualTo(Integer.MAX_VALUE);
		assertThatThrownBy(() -> Pagination.requireOffset(-1, "offset"))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("offset");
		assertThatThrownBy(() -> Pagination.requireAllowedLimit(21, allowedSizes, "batch"))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("batch");
		assertThatThrownBy(() -> Pagination.lookAheadLimit(Integer.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> Pagination.lookAheadLimit(0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void lookAheadDeterminesHasMoreWithoutReplacingTheDatabaseCount() {
		ArrayList<String> rows = new ArrayList<>(List.of("first", "second", "look-ahead"));
		PageSlice<String> page = PageSlice.fromRows(rows, 2, 90);
		assertThat(page.items()).containsExactly("first", "second");
		assertThat(page.hasMore()).isTrue();
		assertThat(page.totalCount()).isEqualTo(90);
		rows.clear();
		assertThat(page.items()).hasSize(2);
		List<String> items = page.items();
		assertThatThrownBy(items::clear).isInstanceOf(UnsupportedOperationException.class);
		assertThat(PageSlice.fromRows(List.of("last"), 2, 90).hasMore()).isFalse();
		assertThat(PageSlice.fromRows(List.of(), 2, 90).totalCount()).isEqualTo(90);
		assertThat(PageSlice.fromRows(List.of("a", "b"), 2, 2).hasMore()).isFalse();
		List<String> emptyRows = List.of();
		assertThatThrownBy(() -> PageSlice.fromRows(emptyRows, 0, 0)).isInstanceOf(IllegalArgumentException.class);
	}
}
