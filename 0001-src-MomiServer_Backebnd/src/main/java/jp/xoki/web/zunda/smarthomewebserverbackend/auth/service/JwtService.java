package jp.xoki.web.zunda.smarthomewebserverbackend.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import io.jsonwebtoken.JwtBuilder;

/** JWTの生成、解析、有効性検証を行うサービス。 */
@Service
public class JwtService {

	private final String secret;
	private final long accessTokenExpireSeconds;
	private final long refreshTokenExpireSeconds;

	private final Clock clock;

	/**
	 * JWT署名鍵とトークン有効期限を設定値から受け取って初期化する。
	 *
	 * @param secret JWT署名に使用する秘密鍵文字列
	 * @param accessTokenExpireSeconds アクセストークンの有効期限秒数
	 * @param refreshTokenExpireSeconds リフレッシュトークンの有効期限秒数
	 * @param clock 発行と検証に共用する時計
	 */
	public JwtService(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration.access-token}") long accessTokenExpireSeconds,
			@Value("${jwt.expiration.refresh-token}") long refreshTokenExpireSeconds, Clock clock
	) {
		this.secret = secret;
		this.accessTokenExpireSeconds = accessTokenExpireSeconds;
		this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
		this.clock = clock;
	}

	// アクセストークン生成（role クレームを含む）
	/**
	 * ユーザー情報からアクセストークンを生成する。
	 *
	 * @param user 認証済みユーザー
	 * @return 生成したアクセストークン
	 */
	public String generateAccessToken(UserDetails user, String sessionId) {
		String role = user.getAuthorities().stream()
				.findFirst()
				.map(a -> a.getAuthority().replace("ROLE_", ""))
				.orElse("USER_ROLE");
		return buildToken(user.getUsername(), accessTokenExpireSeconds, "access", role, sessionId);
	}

	// リフレッシュトークン生成
	/**
	 * ユーザー情報からリフレッシュトークンを生成する。
	 *
	 * @param user 認証済みユーザー
	 * @return 生成したリフレッシュトークン
	 */
	public String generateRefreshToken(UserDetails user, String sessionId) {
		return buildToken(user.getUsername(), refreshTokenExpireSeconds, "refresh", null, sessionId);
	}

	// アクセストークンの有効期限（秒）を返す
	/**
	 * アクセストークンの有効期限秒数を返す。
	 *
	 * @return アクセストークンの有効期限秒数
	 */
	public long getAccessTokenExpiresIn() {
		return accessTokenExpireSeconds;
	}

	// トークンからemailを取得
	/**
	 * JWTのsubjectからユーザー名を取得する。
	 *
	 * @param token 解析対象のJWT
	 * @return JWTに設定されたユーザー名
	 */
	public String extractUsername(String token) {
		return extractClaims(token).getSubject();
	}

	public Instant extractExpiration(String token) {
		return extractClaims(token).getExpiration().toInstant();
	}

	public String extractSessionId(String token) {
		return extractClaims(token).get("sid", String.class);
	}

	// アクセストークンとして有効か検証
	/**
	 * 指定ユーザーに対して有効なアクセストークンか判定する。
	 *
	 * @param token 検証対象のJWT
	 * @param user 比較対象のユーザー
	 * @return 有効なアクセストークンの場合はtrue
	 */
	public boolean isValidAccessToken(String token, UserDetails user) {
		return isValid(token, user, "access");
	}

	// リフレッシュトークンとして有効か検証
	/**
	 * 指定ユーザーに対して有効なリフレッシュトークンか判定する。
	 *
	 * @param token 検証対象のJWT
	 * @param user 比較対象のユーザー
	 * @return 有効なリフレッシュトークンの場合はtrue
	 */
	public boolean isValidRefreshToken(String token, UserDetails user) {
		return isValid(token, user, "refresh");
	}

	// --- private ---

	/**
	 * 共通のJWTクレームを組み立てて署名済みトークンを生成する。
	 *
	 * @param subject JWTのsubject
	 * @param expireSeconds 有効期限秒数
	 * @param tokenType トークン種別
	 * @param role 任意の権限クレーム
	 * @return 署名済みJWT
	 */
	private String buildToken(String subject, long expireSeconds, String tokenType, String role, String sessionId) {
		Instant now = Instant.now(clock);
		JwtBuilder builder = Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(subject)
				.claim("sid", sessionId)
				.claim("type", tokenType)
				.claim(Claims.ISSUED_AT, now.getEpochSecond())
				.claim(Claims.EXPIRATION, now.plusSeconds(expireSeconds).getEpochSecond());

		// role が指定されている場合のみクレームに追加
		if (role != null) {
			builder.claim("role", role);
		}

		return builder.signWith(signingKey()).compact();
	}

	/**
	 * JWTの所有者、種別、有効期限をまとめて検証する。
	 *
	 * @param token 検証対象のJWT
	 * @param user 比較対象のユーザー
	 * @param expectedType 期待するトークン種別
	 * @return 有効なトークンの場合はtrue
	 */
	private boolean isValid(String token, UserDetails user, String expectedType) {
		try {
			Claims claims = extractClaims(token);
			String username = claims.getSubject();
			String type = claims.get("type", String.class);
			return username.equals(user.getUsername())
					&& expectedType.equals(type)
					&& !claims.getExpiration().toInstant().isBefore(Instant.now(clock));
		} catch (JwtException | IllegalArgumentException _) {
			return false;
		}
	}

	/**
	 * 署名検証済みのJWTからClaimsを取得する。
	 *
	 * @param token 解析対象のJWT
	 * @return JWTのClaims
	 */
	private Claims extractClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey())
				.clock(() -> java.util.Date.from(clock.instant()))
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	/**
	 * 設定された秘密鍵文字列からHMAC署名鍵を生成する。
	 *
	 * @return JWT署名検証に使う秘密鍵
	 */
	private SecretKey signingKey() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
