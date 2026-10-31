package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import java.util.stream.Stream;

class VehicleImportStorageTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void acceptsWindowsShortTemporaryPath() throws Exception {
		Path configuredTemp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
		assumeTrue(!configuredTemp.equals(configuredTemp.toRealPath()), "Temporary path is already canonical");
		Path directory = Files.createTempDirectory(configuredTemp, "vehicle-storage-test-");
		Path root = directory.resolve("imports");
		VehicleImportProperties properties = new VehicleImportProperties();
		properties.setRoot(root.toString());
		VehicleImportStorage storage = new VehicleImportStorage(properties, ApplicationClockFactory.systemClock());
		String reference = null;
		try {
			reference = storage.save("source".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			assertThat(storage.resolve(reference)).hasParentRaw(root.toRealPath());
			assertThat(Files.readString(storage.resolve(reference))).isEqualTo("source");
		} finally {
			if (reference != null) storage.delete(reference);
			Files.deleteIfExists(root);
			Files.deleteIfExists(directory);
		}
	}

	@Test
	void rejectsSymbolicLinkInStorageParents() throws Exception {
		Path target = Files.createDirectory(temporaryDirectory.resolve("target"));
		Path link = temporaryDirectory.resolve("link");
		try {
			Files.createSymbolicLink(link, target);
		} catch (UnsupportedOperationException | java.io.IOException exception) {
			assumeTrue(false, "Symbolic links unavailable: " + exception.getClass().getSimpleName());
		}
		VehicleImportProperties properties = new VehicleImportProperties();
		properties.setRoot(link.resolve("imports").toString());
		VehicleImportStorage storage = new VehicleImportStorage(properties, ApplicationClockFactory.systemClock());

		assertThatThrownBy(() -> storage.save(new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
		assertThat(target.resolve("imports")).doesNotExist();
	}

	@Test
	void reusesExistingStorageDirectory() throws Exception {
		Path root = Files.createDirectory(temporaryDirectory.resolve("imports"));
		VehicleImportProperties properties = new VehicleImportProperties();
		properties.setRoot(root.toString());
		VehicleImportStorage storage = new VehicleImportStorage(properties, ApplicationClockFactory.systemClock());

		String first = storage.save(new byte[]{1});
		String second = storage.save(new byte[]{2});

		assertThat(first).isNotEqualTo(second);
		assertThat(storage.resolve(first)).hasParentRaw(root.toRealPath());
		assertThat(storage.resolve(second)).hasParentRaw(root.toRealPath());
		assertThat(Files.readAllBytes(storage.resolve(first))).containsExactly((byte) 1);
		assertThat(Files.readAllBytes(storage.resolve(second))).containsExactly((byte) 2);
		if (Files.getFileAttributeView(root, PosixFileAttributeView.class) != null) {
			assertThat(Files.getPosixFilePermissions(root)).isEqualTo(PosixFilePermissions.fromString("rwx------"));
		}
	}

	@Test
	void rejectsMissingStorageParentWithoutCreatingDirectories() {
		Path parent = temporaryDirectory.resolve("missing");
		VehicleImportProperties properties = new VehicleImportProperties();
		properties.setRoot(parent.resolve("imports").toString());
		VehicleImportStorage storage = new VehicleImportStorage(properties, ApplicationClockFactory.systemClock());

		assertThatThrownBy(() -> storage.save(new byte[]{1}))
				.isInstanceOf(IllegalArgumentException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class);
		assertThat(parent).doesNotExist();
	}

	@Test
	void rejectsRegularFileAsStorageDirectory() throws Exception {
		Path root = temporaryDirectory.resolve("imports");
		Files.writeString(root, "existing");
		VehicleImportProperties properties = new VehicleImportProperties();
		properties.setRoot(root.toString());
		VehicleImportStorage storage = new VehicleImportStorage(properties, ApplicationClockFactory.systemClock());

		assertThatThrownBy(() -> storage.save(new byte[]{1}))
				.isInstanceOf(IllegalArgumentException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class);
		assertThat(Files.readString(root)).isEqualTo("existing");
	}

	@Test
	void rejectsSymbolicLinkAsStorageDirectory() throws Exception {
		Path target = Files.createDirectory(temporaryDirectory.resolve("target"));
		Path link = temporaryDirectory.resolve("imports");
		try {
			Files.createSymbolicLink(link, target);
		} catch (UnsupportedOperationException | java.io.IOException exception) {
			assumeTrue(false, "Symbolic links unavailable: " + exception.getClass().getSimpleName());
		}
		VehicleImportProperties properties = new VehicleImportProperties();
		properties.setRoot(link.toString());
		VehicleImportStorage storage = new VehicleImportStorage(properties, ApplicationClockFactory.systemClock());

		assertThatThrownBy(() -> storage.save(new byte[]{1}))
				.isInstanceOf(IllegalArgumentException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class);
		try (Stream<Path> contents = Files.list(target)) {
			assertThat(contents).isEmpty();
		}
	}

	@Test
	void savesWithOwnerOnlyPermissionsAndDeletesAgedOrphans() throws Exception {
		VehicleImportProperties properties = new VehicleImportProperties();
		Path root = temporaryDirectory.resolve("imports");
		properties.setRoot(root.toString());
		VehicleImportStorage storage = new VehicleImportStorage(properties, ApplicationClockFactory.systemClock());

		String reference = storage.save("source".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Path source = storage.resolve(reference);
		assertThat(Files.readString(source)).isEqualTo("source");
		if (Files.getFileAttributeView(source, PosixFileAttributeView.class) != null) {
			assertThat(Files.getPosixFilePermissions(root)).isEqualTo(PosixFilePermissions.fromString("rwx------"));
			assertThat(Files.getPosixFilePermissions(source)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
		}

		Path orphan = root.resolve(UUID.randomUUID() + ".ocr-page.png");
		Files.writeString(orphan, "orphan");
		Files.setLastModifiedTime(orphan, FileTime.from(Instant.now(ApplicationClockFactory.systemClock()).minusSeconds(172_800)));
		storage.deleteOrphans(Set.of(reference), LocalDateTime.ofInstant(Instant.now(ApplicationClockFactory.systemClock()).minusSeconds(86_400), ApplicationClockFactory.systemClock().getZone()));

		assertThat(orphan).doesNotExist();
		assertThat(source).exists();
		assertThat(storage.delete(reference)).isTrue();
		assertThat(source).doesNotExist();
	}
}
