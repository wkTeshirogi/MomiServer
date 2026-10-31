package jp.xoki.web.zunda.smarthomewebserverbackend.external.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.UUID;

/** 秘密情報を含まないAPIキー管理情報。
 * @param id キーID
 * @param name 連携先名
 * @param keyPrefix 識別用の先頭部分
 * @param scopes スコープ
 * @param status 有効状態
 * @param expiresAt 有効期限
 * @param lastUsedAt 最終利用日時
 * @param createdAt 発行日時
 * @param createdBy 発行者
 * @param revokedAt 無効化日時
 * @param revokedBy 無効化した操作者
 */
public record ExternalApiKeyDto(UUID id, String name, String keyPrefix, Set<String> scopes,
		String status, OffsetDateTime expiresAt, OffsetDateTime lastUsedAt,
		OffsetDateTime createdAt, String createdBy, OffsetDateTime revokedAt, String revokedBy) {
	public ExternalApiKeyDto {
		scopes = scopes == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
	}
}
