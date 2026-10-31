package jp.xoki.web.zunda.smarthomewebserverbackend.external.controller;

import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyIssueRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.service.ExternalApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** WebユーザーJWTで管理する外部APIキー。 */
@RestController
@RequestMapping("/api/external-api-keys")
@AdminOnly
@RequiredArgsConstructor
public class ExternalApiKeyController {
	private final ExternalApiKeyService service;

	/** 一覧。
	 * @return キー管理情報
	 */
	@GetMapping
	public List<ExternalApiKeyDto> list() {
		return service.list();
	}

	/** 発行。
	 * @param request 発行内容
	 * @param authentication 操作者
	 * @return 原文の一度限りの応答
	 */
	@PostMapping
	public ResponseEntity<ExternalApiKeyService.IssuedKey> issue(
			@Valid @RequestBody ExternalApiKeyIssueRequest request,
			Authentication authentication) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.header("Pragma", "no-cache").body(service.issue(request, authentication.getName()));
	}

	/** 無効化。
	 * @param id キーID
	 * @param authentication 操作者
	 * @return 無効化済み管理情報
	 */
	@PostMapping("/{id}/revoke")
	public ExternalApiKeyDto revoke(@PathVariable UUID id, Authentication authentication) {
		return service.revoke(id, authentication.getName());
	}
}
