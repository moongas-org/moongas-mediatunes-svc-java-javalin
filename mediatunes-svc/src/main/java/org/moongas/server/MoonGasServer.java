/*
 * Copyright 2026
 * moongas.org
 */
package org.moongas.server;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;

import org.moongas.config.MediatunesServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;

/**
 * Moongas Mediatunes JSON API service (Java/Javalin port of the Python
 * BlackSheep implementation).
 */
public class MoonGasServer {

    private static final Logger logger = LoggerFactory.getLogger(MoonGasServer.class);

    public record MediatunesApp(
            MediatunesServiceConfig config,
            DbConnection dbConnection,
            MediaFilesDbConnection mediaFilesDb,
            ApiRoutes apiRoutes,
            Javalin javalin) implements AutoCloseable {

        @Override
        public void close() {
            try {
                javalin.stop();
            } catch (Exception e) {
                // ignore
            }
            try {
                dbConnection.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static void main(String[] args) {
        Path configFile = null;
        if (args.length > 0) {
            configFile = Path.of(args[0]);
            logger.info("Loading configuration from file: {}", configFile);
        } else {
            String envConfig = System.getenv("MEDIATUNES_CONFIG");
            if (envConfig != null && !envConfig.isBlank()) {
                configFile = Path.of(envConfig);
                logger.info("Loading configuration from MEDIATUNES_CONFIG: {}", configFile);
            } else {
                logger.warn("No config file specified, loading default configuration");
            }
        }

        MediatunesServiceConfig config;
        try {
            config = configFile != null
                    ? ConfigLoader.load(configFile)
                    : new MediatunesServiceConfig();
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to load configuration from '{}': {}", configFile, e.getMessage());
            System.exit(1);
            return;
        }

        try (MediatunesApp app = createApp(config)) {
            app.javalin().start(app.config().serverConfig.host, app.config().serverConfig.port);
            Thread.currentThread().join();
        } catch (IllegalArgumentException | SQLException | IOException e) {
            logger.error("Failed to initialize mediatunes-svc application: {}", e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds the application (config validation, DB load, routes) without
     * starting the HTTP listener, so tests can drive it via JavalinTest.
     */
    public static MediatunesApp createApp(MediatunesServiceConfig config)
            throws SQLException, IOException {
        String dbPath = config.mediascanDatabaseFilePath;
        if (dbPath == null || dbPath.isBlank()) {
            throw new IllegalArgumentException("No mediascan database file path configured in mediatunes-svc config.");
        }
        if (dbPath.startsWith("sqlite:///")) {
            String sqliteFileStr = dbPath.substring("sqlite:///".length());
            if (!sqliteFileStr.isBlank() && !":memory:".equals(sqliteFileStr)) {
                Path resolved = Path.of(sqliteFileStr).toAbsolutePath().normalize();
                if (!resolved.toFile().exists()) {
                    throw new IllegalArgumentException(
                            "SQLite database file not found at '" + resolved + "' (from config value '" + dbPath + "').");
                }
                if (resolved.toFile().length() == 0) {
                    throw new IllegalArgumentException(
                            "SQLite database file at '" + resolved + "' is empty (0 bytes).");
                }
                dbPath = "jdbc:sqlite:" + resolved;
            }
        } else if (!dbPath.startsWith("jdbc:")) {
            dbPath = "jdbc:sqlite:" + dbPath;
        }

        DbConnection conn = new DbConnection(dbPath);
        MediaFilesDbConnection mediaFilesDb;
        try {
            mediaFilesDb = new MediaFilesDbConnection(conn, config);
        } catch (SQLException | IOException e) {
            conn.close();
            throw e;
        }

        ApiRoutes apiRoutes = new ApiRoutes(config, mediaFilesDb);
        String urlPrefix = config.serverConfig.urlPrefix == null ? "" : config.serverConfig.urlPrefix;

        Javalin javalin = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;
            if (config.serverConfig.staticUrlPath != null && !config.serverConfig.staticUrlPath.isBlank()) {
                javalinConfig.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = config.serverConfig.staticUrlPath;
                    staticFiles.directory = "/static";
                    staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
                });
            }
        });
        javalin.exception(Exception.class, (e, ctx) -> {
            logger.error("Unhandled exception: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("error", String.valueOf(e.getMessage())));
        });

        // media files (/getfile/<path>) are served from the root, not under url_prefix
        javalin.get("/getfile/{path}", new GetFileHandler(config));
        apiRoutes.register(javalin, urlPrefix);

        return new MediatunesApp(config, conn, mediaFilesDb, apiRoutes, javalin);
    }
}
