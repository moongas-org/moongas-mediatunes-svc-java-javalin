/*
 * Copyright 2026
 * moongas.org
 */
package org.moongas.server;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.moongas.config.MediatunesServiceConfig;
import org.moongas.datatypes.MediaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the entire mediascan database into memory at startup and serves all
 * queries from the cached lists, mirroring the pandas DataFrames of the
 * Python implementation (files and albums enriched with track/artist data).
 */
public class MediaFilesDbConnection {

    private static final Logger logger = LoggerFactory.getLogger(MediaFilesDbConnection.class);

    /** A row of the mediafile LEFT JOIN artist result (track + artist geo data). */
    public record MediaFileRow(
            int id,
            String path,
            String albumPath,
            String artistPath,
            String title,
            String artist,
            String albumartist,
            String album,
            String genre,
            String yearStr,
            String countryCode,
            String regionCode,
            String city,
            String languageCode) {

        public int year() {
            return parseYear(yearStr);
        }

        public MediaFile toMediaFile() {
            // size, format and duration are hardcoded, as in the Python row_to_mediafile()
            return new MediaFile(path, 0, "mp3", nullToEmpty(title), nullToEmpty(artist),
                    nullToEmpty(albumartist), nullToEmpty(album), nullToEmpty(genre),
                    year(), 0, nullToEmpty(countryCode), nullToEmpty(regionCode),
                    nullToEmpty(city), nullToEmpty(languageCode));
        }
    }

    /** A row of the artist table. */
    public record ArtistRow(
            String path,
            String name,
            String city,
            String countryCode,
            String regionCode,
            String languageCode) {
    }

    /** An album row enriched with one related track and its artist's geo data. */
    public record AlbumRow(
            int id, String path, String name, int year, boolean explicitCover,
            String artist, String albumartist, String title, String genre,
            String countryCode, String regionCode, String city, String languageCode) {
    }

    private final MediatunesServiceConfig config;
    private final List<MediaFileRow> joinedFiles;
    private final List<ArtistRow> artists;
    private final List<AlbumRow> albums;
    private final Map<String, String> countryCodeNameMap;
    private final Map<String, String> regionCodeNameMap;
    private final Map<String, String> languageCodeNameMap;

    public MediaFilesDbConnection(DbConnection conn, MediatunesServiceConfig config)
            throws SQLException, IOException {
        this.config = config;

        List<String> missingTables = new ArrayList<>();
        for (String table : List.of("mediafile", "artist")) {
            if (!conn.hasTable(table)) {
                missingTables.add(table);
            }
        }
        if (!missingTables.isEmpty()) {
            conn.close();
            throw new IllegalArgumentException(
                    "Database is missing required table(s): " + missingTables);
        }

        joinedFiles = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT * FROM mediafile LEFT JOIN artist ON mediafile.artistpath = artist.path")) {
            while (rs.next()) {
                joinedFiles.add(new MediaFileRow(
                        rs.getInt("id"),
                        rs.getString("path"),
                        getColumnOrEmpty(rs, "albumpath"),
                        rs.getString("artistpath"),
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("albumartist"),
                        rs.getString("album"),
                        rs.getString("genre"),
                        rs.getString("year"),
                        rs.getString("countrycode"),
                        rs.getString("regioncode"),
                        rs.getString("city"),
                        rs.getString("languagecode")));
            }
        }

