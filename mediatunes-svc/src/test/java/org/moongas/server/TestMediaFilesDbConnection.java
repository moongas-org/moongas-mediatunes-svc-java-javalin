package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.moongas.config.MediatunesServiceConfig;
import org.moongas.server.MediaFilesDbConnection.MediaFileRow;
import org.junit.jupiter.api.Test;

public class TestMediaFilesDbConnection {

    String getTestDbUrl(String filename) {
        Path testDbPath = TestResourceLoader.getTestResource(filename);
        return "jdbc:sqlite:" + testDbPath;
    }

    @Test
    void Test_getMediaFiles() throws SQLException, IOException {
        String dbUrl = getTestDbUrl("mediascan.db");
        DbConnection conn = new DbConnection(dbUrl);
        MediaFilesDbConnection md = new MediaFilesDbConnection(conn, new MediatunesServiceConfig());
        int matchCount = 0;
        for (MediaFileRow mf : md.getJoinedFiles()) {
            if (mf.artist().equals("Siouxsie And The Banshees") && mf.title().equals("92°")) {
                matchCount += 1;
            }
        }
        assertEquals(1, matchCount);
        conn.close();
    }

    @Test
    void Test_getMediaFilesByArtist() throws SQLException, IOException {
        String dbUrl = getTestDbUrl("mediascan.db");
        DbConnection conn = new DbConnection(dbUrl);
        MediaFilesDbConnection md = new MediaFilesDbConnection(conn, new MediatunesServiceConfig());
        FilterArgs args = new FilterArgs(null, null, List.of("Siouxsie And The Banshees"),
                null, null, null, null, null, null, null, null, null, null);
        List<MediaFileRow> results = md.filterFiles(args);
        assertEquals(44, results.size());
        conn.close();
    }
}
