package jp.xoki.web.zunda.smarthomewebserverbackend.auth.model;

import java.time.LocalDateTime;

/** ロック開始がDBで確定した後に送る管理者通知。認証秘密情報は持たない。 */
public record LoginLockedEvent(Long userId, LocalDateTime lockedUntil) { }
