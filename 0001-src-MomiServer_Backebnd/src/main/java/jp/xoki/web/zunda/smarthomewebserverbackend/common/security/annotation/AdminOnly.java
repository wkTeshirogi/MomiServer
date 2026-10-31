package jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理者権限が必要なAPIで利用する認可アノテーション。
 *
 * <p>ControllerにSpring Securityの式を直接記載せず、管理者限定という
 * 操作意図を明示するために使用する。</p>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {
}
