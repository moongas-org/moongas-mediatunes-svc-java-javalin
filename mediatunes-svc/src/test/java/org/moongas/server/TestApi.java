package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.moongas.config.MediatunesServiceConfig;
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
                assertEquals("Track 1", data.get("files").get(0).get("title").asText());
                assertEquals("Rock", data.get("files").get(0).get("genre").asText());
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
                assertTrue(data.has("coverPath"));
            }
            try (Response resp = client.get("/api/random-track?genre=Jazz")) {
                assertEquals(404, resp.code());
            }
        });
    }
}
