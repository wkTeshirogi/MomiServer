package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingSaveRequest;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import org.jooq.DSLContext;
import org.jooq.Result;

class BatchGlobalSettingRepositoryTest {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void convertsJdbcTimestampsForReadAndSave(boolean save) throws Exception {
		LocalDateTime created = LocalDateTime.of(2026, 9, 12, 17, 36, 40, 123000000);
		LocalDateTime updated = created.plusMinutes(5);
		DSLContext data = DSL.using(SQLDialect.POSTGRES);
		// SELECT * / RETURNING * receive JDBC timestamp metadata, not LocalDateTime fields.
		Field<?>[] columns = {
				DSL.field("id", Short.class),
				DSL.field("setting_version", Long.class),
				DSL.field("max_concurrent_executions", Integer.class),
				DSL.field("default_timeout_seconds", Integer.class),
                DSL.field("attachment_max_file_mib", Integer.class),
                DSL.field("attachment_max_total_mib", Integer.class),
                DSL.field("attachment_max_count", Integer.class),
                DSL.field("work_directory_retention_days", Integer.class),
				DSL.field("result_retry_interval_seconds", Integer.class),
				DSL.field("result_retry_count", Integer.class),
				DSL.field("unsent_result_retention_days", Integer.class),
				DSL.field("result_wait_timeout_seconds", Integer.class),
				DSL.field("start_wait_timeout_seconds", Integer.class),
				DSL.field("cancel_grace_seconds", Integer.class),
				DSL.field("created_dt", Timestamp.class),
				DSL.field("updated_dt", Timestamp.class)
		};
		Result<Record> result = data.newResult(columns);
		Record row = data.newRecord(columns);
		row.fromArray((short) 1, 2L, 3, 1800, 100, 500, 20, 7, 300, 5, 7, 3600, 3600, 30,
				Timestamp.valueOf(created), Timestamp.valueOf(updated));
		result.add(row);
		try (MockConnection connection = new MockConnection(ctx -> new MockResult[]{new MockResult(1, result)})) {
			BatchGlobalSettingRepository repository = new BatchGlobalSettingRepository(DSL.using(connection, SQLDialect.POSTGRES), ApplicationClockFactory.systemClock());
			BatchGlobalSettingDetailDto actual = save
					? repository.save(new BatchGlobalSettingSaveRequest(3, 1800, 300, 5, 7, 3600, 3600, 30, 100, 500, 20, 7))
					: repository.find().orElseThrow();
			assertThat(actual.getCreatedAt()).isEqualTo(created);
			assertThat(actual.getUpdatedAt()).isEqualTo(updated);
			assertThat(actual.getSettingVersion()).isEqualTo(2L);
			assertThat(actual.getMaxConcurrentExecutions()).isEqualTo(3);
		}
	}
}
