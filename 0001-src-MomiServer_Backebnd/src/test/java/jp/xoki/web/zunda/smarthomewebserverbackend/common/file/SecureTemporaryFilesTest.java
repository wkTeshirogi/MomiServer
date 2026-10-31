package jp.xoki.web.zunda.smarthomewebserverbackend.common.file;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureTemporaryFilesTest {
	@Test
	void aPathWithoutAParentIsRejectedBeforeCreatingAFile() {
		Path directory = Path.of("");
		assertThrows(IOException.class, () -> SecureTemporaryFiles.createOwnerOnlyFile(directory, ".tmp"));
	}
}
