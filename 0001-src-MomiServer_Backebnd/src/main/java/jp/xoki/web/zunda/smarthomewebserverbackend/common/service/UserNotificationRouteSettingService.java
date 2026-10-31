package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** 個別ユーザー宛て通知ルート設定の参照を提供するService。 */
@Service
@RequiredArgsConstructor
public class UserNotificationRouteSettingService {

	/** 通知DBアクセス。 */
	private final NotificationRepository notificationRepository;

	/** ユーザーDBアクセス。 */
	private final UserRepository userRepository;

	/**
	 * 指定ユーザーを直接宛先にしている通知ルート設定を取得する。
	 *
	 * @param userId ユーザーID
	 * @return 個別ユーザー宛て通知ルート設定一覧
	 */
	public List<UserNotificationRouteSettingDetailDto> listByTargetUserId(Long userId) {
		userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, userId));
		return notificationRepository.findRouteSettingsByTargetUserId(userId);
	}
}
