package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationPreferenceDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationPreferenceSaveDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** ユーザー別通知受信設定の参照と保存を提供するService。 */
@Service
@RequiredArgsConstructor
public class UserNotificationPreferenceService {

	/** 通知DBアクセス。 */
	private final NotificationRepository notificationRepository;

	/** ユーザーDBアクセス。 */
	private final UserRepository userRepository;

	/**
	 * ユーザー別通知受信設定を取得する。
	 *
	 * @param userId ユーザーID
	 * @return 全通知種別の通知受信設定
	 */
	public List<UserNotificationPreferenceDetailDto> list(Long userId) {
		requireUser(userId);
		return notificationRepository.findPreferencesByUserId(userId);
	}

	/**
	 * ユーザー別通知受信設定を保存する。
	 *
	 * @param userId ユーザーID
	 * @param preferences 保存内容
	 * @return 保存後の全通知種別の通知受信設定
	 */
	@Transactional
	public List<UserNotificationPreferenceDetailDto> save(
			Long userId,
			List<UserNotificationPreferenceSaveDto> preferences
	) {
		requireUser(userId);
		preferences.forEach(preference -> notificationRepository.savePreference(userId, preference));
		return list(userId);
	}

	/**
	 * ユーザー存在確認を行う。
	 *
	 * @param userId ユーザーID
	 */
	private void requireUser(Long userId) {
		userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, userId));
	}

}
