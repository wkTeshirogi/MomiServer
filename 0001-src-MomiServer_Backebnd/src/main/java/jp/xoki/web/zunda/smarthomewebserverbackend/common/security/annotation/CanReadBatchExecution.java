package jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * バッチ実行要求の参照権限が必要なAPIで利用する認可アノテーション。
 *
 * <p>対象メソッドの第1引数を外部参照用実行IDとして扱い、実行要求に紐づく
 * バッチ定義の要求ロール以上であるかを判定する。</p>
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@batchPermissionService.canReadExecution(authentication, #p0)")
public @interface CanReadBatchExecution {
}
