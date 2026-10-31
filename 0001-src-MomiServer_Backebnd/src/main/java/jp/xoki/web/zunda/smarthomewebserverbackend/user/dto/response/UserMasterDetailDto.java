package jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** ユーザーマスタ詳細をAPIレスポンスとして返すためのDTO。 */
@Getter
@Builder

/**
 * ユーザーマスタ詳細DTO
 */
public class UserMasterDetailDto {
	private Long id;
	private String email;
	private String role;
	private LocalDateTime createdDt;
	private LocalDateTime updatedDt;
	private LocalDateTime lastLoginAt;
}
