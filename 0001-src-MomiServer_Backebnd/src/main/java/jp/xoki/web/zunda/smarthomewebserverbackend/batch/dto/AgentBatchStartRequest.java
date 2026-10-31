package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 再送しても実際の開始日時は変更しない。 */
public record AgentBatchStartRequest(@NotNull LocalDateTime started_at) {
}
