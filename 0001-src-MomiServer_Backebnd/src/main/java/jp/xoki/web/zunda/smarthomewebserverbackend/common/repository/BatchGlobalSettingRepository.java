package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import jp.xoki.web.zunda.jooq.tables.TBatchGlobalSettings;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.BatchGlobalSettingRepositoryConstants.ATTACHMENT_MAX_COUNT_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.BatchGlobalSettingRepositoryConstants.ATTACHMENT_MAX_FILE_MIB_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.BatchGlobalSettingRepositoryConstants.ATTACHMENT_MAX_TOTAL_MIB_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.BatchGlobalSettingRepositoryConstants.WORK_DIRECTORY_RETENTION_DAYS_VALUE;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingSaveRequest;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.time.Clock;

/** 単一行のバッチ共通設定を管理するRepository。 */
@Repository
@RequiredArgsConstructor
public class BatchGlobalSettingRepository {

	private static final TBatchGlobalSettings SETTINGS = TBatchGlobalSettings.T_BATCH_GLOBAL_SETTINGS;
	private static final Field<Short> ID = SETTINGS.ID;
	private static final Field<Long> VERSION = SETTINGS.SETTING_VERSION;
	private static final Field<Integer> MAX_CONCURRENT_EXECUTIONS =
			SETTINGS.MAX_CONCURRENT_EXECUTIONS;
	private static final Field<Integer> DEFAULT_TIMEOUT_SECONDS =
			SETTINGS.DEFAULT_TIMEOUT_SECONDS;
	private static final Field<Integer> RESULT_RETRY_INTERVAL_SECONDS =
			SETTINGS.RESULT_RETRY_INTERVAL_SECONDS;
	private static final Field<Integer> RESULT_RETRY_COUNT =
			SETTINGS.RESULT_RETRY_COUNT;
	private static final Field<Integer> UNSENT_RESULT_RETENTION_DAYS =
			SETTINGS.UNSENT_RESULT_RETENTION_DAYS;
	private static final Field<Integer> RESULT_WAIT_TIMEOUT_SECONDS =
			SETTINGS.RESULT_WAIT_TIMEOUT_SECONDS;
	private static final Field<Integer> START_WAIT_TIMEOUT_SECONDS =
			SETTINGS.START_WAIT_TIMEOUT_SECONDS;
	private static final Field<Integer> CANCEL_GRACE_SECONDS =
			SETTINGS.CANCEL_GRACE_SECONDS;
	private static final Field<LocalDateTime> CREATED_DT = SETTINGS.CREATED_DT;
	private static final Field<LocalDateTime> UPDATED_DT = SETTINGS.UPDATED_DT;
	private static final Field<?>[] COLUMNS = {
			ID, VERSION, MAX_CONCURRENT_EXECUTIONS, DEFAULT_TIMEOUT_SECONDS,
            SETTINGS.ATTACHMENT_MAX_FILE_MIB,
            SETTINGS.ATTACHMENT_MAX_TOTAL_MIB,
            SETTINGS.ATTACHMENT_MAX_COUNT,
            SETTINGS.WORK_DIRECTORY_RETENTION_DAYS,

			RESULT_RETRY_INTERVAL_SECONDS, RESULT_RETRY_COUNT, UNSENT_RESULT_RETENTION_DAYS,
			RESULT_WAIT_TIMEOUT_SECONDS, START_WAIT_TIMEOUT_SECONDS, CANCEL_GRACE_SECONDS,
			CREATED_DT, UPDATED_DT
	};

	private final DSLContext dsl;
    @org.springframework.beans.factory.annotation.Value("${app.batch.defaults.attachment-max-file-mib:100}")
    private int defaultAttachmentMaxFileMib;
    @org.springframework.beans.factory.annotation.Value("${app.batch.defaults.attachment-max-total-mib:500}")
    private int defaultAttachmentMaxTotalMib;
    @org.springframework.beans.factory.annotation.Value("${app.batch.defaults.attachment-max-count:20}")
    private int defaultAttachmentMaxCount;
    @org.springframework.beans.factory.annotation.Value("${app.batch.defaults.work-directory-retention-days:7}")
    private int defaultWorkDirectoryRetentionDays;

