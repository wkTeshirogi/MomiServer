package jp.xoki.web.zunda.smarthomewebserverbackend.auth.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.constants.AuthMessageConstants;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.model.LoginLockedEvent;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.util.Notifications;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** ログイン拒否のコミット済み状態と、通知用トランザクションを分離する。 */
@Service
@RequiredArgsConstructor
public class LoginLockNotificationService {
    private final UserRepository users;
    private final Notifications notifications;

    /** 管理者宛てロック通知を保存する。
     *
     * @param event ロック開始情報
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(LoginLockedEvent event) {

        // 管理者へ、ユーザーIDと解除予定時刻だけを通知する。
        notifications.sendNotice(BrowserNotificationCreateDto.builder()
                .notificationType(NotificationType.SECURITY_ALERT).sourceType(NotificationSourceType.SECURITY_EVENT)
                .sourceId(event.userId().toString()).level(NotificationLevel.WARNING)
                .title(AuthMessageConstants.LOGIN_LOCK_TITLE)
                .message(AuthMessageConstants.LOGIN_LOCK_MESSAGE.formatted(event.userId(), event.lockedUntil()))
                .linkPath("/master/users/" + event.userId()).build(), users.findAdminIds());
    }
}
