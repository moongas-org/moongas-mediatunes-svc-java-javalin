package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import org.moongas.config.MediatunesServiceConfig;
import org.junit.jupiter.api.Test;

/**
 * Startup validation tests mirroring the Python tests/test_app_init.py.
 */
public class TestAppInit {

    @Test
    void Test_missing_db_file_fails() {
        MediatunesServiceConfig config = TestDbs.testConfig(Path.of("/nonexistent/definitely-missing.db"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MoonGasServer.createApp(config));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void Test_empty_db_file_fails() throws IOException {
        Path dbFile = Files.createTempFile("mediatunes-empty", ".db");
        try {
            MediatunesServiceConfig config = TestDbs.testConfig(dbFile);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> MoonGasServer.createApp(config));
            assertTrue(ex.getMessage().contains("is empty"));
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void Test_missing_tables_fails() throws IOException, SQLException {
        Path dbFile = TestDbs.createMissingTablesDb();
        try {
            MediatunesServiceConfig config = TestDbs.testConfig(dbFile);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> MoonGasServer.createApp(config));
            assertTrue(ex.getMessage().contains("missing required table"));
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void Test_valid_db_builds_app() throws IOException, SQLException {
        Path dbFile = TestDbs.createApiTestDb();
        try (MoonGasServer.MediatunesApp app = MoonGasServer.createApp(TestDbs.testConfig(dbFile))) {
            assertEquals(1, app.mediaFilesDb().getJoinedFiles().size());
            assertEquals(1, app.mediaFilesDb().getArtists().size());
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }
}
