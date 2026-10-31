package jp.xoki.web.zunda.smarthomewebserverbackend.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestCreateRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.io.InputStream;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;

@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false",
        "notification.discord.enabled=false", "notification.discord.security-events.enabled=false",
        "spring.batch.job.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class BatchAttachmentIntegrationTest {
    static final Path STORAGE;
    static { try { STORAGE = Files.createTempDirectory("batch-attachment-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.batch.file-storage.root-directory", STORAGE::toString);
    }
    @Autowired BatchAttachmentService files;
    @Autowired BatchExecutionRequestService executions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    long definition() {
        return jdbc.queryForObject("""
                insert into m_batch_definitions(batch_name,view_name,category,execution_type,log_path,execution_command)
                values (?,'fixture','test','shell','test.log','echo fixture') returning id
                """, Long.class, "attachment-" + UUID.randomUUID());
    }
    MockMultipartFile file(String text) {
        return new MockMultipartFile("file", "run.sh", "text/plain", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test
    void replacementKeepsExecutionManifestAndOriginalBytes() throws Exception {
        long definition = definition();
        BatchAttachmentService.Attachment original = files.upload(definition, UUID.randomUUID(), "scripts/run.sh", "script", null, file("old"));
        long device = jdbc.queryForObject("insert into m_devices(device_name,address,status) values ('fixture','127.0.0.1','1') returning id", Long.class);
        BatchExecutionRequestCreateRequest input = new BatchExecutionRequestCreateRequest(); input.setTargetAgentId(device);
        BatchExecutionRequestDetailDto request = executions.createManualRequest(definition, input, "test");
        BatchAttachmentService.Attachment replacement = files.upload(definition, UUID.randomUUID(), "scripts/run.sh", "script", original.id(), file("new"));
        assertThat(replacement.id()).isNotEqualTo(original.id());
        assertThat(files.list(definition)).extracting(BatchAttachmentService.Attachment::id).containsExactly(replacement.id());
        assertThat(files.executionFiles(request.getExecutionId())).extracting(BatchAttachmentService.Attachment::id).containsExactly(original.id());
        try (InputStream body = files.download(request.getExecutionId(), original.id()).getInputStream()) {
            assertThat(new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("old");
        }
        UUID exceptionInput1 = request.getExecutionId();
        long exceptionInput2 = replacement.id();
        assertThatThrownBy(() -> files.download(exceptionInput1, exceptionInput2)).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("update t_batch_global_settings set work_directory_retention_days=2 where id=1");
        assertThat(executions.getDetail(request.getExecutionId()).getWorkDirectoryRetentionDays()).isEqualTo(7);
        assertThat(executions.createManualRequest(definition, input, "test").getWorkDirectoryRetentionDays()).isEqualTo(2);
    }
    @Test
    void replayIsIdempotentAndChangedContentIsRejected() throws Exception {
        long definition = definition(); UUID key = UUID.randomUUID();
        BatchAttachmentService.Attachment original = files.upload(definition, key, "run.sh", "script", null, file("one"));
        assertThat(files.upload(definition, key, "run.sh", "script", null, file("one")).id()).isEqualTo(original.id());
        MockMultipartFile exceptionInput3 = file("two");
        assertThatThrownBy(() -> files.upload(definition, key, "run.sh", "script", null, exceptionInput3))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void upperLimitsDoNotDeleteExistingFilesAndDetachCanResolveExcess() throws Exception {
        long definition = definition();
        BatchAttachmentService.Attachment a = files.upload(definition, UUID.randomUUID(), "a.txt", "input", null, file("a"));
        files.upload(definition, UUID.randomUUID(), "b.txt", "input", null, file("b"));
        jdbc.update("update t_batch_global_settings set attachment_max_count=1 where id=1");
        assertThat(files.exceeds(files.list(definition))).isTrue();
        assertThatThrownBy(() -> files.snapshot(definition)).isInstanceOf(IllegalArgumentException.class);
        UUID exceptionInput4 = UUID.randomUUID();
        MockMultipartFile exceptionInput5 = file("c");
        assertThatThrownBy(() -> files.upload(definition, exceptionInput4, "c.txt", "input", null, exceptionInput5))
                .isInstanceOf(IllegalArgumentException.class);
        files.detach(definition, a.id());
        assertThat(files.snapshot(definition)).hasSize(1);
    }
    @Test
    void byteLimitsApplyToSingleFileAndDefinitionTotal() throws Exception {
        long definition = definition();
        jdbc.update("update t_batch_global_settings set attachment_max_file_mib=1,attachment_max_total_mib=1 where id=1");
        MockMultipartFile oversized = new MockMultipartFile("file", "large.bin", "application/octet-stream", new byte[1048577]);
        UUID exceptionInput6 = UUID.randomUUID();
        assertThatThrownBy(() -> files.upload(definition, exceptionInput6, "large.bin", "input", null, oversized))
                .isInstanceOf(IllegalArgumentException.class);
        MockMultipartFile exact = new MockMultipartFile("file", "exact.bin", "application/octet-stream", new byte[1048576]);
        files.upload(definition, UUID.randomUUID(), "exact.bin", "input", null, exact);
        UUID exceptionInput7 = UUID.randomUUID();
        MockMultipartFile exceptionInput8 = file("x");
        assertThatThrownBy(() -> files.upload(definition, exceptionInput7, "extra.txt", "input", null, exceptionInput8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(files.list(definition)).hasSize(1);
    }

    @Test
    void unsafeAndConflictingPathsAndUnauthorizedUploadsAreRejected() throws Exception {
        for (String path : new String[]{"../x", "/x", "C:/x", "input/../x", "NUL.txt", "a\\b", "x.", "x/"}) {
            assertThatThrownBy(() -> BatchAttachmentService.destination(path)).isInstanceOf(IllegalArgumentException.class);
        }
        long definition = definition();
        files.upload(definition, UUID.randomUUID(), "Input/A.csv", "input", null, file("x"));
        UUID exceptionInput9 = UUID.randomUUID();
        MockMultipartFile exceptionInput10 = file("y");
        assertThatThrownBy(() -> files.upload(definition, exceptionInput9, "input/a.CSV", "input", null, exceptionInput10))
                .isInstanceOf(IllegalArgumentException.class);
        UUID exceptionInput11 = UUID.randomUUID();
        MockMultipartFile exceptionInput12 = file("z");
        assertThatThrownBy(() -> files.upload(definition, exceptionInput11, "Input", "input", null, exceptionInput12))
                .isInstanceOf(IllegalArgumentException.class);
        mvc.perform(multipart("/api/master/batch/" + definition + "/files").file(file("x"))
                .param("upload_request_id", UUID.randomUUID().toString()).param("destination_path", "safe.txt")
                .param("file_category", "input").with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/master/batch/" + definition + "/files").file(file("x"))
                .param("upload_request_id", UUID.randomUUID().toString()).param("destination_path", "safe.txt")
                .param("file_category", "input").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/agent/batch/executions/" + UUID.randomUUID() + "/attachments")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }
}
