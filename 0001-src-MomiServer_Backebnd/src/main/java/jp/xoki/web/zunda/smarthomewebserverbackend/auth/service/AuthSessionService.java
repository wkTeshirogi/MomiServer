package jp.xoki.web.zunda.smarthomewebserverbackend.auth.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.TokenHashes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Clock;

/** 認証セッションとリフレッシュトークンの状態を管理するService。 */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

	/** 認証セッションRepository。 */
	private final AuthSessionRepository repository;

	private final Clock clock;

	/**
	 * 認証セッションを作成する。
	 *
	 * @param sessionId セッションID
	 * @param userEmail ユーザーメールアドレス
	 * @param refreshToken リフレッシュトークン
	 * @param expiresAt セッション有効期限
	 */
	public void create(String sessionId, String userEmail, String refreshToken, Instant expiresAt) {

		// DB照合にはハッシュだけを渡し、再利用できるリフレッシュトークン原文を保存させない。
		repository.create(sessionId, userEmail, TokenHashes.sha256(refreshToken), toLocalDateTime(expiresAt));
	}

	/**
	 * セッションが有効か判定する。
	 *
	 * @param sessionId セッションID
	 * @param userEmail ユーザーメールアドレス
	 * @return 有効な場合はtrue
	 */
	public boolean isActive(String sessionId, String userEmail) {
		return repository.isActive(sessionId, userEmail);
	}

	/**
	 * リフレッシュトークンが現在のセッションで有効か判定する。
	 *
	 * @param sessionId セッションID
	 * @param userEmail ユーザーメールアドレス
	 * @param refreshToken リフレッシュトークン
	 * @return 有効な場合はtrue
	 */
	public boolean isRefreshTokenActive(String sessionId, String userEmail, String refreshToken) {
		return repository.isRefreshTokenActive(sessionId, userEmail, TokenHashes.sha256(refreshToken));
	}

	/**
	 * セッションに紐づくリフレッシュトークンをローテーションする。
	 *
	 * @param sessionId セッションID
	 * @param refreshToken 新しいリフレッシュトークン
	 * @param expiresAt 新しい有効期限
	 */
	public void rotateRefreshToken(String sessionId, String refreshToken, Instant expiresAt) {
		repository.rotateRefreshToken(sessionId, TokenHashes.sha256(refreshToken), toLocalDateTime(expiresAt));
	}

	/**
	 * セッションを失効する。
	 *
	 * @param sessionId セッションID
	 */
	public void revoke(String sessionId) {
		repository.revoke(sessionId);
	}

	/**
	 * ユーザーに紐づく有効な認証セッションをすべて失効する。
	 *
	 * @param userEmail 対象ユーザーのメールアドレス
	 */
	public void revokeByUserEmail(String userEmail) {
		repository.revokeByUserEmail(userEmail);
	}

	/**
	 * Instantを共通ClockのタイムゾーンのLocalDateTimeへ変換する。
	 *
	 * @param instant 変換対象日時
	 * @return 変換後日時
	 */
	private LocalDateTime toLocalDateTime(Instant instant) {

		// 絶対時刻をタイムゾーンなしのDB日時へ変換する境界なので、OS既定ではなく共通Clockのzoneを使う。
		return LocalDateTime.ofInstant(instant, clock.getZone());
	}
}
