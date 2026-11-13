package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.moongas.config.MediatunesServiceConfig;
import org.moongas.datatypes.LanguageNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;

/**
 * Endpoint tests mirroring the Python tests/test_api.py.
 */
public class TestApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path dbFile;
    private MoonGasServer.MediatunesApp app;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        dbFile = TestDbs.createApiTestDb();
        MediatunesServiceConfig config = TestDbs.testConfig(dbFile);
        app = MoonGasServer.createApp(config);
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

    private static JsonNode json(Response response) throws IOException {
        assertNotNull(response.body());
        return MAPPER.readTree(response.body().string());
    }

    @Test
    void Test_api_config() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/config")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertTrue(data.has("playbackMethodLocalEnabled"));
                assertTrue(data.has("webSearchPlaybackMethods"));
                assertTrue(data.has("presentYear"));
            }
        });
    }

    @Test
    void Test_api_tracks() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/tracks")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals(1, data.get("files").size());
                JsonNode track = data.get("files").get(0);
                assertEquals("Track 1", track.get("title").asText());
                assertEquals("Rock", track.get("genre").asText());
                assertEquals(1, track.get("id").asInt());
                assertEquals("/data/Music/A/Artist One/Album [2000]", track.get("albumPath").asText());
                assertEquals(TestDbs.ARTIST_ONE_PATH, track.get("artistPath").asText());
                assertEquals("Album Metadata Name", data.get("album").get("name").asText());
            }
        });
    }

    @Test
    void Test_api_mediafiles() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get("/api/mediafiles?trackid=1")) {
                assertEquals(200, response.code());
                JsonNode mediafiles = json(response).get("mediafiles");
                assertEquals(1, mediafiles.size());
                assertEquals(TestDbs.TRACK_ONE_PATH, mediafiles.get(0).get("path").asText());
                assertEquals(1024, mediafiles.get(0).get("size").asInt());
            }
            for (String query : List.of("", "?trackid=not-an-integer")) {
                try (Response response = client.get("/api/mediafiles" + query)) {
                    assertEquals(400, response.code());
                }
            }
        });
    }

    @Test
    void Test_api_trackstreaming() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String query : List.of("?trackid=1", "?trackid=1&genre=Jazz&maxResults=1")) {
                try (Response response = client.get("/api/trackstreaming" + query)) {
                    assertEquals(200, response.code());
                    assertEquals(MAPPER.readTree("""
                            {"trackstreaming": [
                                {"trackid": 1, "streamingservice": "spotify", "streamingtrackid": "spotify-track-1",
                                 "streamingtrackurl": "https://open.spotify.com/track/spotify-track-1",
                                 "streaming_track_url": "https://open.spotify.com/track/spotify-track-1"},
                                {"trackid": 1, "streamingservice": "youtube", "streamingtrackid": "youtube-track-1",
                                 "streamingtrackurl": "https://youtube.com/watch?v=youtube-track-1",
                                 "streaming_track_url": "https://youtube.com/watch?v=youtube-track-1"}
                            ]}
                            """), json(response));
                }
            }
            try (Response response = client.get("/api/trackstreaming?trackid=2")) {
                assertEquals(200, response.code());
                assertEquals(MAPPER.readTree("""
                        {"trackstreaming": [
                            {"trackid": 2, "streamingservice": "spotify", "streamingtrackid": "spotify-track-2",
                             "streamingtrackurl": "https://open.spotify.com/track/spotify-track-2",
                             "streaming_track_url": "https://open.spotify.com/track/spotify-track-2"}
                        ]}
                        """), json(response));
            }
            for (int trackId : List.of(0, -1, 999)) {
                try (Response response = client.get("/api/trackstreaming?trackid=" + trackId)) {
                    assertEquals(200, response.code());
                    assertEquals(MAPPER.readTree("{\"trackstreaming\": []}"), json(response));
                }
            }
        });
    }

    @Test
    void trackLookupEndpointsRequireIntegerTrackId() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String endpoint : List.of("mediafiles", "trackstreaming")) {
                for (String query : List.of("", "?trackid=", "?trackid=nope",
                        "?trackid=1.5", "?trackid=2147483648", "?trackid[]=1")) {
                    try (Response response = client.get("/api/" + endpoint + query)) {
                        assertEquals(400, response.code(), endpoint + query);
                    }
                }
            }
        });
    }

    @Test
    void trackStreamingUsesConfiguredUrlPrefix() throws Exception {
        app.close();
        MediatunesServiceConfig config = TestDbs.testConfig(dbFile);
        config.serverConfig.urlPrefix = "/custom";
        app = MoonGasServer.createApp(config);
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get("/custom/trackstreaming?trackid=1")) {
                assertEquals(200, response.code());
                assertEquals(2, json(response).get("trackstreaming").size());
            }
            try (Response response = client.get("/api/trackstreaming?trackid=1")) {
                assertEquals(404, response.code());
            }
        });
    }

    @Test
    void Test_api_tracks_filtered() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/tracks?genre=Jazz")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals(0, data.get("files").size());
                assertEquals("", data.get("coverPath").asText());
                assertTrue(data.get("album").isNull());
            }
        });
    }

    @Test
    void filtersTracksBySpotifyPresence() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String value : List.of("true", "1")) {
                try (Response response = client.get("/api/tracks?hasspotify=" + value)) {
                    assertEquals(200, response.code());
                    JsonNode data = json(response);
                    assertEquals(1, data.get("files").size());
                    assertTrue(data.get("files").get(0).get("hasspotify").asBoolean());
                }
            }
            for (String value : List.of("false", "0")) {
                try (Response response = client.get("/api/tracks?hasspotify=" + value)) {
                    assertEquals(200, response.code());
                    JsonNode data = json(response);
                    assertEquals(0, data.get("files").size());
                    assertTrue(data.get("album").isNull());
                }
            }
            try (Response response = client.get("/api/album?hasspotify=true")) {
                assertEquals(200, response.code());
            }
            try (Response response = client.get("/api/random-track?hasspotify=true")) {
                assertEquals(200, response.code());
            }
            try (Response response = client.get("/api/random-track?hasspotify=false")) {
                assertEquals(404, response.code());
            }
        });
    }

    @Test
    void invalidSpotifyPresenceFilterReturnsBadRequest() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String query : List.of("?hasspotify=", "?hasspotify=yes")) {
                for (String endpoint : List.of("tracks", "album", "random-track")) {
                    try (Response response = client.get("/api/" + endpoint + query)) {
                        assertEquals(400, response.code());
                    }
                }
            }
        });
    }

    @Test
    void filtersTracksByLocalMediaAndLyricsPresence() {
        Map<String, Boolean> expectedFlags = Map.of(
                "hasspotify", true,
                "hasmedia", true,
                "haslyricstxt", false,
                "haslyricslrc", true);
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (var entry : expectedFlags.entrySet()) {
                for (String value : List.of("true", "1")) {
                    try (Response response = client.get("/api/tracks?" + entry.getKey() + "=" + value)) {
                        assertEquals(200, response.code());
                        JsonNode files = json(response).get("files");
                        assertEquals(entry.getValue() ? 1 : 0, files.size());
                        if (!files.isEmpty()) {
                            assertEquals(entry.getValue(), files.get(0).get(entry.getKey()).asBoolean());
                        }
                    }
                }
                for (String value : List.of("false", "0")) {
                    try (Response response = client.get("/api/tracks?" + entry.getKey() + "=" + value)) {
                        assertEquals(200, response.code());
                        assertEquals(entry.getValue() ? 0 : 1, json(response).get("files").size());
                    }
                }
            }
            try (Response response = client.get("/api/tracks?hasmedia=true&haslyricstxt=true")) {
                assertEquals(200, response.code());
                assertEquals(0, json(response).get("files").size());
            }
            for (String endpoint : List.of("album", "artists", "random-track")) {
                try (Response response = client.get("/api/" + endpoint + "?hasmedia=true")) {
                    assertEquals(200, response.code(), endpoint);
                }
            }
            try (Response response = client.get("/api/random-track?hasmedia=false")) {
                assertEquals(404, response.code());
            }
        });
    }

    @Test
    void invalidTrackPresenceFiltersReturnBadRequest() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String flag : List.of("hasmedia", "haslyricstxt", "haslyricslrc")) {
                for (String queryValue : List.of("", "yes")) {
                    for (String endpoint : List.of("tracks", "album", "artists", "random-track")) {
                        try (Response response = client.get(
                                "/api/" + endpoint + "?" + flag + "=" + queryValue)) {
                            assertEquals(400, response.code(), endpoint + " " + flag);
                        }
                    }
                }
            }
        });
    }

    @Test
    void expandsArtistArgumentsOnlyForTrackBasedFilters() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String query : List.of(
                    "artist=Missing,+Artist+One,+Nobody",
                    "artist[]=Missing,Artist+One,Nobody",
                    "artist=Missing&artist=Nobody,+Artist+One",
                    "artist=,+Artist+One,,+&genre=oc&explicitCover=true")) {
                for (var endpoint : Map.of("tracks", "files", "artists", "artists").entrySet()) {
                    try (Response response = client.get("/api/" + endpoint.getKey() + "?" + query)) {
                        assertEquals(200, response.code(), query);
                        assertEquals(1, json(response).get(endpoint.getValue()).size(), query);
                    }
                }
                for (var endpoint : Map.of("album", "album", "random-track", "artist").entrySet()) {
                    try (Response response = client.get("/api/" + endpoint.getKey() + "?" + query)) {
                        assertEquals(200, response.code(), query);
                        assertTrue(json(response).has(endpoint.getValue()), query);
                    }
                }
            }
            for (String endpoint : List.of("tracks", "artists", "album", "random-track")) {
                for (String query : List.of(
                        "artist=Missing,+Nobody",
                        "artist=Missing,+Artist+One&genre=Jazz",
                        "artist=Missing,+Artist+One&explicitCover=false",
                        "albumartist=Missing,+Artist+One")) {
                    try (Response response = client.get("/api/" + endpoint + "?" + query)) {
                        boolean single = List.of("album", "random-track").contains(endpoint);
                        assertEquals(single ? 404 : 200, response.code(), endpoint + "?" + query);
                        if (!single) {
                            assertEquals(0, json(response).get(
                                    "tracks".equals(endpoint) ? "files" : "artists").size(), query);
                        }
                    }
                }
            }
            String query = "?artist=Missing,+Artist+One";
            try (Response response = client.get("/api/albums" + query)) {
                assertEquals(200, response.code());
                assertEquals(0, json(response).get("albums").size());
            }
            try (Response response = client.get("/api/artist" + query)) {
                assertEquals(404, response.code());
            }
            try (Response response = client.get("/api/wordcloud/artists" + query)) {
                assertEquals(200, response.code());
                assertEquals(0, json(response).get("words").size());
            }
        });
    }

    @Test
    void matchesSubstringsAcrossTrackAndAlbumEndpoints() {
        List<String> matchingQueries = List.of(
                "artist=missing&artist=oNE",
                "albumartist[]=missing&albumartist[]=aRT",
                "album=lB", "title=aCK", "genre=oC", "year=00",
                "countryCode=u", "regionCode=-a", "city=VILLE", "languageCode=N",
                "artist=one&genre=oc&year=00&explicitCover=true",
                "artist=", "artist=Artist%20One");
        List<String> nonMatchingQueries = List.of(
                "artist=missing&artist=nobody",
                "artist=Artist%20One%20Extra",
                "artist=one&genre=jazz",
                "year=00&minYear=2002",
                "year=00&maxYear=1999",
                "artist=one&explicitCover=false");
        Map<String, String> endpoints = Map.of(
                "tracks", "files", "albums", "albums", "artists", "artists",
                "album", "album", "random-track", "title");
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (var endpoint : endpoints.entrySet()) {
                for (String query : matchingQueries) {
                    String url = "/api/" + endpoint.getKey() + "?" + query;
                    try (Response response = client.get(url)) {
                        assertEquals(200, response.code(), url);
                        JsonNode result = json(response).get(endpoint.getValue());
                        assertNotNull(result, url);
                        if (result.isArray()) {
                            assertEquals(1, result.size(), url);
                        }
                    }
                }
                for (String query : nonMatchingQueries) {
                    String url = "/api/" + endpoint.getKey() + "?" + query;
                    try (Response response = client.get(url)) {
                        if (List.of("album", "random-track").contains(endpoint.getKey())) {
                            assertEquals(404, response.code(), url);
                        } else {
                            assertEquals(200, response.code(), url);
                            assertEquals(0, json(response).get(endpoint.getValue()).size(), url);
                        }
                    }
                }
            }
        });
    }

    @Test
    void matchesArtistAndWordcloudSubstrings() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String query : List.of(
                    "artist=missing&artist=oNE",
                    "artist[]=missing&artist[]=aRT",
                    "countryCode=u", "regionCode=-a", "city=VILLE", "languageCode=N",
                    "artist=one&countryCode=u&regionCode=-a&city=VILLE&languageCode=N",
                    "artist=")) {
                try (Response response = client.get("/api/artist?" + query)) {
                    assertEquals(200, response.code(), query);
                    assertEquals("Artist One", json(response).get("artist").get("name").asText(), query);
                }
                try (Response response = client.get("/api/wordcloud/artists?" + query)) {
                    assertEquals(200, response.code(), query);
                    assertEquals(1, json(response).get("words").size(), query);
                }
            }
            for (String query : List.of("artist=missing&artist=nobody",
                    "artist=Artist%20One%20Extra", "artist=one&city=Toronto")) {
                try (Response response = client.get("/api/artist?" + query)) {
                    assertEquals(404, response.code(), query);
                }
                try (Response response = client.get("/api/wordcloud/artists?" + query)) {
                    assertEquals(200, response.code(), query);
                    assertEquals(0, json(response).get("words").size(), query);
                }
            }
        });
    }

    @Test
    void Test_api_albums() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/albums")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals(1, data.get("albums").size());
                assertEquals("Album Metadata Name", data.get("albums").get(0).get("album").asText());
                assertEquals(2001, data.get("albums").get(0).get("year").asInt());
                JsonNode explicitCover = data.get("albums").get(0).get("explicitCover");
                assertTrue(explicitCover.isBoolean());
                assertTrue(explicitCover.asBoolean());
            }
        });
    }

    @Test
    void Test_api_album() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/album?artist=Artist%20One&album=Album&year=2000")) {
                assertEquals(200, resp.code());
                JsonNode album = json(resp).get("album");
                assertEquals(1, album.get("id").asInt());
                assertEquals("/data/Music/A/Artist One/Album [2000]", album.get("path").asText());
                assertEquals("Album Metadata Name", album.get("name").asText());
                assertEquals(2001, album.get("year").asInt());
                assertTrue(album.get("explicitCover").asBoolean());
            }
        });
    }

    @Test
    void Test_api_album_not_found() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/album?album=Missing")) {
                assertEquals(404, resp.code());
            }
        });
    }

    @Test
    void Test_api_artists() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/artists")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals(1, data.get("artists").size());
                assertEquals("Artist One", data.get("artists").get(0).get("name").asText());
                assertEquals(1, data.get("artists").get(0).get("count").asInt());
            }
        });
    }

    @Test
    void Test_api_artist() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/artist?artist=Artist One")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals("Artist One", data.get("artist").get("name").asText());
                assertEquals("US", data.get("artist").get("countryCode").asText());
            }
        });
    }

    @Test
    void Test_api_artist_not_found() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/artist?artist=Nobody")) {
                assertEquals(404, resp.code());
            }
        });
    }

    @Test
    void Test_api_genres() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/genres")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals(1, data.get("genres").size());
                assertEquals("Rock", data.get("genres").get(0).get("genre").asText());
                assertEquals(1, data.get("genres").get(0).get("count").asInt());
            }
        });
    }

    @Test
    void Test_api_artist_geo() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/artist-geo/countries")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals(1, data.get("items").size());
                assertEquals("Country", data.get("items").get(0).get("name").asText());
                assertEquals("United States", data.get("items").get(0).get("value").asText());
                assertEquals("US", data.get("items").get(0).get("criteria").get("countryCode").asText());
                assertEquals(1, data.get("items").get(0).get("count").asInt());
            }
            try (Response resp = client.get("/api/artist-geo/cities")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals("Huntsville (Alabama, United States)",
                        data.get("items").get(0).get("value").asText());
                assertEquals("Huntsville", data.get("items").get(0).get("criteria").get("city").asText());
            }
            try (Response resp = client.get("/api/artist-geo/bogus")) {
                assertEquals(404, resp.code());
            }
        });
    }

    @Test
    void languageMapAndArtistGeoIncludeLocalizedAndEnglishNames() throws Exception {
        app.close();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                var statement = connection.prepareStatement(
                        "INSERT INTO artist (path, name, countrycode, regioncode, city, languagecode) "
                                + "VALUES (?, ?, 'FR', '', '', ?)")) {
            for (String[] artist : List.of(
                    new String[] {"/data/Music/Spanish", "Spanish Artist", "es"},
                    new String[] {"/data/Music/French One", "French Artist One", "fr"},
                    new String[] {"/data/Music/French Two", "French Artist Two", "fr"})) {
                statement.setString(1, artist[0]);
                statement.setString(2, artist[1]);
                statement.setString(3, artist[2]);
                statement.executeUpdate();
            }
        }
        app = MoonGasServer.createApp(TestDbs.testConfig(dbFile));

        assertEquals(183, StaticJsonData.languageCodeNameMap().size());
        assertEquals(new LanguageNames("Español", "Spanish"),
                StaticJsonData.languageCodeNameMap().get("es"));
        assertEquals(new LanguageNames("日本語", "Japanese"),
                StaticJsonData.languageCodeNameMap().get("ja"));

        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get("/api/artist-geo/languages")) {
                assertEquals(200, response.code());
                JsonNode items = json(response).get("items");
                assertEquals(3, items.size());
                JsonNode french = findLanguageItem(items, "fr");
                assertEquals("Français", french.get("value").get("name").asText());
                assertEquals("French", french.get("value").get("nameEn").asText());
                assertEquals(2, french.get("count").asInt());
                JsonNode spanish = findLanguageItem(items, "es");
                assertEquals("Español", spanish.get("value").get("name").asText());
                assertEquals("Spanish", spanish.get("value").get("nameEn").asText());
                assertEquals(1, spanish.get("count").asInt());
            }
        });
    }

    private static JsonNode findLanguageItem(JsonNode items, String languageCode) {
        for (JsonNode item : items) {
            if (languageCode.equals(item.get("criteria").get("languageCode").asText())) {
                return item;
            }
        }
        throw new AssertionError("No artist-geo item for language code " + languageCode);
    }

    @Test
    void Test_api_wordcloud() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/wordcloud/genres")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals(List.of(Map.of("text", "Rock")),
                        MAPPER.convertValue(data.get("words"), List.class));
            }
            try (Response resp = client.get("/api/wordcloud/artists")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals(List.of(Map.of("text", "Artist One")),
                        MAPPER.convertValue(data.get("words"), List.class));
            }
        });
    }

    @Test
    void Test_api_random_track() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response resp = client.get("/api/random-track")) {
                assertEquals(200, resp.code());
                JsonNode data = json(resp);
                assertEquals("Track 1", data.get("title").asText());
                assertEquals("Artist One", data.get("artist").asText());
                assertEquals("Artist One", data.get("albumartist").asText());
                assertTrue(data.has("coverPath"));
            }
            try (Response resp = client.get("/api/random-track?genre=Jazz")) {
                assertEquals(404, resp.code());
            }
        });
    }
}
