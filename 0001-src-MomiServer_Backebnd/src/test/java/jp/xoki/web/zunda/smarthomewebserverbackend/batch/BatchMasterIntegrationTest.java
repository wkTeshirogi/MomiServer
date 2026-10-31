package jp.xoki.web.zunda.smarthomewebserverbackend.batch;

import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionParametersRecord;
import jp.xoki.web.zunda.jooq.tables.records.MBatchSchedulesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionParameterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionParameterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionHistoryDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionHistoryRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionParameterRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchDefinitionParameterService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionHistoryService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchScheduleService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false",
        "notification.discord.enabled=false", "notification.discord.security-events.enabled=false",
        "spring.batch.job.enabled=false", "app.vehicle-inspection-notification.enabled=false"})
@Transactional
class BatchMasterIntegrationTest {
    @Autowired BatchScheduleService schedules;
    @Autowired BatchScheduleRepository scheduleRepository;
    @Autowired BatchDefinitionParameterService parameters;
    @Autowired BatchDefinitionParameterRepository parameterRepository;
    @Autowired BatchExecutionHistoryService histories;
    @Autowired JdbcTemplate jdbc;

    @Test
    void schedulesRoundTripAndRespectDefinitionAgentAndEnabledFilters() {
        long definitionId = definition();
        long deviceId = device();
        BatchScheduleRegistRequest request = BatchScheduleRegistRequest.builder().batchDefinitionId(definitionId)
                .targetAgentId(deviceId).cronExpression("0 0 1 * * *")
                .startDate(LocalDate.of(2027, 1, 1)).endDate(LocalDate.of(2027, 12, 31))
                .allowHolidayExecution(true).enabled(true).priority(4).build();
        BatchScheduleDetailDto created = schedules.save(request);
        assertThat(created.getId()).isPositive();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertSchedule(created, request);
        assertThat(schedules.getBatchScheduleDetailDto(created.getId())).usingRecursiveComparison().isEqualTo(created);
        assertThat(schedules.getAllBatchSchedules()).extracting(BatchScheduleDetailDto::getId).contains(created.getId());
        assertThat(schedules.getBatchSchedules(definitionId)).extracting(BatchScheduleDetailDto::getId)
                .containsExactly(created.getId());
        assertThat(schedules.getBatchSchedules(Long.MAX_VALUE)).isEmpty();
        assertThat(scheduleRepository.findEnabledByTargetAgentId(deviceId)).extracting(MBatchSchedulesRecord::getId)
                .containsExactly(created.getId());
        assertThat(scheduleRepository.findEnabledByTargetAgentId(Long.MAX_VALUE)).isEmpty();

        request.setId(created.getId());
        request.setCronExpression("0 30 2 * * *");
        request.setStartDate(null);
        request.setEndDate(null);
        request.setAllowHolidayExecution(false);
        request.setEnabled(false);
        request.setPriority(9);
        BatchScheduleDetailDto updated = schedules.save(request);
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertSchedule(updated, request);
        assertThat(scheduleRepository.findEnabledByTargetAgentId(deviceId)).isEmpty();
        assertThat(scheduleRepository.findEnabled()).extracting(MBatchSchedulesRecord::getId)
                .doesNotContain(created.getId());
        assertThat(scheduleRepository.deleteByBatchDefinitionId(definitionId)).isEqualTo(1);
        assertThat(schedules.getBatchScheduleDetailDto(created.getId())).isNull();
    }

    @Test
    void parametersRoundTripAndOnlyEnabledParametersAreSelected() {
        long definitionId = definition();
        BatchDefinitionParameterRegistRequest request = BatchDefinitionParameterRegistRequest.builder().batchDefinitionId(definitionId)
                .parameterName("--date").parameterType("fixed").fixedValue("20270101")
                .dynamicValueType(null).valueFormat("yyyyMMdd").displayOrder(2).enabled(true).build();
        BatchDefinitionParameterDetailDto created = parameters.save(request);
        assertThat(created.getId()).isPositive();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertParameter(created, request);
        assertThat(parameters.getBatchDefinitionParameterDetailDto(created.getId()))
                .usingRecursiveComparison().isEqualTo(created);
        assertThat(parameters.getBatchDefinitionParameters(definitionId))
                .extracting(BatchDefinitionParameterDetailDto::getId).containsExactly(created.getId());
        assertThat(parameters.getBatchDefinitionParameters(Long.MAX_VALUE)).isEmpty();
        assertThat(parameterRepository.findEnabledByBatchDefinitionId(definitionId))
                .extracting(MBatchDefinitionParametersRecord::getId).containsExactly(created.getId());

        request.setId(created.getId());
        request.setParameterName("--updated-date");
        request.setParameterType("dynamic_date");
        request.setFixedValue(null);
        request.setDynamicValueType("today");
        request.setValueFormat("yyyy-MM-dd");
        request.setDisplayOrder(0);
        request.setEnabled(false);
        BatchDefinitionParameterDetailDto updated = parameters.save(request);
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertParameter(updated, request);
        assertThat(parameterRepository.findEnabledByBatchDefinitionId(definitionId)).isEmpty();
        assertThat(parameterRepository.deleteByBatchDefinitionId(definitionId)).isEqualTo(1);
        assertThat(parameters.getBatchDefinitionParameterDetailDto(created.getId())).isNull();
    }

