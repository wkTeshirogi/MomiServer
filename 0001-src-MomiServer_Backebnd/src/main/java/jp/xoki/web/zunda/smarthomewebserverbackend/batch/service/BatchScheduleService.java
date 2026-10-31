package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import java.time.LocalDate;
import java.util.List;
import jp.xoki.web.zunda.jooq.tables.records.MBatchSchedulesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionSummaryDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleExecutionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination.Pagination;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination.PageSlice;

import static jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchScheduleServiceConstants.LOG_PAGE_SIZES;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** バッチスケジュールマスタの取得、登録、更新、DTO変換を行うService。 */
@Service
@RequiredArgsConstructor
public class BatchScheduleService {
	private final BatchScheduleRepository batchScheduleRepository;
	private final BatchScheduleExecutionRepository batchScheduleExecutionRepository;

	/**
	 * 表示期間内のスケジュール実行状態を取得する。
	 *
	 * @param from 開始日
	 * @param to 終了日
	 * @return 日付・時間帯・状態別の実行件数
	 */
	public List<BatchScheduleExecutionSummaryDto> getExecutionSummaries(LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(30))) {
			throw new IllegalArgumentException(BatchMessageConstants.SCHEDULE_PERIOD_INVALID);
		}
		return batchScheduleExecutionRepository.summarize(from, to);
	}

	/** 指定した予定日の完了済み実行ログをページ単位で取得する。 */
	public BatchScheduleExecutionLogPageDto getExecutionLogs(
			long scheduleId, LocalDate date, Integer hour, int offset, int limit) {
		if (scheduleId <= 0 || date == null || (hour != null && (hour < 0 || hour > 23))) {
			throw new IllegalArgumentException(BatchMessageConstants.EXECUTION_LOG_QUERY_INVALID);
		}
		Pagination.requireOffset(offset, BatchMessageConstants.EXECUTION_LOG_QUERY_INVALID);
		Pagination.requireAllowedLimit(limit, LOG_PAGE_SIZES, BatchMessageConstants.EXECUTION_LOG_QUERY_INVALID);
		List<BatchScheduleExecutionLogDto> rows = batchScheduleExecutionRepository.findCompletedLogs(
				scheduleId, date, hour, offset, Pagination.lookAheadLimit(limit));
		long totalCount = batchScheduleExecutionRepository.countCompletedLogs(scheduleId, date, hour);
		PageSlice<BatchScheduleExecutionLogDto> page = PageSlice.fromRows(rows, limit, totalCount);
		return new BatchScheduleExecutionLogPageDto(page.items(), page.hasMore(), page.totalCount());
	}

	/** @return バッチスケジュール一覧 */
	public List<BatchScheduleDetailDto> getAllBatchSchedules() {
		return batchScheduleRepository.findAll().stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * バッチ定義IDに紐づくスケジュール一覧を取得する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return バッチスケジュール一覧
	 */
	public List<BatchScheduleDetailDto> getBatchSchedules(Long batchDefinitionId) {
		return batchScheduleRepository.findByBatchDefinitionId(batchDefinitionId).stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * バッチスケジュール詳細を取得する。
	 *
	 * @param id バッチスケジュールID
	 * @return バッチスケジュール詳細
	 */
	public BatchScheduleDetailDto getBatchScheduleDetailDto(long id) {
		return batchScheduleRepository.findById(id).map(this::toDetailDto).orElse(null);
	}

	/**
	 * バッチスケジュールを登録または更新する。
	 *
	 * @param request 登録更新リクエスト
	 * @return 登録更新後のバッチスケジュール詳細
	 */
	public BatchScheduleDetailDto save(BatchScheduleRegistRequest request) {
		if (request.getId() != null && request.getId() > 0) {
			int updatedCount = batchScheduleRepository.update(toRecord(request));
			if (updatedCount == 0) {
				throw new ResourceNotFoundException("batch schedule", request.getId());
			}
			return getBatchScheduleDetailDto(request.getId());
		}

		Long savedId = batchScheduleRepository.save(toRecord(request));
		return getBatchScheduleDetailDto(savedId);
	}

	private BatchScheduleDetailDto toDetailDto(MBatchSchedulesRecord databaseRecord) {
		return BatchScheduleDetailDto.builder()
				.id(databaseRecord.getId())
				.batchDefinitionId(databaseRecord.getBatchDefinitionId())
				.targetAgentId(databaseRecord.getTargetAgentId())
				.cronExpression(databaseRecord.getCronExpression())
				.startDate(databaseRecord.getStartDate())
				.endDate(databaseRecord.getEndDate())
				.allowHolidayExecution(databaseRecord.getAllowHolidayExecution())
				.enabled(databaseRecord.getEnabled())
				.priority(databaseRecord.getPriority())
				.createdAt(databaseRecord.getCreatedDt())
				.updatedAt(databaseRecord.getUpdatedDt())
				.build();
	}

	private MBatchSchedulesRecord toRecord(BatchScheduleRegistRequest request) {
		MBatchSchedulesRecord databaseRecord = new MBatchSchedulesRecord();
		databaseRecord.setId(request.getId());
		databaseRecord.setBatchDefinitionId(request.getBatchDefinitionId());
		databaseRecord.setTargetAgentId(request.getTargetAgentId());
		databaseRecord.setCronExpression(request.getCronExpression());
		databaseRecord.setStartDate(request.getStartDate());
		databaseRecord.setEndDate(request.getEndDate());
		databaseRecord.setAllowHolidayExecution(request.getAllowHolidayExecution());
		databaseRecord.setEnabled(request.getEnabled());
		databaseRecord.setPriority(request.getPriority());
		return databaseRecord;
	}
}