        artists = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM artist")) {
            while (rs.next()) {
                artists.add(new ArtistRow(
                        rs.getString("path"),
                        rs.getString("name"),
                        rs.getString("city"),
                        rs.getString("countrycode"),
                        rs.getString("regioncode"),
                        rs.getString("languagecode")));
            }
        }

        albums = new ArrayList<>();
        if (conn.hasTable("album")) {
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("""
                            SELECT album.*,
                                COALESCE(mediafile.artist, '') AS artist,
                                COALESCE(mediafile.albumartist, '') AS albumartist,
                                COALESCE(mediafile.title, '') AS title,
                                COALESCE(mediafile.genre, '') AS genre,
                                COALESCE(artist.countrycode, '') AS countrycode,
                                COALESCE(artist.regioncode, '') AS regioncode,
                                COALESCE(artist.city, '') AS city,
                                COALESCE(artist.languagecode, '') AS languagecode
                            FROM album
                            LEFT JOIN mediafile ON mediafile.albumpath = album.path
                            LEFT JOIN artist ON mediafile.artistpath = artist.path
                            """)) {
                while (rs.next()) {
                    albums.add(new AlbumRow(
                            rs.getInt("id"),
                            nullToEmpty(rs.getString("path")),
                            nullToEmpty(rs.getString("name")),
                            rs.getInt("year"),
                            rs.getInt("explicitcover") != 0,
                            rs.getString("artist"),
                            rs.getString("albumartist"),
                            rs.getString("title"),
                            rs.getString("genre"),
                            rs.getString("countrycode"),
                            rs.getString("regioncode"),
                            rs.getString("city"),
                            rs.getString("languagecode")));
                }
            }
        } else {
            logger.warn("Database has no 'album' table; album metadata requests will return 404.");
        }

        countryCodeNameMap = StaticJsonData.countryCodeNameMap();
        regionCodeNameMap = StaticJsonData.regionCodeNameMap();
        languageCodeNameMap = StaticJsonData.languageCodeNameMap();
    }

    private static String getColumnOrEmpty(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            // column absent (e.g. older DB without albumpath)
            return "";
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** year is TEXT in the Python schema; parse like int(float(str(year))). */
    static int parseYear(String yearStr) {
        try {
            return (int) Double.parseDouble(yearStr.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    /** The joined mediafile+artist rows (all tracks with geo data). */
    public List<MediaFileRow> getJoinedFiles() {
        return joinedFiles;
    }

    /** All rows of the artist table. */
    public List<ArtistRow> getArtists() {
        return artists;
    }

    /** Enriched album rows (one per related track), or empty if the table is absent. */
    public List<AlbumRow> getAlbums() {
        return albums;
    }

    public Map<String, String> getCountryCodeNameMap() {
        return countryCodeNameMap;
    }

    public Map<String, String> getRegionCodeNameMap() {
        return regionCodeNameMap;
    }

    public Map<String, String> getLanguageCodeNameMap() {
        return languageCodeNameMap;
    }

    public List<MediaFileRow> filterFiles(FilterArgs args) {
        Set<String> albumPaths = new HashSet<>();
        if (args.explicitCover() != null) {
            for (AlbumRow album : albums) {
                if (args.explicitCover() == album.explicitCover()) {
                    albumPaths.add(album.path());
                }
            }
        }
        List<MediaFileRow> ret = new ArrayList<>();
        for (MediaFileRow row : joinedFiles) {
            if (args.matches(row)
                    && (args.explicitCover() == null || albumPaths.contains(row.albumPath()))) {
                ret.add(row);
            }
        }
        return ret;
    }

    public List<AlbumRow> filterAlbums(FilterArgs args) {
        return albums.stream().filter(args::matchesAlbum).toList();
    }

    /**
     * Cover path for a track: its directory with mediaPath replaced by
     * albumCoversPath (if they differ), plus "cover.jpg".
     */
    public String getCoverPath(String filePath) {
        String dirPath = filePath.substring(0, filePath.lastIndexOf('/') + 1);
        return getAlbumCoverPath(dirPath);
    }

    public String getAlbumCoverPath(String dirPath) {
        String mediaPath = config.playbackMethods.local.mediaPath;
        if (!config.albumCoversPath.equals(mediaPath)) {
            dirPath = dirPath.replace(mediaPath, config.albumCoversPath);
        }
        if (!dirPath.endsWith("/")) {
            dirPath = dirPath + "/";
        }
        return dirPath + "cover.jpg";
    }
}
