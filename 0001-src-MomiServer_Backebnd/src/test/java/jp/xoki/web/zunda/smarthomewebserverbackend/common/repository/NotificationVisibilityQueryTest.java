package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Result;

class NotificationVisibilityQueryTest {

	@ParameterizedTest
	@CsvSource({"false,false", "true,false", "true,true"})
	void listUnreadAndCountUseTheSameRoleRestrictions(boolean canViewMachine, boolean canViewMaster) throws Exception {
		List<String> queries = new ArrayList<>();
		List<List<Object>> bindings = new ArrayList<>();
		DSLContext data = DSL.using(SQLDialect.POSTGRES);
		Result<Record1<Long>> empty = data.newResult(DSL.field("id", Long.class));
		Field<Integer> countField = DSL.field("count", Integer.class);
		Result<Record1<Integer>> count = data.newResult(countField);
		Record1<Integer> countRow = data.newRecord(countField);
		countRow.set(countField, 0);
		count.add(countRow);
		try (MockConnection connection = new MockConnection(ctx -> {
			queries.add(ctx.sql().toLowerCase());
			bindings.add(Arrays.asList(ctx.bindings()));
			return new MockResult[]{new MockResult(1, ctx.sql().toLowerCase().contains("count") ? count : empty)};
		})) {
			NotificationRepository repository = new NotificationRepository(DSL.using(connection, SQLDialect.POSTGRES), ApplicationClockFactory.systemClock());
			repository.findByUserId(42L, 10, 0, canViewMachine, canViewMaster);
			repository.findUnreadByUserId(42L, 10, 0, canViewMachine, canViewMaster);
			repository.countUnreadByUserId(42L, canViewMachine, canViewMaster);
		}
		assertThat(queries).hasSize(3);
		assertThat(bindings).allSatisfy(values -> {
			assertThat(values).contains(42L);
			if (canViewMachine) {
				assertThat(values).doesNotContain("/machine/status%");
			} else {
				assertThat(values).contains("/machine/status%");
			}
			if (canViewMaster) {
				assertThat(values).doesNotContain("/master%", "batch_execution");
			} else {
				assertThat(values).contains("/master%", "batch_execution");
			}
		});
	}
}
