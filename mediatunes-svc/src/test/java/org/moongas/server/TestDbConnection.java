package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.file.Files;

public class TestDbConnection {

    String getTestDbUrl(String filename) {
        Path testDbPath = TestResourceLoader.getTestResource(filename);
        return "jdbc:sqlite:" + testDbPath;
    }

    @Test
    void Test_Constructor_throwsException() {
        String dbUrl = getTestDbUrl("invalid.db");
        assertThrows(SQLException.class, 
            () -> new DbConnection(dbUrl));
    }

    @Test
    void Test_Constructor_success() throws Exception {
        Path dbFile = TestDbs.createApiTestDb();
        try (DbConnection conn = new DbConnection("jdbc:sqlite:" + dbFile)) {
            assertNotNull(conn);
            assertEquals(true, conn.hasTable("track"));
            assertEquals(true, conn.hasTable("album"));
            assertEquals(true, conn.hasTable("artist"));
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void Test_get_track_with_joined_ids() throws Exception {
        Path dbFile = TestDbs.createApiTestDb();
        try (DbConnection conn = new DbConnection("jdbc:sqlite:" + dbFile);
                PreparedStatement stmt = conn.prepareStatement("""
                        SELECT track.id, track.path, album.path AS album_path, artist.path AS artist_path
                        FROM track
                        LEFT JOIN album ON track.albumid = album.id
                        LEFT JOIN artist ON track.artistid = artist.id
                        WHERE track.id = ?
                        """)) {
            stmt.setInt(1, 1);
            try (ResultSet rs = stmt.executeQuery()) {
                assertEquals(true, rs.next());
                assertEquals(TestDbs.TRACK_ONE_PATH, rs.getString("path"));
                assertEquals("/data/Music/A/Artist One/Album [2000]", rs.getString("album_path"));
                assertEquals(TestDbs.ARTIST_ONE_PATH, rs.getString("artist_path"));
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }
}
