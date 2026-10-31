package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleService;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleInspectionNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 保存が確定したときだけ、実通知と通知済み段階が同じトランザクションで作られることを確認する。 */
@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false", "notification.discord.enabled=false",
        "notification.discord.security-events.enabled=false", "spring.batch.job.enabled=false",
        "app.vehicle-inspection-notification.enabled=false"})
class VehicleSavedNotificationIntegrationTest {
    @Autowired VehicleService vehicles;
    @Autowired VehicleInspectionNotificationService notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void saveCreatesDueTodayOnceAndNextDayCreatesIndependentExpiredStage() {
        LocalDate today = LocalDate.now(clock);
        Long id = vehicles.create(request(today), "test").getId();
        assertThat(stages(id)).containsExactly("DUE_TODAY");
        assertThat(notifications.notifyIfDue(id, today)).isFalse();
        assertThat(notifications.notifyIfDue(id, today.plusDays(1))).isTrue();
        assertThat(notifications.notifyIfDue(id, today.plusDays(2))).isFalse();
        assertThat(stages(id)).containsExactlyInAnyOrder("DUE_TODAY", "EXPIRED");
    }

    @Test
    void expiryUpdateEvaluatesNewPeriodWithoutResolvingOldNotification() {
        LocalDate today = LocalDate.now(clock);
        VehicleSaveRequest input = request(today.plusDays(100));
        Long id = vehicles.create(input, "test").getId();
        assertThat(stages(id)).isEmpty();
        input.setInspectionExpiryDate(today.plusDays(10));
        vehicles.update(id, input, "test");
        assertThat(stages(id)).containsExactly("ONE_MONTH");
        input.setInspectionExpiryDate(today.plusDays(20));
        vehicles.update(id, input, "test");
        assertThat(stages(id)).containsExactly("ONE_MONTH", "ONE_MONTH");
        assertThat(jdbc.queryForObject("select count(*) from t_vehicle_inspection_notifications "
                + "where vehicle_id=? and notification_id is not null", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void rollbackDoesNotPublishNotification() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        Long id = transaction.execute(status -> {
            Long createdId = vehicles.create(request(LocalDate.now(clock)), "test").getId();
            assertThat(stages(createdId)).isEmpty();
            status.setRollbackOnly();
            return createdId;
        });
        assertThat(stages(id)).isEmpty();
    }

    private java.util.List<String> stages(Long id) {
        return jdbc.queryForList("select notification_stage from t_vehicle_inspection_notifications where vehicle_id=? "
                + "order by inspection_expiry_date,notification_stage", String.class, id);
    }

    private VehicleSaveRequest request(LocalDate expiry) {
        String unique = UUID.randomUUID().toString();
        return VehicleSaveRequest.builder().displayName("Notice test").registrationNumber(unique)
                .chassisNumber(unique).vehicleName("Test vehicle").inspectionExpiryDate(expiry).build();
    }
}