    @Test
    void executionHistoryRoundTripsAndListsNewestFirstWithoutOtherDefinitions() {
        long definitionId = definition();
        BatchExecutionHistoryRegistRequest request = BatchExecutionHistoryRegistRequest.builder().batchDefinitionId(definitionId)
                .testExecution(false).executionStartedDt(LocalDateTime.of(2027, 1, 1, 1, 0))
                .executionResult("running").logPath("batch.log").message("started").build();
        BatchExecutionHistoryDetailDto first = histories.save(request);
        assertThat(first.getId()).isPositive();
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(first.getUpdatedAt()).isNotNull();
        assertHistory(first, request);
        assertThat(histories.getBatchExecutionHistoryDetailDto(first.getId())).usingRecursiveComparison().isEqualTo(first);
        request.setExecutionStartedDt(request.getExecutionStartedDt().plusHours(1));
        BatchExecutionHistoryDetailDto second = histories.save(request);
        assertThat(histories.getBatchExecutionHistories(definitionId))
                .extracting(BatchExecutionHistoryDetailDto::getId).containsExactly(second.getId(), first.getId());
        assertThat(histories.getBatchExecutionHistories(Long.MAX_VALUE)).isEmpty();

        request.setId(first.getId());
        request.setTestExecution(true);
        request.setExecutionFinishedDt(request.getExecutionStartedDt().plusMinutes(1));
        request.setExecutionResult("success");
        request.setExitCode(0);
        request.setLogPath("finished.log");
        request.setMessage("completed");
        BatchExecutionHistoryDetailDto updated = histories.save(request);
        assertThat(updated.getId()).isEqualTo(first.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertHistory(updated, request);
        assertThat(histories.getBatchExecutionHistoryDetailDto(first.getId()))
                .usingRecursiveComparison().isEqualTo(updated);
    }

    @Test
    void missingRecordsReturnNullForDetailsAndRejectUpdates() {
        assertThat(schedules.getBatchScheduleDetailDto(Long.MAX_VALUE)).isNull();
        assertThat(parameters.getBatchDefinitionParameterDetailDto(Long.MAX_VALUE)).isNull();
        assertThat(histories.getBatchExecutionHistoryDetailDto(Long.MAX_VALUE)).isNull();
        BatchScheduleRegistRequest schedule = BatchScheduleRegistRequest.builder().id(Long.MAX_VALUE).build();
        BatchDefinitionParameterRegistRequest parameter = BatchDefinitionParameterRegistRequest.builder().id(Long.MAX_VALUE).build();
        BatchExecutionHistoryRegistRequest history = BatchExecutionHistoryRegistRequest.builder().id(Long.MAX_VALUE).build();
        assertThatThrownBy(() -> schedules.save(schedule)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> parameters.save(parameter)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> histories.save(history)).isInstanceOf(ResourceNotFoundException.class);
    }

    private long definition() {
        return jdbc.queryForObject("""
                insert into m_batch_definitions(batch_name,view_name,category,execution_type,log_path,execution_command)
                values (?,'Master test','test','shell','test.log','echo test') returning id
                """, Long.class, "master-test-" + UUID.randomUUID());
    }

    private long device() {
        return jdbc.queryForObject("""
                insert into m_devices(device_name,address,status)
                values ('master-test','192.0.2.10','1') returning id
                """, Long.class);
    }

    private void assertSchedule(BatchScheduleDetailDto actual, BatchScheduleRegistRequest expected) {
        assertThat(actual).usingRecursiveComparison().comparingOnlyFields("batchDefinitionId", "targetAgentId",
                "cronExpression", "startDate", "endDate", "allowHolidayExecution", "enabled", "priority")
                .isEqualTo(expected);
    }

    private void assertParameter(BatchDefinitionParameterDetailDto actual, BatchDefinitionParameterRegistRequest expected) {
        assertThat(actual).usingRecursiveComparison().comparingOnlyFields("batchDefinitionId", "parameterName",
                "parameterType", "fixedValue", "dynamicValueType", "valueFormat", "displayOrder", "enabled")
                .isEqualTo(expected);
    }

    private void assertHistory(BatchExecutionHistoryDetailDto actual, BatchExecutionHistoryRegistRequest expected) {
        assertThat(actual).usingRecursiveComparison().comparingOnlyFields("batchDefinitionId", "testExecution",
                "executionStartedDt", "executionFinishedDt", "executionResult", "exitCode", "logPath", "message")
                .isEqualTo(expected);
    }
}
