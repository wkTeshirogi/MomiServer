package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination.Pagination;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationCursor;

import java.util.List;
import java.util.UUID;
import jp.xoki.web.zunda.jooq.tables.records.MUsersRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationUnreadCountDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserBrowserNotificationDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.NotificationServiceConstants.DEFAULT_LIMIT;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.NotificationServiceConstants.MAX_LIMIT;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** ログインユーザー向け通知の参照と状態更新を提供するService。 */
@Service
@RequiredArgsConstructor
public class NotificationService {



	/** 通知DBアクセス。 */
	private final NotificationRepository notificationRepository;

	/** ユーザーDBアクセス。 */
	private final UserRepository userRepository;

	/**
	 * ログインユーザーの通知一覧を取得する。
	 *
	 * @param authentication 認証情報
	 * @param limit 最大取得件数
	 * @param offset 読み飛ばす件数
	 * @return 通知一覧
	 */
	public List<UserBrowserNotificationDetailDto> list(
			Authentication authentication, Integer limit, int offset) {

		// 配信先であることに加え、現在の画面閲覧権限でも絞る。配信後にロールが変わった通知も無条件には公開しない。
		return notificationRepository.findByUserId(
				resolveUserId(authentication), normalizeLimit(limit), normalizeOffset(offset),
				canViewMachine(authentication), canViewMaster(authentication));
	}

	/**
	 * ログインユーザーの未読通知一覧を取得する。
	 *
	 * @param authentication 認証情報
	 * @param limit 最大取得件数
	 * @param offset 読み飛ばす件数
	 * @return 未読通知一覧
	 */
	public List<UserBrowserNotificationDetailDto> listUnread(
			Authentication authentication, Integer limit, int offset) {
		return notificationRepository.findUnreadByUserId(resolveUserId(authentication), normalizeLimit(limit),
				normalizeOffset(offset),
				canViewMachine(authentication), canViewMaster(authentication));
	}

	/**
	 * ログインユーザーの未読通知件数を取得する。
	 *
	 * @param authentication 認証情報
	 * @return 未読通知件数
	 */
	public NotificationUnreadCountDto countUnread(Authentication authentication) {

		// サイドバー件数にも一覧と同じ閲覧条件を渡し、閲覧不可の通知の存在や件数を露出させない。
		return new NotificationUnreadCountDto(notificationRepository.countUnreadByUserId(resolveUserId(authentication),
				canViewMachine(authentication), canViewMaster(authentication)));
	}

	private boolean canViewMachine(Authentication authentication) {
		return hasRole(authentication, "ADMIN") || hasRole(authentication, "OPERATOR");
	}

	private boolean canViewMaster(Authentication authentication) {
		return hasRole(authentication, "ADMIN");
	}

	private boolean hasRole(Authentication authentication, String role) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
	}

	/**
	 * ログインユーザーの通知を配信済みに更新する。
	 *
	 * @param authentication 認証情報
	 * @param notificationId 通知ID
	 */
	@Transactional
	public void markDelivered(Authentication authentication, UUID notificationId) {
		requireUpdated(notificationRepository.markDelivered(notificationId, resolveUserId(authentication)), notificationId);
	}

	/**
	 * ログインユーザーの通知を既読に更新する。
	 *
	 * @param authentication 認証情報
	 * @param notificationId 通知ID
	 */
	@Transactional
	public void markRead(Authentication authentication, UUID notificationId) {
		requireUpdated(notificationRepository.markRead(notificationId, resolveUserId(authentication)), notificationId);
	}

	/**
	 * ログインユーザーの通知を非表示に更新する。
	 *
	 * @param authentication 認証情報
	 * @param notificationId 通知ID
	 */
	@Transactional
	public void dismiss(Authentication authentication, UUID notificationId) {
		requireUpdated(notificationRepository.dismiss(notificationId, resolveUserId(authentication)), notificationId);
	}

	/**
	 * 認証情報からユーザーIDを解決する。
	 *
	 * @param authentication 認証情報
	 * @return ユーザーID
	 */
	private Long resolveUserId(Authentication authentication) {
		String email = authentication != null ? authentication.getName() : null;

		if (!StringUtils.hasText(email)) {
			throw new ResourceNotFoundException("User", "anonymous");
		}

		return userRepository.findByEmail(email)
				.map(MUsersRecord::getId)
				.orElseThrow(() -> new ResourceNotFoundException("User", email));
	}

	/** カーソル方式で通知一覧を取得する。
	 *
	 * @param authentication 認証情報
	 * @param limit 取得件数
	 * @param cursor 継続位置
	 * @param unreadOnly 未読限定
	 * @return ページ応答
	 */
	public NotificationPageDto page(
			Authentication authentication, Integer limit, String cursor, boolean unreadOnly) {
		return notificationRepository.findPage(resolveUserId(authentication), normalizeLimit(limit),
				NotificationCursor.decode(cursor), unreadOnly,
				canViewMachine(authentication), canViewMaster(authentication));
	}

	/**
	 * 通知一覧の取得件数を範囲内に丸める。
	 *
	 * @param limit リクエストされた取得件数
	 * @return 実際に使用する取得件数
	 */
	private int normalizeLimit(Integer limit) {
		return Pagination.clampLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
	}

	/**
	 * 読み飛ばし件数が有効範囲であることを確認する。
	 *
	 * @param offset 読み飛ばす件数
	 * @return 検証済みの読み飛ばし件数
	 */
	private int normalizeOffset(int offset) {
		return Pagination.requireOffset(offset, CommonMessageConstants.OFFSET_INVALID);
	}

	/**
	 * 更新対象が存在したことを確認する。
	 *
	 * @param count 更新件数
	 * @param notificationId 通知ID
	 */
	private void requireUpdated(int count, UUID notificationId) {
		if (count == 0) {
			throw new ResourceNotFoundException("Notification", notificationId);
		}
	}
}
