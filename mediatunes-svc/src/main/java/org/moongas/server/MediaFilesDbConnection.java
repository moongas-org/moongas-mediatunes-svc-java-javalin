/*
 * Copyright 2026
 * moongas.org
 */
package org.moongas.server;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.moongas.config.MediatunesServiceConfig;
import org.moongas.datatypes.MediaFile;

/**
 * Loads mediascan tracks, albums, artists, and media files at startup.
 */
public class MediaFilesDbConnection {

    public record TrackRow(
            int id,
            String path,
            Integer albumId,
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
            return new MediaFile(id, path, albumPath, artistPath, 0, "mp3",
                    title, artist, albumartist, album, genre, optionalYear(yearStr),
                    0, countryCode, regionCode, city, languageCode);
        }
    }

    public record ArtistRow(
            String path,
            String name,
            String city,
            String countryCode,
            String regionCode,
            String languageCode) {
    }

    public record AlbumRow(
            int id,
            String path,
            String name,
            int year,
            boolean explicitCover,
            String artist,
            String genre,
            String countryCode,
            String regionCode,
            String city,
            String languageCode) {
    }

    private final MediatunesServiceConfig config;
    private final List<TrackRow> joinedTracks;
    private final List<ArtistRow> artists;
    private final List<AlbumRow> albums;
    private final List<Map<String, Object>> mediaFiles;
    private final Map<String, String> countryCodeNameMap;
    private final Map<String, String> regionCodeNameMap;
    private final Map<String, String> languageCodeNameMap;

    public MediaFilesDbConnection(DbConnection conn, MediatunesServiceConfig config)
            throws SQLException, IOException {
        this.config = config;

        List<String> missingTables = new ArrayList<>();
        for (String table : List.of("track", "artist", "album", "mediafile")) {
            if (!conn.hasTable(table)) {
                missingTables.add(table);
            }
        }
        if (!missingTables.isEmpty()) {
            conn.close();
            throw new IllegalArgumentException(
                    "Database is missing required table(s): " + missingTables);
        }

        joinedTracks = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("""
                        SELECT track.id AS track_id,
                            track.path AS track_path,
                            track.albumid AS track_albumid,
                            track.title AS track_title,
                            track.artist AS track_artist,
                            track.albumartist AS track_albumartist,
                            track.album AS track_album,
                            track.genre AS track_genre,
                            track.year AS track_year,
                            artist.path AS artist_path,
                            album.path AS album_path,
                            artist.countrycode AS countrycode,
                            artist.regioncode AS regioncode,
                            artist.city AS city,
                            artist.languagecode AS languagecode
                        FROM track
                        LEFT JOIN artist ON track.artistid = artist.id
                        LEFT JOIN album ON track.albumid = album.id
                        """)) {
            while (rs.next()) {
                joinedTracks.add(new TrackRow(
                        rs.getInt("track_id"),
                        rs.getString("track_path"),
                        getInteger(rs, "track_albumid"),
                        rs.getString("album_path"),
                        rs.getString("artist_path"),
                        rs.getString("track_title"),
                        rs.getString("track_artist"),
                        rs.getString("track_albumartist"),
                        rs.getString("track_album"),
                        rs.getString("track_genre"),
                        rs.getString("track_year"),
                        rs.getString("countrycode"),
                        rs.getString("regioncode"),
                        rs.getString("city"),
                        rs.getString("languagecode")));
            }
        }

        artists = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("""
                        SELECT path, name, city, countrycode, regioncode, languagecode
                        FROM artist
                        """)) {
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
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("""
                        SELECT album.id AS album_id,
                            album.path AS album_path,
                            album.name AS album_name,
                            album.year AS album_year,
                            album.explicitcover AS album_explicitcover,
                            album.genre AS album_genre,
                            artist.name AS artist_name,
                            artist.countrycode AS countrycode,
                            artist.regioncode AS regioncode,
                            artist.city AS city,
                            artist.languagecode AS languagecode
                        FROM album
                        LEFT JOIN artist ON album.artistid = artist.id
                        """)) {
            while (rs.next()) {
                albums.add(new AlbumRow(
                        rs.getInt("album_id"),
                        rs.getString("album_path"),
                        rs.getString("album_name"),
                        rs.getInt("album_year"),
                        rs.getInt("album_explicitcover") != 0,
                        rs.getString("artist_name"),
                        rs.getString("album_genre"),
                        rs.getString("countrycode"),
                        rs.getString("regioncode"),
                        rs.getString("city"),
                        rs.getString("languagecode")));
            }
        }

        mediaFiles = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM mediafile")) {
            ResultSetMetaData metadata = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    row.put(metadata.getColumnLabel(i), rs.getObject(i));
                }
                mediaFiles.add(row);
            }
        }

        countryCodeNameMap = StaticJsonData.countryCodeNameMap();
        regionCodeNameMap = StaticJsonData.regionCodeNameMap();
        languageCodeNameMap = StaticJsonData.languageCodeNameMap();
    }

    private static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static int parseYear(String yearStr) {
        try {
            return (int) Double.parseDouble(yearStr.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    private static Integer optionalYear(String yearStr) {
        return yearStr == null || yearStr.isBlank() ? null : parseYear(yearStr);
    }

    public List<TrackRow> getJoinedTracks() {
        return joinedTracks;
    }

    public List<ArtistRow> getArtists() {
        return artists;
    }

    public List<AlbumRow> getAlbums() {
        return albums;
    }

    public List<Map<String, Object>> getMediaFilesForTrack(int trackId) {
        return mediaFiles.stream()
                .filter(row -> row.get("trackid") instanceof Number id && id.intValue() == trackId)
                .toList();
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

    public List<TrackRow> filterTracks(FilterArgs args) {
        args = args.expandArtistArgs();
        Set<Integer> albumIds = new HashSet<>();
        if (args.explicitCover() != null) {
            for (AlbumRow album : albums) {
                if (args.explicitCover() == album.explicitCover()) {
                    albumIds.add(album.id());
                }
            }
        }
        List<TrackRow> ret = new ArrayList<>();
        for (TrackRow row : joinedTracks) {
            if (args.matches(row)
                    && (args.explicitCover() == null || albumIds.contains(row.albumId()))) {
                ret.add(row);
            }
        }
        return ret;
    }

    public List<AlbumRow> filterAlbums(FilterArgs args) {
        return albums.stream().filter(args::matchesAlbum).toList();
    }

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
