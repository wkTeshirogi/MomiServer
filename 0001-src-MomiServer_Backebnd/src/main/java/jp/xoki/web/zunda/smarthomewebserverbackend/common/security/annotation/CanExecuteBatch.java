package jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * バッチ定義ごとの実行権限が必要なAPIで利用する認可アノテーション。
 *
 * <p>対象メソッドの第1引数をバッチ定義IDとして扱い、
 * {@link jp.xoki.web.zunda.smarthomewebserverbackend.common.security.BatchPermissionService}
 * に実行可否判定を委譲する。</p>
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@batchPermissionService.canExecute(authentication, #p0)")
public @interface CanExecuteBatch {
}
