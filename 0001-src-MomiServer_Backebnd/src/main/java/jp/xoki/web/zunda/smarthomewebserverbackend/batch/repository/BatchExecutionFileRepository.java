package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import jp.xoki.web.zunda.jooq.tables.TBatchExecutionFiles;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileDetailDto;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** バッチ実行ファイルテーブルへアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class BatchExecutionFileRepository {

	/** バッチ実行ファイルテーブル。 */
	private static final TBatchExecutionFiles T_BATCH_EXECUTION_FILES = TBatchExecutionFiles.T_BATCH_EXECUTION_FILES;

	/** DB内部ID。 */
	private static final Field<Long> ID = T_BATCH_EXECUTION_FILES.ID;

	/** 紐づくバッチ実行要求ID。 */
	private static final Field<Long> EXECUTION_REQUEST_ID = T_BATCH_EXECUTION_FILES.EXECUTION_REQUEST_ID;

	/** ファイル名。 */
	private static final Field<String> FILE_NAME = T_BATCH_EXECUTION_FILES.FILE_NAME;

	/** サーバー上のファイルパス。 */
	private static final Field<String> FILE_PATH = T_BATCH_EXECUTION_FILES.FILE_PATH;

	/** Content-Type。 */
	private static final Field<String> CONTENT_TYPE = T_BATCH_EXECUTION_FILES.CONTENT_TYPE;

	/** ファイルサイズ。 */
	private static final Field<Long> FILE_SIZE = T_BATCH_EXECUTION_FILES.FILE_SIZE;

	/** ファイル内容のSHA-256ハッシュ。 */
	private static final Field<String> SHA256 = T_BATCH_EXECUTION_FILES.SHA256;

	/** 作成日時。 */
	private static final Field<LocalDateTime> CREATED_DT = T_BATCH_EXECUTION_FILES.CREATED_DT;

	/** 更新日時。 */
	private static final Field<LocalDateTime> UPDATED_DT = T_BATCH_EXECUTION_FILES.UPDATED_DT;

	/** jOOQ実行コンテキスト。 */
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * バッチ実行ファイル情報を登録する。
	 *
	 * @param file 登録するファイル詳細
	 * @return 登録されたDB内部ID
	 */
	public Long create(BatchExecutionFileDetailDto file) {
		LocalDateTime now = LocalDateTime.now(clock);
		return dsl.insertInto(T_BATCH_EXECUTION_FILES)
				.set(EXECUTION_REQUEST_ID, file.getExecutionRequestId())
				.set(FILE_NAME, file.getFileName())
				.set(FILE_PATH, file.getFilePath())
				.set(CONTENT_TYPE, file.getContentType())
				.set(FILE_SIZE, file.getFileSize())
				.set(SHA256, file.getSha256())
				.set(CREATED_DT, now)
				.set(UPDATED_DT, now)
				.returningResult(ID)
				.fetchOne(ID);
	}

	/**
	 * ファイルIDに一致するバッチ実行ファイル詳細を取得する。
	 *
	 * @param id ファイルID
	 * @return バッチ実行ファイル詳細
	 */
	public Optional<BatchExecutionFileDetailDto> findById(Long id) {
		return dsl.selectFrom(T_BATCH_EXECUTION_FILES)
				.where(ID.eq(id))
				.fetchOptional(this::toDto);
	}

	/**
	 * バッチ実行要求IDに紐づくファイル一覧を取得する。
	 *
	 * @param executionRequestId バッチ実行要求ID
	 * @return ファイル詳細一覧
	 */
	public List<BatchExecutionFileDetailDto> findByExecutionRequestId(Long executionRequestId) {
		return dsl.selectFrom(T_BATCH_EXECUTION_FILES)
				.where(EXECUTION_REQUEST_ID.eq(executionRequestId))
				.orderBy(ID.asc())
				.fetch(this::toDto);
	}

	/**
	 * バッチ実行ファイルレコードをDTOへ変換する。
	 *
	 * @param record バッチ実行ファイルレコード
	 * @return バッチ実行ファイル詳細DTO
	 */
	private BatchExecutionFileDetailDto toDto(Record databaseRecord) {
		return BatchExecutionFileDetailDto.builder()
				.id(databaseRecord.get(ID))
				.executionRequestId(databaseRecord.get(EXECUTION_REQUEST_ID))
				.fileName(databaseRecord.get(FILE_NAME))
				.filePath(databaseRecord.get(FILE_PATH))
				.contentType(databaseRecord.get(CONTENT_TYPE))
				.fileSize(databaseRecord.get(FILE_SIZE))
				.sha256(databaseRecord.get(SHA256))
				.createdAt(databaseRecord.get(CREATED_DT, LocalDateTime.class))
				.updatedAt(databaseRecord.get(UPDATED_DT, LocalDateTime.class))
				.build();
	}
}
