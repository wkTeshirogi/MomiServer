package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service;

import java.time.Clock;
import java.time.LocalDate;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 保存のコミット後に別Beanの通知トランザクションを開始する。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleSavedNotificationListener {
    private final VehicleInspectionNotificationService notifications;
    private final Clock clock;
    private final PlatformTransactionManager transactions;

    /** 保存済み車両の期限判定
     *
     * 通知失敗で保存結果を失敗へ戻さず、次の日次判定で補完できるようにする。
     * @param event 保存が確定した車両
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaved(VehicleSavedEvent event) {
        try {

            // 日次の既存トランザクション契約は維持し、コミット後のこの入口だけ新しいトランザクションを開始する。
            TransactionTemplate transaction = new TransactionTemplate(transactions);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> notifications.notifyIfDue(event.vehicleId(), LocalDate.now(clock)));
        } catch (RuntimeException exception) {
            log.error("Vehicle notification after save failed. vehicleId={}", event.vehicleId(), exception);
        }
    }
}
