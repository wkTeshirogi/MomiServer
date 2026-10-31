package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** バッチ実行に紐づくファイル情報を登録するリクエストDTO。 */
@Getter
@Setter
public class BatchExecutionFileRegisterRequest {

	/** サーバー上のファイルパス。 */
	@NotBlank(message = BatchMessageConstants.BATCH_EXECUTION_FILE_REGISTER_FILE_PATH_REQUIRED)
	@Size(max = 1000, message = BatchMessageConstants.BATCH_EXECUTION_FILE_REGISTER_FILE_PATH_SIZE)
	@JsonProperty("file_path")
	private String filePath;

	/** 画面やAgentに表示するファイル名。未指定時はパスのファイル名を利用する。 */
	@Size(max = 255, message = BatchMessageConstants.BATCH_EXECUTION_FILE_REGISTER_FILE_NAME_SIZE)
	@JsonProperty("file_name")
	private String fileName;

	/** ファイルのContent-Type。 */
	@Size(max = 255, message = BatchMessageConstants.BATCH_EXECUTION_FILE_REGISTER_CONTENT_TYPE_SIZE)
	@JsonProperty("content_type")
	private String contentType;
}
