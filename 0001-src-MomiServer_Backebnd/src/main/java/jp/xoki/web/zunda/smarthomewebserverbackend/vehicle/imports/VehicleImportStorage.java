package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.file.SecureTemporaryFiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Stream;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleMessageConstants;

/** 原本をランダム参照名で一時保存し、トランザクション確定後に安全に削除する。 */
@Component
public class VehicleImportStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger(VehicleImportStorage.class);
	private final VehicleImportProperties properties;

	private final Clock clock;

	public VehicleImportStorage(VehicleImportProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public String save(byte[] bytes) {
		Path target = null;
		try {
			Path root = root();
			target = SecureTemporaryFiles.createOwnerOnlyFile(root, "");
			Path fileName = target.getFileName();
			if (fileName == null || !root.equals(target.getParent())) throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STORAGE_PATH_INVALID);
			String reference = fileName.toString();
			Files.write(target, bytes, StandardOpenOption.WRITE);
			return reference;
		} catch (IOException | RuntimeException exception) {
			if (target != null) {
				try { Files.deleteIfExists(target); }
				catch (IOException cleanupException) { exception.addSuppressed(cleanupException); }
			}
			LOGGER.warn("Vehicle import temporary source storage failed.", exception);
			throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STORAGE_WRITE_FAILED, exception);
		}
	}

	public Path resolve(String reference) {
		if (reference == null || !reference.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STORAGE_REFERENCE_INVALID);
		try {
			Path root = root();
			Path file = root.resolve(reference).normalize();
			if (!root.equals(file.getParent()) || Files.isSymbolicLink(file)) throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STORAGE_ACCESS_UNSAFE);
			return file;
		} catch (IOException exception) { throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STORAGE_ACCESS_FAILED, exception); }
	}

	public void deleteAfterCommit(String reference) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override public void afterCommit() { delete(reference); }
			});
		} else delete(reference);
	}

	public void deleteAfterRollback(String reference) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) delete(reference);
			}
		});
	}

	public boolean delete(String reference) {
		try {
			Files.deleteIfExists(resolve(reference));
			return true;
		} catch (IOException | IllegalArgumentException exception) {
			LOGGER.warn("Vehicle import temporary source deletion failed. reference={}", reference, exception);
			return false;
		}
	}

	public void deleteOrphans(Set<String> referenced, LocalDateTime olderThan) {
		try (Stream<Path> files = Files.list(root())) {
			files.filter(path -> isOrphan(path, referenced, olderThan)).forEach(this::deleteOrphan);
		} catch (IOException | IllegalArgumentException exception) {
			LOGGER.warn("Vehicle import orphan sweep failed.", exception);
		}
	}

	private void deleteOrphan(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			LOGGER.warn("Vehicle import orphan deletion failed. reference={}", path.getFileName(), exception);
		}
	}

	private Path root() throws IOException {
		Path root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
		rejectSymbolicLinks(root);
		createDirectories(root);
		Path realRoot = root.toRealPath();
		if (Files.isSymbolicLink(root) || !Files.isDirectory(realRoot, LinkOption.NOFOLLOW_LINKS)) {
			throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STORAGE_UNSAFE);
		}
		SecureTemporaryFiles.restrictToOwner(realRoot, true);
		return realRoot;
	}

	private void rejectSymbolicLinks(Path path) {
		for (Path component = path; component != null; component = component.getParent()) {
			if (Files.isSymbolicLink(component)) {
				throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STORAGE_UNSAFE);
			}
		}
	}

	private void createDirectories(Path root) throws IOException {
		if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
		Path parent = root.getParent();
		if (parent == null || !Files.isDirectory(parent)) throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STORAGE_PARENT_MISSING);
		PosixFileAttributeView posix = Files.getFileAttributeView(parent, PosixFileAttributeView.class);
		if (posix != null) {
			Files.createDirectory(root, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
					SecureTemporaryFiles.directoryPermissions()));
		} else {
			Files.createDirectory(root);
			SecureTemporaryFiles.restrictToOwner(root, true);
		}
	}

	private boolean isOrphan(Path path, Set<String> referenced, LocalDateTime olderThan) {
		Path fileName = path.getFileName();
		if (fileName == null) return false;
		String reference = fileName.toString();
		if (!reference.matches("[0-9a-fA-F-]{36}(?:\\.ocr-page\\.png)?") || referenced.contains(reference) || Files.isSymbolicLink(path)
				|| !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
		try {
			LocalDateTime modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant(), clock.getZone());
			return modified.isBefore(olderThan);
		} catch (IOException exception) {
			LOGGER.warn("Vehicle import temporary source metadata could not be read. reference={}", reference, exception);
			return false;
		}
	}
}