	private final Clock clock;


	public Optional<BatchGlobalSettingDetailDto> find() {
		return dsl.select(COLUMNS).from(SETTINGS).where(ID.eq((short) 1)).fetchOptional(this::toDto);
	}

	/** 同時保存時にもバージョンをDB上で加算し、保存した行を返す。 */
	public BatchGlobalSettingDetailDto save(BatchGlobalSettingSaveRequest request) {
		Record saved = dsl.insertInto(SETTINGS)
				.set(ID, (short) 1)
				.set(VERSION, 1L)
				.set(MAX_CONCURRENT_EXECUTIONS, request.max_concurrent_executions())
				.set(DEFAULT_TIMEOUT_SECONDS, request.default_timeout_seconds())
				.set(RESULT_RETRY_INTERVAL_SECONDS, request.result_retry_interval_seconds())
				.set(RESULT_RETRY_COUNT, request.result_retry_count())
				.set(UNSENT_RESULT_RETENTION_DAYS, request.unsent_result_retention_days())
				.set(RESULT_WAIT_TIMEOUT_SECONDS, request.result_wait_timeout_seconds())
				.set(START_WAIT_TIMEOUT_SECONDS, request.start_wait_timeout_seconds())
				.set(CANCEL_GRACE_SECONDS, request.cancel_grace_seconds())
                .set(SETTINGS.ATTACHMENT_MAX_FILE_MIB, request.attachment_max_file_mib() == null ? detailValue(SETTINGS.ATTACHMENT_MAX_FILE_MIB, defaultAttachmentMaxFileMib) : request.attachment_max_file_mib())
                .set(SETTINGS.ATTACHMENT_MAX_TOTAL_MIB, request.attachment_max_total_mib() == null ? detailValue(SETTINGS.ATTACHMENT_MAX_TOTAL_MIB, defaultAttachmentMaxTotalMib) : request.attachment_max_total_mib())
                .set(SETTINGS.ATTACHMENT_MAX_COUNT, request.attachment_max_count() == null ? detailValue(SETTINGS.ATTACHMENT_MAX_COUNT, defaultAttachmentMaxCount) : request.attachment_max_count())
                .set(SETTINGS.WORK_DIRECTORY_RETENTION_DAYS, request.work_directory_retention_days() == null ? detailValue(SETTINGS.WORK_DIRECTORY_RETENTION_DAYS, defaultWorkDirectoryRetentionDays) : request.work_directory_retention_days())

				.onConflict(ID).doUpdate()
				.set(VERSION, VERSION.plus(1L))
				.set(MAX_CONCURRENT_EXECUTIONS, request.max_concurrent_executions())
				.set(DEFAULT_TIMEOUT_SECONDS, request.default_timeout_seconds())
				.set(RESULT_RETRY_INTERVAL_SECONDS, request.result_retry_interval_seconds())
				.set(RESULT_RETRY_COUNT, request.result_retry_count())
				.set(UNSENT_RESULT_RETENTION_DAYS, request.unsent_result_retention_days())
				.set(RESULT_WAIT_TIMEOUT_SECONDS, request.result_wait_timeout_seconds())
				.set(START_WAIT_TIMEOUT_SECONDS, request.start_wait_timeout_seconds())
				.set(CANCEL_GRACE_SECONDS, request.cancel_grace_seconds())
                .set(SETTINGS.ATTACHMENT_MAX_FILE_MIB, request.attachment_max_file_mib() == null ? detailValue(SETTINGS.ATTACHMENT_MAX_FILE_MIB, defaultAttachmentMaxFileMib) : request.attachment_max_file_mib())
                .set(SETTINGS.ATTACHMENT_MAX_TOTAL_MIB, request.attachment_max_total_mib() == null ? detailValue(SETTINGS.ATTACHMENT_MAX_TOTAL_MIB, defaultAttachmentMaxTotalMib) : request.attachment_max_total_mib())
                .set(SETTINGS.ATTACHMENT_MAX_COUNT, request.attachment_max_count() == null ? detailValue(SETTINGS.ATTACHMENT_MAX_COUNT, defaultAttachmentMaxCount) : request.attachment_max_count())
                .set(SETTINGS.WORK_DIRECTORY_RETENTION_DAYS, request.work_directory_retention_days() == null ? detailValue(SETTINGS.WORK_DIRECTORY_RETENTION_DAYS, defaultWorkDirectoryRetentionDays) : request.work_directory_retention_days())

				.set(UPDATED_DT, LocalDateTime.now(clock))
				.returning(COLUMNS).fetchOne();
		if (saved == null) {
			throw new IllegalStateException("Batch global settings were not saved");
		}
		return toDto(saved);
	}

