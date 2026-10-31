package jp.xoki.web.zunda.smarthomewebserverbackend.auth.service;

import io.jsonwebtoken.JwtException;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.LoginRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.LogoutRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.RefreshRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.RegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request.UserSettingsRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.response.AuthResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.InvalidTokenException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Clock;
import java.time.LocalDateTime;
import jp.xoki.web.zunda.jooq.tables.records.MUsersRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.model.LoginLockedEvent;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.SecuritySettingProperties;
import org.springframework.context.ApplicationEventPublisher;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.constants.AuthMessageConstants;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** ユーザー登録、ログイン、設定変更、JWT再発行を扱う認証サービス。 */
@Service
@RequiredArgsConstructor
public class AuthService {

	/** Spring Securityの認証処理。 */
	private final AuthenticationManager authenticationManager;

	/** 認証ユーザー情報の取得Service。 */
	private final UserDetailsService userDetailsService;

	/** JWTの発行と検証を行うService。 */
	private final JwtService jwtService;

	/** リフレッシュトークンのセッション状態を管理するService。 */
	private final AuthSessionService authSessionService;

	/** ユーザー情報Repository。 */
	private final UserRepository userRepository;

	/** パスワードハッシュ化処理。 */
	private final PasswordEncoder passwordEncoder;
	private final SecuritySettingProperties security;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	/**
	 * ユーザーを登録し、既存ユーザーの場合は設定を更新して認証トークンを返す。
	 *
	 * @param request 登録または更新するユーザー情報
	 * @return 認証トークン情報
	 */
	public AuthResponse register(RegisterRequest request) {

		// 登録済みメールアドレスの場合は新規作成ではなくユーザー設定更新として扱う。
		if (userRepository.findByEmail(request.getEmail()).isPresent()) {
			updateUser(request.getEmail(), request.getPassword(), request.getRole().getStoredName());
			return buildAuthResponse(userDetailsService.loadUserByUsername(request.getEmail()));
		}

		// 新規登録時は認証情報を作れないためパスワードを必須にする。
		if (request.getPassword() == null || request.getPassword().isBlank()) {
			throw new IllegalArgumentException(AuthMessageConstants.REGISTRATION_PASSWORD_REQUIRED);
		}
		userRepository.save(request.getEmail(), passwordEncoder.encode(request.getPassword()),
				request.getRole().getStoredName());
		return buildAuthResponse(userDetailsService.loadUserByUsername(request.getEmail()));
	}

	/**
	 * ユーザー設定を更新する。パスワード未入力時は既存パスワードを維持する。
	 *
	 * @param request 更新するユーザー設定
	 */
	public void changeUserSettings(UserSettingsRequest request) {
		String encodedPassword = null;

		// パスワード指定がある場合のみハッシュ化して更新対象にする。
		if (request.getPassword() != null && !request.getPassword().isBlank()) {
			encodedPassword = passwordEncoder.encode(request.getPassword());
		}

		int updated = userRepository.updateSettings(request.getEmail(), encodedPassword,
				request.getRole().getStoredName());

		// 更新件数が0の場合は対象ユーザーが存在しない。
		if (updated == 0) {
			throw new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, request.getEmail());
		}

