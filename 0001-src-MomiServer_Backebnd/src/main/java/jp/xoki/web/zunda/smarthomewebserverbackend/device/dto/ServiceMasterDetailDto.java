package jp.xoki.web.zunda.smarthomewebserverbackend.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** サービスマスタ詳細をAPIレスポンスとして返すためのDTO。 */
@Getter
@Builder
public class ServiceMasterDetailDto {
	private Long id;
	@JsonProperty("service_name")
	private String serviceName;
	@JsonProperty("view_name")
	private String viewName;
	@JsonProperty("target_machine")
	private Long targetMachine;
	private String status;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	@JsonProperty("log_path")
	private String logPath;
	@JsonProperty("use_journalctl")
	private boolean useJournalctl;
	@JsonProperty("allow_start")
	private boolean allowStart;
	@JsonProperty("allow_stop")
	private boolean allowStop;
	@JsonProperty("allow_restart")
	private boolean allowRestart;
	@JsonProperty("auto_restart")
	private boolean autoRestart;
	@JsonProperty("notice_level")
	private String noticeLevel;
	private String description;
}
