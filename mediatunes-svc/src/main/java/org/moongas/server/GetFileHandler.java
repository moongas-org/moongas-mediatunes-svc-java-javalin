/*
 * Copyright 2026
 * moongas.org
 */
package org.moongas.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.moongas.config.MediatunesServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;

/**
 * Serves raw media files from the local media path (or album covers path),
 * mirroring the Python /getfile/{path} route, including the .jpg -> .webp
 * fallback and the path-prefix traversal guard.
 */
public class GetFileHandler implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(GetFileHandler.class);

    private final MediatunesServiceConfig config;

    public GetFileHandler(MediatunesServiceConfig config) {
        this.config = config;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        String path = ctx.pathParam("path");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String pathPrefix = config.playbackMethods.local.mediaPath;
        if (path.startsWith(config.albumCoversPath)) {
            pathPrefix = config.albumCoversPath;
        }

        if (!Files.exists(Path.of(path))) {
            // if it's a jpg, try looking for webp instead of jpg
            if (!path.endsWith(".jpg")) {
                logger.error("File not found (and not a jpg): \"{}\"", path);
                throw new NotFoundResponse();
            }
            String oldpath = path;
            path = path.substring(0, path.length() - ".jpg".length()) + ".webp";
            logger.warn("Couldn't find file, trying different file extension:\noldpath={}\nnewpath={}", oldpath, path);
        }
        if (!Files.exists(Path.of(path))) {
            logger.error("File not found: \"{}\"", path);
            throw new NotFoundResponse();
        }

        if (!pathPrefix.endsWith("/")) {
            pathPrefix = pathPrefix + "/";
        }
        if (!path.startsWith(pathPrefix)) {
            logger.warn("path ({}) doesn't match expected media path prefix ({}), refusing to serve it",
                    path, pathPrefix);
            throw new NotFoundResponse();
        }

        String contentType = Files.probeContentType(Path.of(path));
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        ctx.contentType(contentType);
        ctx.result(sizedStream(Path.of(path)));
    }

    private static InputStream sizedStream(Path path) throws IOException {
        return Files.newInputStream(path);
    }
}
