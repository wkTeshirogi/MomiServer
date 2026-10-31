package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import static jp.xoki.web.zunda.jooq.Tables.M_BATCH_DEFINITIONS;
import static jp.xoki.web.zunda.jooq.Tables.M_BATCH_FILES;
import static jp.xoki.web.zunda.jooq.Tables.T_BATCH_EXECUTION_REQUESTS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchDefinitionConstants.ACTIVE_STATUS;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchFileStatus;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** 添付ファイルの不変メタデータと実行時マニフェストを扱う。 */
@Repository
@RequiredArgsConstructor
public class BatchAttachmentRepository {
    private final DSLContext dsl;

    public boolean lockActiveDefinition(long id) {
        return dsl.select(M_BATCH_DEFINITIONS.ID).from(M_BATCH_DEFINITIONS)
                .where(M_BATCH_DEFINITIONS.ID.eq(id)).and(M_BATCH_DEFINITIONS.STATUS.eq(ACTIVE_STATUS))
                .forUpdate().fetchOne() != null;
    }

    public List<Record> active(long definitionId) {
        return dsl.select(M_BATCH_FILES.fields()).from(M_BATCH_FILES)
                .where(M_BATCH_FILES.BATCH_DEFINITION_ID.eq(definitionId)).and(M_BATCH_FILES.STATUS.eq(BatchFileStatus.ACTIVE.getStoredName()))
                .orderBy(M_BATCH_FILES.ID).fetch();
    }

    public Record byRequest(UUID requestId) {
        return dsl.selectFrom(M_BATCH_FILES).where(M_BATCH_FILES.UPLOAD_REQUEST_ID.eq(requestId)).fetchOne();
    }

    public Record activeReplacement(long fileId, long definitionId) {
        return dsl.selectFrom(M_BATCH_FILES).where(M_BATCH_FILES.ID.eq(fileId))
                .and(M_BATCH_FILES.BATCH_DEFINITION_ID.eq(definitionId)).and(M_BATCH_FILES.STATUS.eq(BatchFileStatus.ACTIVE.getStoredName())).fetchOne();
    }

    public void replace(long id) {
        dsl.update(M_BATCH_FILES).set(M_BATCH_FILES.STATUS, BatchFileStatus.DELETED.getStoredName())
                .set(M_BATCH_FILES.UPDATED_DT, currentOffsetDateTime()).where(M_BATCH_FILES.ID.eq(id)).execute();
    }

    /** 添付本文の検証後に確定した、不変の保存メタデータ。 */
    public record UploadMetadata(String original, String destination, String key, String category,
            long size, String checksum, String storage) { }

    public Record create(long definitionId, UUID requestId, UploadMetadata file, Long replacesId) {
        return dsl.insertInto(M_BATCH_FILES).set(M_BATCH_FILES.BATCH_DEFINITION_ID, definitionId)
                .set(M_BATCH_FILES.UPLOAD_REQUEST_ID, requestId).set(M_BATCH_FILES.FILE_NAME, file.original())
                .set(M_BATCH_FILES.DESTINATION_PATH, file.destination()).set(M_BATCH_FILES.FILE_NAME_KEY, file.key())
                .set(M_BATCH_FILES.FILE_CATEGORY, file.category()).set(M_BATCH_FILES.FILE_SIZE, file.size())
                .set(M_BATCH_FILES.CHECKSUM_SHA256, file.checksum()).set(M_BATCH_FILES.STORAGE_PATH, file.storage())
                .set(M_BATCH_FILES.REPLACES_ID, replacesId).returning().fetchSingle();
    }

    public void detach(long definitionId, long fileId) {
        dsl.update(M_BATCH_FILES).set(M_BATCH_FILES.STATUS, BatchFileStatus.DELETED.getStoredName())
                .set(M_BATCH_FILES.UPDATED_DT, currentOffsetDateTime()).where(M_BATCH_FILES.ID.eq(fileId))
                .and(M_BATCH_FILES.BATCH_DEFINITION_ID.eq(definitionId)).execute();
    }

    public String manifest(UUID executionId) {
        JSONB json = dsl.select(T_BATCH_EXECUTION_REQUESTS.ATTACHMENTS).from(T_BATCH_EXECUTION_REQUESTS)
                .where(T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID.eq(executionId)).fetchOne(T_BATCH_EXECUTION_REQUESTS.ATTACHMENTS);
        return json == null ? null : json.data();
    }

    public String storagePath(long fileId) {
        return dsl.select(M_BATCH_FILES.STORAGE_PATH).from(M_BATCH_FILES)
                .where(M_BATCH_FILES.ID.eq(fileId)).fetchOne(M_BATCH_FILES.STORAGE_PATH);
    }
}
