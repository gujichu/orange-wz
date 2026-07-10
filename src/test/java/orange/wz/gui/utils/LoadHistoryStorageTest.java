package orange.wz.gui.utils;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LoadHistoryStorageTest {

    @Test
    void resolveHistoryPath_returnsAbsolutePathEvenWhenFileMissing() {
        Path path = LoadHistoryStorage.resolveHistoryPath();
        assertNotNull(path);
        assertTrue(path.isAbsolute());
        assertEquals("history.json", path.getFileName().toString());
    }

    @Test
    void addAndListEntries_roundTrip() throws Exception {
        Path tempDir = Files.createTempDirectory("orange-wz-history");
        Path history = tempDir.resolve("tools").resolve("history.json");
        Files.createDirectories(history.getParent());

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            LoadHistoryStorage storage = LoadHistoryStorage.getInstance();
            storage.addEntries(java.util.List.of(tempDir.resolve("demo.wz").toFile()));

            assertTrue(Files.isRegularFile(history));
            assertFalse(storage.listByTimeDesc().isEmpty());
            assertEquals("demo.wz", storage.listByTimeDesc().getFirst().fileName());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}
