package jp.xoki.web.zunda.smarthomewebserverbackend.external.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** 連携先名、明示的スコープ、有効期限による発行入力。
 * @param name 連携先名
 * @param scopes 許可スコープ
 * @param expiresAt 任意の有効期限
 */
public record ExternalApiKeyIssueRequest(@NotBlank @Size(max = 100) String name,
		@NotEmpty Set<@NotBlank @Pattern(regexp = "inventory:(read|write)") String> scopes,
		@Future OffsetDateTime expiresAt) {
	public ExternalApiKeyIssueRequest {
		scopes = scopes == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
	}
}
