package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthorization;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.io.IOException;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

@RestController
@RequiredArgsConstructor
public class BatchAttachmentController {
    private final BatchAttachmentService files;
    private final AgentAuthorization authorization;

    @GetMapping("/api/master/batch/{definitionId}/files")
    @AdminOnly
    public Map<String, Object> list(@PathVariable long definitionId) {
        List<BatchAttachmentService.Attachment> attachments = files.list(definitionId);
        return Map.of("files", attachments, "exceeds_limits", files.exceeds(attachments));
    }

    @PostMapping(value = "/api/master/batch/{definitionId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminOnly
    public BatchAttachmentService.Attachment upload(@PathVariable long definitionId,
            @RequestParam("upload_request_id") UUID uploadRequestId,
            @RequestParam("destination_path") String destinationPath,
            @RequestParam("file_category") String fileCategory,
            @RequestParam(value = "replaces_id", required = false) Long replacesId,
            @RequestParam MultipartFile file) throws IOException {
        return files.upload(definitionId, uploadRequestId, destinationPath, fileCategory, replacesId, file);
    }

    @PostMapping("/api/master/batch/{definitionId}/files/{fileId}/status")
    @AdminOnly
    public void detach(@PathVariable long definitionId, @PathVariable long fileId,
            @RequestParam String status) {
        if (!"deleted".equals(status)) { throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_STATUS_INVALID); }
        files.detach(definitionId, fileId);
    }

    @GetMapping("/api/v1/agent/batch/executions/{id}/attachments")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public List<BatchAttachmentService.Attachment> manifest(@PathVariable UUID id) {
        authorization.requireExecution(id);
        return files.executionFiles(id);
    }

    @GetMapping("/api/v1/agent/batch/executions/{id}/attachments/{fileId}")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional(rollbackFor = IOException.class)
    public ResponseEntity<Resource> download(@PathVariable UUID id, @PathVariable long fileId) throws IOException {
        authorization.requireExecution(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Cache-Control", "no-store").body(files.download(id, fileId));
    }
}
