package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** SecureTemporaryFilesで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class SecureTemporaryFilesConstants {

	public static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.copyOf(PosixFilePermissions.fromString("rwx------"));

	public static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.copyOf(PosixFilePermissions.fromString("rw-------"));

	private SecureTemporaryFilesConstants() {
	}
}
