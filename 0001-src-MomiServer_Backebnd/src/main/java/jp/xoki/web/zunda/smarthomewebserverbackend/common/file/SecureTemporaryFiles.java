package jp.xoki.web.zunda.smarthomewebserverbackend.common.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.SecureTemporaryFilesConstants.DIRECTORY_PERMISSIONS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.SecureTemporaryFilesConstants.FILE_PERMISSIONS;

/** 所有者だけが利用できる一時ファイルとディレクトリの権限を管理する。 */
public final class SecureTemporaryFiles {


	private SecureTemporaryFiles() { }

	public static Path createOwnerOnlyFile(Path directory, String suffix) throws IOException {
		Path target = directory.resolve(UUID.randomUUID() + suffix).normalize();
		if (!directory.normalize().equals(target.getParent())) throw new IOException("Invalid temporary file path");
		PosixFileAttributeView posix = Files.getFileAttributeView(directory, PosixFileAttributeView.class);
		try {
			if (posix != null) {
				Files.createFile(target, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
			} else {
				Files.createFile(target);
				restrictToOwner(target, false);
			}
			return target;
		} catch (IOException | RuntimeException exception) {
			try { Files.deleteIfExists(target); }
			catch (IOException cleanupException) { exception.addSuppressed(cleanupException); }
			throw exception;
		}
	}

	public static void restrictToOwner(Path path, boolean directory) throws IOException {
		PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		if (posix != null) {
			Set<PosixFilePermission> expected = directory ? DIRECTORY_PERMISSIONS : FILE_PERMISSIONS;
			posix.setPermissions(expected);
			if (!posix.readAttributes().permissions().equals(expected)) throw new IOException("POSIX permissions were not applied");
			return;
		}
		AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		if (acl == null) throw new IOException("Owner-only file permissions are unsupported");
		AclEntry ownerEntry = AclEntry.newBuilder().setType(AclEntryType.ALLOW)
				.setPrincipal(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
				.setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
		acl.setAcl(List.of(ownerEntry));
		if (!acl.getAcl().equals(List.of(ownerEntry))) throw new IOException("Owner-only ACL was not applied");
	}

	public static Set<PosixFilePermission> directoryPermissions() { return DIRECTORY_PERMISSIONS; }
}