	private Integer detailValue(Field<Integer> column, int fallback) {
        Integer value = dsl.select(column).from(SETTINGS)
                .where(ID.eq((short)1)).fetchOne(0, Integer.class);
        if (value == null) {
            return fallback;
        }
        return value;
    }

    private BatchGlobalSettingDetailDto toDto(Record databaseRecord) {
		return BatchGlobalSettingDetailDto.builder()
				.settingVersion(databaseRecord.get(VERSION))
                .attachmentMaxFileMib(databaseRecord.get(ATTACHMENT_MAX_FILE_MIB_VALUE, Integer.class) == null ? defaultAttachmentMaxFileMib : databaseRecord.get(ATTACHMENT_MAX_FILE_MIB_VALUE, Integer.class))
                .attachmentMaxTotalMib(databaseRecord.get(ATTACHMENT_MAX_TOTAL_MIB_VALUE, Integer.class) == null ? defaultAttachmentMaxTotalMib : databaseRecord.get(ATTACHMENT_MAX_TOTAL_MIB_VALUE, Integer.class))
                .attachmentMaxCount(databaseRecord.get(ATTACHMENT_MAX_COUNT_VALUE, Integer.class) == null ? defaultAttachmentMaxCount : databaseRecord.get(ATTACHMENT_MAX_COUNT_VALUE, Integer.class))
                .workDirectoryRetentionDays(databaseRecord.get(WORK_DIRECTORY_RETENTION_DAYS_VALUE, Integer.class) == null ? defaultWorkDirectoryRetentionDays : databaseRecord.get(WORK_DIRECTORY_RETENTION_DAYS_VALUE, Integer.class))

				.maxConcurrentExecutions(databaseRecord.get(MAX_CONCURRENT_EXECUTIONS))
				.defaultTimeoutSeconds(databaseRecord.get(DEFAULT_TIMEOUT_SECONDS))
				.resultRetryIntervalSeconds(databaseRecord.get(RESULT_RETRY_INTERVAL_SECONDS))
				.resultRetryCount(databaseRecord.get(RESULT_RETRY_COUNT))
				.unsentResultRetentionDays(databaseRecord.get(UNSENT_RESULT_RETENTION_DAYS))
				.resultWaitTimeoutSeconds(databaseRecord.get(RESULT_WAIT_TIMEOUT_SECONDS))
				.startWaitTimeoutSeconds(databaseRecord.get(START_WAIT_TIMEOUT_SECONDS))
				.cancelGraceSeconds(databaseRecord.get(CANCEL_GRACE_SECONDS))
				.createdAt(databaseRecord.get(CREATED_DT, LocalDateTime.class))
				.updatedAt(databaseRecord.get(UPDATED_DT, LocalDateTime.class))
				.build();
	}
}