		if (request.getRole() == RoleEnum.BAN_USER_ROLE) {
			authSessionService.revokeByUserEmail(request.getEmail());
		}
	}

	/**
	 * 既存ユーザーのパスワードと権限を更新する。
	 *
	 * @param email 更新対象のメールアドレス
	 * @param password 平文パスワード。未指定の場合は既存値を維持する
	 * @param role 更新後の権限
	 */
	private void updateUser(String email, String password, String role) {
		String encodedPassword = null;

		// パスワード未入力時はRepository側で既存パスワードを維持する。
		if (password != null && !password.isBlank()) {
			encodedPassword = passwordEncoder.encode(password);
		}

		int updated = userRepository.updateSettings(email, encodedPassword, role);

		// 更新件数が0の場合は対象ユーザーが存在しない。
		if (updated == 0) {
			throw new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, email);
		}

		if (RoleEnum.BAN_USER_ROLE.getStoredName().equals(role)) {
			authSessionService.revokeByUserEmail(email);
		}
	}

	/**
	 * 認証情報を検証してトークンを発行する。
	 *
	 * @param request ログイン情報
	 * @return 認証トークン情報
	 */
	@Transactional(noRollbackFor = BadCredentialsException.class)
	public AuthResponse login(LoginRequest request) {

		// パスワード変更と直列化し、旧パスワードの検証後に新しいセッションが残る競合を防ぐ。
		MUsersRecord account =
				userRepository.findByEmailForUpdate(request.getEmail()).orElse(null);
		LocalDateTime now = LocalDateTime.now(clock);
		int failures = account == null ? 0 : account.getLoginFailureCount();
		if (account != null && account.getLoginLockedUntil() != null) {
			if (now.isBefore(account.getLoginLockedUntil())) {
				throw new BadCredentialsException("Authentication failed");
			}

			// 期間が終了したロックは、次の認証試行前に解除し失敗回数も戻す。
			failures = 0;
			userRepository.updateLoginState(account.getId(), 0, null);
		}
		if (account != null && failures >= security.getLoginFailureTolerance()) {
			LocalDateTime until = now.plusMinutes(security.getLoginLockMinutes());
			userRepository.updateLoginState(account.getId(), failures, until);
			events.publishEvent(new LoginLockedEvent(account.getId(), until));
			throw new BadCredentialsException("Authentication failed");
		}
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
					request.getEmail(), request.getPassword()));
		} catch (BadCredentialsException exception) {

			// 認証拒否でもこの状態更新はコミットする。存在しないアカウントの状態は作成しない。
			if (account != null) userRepository.updateLoginState(account.getId(), failures + 1, null);
			throw exception;
		}
		if (account != null) userRepository.updateLoginState(account.getId(), 0, null);
		return buildAuthResponse(userDetailsService.loadUserByUsername(request.getEmail()));
	}

	/**
	 * リフレッシュトークンを検証して新しいトークンを発行する。
	 *
	 * @param request リフレッシュ要求
	 * @return 新しく発行した認証トークン情報
	 */
	public AuthResponse refresh(RefreshRequest request) {
		String token = request.getRefreshToken();

		String username;
		String sessionId;
		try {
			username = jwtService.extractUsername(token);
			sessionId = jwtService.extractSessionId(token);
		} catch (JwtException | IllegalArgumentException exception) {
			throw new InvalidTokenException("Invalid refresh token", exception);
		}

		UserDetails user = userDetailsService.loadUserByUsername(username);
		if (isBanned(user)) {
			authSessionService.revoke(sessionId);
			throw new InvalidTokenException("Invalid refresh token");
		}

		// JWT自体がリフレッシュトークンとして有効か検証する。
		if (!jwtService.isValidRefreshToken(token, user)) {
			throw new InvalidTokenException("Invalid refresh token");
		}

		// DB上のセッションが有効で、最新のリフレッシュトークンと一致するか検証する。
		if (!authSessionService.isRefreshTokenActive(sessionId, username, token)) {
			throw new InvalidTokenException("Invalid refresh token");
		}
		return rotateSessionAuthResponse(user, sessionId);
	}

	/**
	 * ログアウト要求に基づき、アクセストークンとリフレッシュトークンのセッションを失効する。
	 *
	 * @param authorizationHeader Authorizationヘッダー
	 * @param request ログアウト要求
	 */
	public void logout(String authorizationHeader, LogoutRequest request) {
		revokeBearerToken(authorizationHeader);

		// リクエストボディがある場合はリフレッシュトークン側のセッションも失効する。
		if (request != null) {
			revokeToken(request.getRefreshToken());
		}
	}

	/**
	 * Spring Securityのユーザー情報からAPIレスポンス用のトークン情報を組み立てる。
	 *
	 * @param user 認証済みユーザー
	 * @return 認証トークン情報
	 */
	private AuthResponse buildAuthResponse(UserDetails user) {
		ensureLoginAllowed(user);
		String sessionId = UUID.randomUUID().toString();
		String accessToken = jwtService.generateAccessToken(user, sessionId);
		String refreshToken = jwtService.generateRefreshToken(user, sessionId);
		authSessionService.create(sessionId, user.getUsername(), refreshToken,
				jwtService.extractExpiration(refreshToken));

		return AuthResponse.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.expiresnIn(jwtService.getAccessTokenExpiresIn())
				.build();
	}

	/**
	 * アクセス禁止ユーザーへの認証トークン発行を拒否する。
	 *
	 * @param user 認証対象ユーザー
	 */
	private void ensureLoginAllowed(UserDetails user) {
		if (isBanned(user)) {
			authSessionService.revokeByUserEmail(user.getUsername());
			throw new BadCredentialsException("Authentication failed");
		}
	}

	/**
	 * ユーザーがアクセス禁止ロールか判定する。
	 *
	 * @param user 判定対象ユーザー
	 * @return アクセス禁止ロールの場合はtrue
	 */
	private boolean isBanned(UserDetails user) {
		return user.getAuthorities().stream()
				.anyMatch(authority -> ("ROLE_" + RoleEnum.BAN_USER_ROLE.getStoredName())
						.equals(authority.getAuthority()));
	}

	/**
	 * 既存セッションIDを維持したまま、新しいアクセストークンとリフレッシュトークンを発行する。
	 *
	 * @param user 認証済みユーザー
	 * @param sessionId 継続するセッションID
	 * @return 再発行した認証トークン情報
	 */
	private AuthResponse rotateSessionAuthResponse(UserDetails user, String sessionId) {
		String accessToken = jwtService.generateAccessToken(user, sessionId);
		String refreshToken = jwtService.generateRefreshToken(user, sessionId);
		authSessionService.rotateRefreshToken(sessionId, refreshToken,
				jwtService.extractExpiration(refreshToken));

		return AuthResponse.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.expiresnIn(jwtService.getAccessTokenExpiresIn())
				.build();
	}

	/**
	 * AuthorizationヘッダーのBearerトークンに対応するセッションを失効する。
	 *
	 * @param authorizationHeader Authorizationヘッダー
	 */
	private void revokeBearerToken(String authorizationHeader) {

		// Bearer形式ではない場合は失効対象のJWTを取り出せないため何もしない。
		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
			return;
		}
		revokeToken(authorizationHeader.substring(7));
	}

	/**
	 * JWTからセッションIDを抽出してセッションを失効する。
	 *
	 * @param token 失効対象JWT
	 */
	private void revokeToken(String token) {

		// 空のトークンはログアウトの冪等性を優先して無視する。
		if (token == null || token.isBlank()) {
			return;
		}
		try {
			authSessionService.revoke(jwtService.extractSessionId(token));
		} catch (JwtException | IllegalArgumentException _) {

			// 不正なトークンは失効対象が存在しないため、ログアウト処理としては成功扱いにする。
		}
	}
}
