package jp.xoki.web.zunda.smarthomewebserverbackend.external.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalApiConstants;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyIssueRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.repository.ExternalApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.SecuritySettingProperties;
import static jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalApiKeyServiceConstants.PREFIX_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalApiKeyServiceConstants.SCOPES;
import java.time.Clock;
import java.util.Optional;

/** APIキーの発行・失効管理。発行者のロールは外部認証へ引き継がない。 */
@Service
@RequiredArgsConstructor
public class ExternalApiKeyService {
	private final ExternalApiKeyRepository repository;

	private final Clock clock;
	private final SecuritySettingProperties security;

	/** 一覧取得。
	 * @return 管理情報一覧
	 */
	public List<ExternalApiKeyDto> list() {
		return repository.list();
	}

	/** 新規発行。
	 *
	 * 原文は返却時のみ保持し、ハッシュと監査履歴を一括保存する。
	 * @param request 発行内容
	 * @param actor 発行者
	 * @return 一度だけ表示する秘密値と管理情報
	 */
	@Transactional
	public IssuedKey issue(ExternalApiKeyIssueRequest request, String actor) {
		if (request.scopes() == null || request.scopes().isEmpty() || !SCOPES.containsAll(request.scopes())
				|| request.expiresAt() != null && !request.expiresAt().isAfter(OffsetDateTime.now(clock))) {
			throw new IllegalArgumentException("Invalid scopes or expiration");
		}
		UUID id = UUID.randomUUID();
		String secret = ExternalSecrets.generate();
		repository.insert(id, request.name().trim(), secret.substring(0, PREFIX_LENGTH),
				ExternalSecrets.hash(secret), request.scopes(), request.expiresAt(), actor);
		repository.event(id, "ISSUED", actor);
		return new IssuedKey(repository.find(id).orElseThrow(), secret);
	}

	/** 無効化。
	 * @param id キーID
	 * @param actor 操作者
	 * @return 無効化後の管理情報
	 */
	@Transactional
	public ExternalApiKeyDto revoke(UUID id, String actor) {
		if (repository.revoke(id, actor)) {
			repository.event(id, "REVOKED", actor);
		}
		return repository.find(id).orElseThrow(() -> new ResourceNotFoundException("API key", id));
	}

	/** 毎回DBで失効状態を確認する認証。
	 * @param secret 提示された秘密値
	 * @return 有効なキー情報
	 */
	public ExternalApiKeyDto authenticate(String secret) {
		if (secret == null || !secret.matches( ExternalApiConstants.API_KEY_PREFIX + "_[A-Za-z0-9_-]{43}")) {
			throw new BadCredentialsException("Invalid API key");
		}
		return repository.findByHash(ExternalSecrets.hash(secret)).filter(this::active)
				.orElseThrow(() -> new BadCredentialsException("Invalid API key"));
	}

	/** 利用回数を消費する。
	 * @param key キー情報
	 * @return 消費後回数。上限到達時は空
	 */
	public java.util.Optional<Integer> consume(ExternalApiKeyDto key) {
		Optional<Integer> count = repository.consume(key.id(), security.getExternalInventoryHourlyLimit());
		if (count.isEmpty() && repository.find(key.id()).filter(this::active).isEmpty()) {
			throw new BadCredentialsException("Invalid API key");
		}
		return count;
	}

	private boolean active(ExternalApiKeyDto key) {
		return "ACTIVE".equals(key.status())
				&& (key.expiresAt() == null || key.expiresAt().isAfter(OffsetDateTime.now(clock)));
	}

	/** 発行時に限り原文を返す応答。
	 *
	 * ログへの誤出力を防ぐ。
	 * @param key 管理情報
	 * @param secret 発行時のみ返す原文
	 */
	public record IssuedKey(ExternalApiKeyDto key, String secret) {
		@Override
		public String toString() {
			return "IssuedKey[id=" + key.id() + ", secret=REDACTED]";
		}
	}
}
