package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;

class TestAlbumApi {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Path dbFile;
    private MoonGasServer.MediatunesApp app;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        dbFile = TestDbs.createApiTestDb();
        reload();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (app != null) {
            app.close();
        }
        if (dbFile != null) {
            Files.deleteIfExists(dbFile);
        }
    }

    private void reload(String... statements) throws SQLException, IOException {
        if (app != null) {
            app.close();
        }
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                var stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        }
        app = MoonGasServer.createApp(TestDbs.testConfig(dbFile));
    }

    private void loadMultipleAlbums() throws SQLException, IOException {
        app.close();
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            TestDbs.insertAdditionalAlbums(conn);
        }
        reload();
    }

    private static JsonNode json(Response response) throws IOException {
        return MAPPER.readTree(response.body().string());
    }

    @Test
    void filtersAlbumMetadataAndRelatedTrackAndArtistData() {
        Map<String, Integer> cases = Map.ofEntries(
                Map.entry("explicitCover=true", 1),
                Map.entry("explicitCover=TRUE", 1),
                Map.entry("explicitCover=1", 1),
                Map.entry("explicitCover=false", 0),
                Map.entry("explicitCover=FALSE", 0),
                Map.entry("explicitCover=0", 0),
                Map.entry("album[]=album%20metadata%20name&year[]=2001", 1),
                Map.entry("album=Album", 1),
                Map.entry("album=metadata", 1),
                Map.entry("album=Missing", 0),
                Map.entry("year=2000", 0),
                Map.entry("minYear=2001&maxYear=2001", 1),
                Map.entry("minYear=2002", 0),
                Map.entry("maxYear=2000", 0),
                Map.entry("artist=Artist%20One&albumartist=Artist%20One&genre=Rock&title=Track%201", 1),
                Map.entry("countryCode=us&regionCode=us-al&city=huntsville&languageCode=EN", 1),
                Map.entry("genre=Jazz", 0),
                Map.entry("artist=Nobody", 0),
                Map.entry("artist=one&artist=nobody", 1),
                Map.entry("countryCode=CA", 0),
                Map.entry("artist=Nobody&genre=Rock&minYear=2000", 0),
                Map.entry("explicitCover=true&genre=Rock&year=2001", 1),
                Map.entry("explicitCover=false&genre=Rock", 0),
                Map.entry("genre=Jazz&genre=Rock", 1));
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (var entry : cases.entrySet()) {
                try (Response response = client.get("/api/albums?" + entry.getKey())) {
                    assertEquals(200, response.code(), entry.getKey());
                    assertEquals(entry.getValue().intValue(), json(response).get("albums").size(), entry.getKey());
                }
            }
        });
    }

    @Test
    void matchesWuTangAmongRepeatedArtistSubstrings() throws Exception {
        reload("UPDATE artist SET name = 'Wu-Tang Clan'",
                "UPDATE mediafile SET artist = 'Wu-Tang Clan', albumartist = 'Wu-Tang Clan'");
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (var endpoint : Map.of("tracks", "files", "albums", "albums",
                    "artists", "artists", "wordcloud/artists", "words").entrySet()) {
                try (Response response = client.get(
                        "/api/" + endpoint.getKey() + "?artist=wu-tang&artist=dr.+dre")) {
                    assertEquals(200, response.code());
                    assertEquals(1, json(response).get(endpoint.getValue()).size(), endpoint.getKey());
                }
            }
            try (Response response = client.get("/api/artist?artist=wu-tang&artist=dr.+dre")) {
                assertEquals(200, response.code());
                assertEquals("Wu-Tang Clan", json(response).get("artist").get("name").asText());
            }
        });
    }

    @Test
    void rejectsInvalidAndBlankBooleans() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String endpoint : List.of("albums", "album", "tracks", "random-track", "artists")) {
                for (String query : List.of("explicitCover=yes", "explicitCover=2", "explicitCover=",
                        "explicitCover=not-a-bool", "explicitCover", "explicitCover=%20true")) {
                    try (Response response = client.get("/api/" + endpoint + "?" + query)) {
                        assertEquals(400, response.code(), endpoint + "?" + query);
                    }
                }
            }
        });
    }

    @Test
    void keepsDistinctPathsAndStandaloneAlbumsAndMatchesFiltersOnSameTrack() throws Exception {
        loadMultipleAlbums();
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get("/api/albums?sort=year")) {
                assertEquals(200, response.code());
                JsonNode albums = json(response).get("albums");
                assertEquals(3, albums.size());
                Set<String> covers = new HashSet<>();
                List<Boolean> explicitCovers = new ArrayList<>();
                for (JsonNode album : albums) {
                    covers.add(album.get("coverPath").asText());
                    assertTrue(album.get("explicitCover").isBoolean());
                    explicitCovers.add(album.get("explicitCover").asBoolean());
                }
                assertEquals(3, covers.size());
                assertEquals(List.of(true, false, false), explicitCovers);
                assertEquals(2001, albums.get(0).get("year").asInt());
                assertEquals(2001, albums.get(1).get("year").asInt());
                assertEquals(1990, albums.get(2).get("year").asInt());
                assertEquals("Artist One", albums.get(0).get("artist").asText());
                assertEquals("Artist One", albums.get(1).get("artist").asText());
                assertEquals("", albums.get(2).get("artist").asText());
            }
            for (var entry : Map.of(
                    "explicitCover=false", 2,
                    "countryCode=US", 2,
                    "artist=Guest%20Artist&genre=Rock", 0,
                    "artist=Guest%20Artist&genre=Jazz", 1).entrySet()) {
                try (Response response = client.get("/api/albums?" + entry.getKey())) {
                    assertEquals(200, response.code());
                    JsonNode albums = json(response).get("albums");
                    assertEquals(entry.getValue().intValue(), albums.size(), entry.getKey());
                    if ("explicitCover=false".equals(entry.getKey())) {
                        for (JsonNode album : albums) {
                            assertTrue(album.get("explicitCover").isBoolean());
                            assertEquals(false, album.get("explicitCover").asBoolean());
                        }
                    }
                }
            }
        });
    }

    @Test
    void sortsBeforeApplyingAlbumLimit() throws Exception {
        loadMultipleAlbums();
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String sort : List.of("artist", "album", "year", "random")) {
                app.config().maxResultsAlbumCovers = 0;
                JsonNode all;
                try (Response response = client.get("/api/albums?sort=" + sort)) {
                    assertEquals(200, response.code());
                    all = json(response).get("albums");
                    assertEquals(3, all.size());
                }
                if (!"random".equals(sort)) {
                    List<String> actual = new ArrayList<>();
                    for (JsonNode album : all) {
                        actual.add(album.get(sort).asText());
                    }
                    List<String> expected = new ArrayList<>(actual);
                    expected.sort("year".equals(sort) ? Comparator.reverseOrder() : Comparator.naturalOrder());
                    assertEquals(expected, actual);
                }
                app.config().maxResultsAlbumCovers = 1;
                try (Response response = client.get("/api/albums?sort=" + sort)) {
                    assertEquals(200, response.code());
                    JsonNode limited = json(response).get("albums");
                    assertEquals(1, limited.size());
                    if (!"random".equals(sort)) {
                        assertEquals(all.get(0), limited.get(0));
                    }
                }
            }
        });
    }

    @Test
    void buildsCoversFromAlbumPathAndConfiguredRoot() {
        app.config().playbackMethods.local.mediaPath = "/data/Music/";
        app.config().albumCoversPath = "/covers/";
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get("/api/albums")) {
                assertEquals(200, response.code());
                assertEquals("/covers/A/Artist One/Album [2000]/cover.jpg",
                        json(response).get("albums").get(0).get("coverPath").asText());
            }
        });
    }

    @Test
    void joinsArtistGeographyThroughEachRelatedTrack() throws Exception {
        loadMultipleAlbums();
        reload("""
                INSERT INTO artist (path, name, countrycode, regioncode, city, languagecode)
                VALUES ('/data/Music/Guests', 'Guests', 'CA', 'CA-ON', 'Toronto', 'fr')
                """,
                "UPDATE mediafile SET artistpath = '/data/Music/Guests' WHERE title = 'Second Track'");
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (var entry : Map.of(
                    "countryCode=CA&genre=Jazz", 1,
                    "countryCode=CA&genre=Rock", 0,
                    "countryCode=US&artist=Guest%20Artist", 0,
                    "countryCode=US&genre=Rock", 1,
                    "regionCode=CA-ON&city=Toronto&languageCode=fr", 1).entrySet()) {
                try (Response response = client.get("/api/albums?" + entry.getKey())) {
                    assertEquals(200, response.code());
                    assertEquals(entry.getValue().intValue(), json(response).get("albums").size(), entry.getKey());
                }
            }
            try (Response response = client.get("/api/album?title=Other%20Track&explicitCover=false")) {
                assertEquals(200, response.code());
                JsonNode album = json(response).get("album");
                assertTrue(album.get("explicitCover").isBoolean());
                assertEquals(false, album.get("explicitCover").asBoolean());
            }
        });
    }

    @Test
    void appliesExplicitCoverToTracksAndRandomTrack() throws Exception {
        loadMultipleAlbums();
        Map<String, Set<String>> cases = Map.of(
                "explicitCover=true", Set.of("Track 1", "Second Track"),
                "explicitCover=false", Set.of("Other Track"),
                "explicitCover=TRUE&genre=Jazz", Set.of("Second Track"),
                "explicitCover=0&genre=Jazz", Set.of("Other Track"),
                "explicitCover=false&genre=Rock", Set.of(),
                "explicitCover=true&artist=Missing", Set.of());
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (var entry : cases.entrySet()) {
                try (Response response = client.get("/api/tracks?" + entry.getKey())) {
                    assertEquals(200, response.code());
                    Set<String> titles = new HashSet<>();
                    for (JsonNode file : json(response).get("files")) {
                        titles.add(file.get("title").asText());
                    }
                    assertEquals(entry.getValue(), titles, entry.getKey());
                }
                try (Response response = client.get("/api/random-track?" + entry.getKey())) {
                    assertEquals(entry.getValue().isEmpty() ? 404 : 200, response.code(), entry.getKey());
                    if (!entry.getValue().isEmpty()) {
                        assertTrue(entry.getValue().contains(json(response).get("title").asText()));
                    }
                }
            }
        });
    }

    @Test
    void preservesSingleAlbumTrackTagFiltersAndAppliesExplicitCover() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (var entry : Map.of("true", 200, "1", 200, "false", 404, "0", 404).entrySet()) {
                try (Response response = client.get(
                        "/api/album?album=Album&year=2000&explicitCover=" + entry.getKey())) {
                    assertEquals(entry.getValue().intValue(), response.code());
                    if (response.code() == 200) {
                        JsonNode album = json(response).get("album");
                        assertEquals(2001, album.get("year").asInt());
                        assertTrue(album.get("explicitCover").isBoolean());
                        assertTrue(album.get("explicitCover").asBoolean());
                    }
                }
            }
        });
    }

    @Test
    void handlesUnmatchedEmptyAndLegacyMetadata() throws Exception {
        for (String sql : List.of("UPDATE album SET path = '/other/album'",
                "DELETE FROM album", "DROP TABLE album")) {
            reload(sql);
            JavalinTest.test(app.javalin(), (server, client) -> {
                try (Response response = client.get("/api/albums")) {
                    assertEquals(200, response.code());
                    assertEquals(sql.startsWith("UPDATE") ? 1 : 0, json(response).get("albums").size());
                }
                try (Response response = client.get("/api/album?album=Album")) {
                    assertEquals(404, response.code());
                }
                for (String value : List.of("true", "false")) {
                    try (Response response = client.get("/api/tracks?explicitCover=" + value)) {
                        assertEquals(200, response.code());
                        assertEquals(0, json(response).get("files").size());
                    }
                    try (Response response = client.get("/api/random-track?explicitCover=" + value)) {
                        assertEquals(404, response.code());
                    }
                }
                try (Response response = client.get("/api/tracks")) {
                    assertEquals(200, response.code());
                    assertEquals(1, json(response).get("files").size());
                }
                try (Response response = client.get("/api/artist?artist=Artist%20One")) {
                    assertEquals(200, response.code());
                }
            });
        }
    }
}
