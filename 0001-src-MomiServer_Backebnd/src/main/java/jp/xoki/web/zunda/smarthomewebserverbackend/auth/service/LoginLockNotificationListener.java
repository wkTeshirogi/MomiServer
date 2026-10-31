package jp.xoki.web.zunda.smarthomewebserverbackend.auth.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.model.LoginLockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** ロックのコミット後に管理者だけへ通知する。通知失敗でロックを取り消さない。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginLockNotificationListener {
    private final LoginLockNotificationService notifications;

    /** ロック開始通知
     *
     * ロック継続中にはイベントを発行せず、通知の重複を避ける。
     * @param event 確定したロック状態
     */
    @TransactionalEventListener
    public void onLocked(LoginLockedEvent event) {
        try {
            notifications.send(event);
        } catch (RuntimeException exception) {
            log.error("Login lock notification failed. userId={}", event.userId(), exception);
        }
    }
}
