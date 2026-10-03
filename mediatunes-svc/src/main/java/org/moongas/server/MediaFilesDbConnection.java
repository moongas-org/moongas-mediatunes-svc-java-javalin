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
import java.util.List;
import java.util.Map;

import org.moongas.config.MediatunesServiceConfig;
import org.moongas.datatypes.MediaFile;

/**
 * Loads the entire mediascan database into memory at startup and serves all
 * queries from the cached lists, mirroring the pandas DataFrames of the
 * Python implementation (files, artists, and the LEFT JOIN of both).
 */
public class MediaFilesDbConnection {

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

    private final MediatunesServiceConfig config;
    private final List<MediaFileRow> joinedFiles;
    private final List<ArtistRow> artists;
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
        List<MediaFileRow> ret = new ArrayList<>();
        for (MediaFileRow row : joinedFiles) {
            if (args.matches(row)) {
                ret.add(row);
            }
        }
        return ret;
    }

    /**
     * Cover path for a track: its directory with mediaPath replaced by
     * albumCoversPath (if they differ), plus "cover.jpg".
     */
    public String getCoverPath(String filePath) {
        String dirPath = filePath.substring(0, filePath.lastIndexOf('/') + 1);
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
