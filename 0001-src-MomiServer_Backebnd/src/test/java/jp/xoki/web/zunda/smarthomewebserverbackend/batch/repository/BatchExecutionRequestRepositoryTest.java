package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService;

class BatchExecutionRequestRepositoryTest {
	@Test
	void dispatchQueryIncludesScheduledTimeGateAlongsideExistingGuards() throws Exception {
		AtomicReference<String> sql = new AtomicReference<String>();
		AtomicReference<Object[]> bindings = new AtomicReference<Object[]>();
		LocalDateTime now = LocalDateTime.of(2026, 9, 12, 12, 0);
		try (MockConnection connection = new MockConnection(context -> {
			sql.set(context.sql());
			bindings.set(context.bindings());
			return new MockResult[]{new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult(DSL.field("id", Long.class)))};
		})) {
			BatchAttachmentService files = org.mockito.Mockito.mock(jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService.class);
            BatchGlobalSettingService settings = org.mockito.Mockito.mock(jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService.class);
            org.mockito.Mockito.when(settings.detail()).thenReturn(jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto.builder().workDirectoryRetentionDays(7).build());
            BatchExecutionRequestRepository repository = new BatchExecutionRequestRepository(DSL.using(connection, SQLDialect.POSTGRES), files, settings, new com.fasterxml.jackson.databind.ObjectMapper(), ApplicationClockFactory.systemClock());
			assertThat(repository.findPendingDispatch(now, 50)).isEmpty();
			assertThat(sql.get()).contains("\"public\".\"t_batch_execution_requests\".\"scheduled_fire_time\" is null or "
                        + "\"public\".\"t_batch_execution_requests\".\"scheduled_fire_time\" <= cast(? as timestamp(3))")
					.contains("\"cancel_requested_at\" is null", "\"start_deadline_at\" >", "\"last_dispatch_attempt_at\"");
			assertThat(bindings.get()).contains(Timestamp.valueOf(now), "queued", "dispatched");
		}
	}

	@Test
	void insertPersistsExpiredReasonAndFinishTimeAndDoesNotOverwriteDuplicates() throws Exception {
		AtomicReference<String> sql = new AtomicReference<String>();
		AtomicReference<Object[]> bindings = new AtomicReference<Object[]>();
		LocalDateTime now = LocalDateTime.of(2026, 9, 12, 12, 0);
		try (MockConnection connection = new MockConnection(context -> {
			sql.set(context.sql());
			bindings.set(context.bindings());
			return new MockResult[]{new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult(DSL.field("id", Long.class)))};
		})) {
			BatchAttachmentService files = org.mockito.Mockito.mock(jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService.class);
            BatchGlobalSettingService settings = org.mockito.Mockito.mock(jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService.class);
            org.mockito.Mockito.when(settings.detail()).thenReturn(jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto.builder().workDirectoryRetentionDays(7).build());
            BatchExecutionRequestRepository repository = new BatchExecutionRequestRepository(DSL.using(connection, SQLDialect.POSTGRES), files, settings, new com.fasterxml.jackson.databind.ObjectMapper(), ApplicationClockFactory.systemClock());
			BatchExecutionRequestDetailDto request = BatchExecutionRequestDetailDto.builder().batchDefinitionId(1L).executionStatus(BatchExecutionStatus.EXPIRED)
					.confirmationStatus("confirmed").finishedAt(now).message("execution skipped").build();
			assertThat(repository.create(request)).isNull();
			assertThat(sql.get()).contains("\"finished_at\"", "\"message\"", "on conflict do nothing", "returning \"public\".\"t_batch_execution_requests\".\"id\"")
					.doesNotContain("do update");
			assertThat(bindings.get()).contains("expired", "confirmed", "execution skipped", Timestamp.valueOf(now));
		}
	}
}
