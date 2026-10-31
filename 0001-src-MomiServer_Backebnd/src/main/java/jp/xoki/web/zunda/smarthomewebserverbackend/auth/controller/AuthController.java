package jp.xoki.web.zunda.smarthomewebserverbackend.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.LoginRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.LogoutRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.RefreshRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.UserSettingsRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.response.AuthResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.AuthService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 認証、トークン更新、ユーザー設定変更のAPIを提供するコントローラー。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "AuthController", description = "認証関連のAPI")
public class AuthController {

	/** 認証関連の業務処理Service。 */
	private final AuthService service;

	/**
	 * ログイン情報を検証し、アクセストークンとリフレッシュトークンを返す。
	 *
	 * @param request ログイン情報
	 * @return 認証トークン情報
	 */
	@Operation(summary = "ユーザーログイン", description = "メールアドレスとパスワードを検証し、認証トークンを返します。")
	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(service.login(request));
	}

	/**
	 * リフレッシュトークンを検証し、新しいトークン一式を返す。
	 *
	 * @param request リフレッシュトークン情報
	 * @return 新しく発行した認証トークン情報
	 */
	@Operation(summary = "トークンリフレッシュ", description = "リフレッシュトークンから新しい認証トークンを発行します。")
	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ResponseEntity.ok(service.refresh(request));
	}

	/**
	 * アクセストークンとリフレッシュトークンを失効する。
	 *
	 * @param authorizationHeader Authorizationヘッダー
	 * @param request ログアウト要求
	 * @return 空レスポンス
	 */
	@Operation(summary = "ログアウト", description = "アクセストークンとリフレッシュトークンを失効します。")
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
			@RequestBody(required = false) LogoutRequest request
	) {
		service.logout(authorizationHeader, request);
		return ResponseEntity.noContent().build();
	}

	/**
	 * ユーザー設定を変更する。
	 *
	 * @param request 変更対象のユーザー設定
	 */
	@Operation(summary = "ユーザー設定変更", description = "ユーザーの設定を変更します。")
	@PostMapping("/settings")
	@AdminOnly
	public void changeUserSettings(@Valid @RequestBody UserSettingsRequest request) {
		service.changeUserSettings(request);
	}
}
