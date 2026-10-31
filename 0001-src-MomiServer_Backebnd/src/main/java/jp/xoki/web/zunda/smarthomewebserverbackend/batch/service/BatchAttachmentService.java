package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService;
import lombok.RequiredArgsConstructor;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchAttachmentRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchAttachmentRepository.UploadMetadata;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import static jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchAttachmentServiceConstants.MIB;
import java.io.InputStream;
import java.io.OutputStream;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** Definition attachments are immutable; execution manifests keep old versions reachable. */
@Service
@RequiredArgsConstructor
public class BatchAttachmentService {
    private final BatchAttachmentRepository repository;
    private final ObjectMapper mapper;
    private final BatchGlobalSettingService settings;
    @Value("${app.batch.file-storage.root-directory:}")
    private String storageRoot;
    public record Attachment(long id, String file_name, String destination_path, String file_category,
            long file_size, String checksum_sha256, OffsetDateTime created_at) { }

    private record UploadPreparation(String target, String category, Long replacesId,
            Record previous, long limit, String key) { }

    private record UploadedFile(long size, String checksum) { }

    public static String destination(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 500 || !Normalizer.isNormalized(raw, Normalizer.Form.NFC)) {
            throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_PATH_INVALID);
        }
        for (String part : raw.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || part.length() > 100 || part.chars().anyMatch(c -> c < 32 || c == 127 || "\\:*?\"<>|".indexOf(c) >= 0)
                    || part.split("\\.")[0].matches("(?i)(CON|PRN|AUX|NUL|COM\\d|LPT\\d)")) {
                throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_PATH_NAME_INVALID);
            }
        }
        return raw;
    }

    private void lockDefinition(long id) {
        if (!repository.lockActiveDefinition(id)) {
            throw new IllegalArgumentException(BatchMessageConstants.DEFINITION_NOT_FOUND);
        }
    }

    public List<Attachment> list(long definitionId) {
        return repository.active(definitionId).stream().map(this::view).toList();
    }

    public boolean exceeds(List<Attachment> files) {
        BatchGlobalSettingDetailDto s = settings.detail();
        return files.size() > s.getAttachmentMaxCount()
                || files.stream().mapToLong(Attachment::file_size).sum() > s.getAttachmentMaxTotalMib() * MIB
                || files.stream().anyMatch(f -> f.file_size() > s.getAttachmentMaxFileMib() * MIB);
    }

    /** Called in the execution INSERT transaction, serialized with uploads and detaches. */
    public List<Attachment> snapshot(long definitionId) {
        lockDefinition(definitionId);
        List<Attachment> files = list(definitionId);
        if (exceeds(files)) {
            throw new IllegalArgumentException(BatchMessageConstants.SNAPSHOT_LIMIT_EXCEEDED);
        }
        return files;
    }

    @Transactional(rollbackFor = Exception.class)
    public Attachment upload(long definitionId, UUID requestId, String target, String category,
            Long replacesId, MultipartFile file) throws IOException {
        UploadPreparation preparation = prepareUpload(definitionId, requestId, target, category, replacesId, file);
        Path root = root();
        Path temporary = Files.createTempFile(root, ".upload-", ".tmp");
        Path stored = root.resolve(UUID.randomUUID().toString());
        try {
            UploadedFile uploaded = copyUpload(file, temporary, preparation.limit());
            if (preparation.previous() != null) {
                verifyIdempotentUpload(definitionId, preparation, uploaded.checksum());
                return view(preparation.previous());
            }
            return persistUpload(definitionId, requestId, preparation, file, temporary, stored, uploaded);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private UploadPreparation prepareUpload(long definitionId, UUID requestId, String target, String category,
            Long replacesId, MultipartFile file) {
        destination(target);
        if (!Set.of("script", "input", "config").contains(category)) {
            throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_TYPE_INVALID);
        }
        lockDefinition(definitionId);
        Record previous = repository.byRequest(requestId);
        List<Attachment> active = list(definitionId);
        if (previous == null && exceeds(active)) {
            throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_LIMIT_EXCEEDED);
        }
        Record replaced = replacesId == null ? null : repository.activeReplacement(replacesId, definitionId);
        if (previous == null && replacesId != null && replaced == null) {
            throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_REPLACEMENT_CONFLICT);
        }
        String key = target.toLowerCase(Locale.ROOT);
        ArrayList<Attachment> remaining = new ArrayList<>(active);
        remaining.removeIf(f -> replacesId != null && f.id() == replacesId);
        if (previous == null && hasDestinationConflict(remaining, key)) {
            throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_PATH_DUPLICATE);
        }
        BatchGlobalSettingDetailDto s = settings.detail();
        if (previous == null && remaining.size() >= s.getAttachmentMaxCount()) {
            throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_COUNT_EXCEEDED);
        }
        long limit = previous == null ? Math.min(s.getAttachmentMaxFileMib() * MIB,
                s.getAttachmentMaxTotalMib() * MIB - remaining.stream().mapToLong(Attachment::file_size).sum())
                : previous.get("file_size", Long.class);
        if (file.getSize() > limit) {
            throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_SIZE_EXCEEDED);
        }
        return new UploadPreparation(target, category, replacesId, previous, limit, key);
    }

    private boolean hasDestinationConflict(List<Attachment> files, String targetKey) {
        return files.stream().anyMatch(file -> {
            String other = file.destination_path().toLowerCase(Locale.ROOT);
            return targetKey.equals(other) || targetKey.startsWith(other + "/") || other.startsWith(targetKey + "/");
        });
    }

    private UploadedFile copyUpload(MultipartFile file, Path temporary, long limit) throws IOException {
        MessageDigest digest = sha256();
        long count = 0;
        try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) {
                count += length;
                if (count > limit) {
                    throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_SIZE_EXCEEDED);
                }
                digest.update(buffer, 0, length);
                output.write(buffer, 0, length);
            }
        }
        return new UploadedFile(count, HexFormat.of().formatHex(digest.digest()));
    }

    private MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private void verifyIdempotentUpload(long definitionId, UploadPreparation preparation, String checksum) {
        Record previous = preparation.previous();
        if (!previous.get("batch_definition_id", Long.class).equals(definitionId)
                || !preparation.target().equals(previous.get("destination_path"))
                || !checksum.equals(previous.get("checksum_sha256"))
                || !preparation.category().equals(previous.get("file_category"))
                || !java.util.Objects.equals(preparation.replacesId(), previous.get("replaces_id", Long.class))) {
            throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_UPLOAD_CONFLICT);
        }
    }

    private Attachment persistUpload(long definitionId, UUID requestId, UploadPreparation preparation,
            MultipartFile file, Path temporary, Path stored, UploadedFile uploaded) throws IOException {
        Path storedName = stored.getFileName();
        if (storedName == null) {
            throw new IOException("Attachment path has no file name");
        }
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(temporary, stored); // UUID destination; never replace an existing object.
        registerRollbackCleanup(stored);
        if (preparation.replacesId() != null) {
            repository.replace(preparation.replacesId());
        }
        String original = originalFileName(file.getOriginalFilename());
        UploadMetadata metadata = new UploadMetadata(original, preparation.target(), preparation.key(),
                preparation.category(), uploaded.size(), uploaded.checksum(), storedName.toString());
        return view(repository.create(definitionId, requestId, metadata, preparation.replacesId()));
    }

    private void registerRollbackCleanup(Path stored) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { Files.deleteIfExists(stored); } catch (IOException _) { /* retained for operator recovery */ }
                }
            }
        });
    }

    private String originalFileName(String original) {
        if (original == null) { original = "file"; }
        original = original.replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1);
        if (original.length() > 255) { original = original.substring(0, 255); }
        return original;
    }

    @Transactional
    public void detach(long definitionId, long id) {
        lockDefinition(definitionId);
        repository.detach(definitionId, id);
    }

    public List<Attachment> executionFiles(UUID executionId) {
        String json = repository.manifest(executionId);
        try { return mapper.readValue(json, new TypeReference<List<Attachment>>() { }); }
        catch (Exception _) { throw new IllegalStateException("Invalid attachment manifest"); }
    }

    public InputStreamResource download(UUID executionId, long fileId) throws IOException {
        if (executionFiles(executionId).stream().noneMatch(f -> f.id() == fileId)) {
            throw new IllegalArgumentException(BatchMessageConstants.EXECUTION_ATTACHMENT_MISMATCH);
        }
        String stored = repository.storagePath(fileId);
        if (stored == null || !stored.matches("[a-f0-9-]{36}")) { throw new IOException("Invalid storage reference"); }
        Path path = root().resolve(stored);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("Attachment unavailable"); }
        return new InputStreamResource(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS));
    }

    private Path root() throws IOException {
        if (storageRoot.isBlank()) { throw new IllegalArgumentException(BatchMessageConstants.ATTACHMENT_STORAGE_MISSING); }
        Path path = Path.of(storageRoot);
        if (!path.isAbsolute() || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Attachment storage must be an existing absolute directory");
        }
        Path cursor = path;
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) { throw new IOException("Linked storage paths are not supported"); }
            cursor = cursor.getParent();
        }
        return path.toRealPath();
    }

    private Attachment view(Record r) {
        return new Attachment(r.get("id", Long.class), r.get("file_name", String.class),
                r.get("destination_path", String.class), r.get("file_category", String.class),
                r.get("file_size", Long.class), r.get("checksum_sha256", String.class),
                r.get("created_dt", OffsetDateTime.class));
    }
}
