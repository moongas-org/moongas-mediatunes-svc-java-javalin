package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.sql.DriverManager;

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
