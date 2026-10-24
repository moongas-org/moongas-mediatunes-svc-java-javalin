package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;

class TestGetFile {
    @TempDir
    Path directory;

    private Path dbFile;
    private Path mediaFile;
    private byte[] content;
    private MoonGasServer.MediatunesApp app;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = TestDbs.createApiTestDb();
        var config = TestDbs.testConfig(dbFile);
        config.playbackMethods.local.mediaPath = directory.resolve("media").toString();
        config.albumCoversPath = directory.resolve("covers").toString();
        config.serverConfig.urlPrefix = "/service";
        mediaFile = Files.createDirectories(directory.resolve("media/nested")).resolve("audio.mp3");
        content = new byte[262144];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }
        Files.write(mediaFile, content);
        app = MoonGasServer.createApp(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (app != null) {
            app.close();
        }
        if (dbFile != null) {
            Files.deleteIfExists(dbFile);
        }
    }

    private static String fileUrl(Path path) {
        return "/getfile/" + path.toString().substring(1);
    }

    private void assertFullFile(Response response) throws Exception {
        assertEquals(200, response.code());
        assertEquals("bytes", response.header("Accept-Ranges"));
        assertEquals("attachment", response.header("Content-Disposition"));
        assertEquals(Integer.toString(content.length), response.header("Content-Length"));
        assertEquals("audio/mpeg", response.header("Content-Type"));
        assertNull(response.header("Content-Range"));
        assertNull(response.header("Content-Encoding"));
        assertArrayEquals(content, response.body().bytes());
    }

    @Test
    void servesFullFileAtRootWithLengthAndNoCompression() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get(fileUrl(mediaFile),
                    request -> request.header("Accept-Encoding", "gzip"))) {
                assertFullFile(response);
            }
        });
    }

    @ParameterizedTest
    @CsvSource({
            "bytes=100000-100999, 100000, 100999",
            "bytes=0-0, 0, 0",
            "bytes=100000-, 100000, 262143",
            "bytes=-1000, 261144, 262143",
            "bytes=262140-999999, 262140, 262143",
            "bytes=-999999, 0, 262143",
            "bytes=0-262143, 0, 262143",
            "Bytes=100000-100999, 100000, 100999",
            "bytes=262140-9999999999999999999999999, 262140, 262143",
            "bytes=-9999999999999999999999999, 0, 262143"
    })
    void servesByteRange(String range, int start, int end) {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get(fileUrl(mediaFile),
                    request -> request.header("Range", range).header("Accept-Encoding", "gzip"))) {
                assertEquals(206, response.code());
                assertEquals("bytes", response.header("Accept-Ranges"));
                assertEquals("bytes " + start + "-" + end + "/" + content.length,
                        response.header("Content-Range"));
                assertEquals(Integer.toString(end - start + 1), response.header("Content-Length"));
                assertEquals("audio/mpeg", response.header("Content-Type"));
                assertNull(response.header("Content-Encoding"));
                assertArrayEquals(Arrays.copyOfRange(content, start, end + 1), response.body().bytes());
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes=262144-", "bytes=999999-1000000", "bytes=-0",
            "bytes=9999999999999999999999999-"})
    void rejectsUnsatisfiableRange(String range) {
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get(fileUrl(mediaFile),
                    request -> request.header("Range", range))) {
                assertEquals(416, response.code());
                assertEquals("bytes */262144", response.header("Content-Range"));
                assertEquals("bytes", response.header("Accept-Ranges"));
                assertEquals("0", response.header("Content-Length"));
                assertArrayEquals(new byte[0], response.body().bytes());
            }
        });
    }

    @Test
    void ignoresMalformedUnsupportedAndExcessivelyLongRanges() {
        String[] ranges = {"items=0-1", "bytes=bad", "bytes=-", "bytes=20-10",
                "bytes=0-1,10-11", "bytes=1" + "0".repeat(5000) + "-", "bytes=+1-2",
                "bytes=0 - 1", "bytes=999999999999999999999-999999999999999999998"};
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String range : ranges) {
                try (Response response = client.get(fileUrl(mediaFile),
                        request -> request.header("Range", range))) {
                    assertFullFile(response);
                }
            }
        });
    }

    @Test
    void ignoresRangeWhenIfRangeIsPresent() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String validator : new String[] {"\"old-version\"", "Wed, 21 Oct 2015 07:28:00 GMT", ""}) {
                try (Response response = client.get(fileUrl(mediaFile),
                        request -> request.header("Range", "bytes=0-9").header("If-Range", validator))) {
                    assertFullFile(response);
                }
            }
        });
    }

    @Test
    void headReturnsFullHeadersAndIgnoresRange() {
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (String range : new String[] {"bytes=0-9", "bytes=999999-"}) {
                try (Response response = client.request(fileUrl(mediaFile),
                        request -> request.head().header("Range", range))) {
                    assertEquals(200, response.code());
                    assertEquals("bytes", response.header("Accept-Ranges"));
                    assertEquals("262144", response.header("Content-Length"));
                    assertEquals("audio/mpeg", response.header("Content-Type"));
                    assertNull(response.header("Content-Range"));
                    assertArrayEquals(new byte[0], response.body().bytes());
                }
            }
        });
    }

    @Test
    void handlesEmptyFile() throws Exception {
        Files.write(mediaFile, new byte[0]);
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get(fileUrl(mediaFile))) {
                assertEquals(200, response.code());
                assertEquals("0", response.header("Content-Length"));
                assertArrayEquals(new byte[0], response.body().bytes());
            }
            for (String range : new String[] {"bytes=0-0", "bytes=-1", "bytes=0-"}) {
                try (Response response = client.get(fileUrl(mediaFile),
                        request -> request.header("Range", range))) {
                    assertEquals(416, response.code());
                    assertEquals("bytes */0", response.header("Content-Range"));
                    assertEquals("0", response.header("Content-Length"));
                    assertArrayEquals(new byte[0], response.body().bytes());
                }
            }
        });
    }

    @Test
    void appliesRangeToWebpFallback() throws Exception {
        Path cover = Files.createDirectories(directory.resolve("covers")).resolve("cover.webp");
        Files.writeString(cover, "webp content");
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get(fileUrl(cover.resolveSibling("cover.jpg")),
                    request -> request.header("Range", "bytes=0-3"))) {
                assertEquals(206, response.code());
                assertEquals("image/webp", response.header("Content-Type"));
                assertEquals("bytes 0-3/12", response.header("Content-Range"));
                assertEquals("4", response.header("Content-Length"));
                assertEquals("webp", response.body().string());
            }
        });
    }

    @Test
    void preservesNotFoundAndAllowedPathChecks() throws Exception {
        Path outside = Files.writeString(directory.resolve("outside.mp3"), "not media");
        Path sibling = Files.createDirectories(directory.resolve("media-other")).resolve("audio.mp3");
        Files.writeString(sibling, "not media either");
        JavalinTest.test(app.javalin(), (server, client) -> {
            for (Path path : new Path[] {outside, sibling, mediaFile.resolveSibling("missing.mp3"),
                    mediaFile.resolveSibling("missing.jpg")}) {
                try (Response response = client.get(fileUrl(path),
                        request -> request.header("Range", "bytes=0-3"))) {
                    assertEquals(404, response.code());
                }
            }
        });
    }

    @Test
    void servesUnknownContentType() throws Exception {
        Path unknown = mediaFile.resolveSibling("file.moongas-unknown");
        Files.writeString(unknown, "unknown");
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get(fileUrl(unknown),
                    request -> request.header("Range", "bytes=0-2"))) {
                assertEquals(206, response.code());
                assertEquals("application/octet-stream", response.header("Content-Type"));
                assertEquals("unk", response.body().string());
            }
        });
    }

    @Test
    void seeksBeyondIntegerOffsetsWithoutReadingWholeFile() throws Exception {
        long start = 5L * 1024 * 1024 * 1024;
        byte[] tail = {1, 2, 3, 4};
        try (var source = Files.newByteChannel(mediaFile, StandardOpenOption.WRITE)) {
            source.position(start);
            source.write(ByteBuffer.wrap(tail));
        }
        JavalinTest.test(app.javalin(), (server, client) -> {
            try (Response response = client.get(fileUrl(mediaFile),
                    request -> request.header("Range", "bytes=" + start + "-"))) {
                assertEquals(206, response.code());
                assertEquals("bytes " + start + "-" + (start + 3) + "/" + (start + 4),
                        response.header("Content-Range"));
                assertEquals("4", response.header("Content-Length"));
                assertArrayEquals(tail, response.body().bytes());
            }
        });
    }
}
