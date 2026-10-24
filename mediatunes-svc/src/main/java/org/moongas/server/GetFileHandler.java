/*
 * Copyright 2026
 * moongas.org
 */
package org.moongas.server;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.moongas.config.MediatunesServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.http.NotFoundResponse;

/**
 * Serves raw media files from the local media path (or album covers path),
 * mirroring the Python /getfile/{path} route, including the .jpg -> .webp
 * fallback and the path-prefix traversal guard.
 */
public class GetFileHandler implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(GetFileHandler.class);
    private static final Pattern BYTE_RANGE = Pattern.compile("bytes=([0-9]*)-([0-9]*)",
            Pattern.CASE_INSENSITIVE);

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

        Path file = Path.of(path);
        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        ctx.contentType(contentType);
        ctx.header("Accept-Ranges", "bytes");
        ctx.header("Content-Disposition", "attachment");
        try (SeekableByteChannel source = Files.newByteChannel(file)) {
            long size = source.size();
            ctx.header("Content-Length", Long.toString(size));
            if (ctx.method() == HandlerType.HEAD) {
                return;
            }

            ByteRange range = ctx.header("If-Range") == null
                    ? parseRange(ctx.header("Range"), size) : null;
            long start = range == null ? 0 : range.start();
            long end = range == null ? size - 1 : range.end();
            if (range != null) {
                if (start > end) {
                    logger.debug("Requested byte range is unsatisfiable for {}", path);
                    ctx.status(416);
                    ctx.header("Content-Range", "bytes */" + size);
                    ctx.header("Content-Length", "0");
                    return;
                }
                ctx.status(206);
                ctx.header("Content-Range", "bytes " + start + "-" + end + "/" + size);
            }
            long remaining = end - start + 1;
            ctx.header("Content-Length", Long.toString(remaining));
            source.position(start);
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            // Range offsets and Content-Length refer to the uncompressed file bytes.
            var output = ctx.res().getOutputStream();
            while (remaining > 0) {
                buffer.clear().limit((int) Math.min(buffer.capacity(), remaining));
                int read = source.read(buffer);
                if (read < 0) {
                    throw new IOException("Unexpected end of file while streaming " + path);
                }
                output.write(buffer.array(), 0, read);
                remaining -= read;
            }
        }
    }

    private record ByteRange(long start, long end) {
    }

    private static ByteRange parseRange(String header, long size) {
        if (header == null) {
            return null;
        }
        var match = BYTE_RANGE.matcher(header.strip());
        if (!match.matches() || (match.group(1).isEmpty() && match.group(2).isEmpty())) {
            logger.debug("Ignoring malformed or unsupported Range header");
            return null;
        }
        String first = match.group(1);
        String last = match.group(2);
        // Match the Python service's integer parsing limit and bound parsing work.
        if (first.length() > 4300 || last.length() > 4300) {
            logger.debug("Ignoring Range header with excessive integer digits");
            return null;
        }
        BigInteger fileSize = BigInteger.valueOf(size);
        if (first.isEmpty()) {
            long suffix = new BigInteger(last).min(fileSize).longValueExact();
            return new ByteRange(size - suffix, size - 1);
        }
        BigInteger start = new BigInteger(first);
        BigInteger end = last.isEmpty() ? null : new BigInteger(last);
        if (end != null && end.compareTo(start) < 0) {
            logger.debug("Ignoring reversed Range header");
            return null;
        }
        return new ByteRange(start.min(fileSize).longValueExact(),
                end == null ? size - 1 : end.min(BigInteger.valueOf(size - 1)).longValueExact());
    }
}
