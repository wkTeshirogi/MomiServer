package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** 作成日時・行IDの降順を維持するカーソル。毎回ユーザーと閲覧権限を別途検証する。 */
public record NotificationCursor(LocalDateTime createdAt, long id, long snapshotId) {

    /** 継続位置の復元
     *
     * 不正値はoffset方式へフォールバックせず、入力エラーとして拒否する。
     * @param value APIから受け取った継続位置
     * @return カーソル。初回はnull
     */
    public static NotificationCursor decode(String value) {
        if (value == null) return null;
        try {
            if (value.length() > 200) throw new IllegalArgumentException("Invalid notification cursor");
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Invalid notification cursor");
            NotificationCursor cursor = new NotificationCursor(LocalDateTime.parse(parts[0]),
                    Long.parseLong(parts[1]), Long.parseLong(parts[2]));
            if (cursor.id() <= 0 || cursor.snapshotId() < cursor.id()) {
                throw new IllegalArgumentException("Invalid notification cursor");
            }
            return cursor;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid notification cursor", exception);
        }
    }

    /** @return 次回の継続位置。秘密情報を含まない */
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (createdAt + "|" + id + "|" + snapshotId).getBytes(StandardCharsets.UTF_8));
    }
}
