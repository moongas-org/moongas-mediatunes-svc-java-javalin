package org.moongas.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.moongas.config.MediatunesServiceConfig;

/**
 * Helpers for creating small throwaway SQLite databases, mirroring the Python
 * test fixtures (tests/test_api.py, tests/test_app_init.py).
 */
public final class TestDbs {

    public static final String ARTIST_ONE_PATH = "/data/Music/A/Artist One";
    public static final String TRACK_ONE_PATH =
            "/data/Music/A/Artist One/Album [2000]/01 - Track 1.mp3";

    private TestDbs() {
    }

    public static Path tempDbPath() throws IOException {
        Path file = Files.createTempFile("mediatunes-test", ".db");
        // SQLite refuses to open a 0-byte file path that doesn't exist as a db;
        // delete so the JDBC driver creates a fresh one.
        Files.delete(file);
        return file;
    }

    public static void createSchema(Connection conn) throws SQLException {
        conn.createStatement().execute("""
                CREATE TABLE track (
                    id INTEGER PRIMARY KEY,
                    path TEXT,
                    albumid INTEGER,
                    artistid INTEGER,
                    title TEXT,
                    artist TEXT,
                    albumartist TEXT,
                    album TEXT,
                    genre TEXT,
                    year TEXT
                )
                """);
        conn.createStatement().execute("""
                CREATE TABLE album (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    path TEXT UNIQUE,
                    name TEXT,
                    year INTEGER,
                    explicitcover BOOLEAN,
                    genre TEXT,
                    artistid INTEGER
                )
                """);
        conn.createStatement().execute("""
                CREATE TABLE artist (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    path TEXT UNIQUE,
                    name TEXT,
                    countrycode TEXT,
                    regioncode TEXT,
                    city TEXT,
                    languagecode TEXT
                )
                """);
        conn.createStatement().execute("""
                CREATE TABLE mediafile (
                    id INTEGER PRIMARY KEY,
                    trackid INTEGER,
                    path TEXT,
                    size INTEGER
                )
                """);
        conn.createStatement().execute("""
                CREATE TABLE trackstreaming (
                    trackid INTEGER NOT NULL,
                    streamingservice TEXT NOT NULL,
                    streamingtrackid TEXT NOT NULL
                )
                """);
    }

    public static void insertArtistOne(Connection conn) throws SQLException {
        conn.createStatement().execute(
                "INSERT INTO artist (path, name, countrycode, regioncode, city, languagecode) "
                        + "VALUES ('" + ARTIST_ONE_PATH + "', 'Artist One', 'US', 'US-AL', 'Huntsville', 'en')");
    }

    public static void insertTrackOne(Connection conn) throws SQLException {
        conn.createStatement().execute(
                "INSERT INTO track (id, path, albumid, artistid, title, artist, albumartist, album, genre, year) "
                        + "VALUES (1, '" + TRACK_ONE_PATH + "', 1, 1, 'Track 1', "
                        + "'Artist One', 'Artist One', 'Album', 'Rock', '2000')");
    }

    public static void insertAlbumOne(Connection conn) throws SQLException {
        conn.createStatement().execute(
                "INSERT INTO album (path, name, year, explicitcover, genre, artistid) "
                        + "VALUES ('/data/Music/A/Artist One/Album [2000]', 'Album Metadata Name', 2001, 1, 'Rock', 1)");
        conn.createStatement().execute(
                "INSERT INTO mediafile (id, trackid, path, size) VALUES (1, 1, '"
                        + TRACK_ONE_PATH + "', 1024)");
    }

    public static void insertAdditionalAlbums(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO album (path, name, year, explicitcover, genre, artistid) VALUES
                        ('/data/Music/A/Artist One/Other [2000]', 'Album Metadata Name', 2001, 0, 'Jazz', 1),
                        ('/data/Music/A/Artist One/Standalone [1990]', 'Standalone', 1990, 0, 'Rock', NULL)
                    """);
            stmt.execute("""
                    INSERT INTO track
                        (id, path, albumid, artistid, title, artist, albumartist, album, genre, year)
                    VALUES
                        (2, '/data/Music/A/Artist One/Other [2000]/01.mp3',
                         2, 1,
                         'Other Track', 'Artist One', '', 'Other', 'Jazz', '2000'),
                        (3, '/data/Music/A/Artist One/Album [2000]/02.mp3',
                         1, 1,
                         'Second Track', 'Guest Artist', 'Artist One', 'Album', 'Jazz', '2000')
                    """);
            stmt.execute("""
                    INSERT INTO mediafile (id, trackid, path, size) VALUES
                        (2, 2, '/data/Music/A/Artist One/Other [2000]/01.mp3', 2048),
                        (3, 3, '/data/Music/A/Artist One/Album [2000]/02.mp3', 4096)
                    """);
        }
    }

    /** Valid DB with one artist ('Artist One', US/US-AL/Huntsville/en) and one track ('Track 1', Album, Rock, 2000). */
    public static Path createApiTestDb() throws IOException, SQLException {
        Path dbFile = tempDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            createSchema(conn);
            insertArtistOne(conn);
            insertTrackOne(conn);
            insertAlbumOne(conn);
            try (var stmt = conn.createStatement()) {
                stmt.execute("""
                        INSERT INTO trackstreaming (trackid, streamingservice, streamingtrackid) VALUES
                            (1, 'spotify', 'spotify-track-1'),
                            (1, 'youtube', 'youtube-track-1'),
                            (2, 'spotify', 'spotify-track-2')
                        """);
            }
        }
        return dbFile;
    }

    /** Valid schema, no rows. */
    public static Path createEmptyValidDb() throws IOException, SQLException {
        Path dbFile = tempDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            createSchema(conn);
        }
        return dbFile;
    }

    /** DB with the required tables missing. */
    public static Path createMissingTablesDb() throws IOException, SQLException {
        Path dbFile = tempDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            conn.createStatement().execute("CREATE TABLE bogus (id INTEGER PRIMARY KEY)");
        }
        return dbFile;
    }

    public static MediatunesServiceConfig testConfig(Path dbFile) {
        MediatunesServiceConfig config = new MediatunesServiceConfig();
        config.mediascanDatabaseFilePath = "sqlite:///" + dbFile;
        // serve the API at /api, matching mediatunes-config.yml
        config.serverConfig.urlPrefix = "/api";
        config.serverConfig.staticUrlPath = null;
        config.playbackMethods.local.enabled = true;
        config.playbackMethods.local.mediaPath = "/data/";
        config.albumCoversPath = "/var/www/html/Covers/";
        return config;
    }
}
