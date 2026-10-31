package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationRoleCode;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationTargetType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** 通知ルート設定マスタの参照と保存を提供するService。 */
@Service
@RequiredArgsConstructor
public class NotificationRouteSettingService {

	/** 通知DBアクセス。 */
	private final NotificationRepository notificationRepository;

	/** ユーザーDBアクセス。 */
	private final UserRepository userRepository;

	/**
	 * 通知ルート設定一覧を取得する。
	 *
	 * @return 通知ルート設定一覧
	 */
	public List<NotificationRouteSettingDetailDto> list() {
		return notificationRepository.findRouteSettings();
	}

	/**
	 * 通知ルート設定詳細を取得する。
	 *
	 * @param id 通知ルート設定ID
	 * @return 通知ルート設定詳細
	 */
	public NotificationRouteSettingDetailDto detail(Long id) {
		return notificationRepository.findRouteSettingById(id)
				.orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.NOTIFICATION_ROUTE_RESOURCE, id));
	}

	/**
	 * 通知ルート設定を登録または更新する。
	 *
	 * @param request 保存内容
	 * @return 保存後の通知ルート設定詳細
	 */
	@Transactional
	public NotificationRouteSettingDetailDto save(NotificationRouteSettingSaveRequest request) {
		if (request.id() != null) {
			detail(request.id());
		}

		NotificationRouteSettingSaveRequest normalizedRequest = validateAndNormalizeTarget(request);
		Long savedId = notificationRepository.saveRouteSetting(normalizedRequest);
		return detail(savedId);
	}

	/**
	 * 通知対象種別に応じた必須項目と参照先を検証し、通知ロールコードを正規化する。
	 *
	 * @param request 保存内容
	 * @return 通知対象を正規化した保存内容
	 */
	private NotificationRouteSettingSaveRequest validateAndNormalizeTarget(
			NotificationRouteSettingSaveRequest request
	) {
		if (request.target_type() == NotificationTargetType.USER) {
			if (request.target_user_id() == null) {
				throw new IllegalArgumentException(CommonMessageConstants.ROUTE_TARGET_USER_REQUIRED);
			}
			userRepository.findById(request.target_user_id())
					.orElseThrow(() -> new ResourceNotFoundException(
							CommonMessageConstants.USER_RESOURCE,
							request.target_user_id()));
			return request;
		}

		if (!StringUtils.hasText(request.target_role())) {
			throw new IllegalArgumentException(CommonMessageConstants.ROUTE_TARGET_ROLE_REQUIRED);
		}

		NotificationRoleCode roleCode;
		try {
			roleCode = NotificationRoleCode.from(request.target_role().trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(CommonMessageConstants.ROUTE_TARGET_ROLE_INVALID, e);
		}

		if (!notificationRepository.existsEnabledNotificationRole(roleCode.getStoredName())) {
			throw new IllegalArgumentException(CommonMessageConstants.ROUTE_TARGET_ROLE_UNAVAILABLE);
		}

		return new NotificationRouteSettingSaveRequest(
				request.id(),
				request.notification_type(),
				request.source_type(),
				request.source_id(),
				request.target_type(),
				roleCode.getStoredName(),
				request.target_user_id(),
				request.minimum_level(),
				request.browser_enabled(),
				request.discord_enabled(),
				request.enabled());
	}
}
