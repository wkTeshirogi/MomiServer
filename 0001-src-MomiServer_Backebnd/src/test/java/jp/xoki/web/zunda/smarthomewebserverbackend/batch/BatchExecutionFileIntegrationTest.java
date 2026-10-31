package jp.xoki.web.zunda.smarthomewebserverbackend.batch;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileRegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionFileService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.InputStream;

@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false",
        "notification.discord.enabled=false", "notification.discord.security-events.enabled=false",
        "spring.batch.job.enabled=false", "app.vehicle-inspection-notification.enabled=false"})
@Transactional
class BatchExecutionFileIntegrationTest {
    @Autowired BatchExecutionFileService files;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path directory;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "display-name.txt"})
    void registrationPersistsMetadataAndDownloadsOriginalContent(String displayName) throws Exception {
        UUID executionId = execution();
        Path source = Files.writeString(directory.resolve("original.txt"), "abc");
        BatchExecutionFileRegisterRequest request = request(source);
        request.setFileName(displayName);
        BatchExecutionFileDetailDto saved = files.register(executionId, request);
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getFileName()).isEqualTo("display-name.txt".equals(displayName) ? displayName : "original.txt");
        assertThat(saved.getFilePath()).isEqualTo(source.toAbsolutePath().toString());
        assertThat(saved.getFileSize()).isEqualTo(3L);
        assertThat(saved.getContentType()).isEqualTo("text/plain");
        assertThat(saved.getSha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        Long requestId = jdbc.queryForObject("select id from t_batch_execution_requests where execution_id=?",
                Long.class, executionId);
        assertThat(saved.getExecutionRequestId()).isEqualTo(requestId);
        assertThat(files.getFile(executionId, saved.getId())).usingRecursiveComparison().isEqualTo(saved);
        try (InputStream input = files.download(executionId, saved.getId()).getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo(new byte[]{'a', 'b', 'c'});
        }
        BatchExecutionFileDetailDto second = files.register(executionId, request);
        assertThat(files.list(executionId)).extracting(BatchExecutionFileDetailDto::getId)
                .containsExactly(saved.getId(), second.getId());
        assertThat(files.list(execution())).isEmpty();
    }

    @Test
    void filesFromAnotherExecutionCannotBeReadOrDownloaded() throws Exception {
        UUID owner = execution();
        UUID other = execution();
        BatchExecutionFileDetailDto saved = files.register(owner, request(Files.writeString(directory.resolve("owned.txt"), "abc")));
        Long fileId = saved.getId();
        assertThat(files.list(other)).isEmpty();
        assertThatThrownBy(() -> files.getFile(other, fileId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> files.download(other, fileId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removedPhysicalFileRetainsMetadataButCannotBeDownloaded() throws Exception {
        UUID executionId = execution();
        Path source = Files.writeString(directory.resolve("removed.txt"), "abc");
        BatchExecutionFileDetailDto saved = files.register(executionId, request(source));
        Long fileId = saved.getId();
        Files.delete(source);
        assertThat(files.getFile(executionId, fileId)).usingRecursiveComparison().isEqualTo(saved);
        assertThatThrownBy(() -> files.download(executionId, fileId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void missingFilesAndDirectoriesAreNotRegistered() {
        UUID executionId = execution();
        BatchExecutionFileRegisterRequest missing = request(directory.resolve("missing.txt"));
        BatchExecutionFileRegisterRequest folder = request(directory);
        assertThatThrownBy(() -> files.register(executionId, missing)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> files.register(executionId, folder)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(files.list(executionId)).isEmpty();
        assertThatThrownBy(() -> files.getFile(executionId, Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> files.download(executionId, Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void allOperationsRejectUnknownExecution() {
        UUID missing = UUID.randomUUID();
        BatchExecutionFileRegisterRequest request = request(directory.resolve("missing.txt"));
        assertThatThrownBy(() -> files.list(missing)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> files.getFile(missing, 1L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> files.download(missing, 1L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> files.register(missing, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    private BatchExecutionFileRegisterRequest request(Path path) {
        BatchExecutionFileRegisterRequest request = new BatchExecutionFileRegisterRequest();
        request.setFilePath(path.toString());
        request.setContentType("text/plain");
        return request;
    }

    private UUID execution() {
        Long definition = jdbc.queryForObject("""
                insert into m_batch_definitions(batch_name,view_name,category,execution_type,log_path,execution_command)
                values (?,'File test','test','shell','test.log','echo test') returning id
                """, Long.class, "file-test-" + UUID.randomUUID());
        Long device = jdbc.queryForObject("""
                insert into m_devices(device_name,address,status)
                values ('file-test','192.0.2.10','1') returning id
                """, Long.class);
        return jdbc.queryForObject("""
                insert into t_batch_execution_requests(batch_definition_id,target_agent_id,requested_type,
                execution_status,execution_command,start_deadline_at)
                values (?,?,'manual','success','echo test',CURRENT_TIMESTAMP) returning execution_id
                """, UUID.class, definition, device);
    }
}
