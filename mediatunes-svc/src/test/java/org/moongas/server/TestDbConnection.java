package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
    void Test_Constructor_success() throws SQLException {
        String dbUrl = getTestDbUrl("mediascan.db");
        DbConnection conn = new DbConnection(dbUrl);
        assertNotNull(conn);
    }

    @Test
    void Test_get_something() throws SQLException {
        String dbUrl = getTestDbUrl("mediascan.db");
        DbConnection conn = new DbConnection(dbUrl);
        assertNotNull(conn);

        //  WHERE status = ?
        // 
        String sqlQuery = "SELECT id, path, albumpath, artistpath, title, artist, albumartist, album, genre, year FROM mediafile where albumartist = \"Siouxsie And The Banshees\" and title = \"92°\";";

        PreparedStatement pstmt = conn.prepareStatement(sqlQuery);

        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String path = rs.getString("path");
                assertEquals("/data/MusicOther/Siouxsie And The Banshees/Tinderbox [1986]/07 - 92 degrees.mp3", path);
                String albumpath = rs.getString("albumpath");
                assertEquals("/data/MusicOther/Siouxsie And The Banshees/Tinderbox [1986]", albumpath);
                String artistpath = rs.getString("artistpath");
                assertEquals("/data/MusicOther/Siouxsie And The Banshees", artistpath);
                String title = rs.getString("title");
                assertEquals("92°", title);
                String artist = rs.getString("artist");
                assertEquals("Siouxsie And The Banshees", artist);
                String albumartist = rs.getString("albumartist");
                assertEquals("Siouxsie And The Banshees", albumartist);
                String album = rs.getString("album");
                assertEquals("Tinderbox", album);
                String genre = rs.getString("genre");
                assertEquals("Alternative Rock", genre);
                int year = rs.getInt("year");
                assertEquals(1986, year);

                System.out.println("id: " + id 
                + " | path: " + path 
                + " | albumpath " + albumpath 
                + " | artistpath: " + artistpath 
                + " | title: " + title 
                + " | artist: " + artist
                + " | albumartist: " + albumartist
                + " | album: " + album
                + " | genre: " + genre
                + " | year: " + year);
                System.out.println();
            }
        }

    }
}
