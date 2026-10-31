package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.HashMap;

/** 専用検証DBで車検更新の認可、項目分離、競合、履歴保存を検証する。 */
@SpringBootTest(properties = {"mqtt.enabled=false", "notification.discord.enabled=false",
        "notification.discord.security-events.enabled=false", "spring.batch.job.enabled=false",
        "app.vehicle-inspection-notification.enabled=false", "gateway.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class VehicleInspectionRenewalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired VehicleService vehicles;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATOR"})
    void renewalUpdatesOnlyInspectionFieldsAndRecordsActor(String role) throws Exception {
        long id = createVehicle();
        Map<String, Object> before = jdbc.queryForMap("select * from t_vehicles where id=?", id);
        mvc.perform(put(path(id)).with(user("renewal-operator").roles(role)).contentType("application/json")
                .content(body("2026-11-01", "2028-11-01", "2026-10-24")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspection_expiry_date").value("2028-11-01"))
                .andExpect(jsonPath("$.issued_date").value("2026-10-24"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.updated_by").value("renewal-operator"));
        Map<String, Object> after = jdbc.queryForMap("select * from t_vehicles where id=?", id);
        for (String field : new String[]{"inspection_expiry_date", "issued_date", "status", "updated_by", "updated_dt"}) {
            before.remove(field);
            after.remove(field);
        }
        assertThat(after).isEqualTo(before);
        Map<String, Object> history = jdbc.queryForMap("select previous_expiry_date, new_expiry_date, changed_by "
                + "from t_vehicle_inspection_histories where vehicle_id=? and change_type='EXPIRY_UPDATED'", id);
        assertThat(history).containsEntry("previous_expiry_date", java.sql.Date.valueOf("2026-11-01"))
                .containsEntry("new_expiry_date", java.sql.Date.valueOf("2028-11-01"))
                .containsEntry("changed_by", "renewal-operator");
    }

    @Test
    void omittedIssueDatePreservesStoredValueAndRepeatedRenewalConflicts() throws Exception {
        long id = createVehicle();
        String request = body("2026-11-01", "2028-11-01", null);
        mvc.perform(put(path(id)).with(user("operator").roles("OPERATOR"))
                .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.issued_date").value("2024-11-01"));
        mvc.perform(put(path(id)).with(user("operator").roles("OPERATOR"))
                .contentType("application/json").content(request)).andExpect(status().isConflict());
        assertThat(expiryHistoryCount(id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "BAN_USER"})
    void readOnlyAndBannedRolesCannotRenew(String role) throws Exception {
        long id = createVehicle();
        mvc.perform(put(path(id)).with(user("viewer").roles(role)).contentType("application/json")
                .content(body("2026-11-01", "2028-11-01", null))).andExpect(status().isForbidden());
        assertThat(expiryHistoryCount(id)).isZero();
    }

    @Test
    void anonymousRenewalIsRejected() throws Exception {
        long id = createVehicle();
        mvc.perform(put(path(id)).contentType("application/json")
                .content(body("2026-11-01", "2028-11-01", null))).andExpect(status().isUnauthorized());
        assertThat(expiryHistoryCount(id)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-01", "2026-10-31"})
    void sameOrEarlierExpiryIsRejectedWithoutHistory(String expiry) throws Exception {
        long id = createVehicle();
        mvc.perform(put(path(id)).with(user("operator").roles("OPERATOR")).contentType("application/json")
                .content(body("2026-11-01", expiry, null))).andExpect(status().isBadRequest());
        assertThat(expiryHistoryCount(id)).isZero();
        assertThat(vehicles.detail(id).getInspectionExpiryDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"previous_expiry_date\":\"2026-11-01\"}",
            "{\"inspection_expiry_date\":\"2028-11-01\"}",
            "{\"previous_expiry_date\":\"2026-11-01\",\"inspection_expiry_date\":\"bad\"}"})
    void datesMustBePresentAndWellFormed(String request) throws Exception {
        long id = createVehicle();
        mvc.perform(put(path(id)).with(user("operator").roles("OPERATOR")).contentType("application/json")
                .content(request)).andExpect(status().isBadRequest());
        assertThat(expiryHistoryCount(id)).isZero();
    }

    @Test
    void archivedAndMissingVehiclesCannotBeRenewed() throws Exception {
        long id = createVehicle();
        vehicles.archive(id, "admin");
        String request = body("2026-11-01", "2028-11-01", null);
        mvc.perform(put(path(id)).with(user("operator").roles("OPERATOR"))
                .contentType("application/json").content(request)).andExpect(status().isBadRequest());
        mvc.perform(put(path(Long.MAX_VALUE)).with(user("operator").roles("OPERATOR"))
                .contentType("application/json").content(request)).andExpect(status().isNotFound());
        assertThat(expiryHistoryCount(id)).isZero();
    }

    private long createVehicle() {
        String unique = UUID.randomUUID().toString();
        return vehicles.create(VehicleSaveRequest.builder().displayName("Renewal test")
                .registrationNumber(unique).chassisNumber(unique).vehicleName("Test vehicle")
                .inspectionExpiryDate(LocalDate.of(2026, 11, 1)).issuedDate(LocalDate.of(2024, 11, 1))
                .notes("Unrelated fields must remain unchanged").build(), "creator").getId();
    }

    private String path(long id) {
        return "/api/vehicles/" + id + "/inspection";
    }

    private String body(String previous, String expiry, String issued) throws Exception {
        HashMap<String, String> input = new java.util.HashMap<>(Map.of("previous_expiry_date", previous, "inspection_expiry_date", expiry));
        if (issued != null) input.put("issued_date", issued);
        return mapper.writeValueAsString(input);
    }

    private int expiryHistoryCount(long id) {
        return jdbc.queryForObject("select count(*) from t_vehicle_inspection_histories "
                + "where vehicle_id=? and change_type='EXPIRY_UPDATED'", Integer.class, id);
    }
}
