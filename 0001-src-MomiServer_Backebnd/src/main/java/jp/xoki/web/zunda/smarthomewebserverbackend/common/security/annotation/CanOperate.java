package jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理者または運用担当者に許可する操作系APIで利用する認可アノテーション。
 *
 * <p>マスタ定義やシステム設定の管理は{@link AdminOnly}を使用し、
 * 日常運用として実行できる操作に限定して使用する。</p>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public @interface CanOperate {
}
