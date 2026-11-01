package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;

class TestMediaFilesDbConnection {

    @Test
    void loadsTracksAndFiltersThemUsingTheNewSchema() throws Exception {
        var dbFile = TestDbs.createApiTestDb();
        try (DbConnection conn = new DbConnection("jdbc:sqlite:" + dbFile)) {
            MediaFilesDbConnection db = new MediaFilesDbConnection(conn, TestDbs.testConfig(dbFile));
            assertEquals(1, db.getJoinedTracks().size());
            assertEquals("Track 1", db.getJoinedTracks().get(0).title());
            assertEquals(1, db.getJoinedTracks().get(0).albumId());
            assertEquals(1, db.getMediaFilesForTrack(1).size());
            assertEquals(0, db.getMediaFilesForTrack(2).size());
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void loadsRawTrackStreamingRowsAtStartup() throws Exception {
        var dbFile = TestDbs.createApiTestDb();
        try {
            try (var conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                    var stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE trackstreaming ADD COLUMN note TEXT");
            }
            try (DbConnection conn = new DbConnection("jdbc:sqlite:" + dbFile)) {
                MediaFilesDbConnection db = new MediaFilesDbConnection(conn, TestDbs.testConfig(dbFile));
                try (var stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM trackstreaming");
                }
                var rows = db.getTrackStreamingForTrack(1);
                assertEquals(2, rows.size());
                assertEquals("spotify", rows.get(0).get("streamingservice"));
                assertEquals("spotify-track-1", rows.get(0).get("streamingtrackid"));
                assertEquals(4, rows.get(0).size());
                assertTrue(rows.get(0).containsKey("note"));
                assertNull(rows.get(0).get("note"));
                var otherRows = db.getTrackStreamingForTrack(2);
                assertEquals(1, otherRows.size());
                assertEquals(2, otherRows.get(0).get("trackid"));
                assertEquals("spotify-track-2", otherRows.get(0).get("streamingtrackid"));
                assertEquals(List.of(), db.getTrackStreamingForTrack(999));
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void emptyTrackStreamingTableReturnsNoEntries() throws Exception {
        var dbFile = TestDbs.createEmptyValidDb();
        try (DbConnection conn = new DbConnection("jdbc:sqlite:" + dbFile)) {
            MediaFilesDbConnection db = new MediaFilesDbConnection(conn, TestDbs.testConfig(dbFile));
            assertTrue(db.getTrackStreamingForTrack(1).isEmpty());
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void filtersByAlbumArtist() throws Exception {
        var dbFile = TestDbs.createApiTestDb();
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            TestDbs.insertAdditionalAlbums(conn);
        }
        try (DbConnection conn = new DbConnection("jdbc:sqlite:" + dbFile)) {
            MediaFilesDbConnection db = new MediaFilesDbConnection(conn, TestDbs.testConfig(dbFile));
            FilterArgs args = new FilterArgs(null, null, java.util.List.of("Artist One"),
                    null, null, null, null, null, null, null, null, null, null, null);
            assertEquals(2, db.filterTracks(args).size());
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }
}
