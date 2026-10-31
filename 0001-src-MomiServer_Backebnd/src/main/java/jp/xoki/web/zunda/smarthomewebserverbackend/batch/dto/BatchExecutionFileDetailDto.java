package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** バッチ実行に紐づくファイル詳細を返却するDTO。 */
@Getter
@Builder
public class BatchExecutionFileDetailDto {

	/** ファイル情報のDB内部ID。 */
	private Long id;

	/** 紐づくバッチ実行要求ID。 */
	@JsonProperty("execution_request_id")
	private Long executionRequestId;

	/** 表示用ファイル名。 */
	@JsonProperty("file_name")
	private String fileName;

	/** サーバー上のファイルパス。 */
	@JsonProperty("file_path")
	private String filePath;

	/** Content-Type。 */
	@JsonProperty("content_type")
	private String contentType;

	/** ファイルサイズ。 */
	@JsonProperty("file_size")
	private Long fileSize;

	/** ファイル内容のSHA-256ハッシュ。 */
	private String sha256;

	/** 作成日時。 */
	private LocalDateTime createdAt;

	/** 更新日時。 */
	private LocalDateTime updatedAt;
}
