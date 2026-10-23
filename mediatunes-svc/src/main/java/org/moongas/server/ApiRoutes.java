/*
 * Copyright 2026
 * Brett Tolbert
 */
package org.moongas.server;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.moongas.config.MediatunesServiceConfig;
import org.moongas.datatypes.AlbumInfo;
import org.moongas.datatypes.GeoItem;
import org.moongas.datatypes.MediaFile;

import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;

/**
 * JSON API routes consumed by the mediatunes web frontend, mirroring the
 * Python BlackSheep implementation's app/api/routes.py.
 */
public class ApiRoutes {

    private final MediatunesServiceConfig config;
    private final MediaFilesDbConnection db;

    public ApiRoutes(MediatunesServiceConfig config, MediaFilesDbConnection db) {
        this.config = config;
        this.db = db;
    }

    public void register(Javalin app, String urlPrefix) {
        String p = urlPrefix == null ? "" : urlPrefix;

        app.get(p + "/config", ctx -> {
            MediatunesServiceConfig cfg = config;
            List<Map<String, String>> webSearch = new ArrayList<>();
            for (MediatunesServiceConfig.WebSearchPlaybackMethod m : cfg.playbackMethods.webSearch) {
                if (m.enabled) {
                    webSearch.add(Map.of("name", m.name, "searchQueryUrlFormat", m.searchQueryURLFormat));
                }
            }
            ctx.json(Map.of(
                    "playbackMethodLocalEnabled", cfg.playbackMethods.local.enabled,
                    "webSearchPlaybackMethods", webSearch,
                    "ageVerification", cfg.ageVerification,
                    "limitBandwidth", cfg.limitBandwidth,
                    "maxResults", cfg.maxResults,
                    "maxResultsAlbumCovers", cfg.maxResultsAlbumCovers,
                    "presentYear", Year.now().getValue()));
        });

        app.get(p + "/albums", ctx -> {
            FilterArgs args = FilterArgs.from(ctx);
            List<AlbumInfo> albums = getAlbums(db.filterAlbums(args), args);
            List<Map<String, Object>> albumsJson = new ArrayList<>();
            for (AlbumInfo album : albums) {
                albumsJson.add(Map.of(
                        "artist", album.artist(),
                        "album", album.album(),
                        "year", album.year(),
                        "coverPath", album.coverPath(),
                        "explicitCover", album.explicitCover()));
            }
            ctx.json(Map.of("albums", albumsJson));
        });

        app.get(p + "/album", ctx -> {
            FilterArgs args = FilterArgs.from(ctx);
            List<String> albumPaths = db.filterFiles(args).stream()
                    .map(MediaFilesDbConnection.MediaFileRow::albumPath)
                    .toList();
            MediaFilesDbConnection.AlbumRow found = null;
            for (MediaFilesDbConnection.AlbumRow album : db.getAlbums()) {
                if (albumPaths.contains(album.path())) {
                    found = album;
                    break;
                }
            }
            if (found == null) {
                throw new NotFoundResponse();
            }
            ctx.json(Map.of("album", Map.of(
                    "id", found.id(),
                    "path", found.path(),
                    "name", found.name(),
                    "year", found.year(),
                    "explicitCover", found.explicitCover())));
        });

        app.get(p + "/tracks", ctx -> {
            FilterArgs args = FilterArgs.from(ctx);
            List<MediaFilesDbConnection.MediaFileRow> filtered = db.filterFiles(args);
            List<MediaFilesDbConnection.MediaFileRow> tracks = sortTracks(filtered, args);
            List<MediaFile> files = new ArrayList<>();
            for (MediaFilesDbConnection.MediaFileRow row : tracks) {
                files.add(row.toMediaFile());
            }
            String coverPath = files.isEmpty()
                    ? ""
                    : db.getCoverPath(files.get(0).path());
            ctx.json(Map.of("files", files, "coverPath", coverPath));
        });

        app.get(p + "/artists", ctx -> {
            FilterArgs args = FilterArgs.from(ctx);
            List<MediaFilesDbConnection.MediaFileRow> filtered = db.filterFiles(args);
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (MediaFilesDbConnection.MediaFileRow row : filtered) {
                String name = row.albumartist();
                counts.merge(name, 1, Integer::sum);
            }
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
            String sort = args.sort();
            if ("name".equals(sort)) {
                entries.sort(Map.Entry.comparingByKey());
            } else if ("random".equals(sort)) {
                Collections.shuffle(entries);
            } else { // default sort: by count (descending)
                entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            }
            List<Map<String, Object>> artists = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : entries) {
                artists.add(Map.of("name", entry.getKey(), "count", entry.getValue()));
            }
            ctx.json(Map.of("artists", artists));
        });

        app.get(p + "/artist", ctx -> {
            FilterArgs args = FilterArgs.from(ctx);
            MediaFilesDbConnection.ArtistRow found = null;
            for (MediaFilesDbConnection.ArtistRow row : db.getArtists()) {
                if (args.matchesArtist(row)) {
                    found = row;
                    break;
                }
            }
            if (found == null) {
                throw new NotFoundResponse();
            }
            ctx.json(Map.of("artist", Map.of(
                    "name", found.name(),
                    "countryCode", found.countryCode(),
                    "regionCode", found.regionCode(),
                    "city", found.city(),
                    "languageCode", found.languageCode())));
        });

        app.get(p + "/genres", ctx -> {
            String sort = ctx.queryParam("sort");
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (MediaFilesDbConnection.MediaFileRow row : db.getJoinedFiles()) {
                counts.merge(row.genre(), 1, Integer::sum);
            }
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
            if ("name".equals(sort)) {
                entries.sort(Map.Entry.comparingByKey());
            } else { // default sort: by count (descending)
                entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            }
            List<Map<String, Object>> genres = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : entries) {
                genres.add(Map.of("genre", entry.getKey(), "count", entry.getValue()));
            }
            ctx.json(Map.of("genres", genres));
        });

        app.get(p + "/artist-geo/{kind}", ctx -> {
            String kind = ctx.pathParam("kind");
            if (!List.of("countries", "regions", "cities", "languages").contains(kind)) {
                throw new NotFoundResponse();
            }
            ctx.json(Map.of("items", artistGeoCounts(kind)));
        });

        app.get(p + "/wordcloud/genres", ctx -> {
            List<String> genres = db.getJoinedFiles().stream()
                    .map(MediaFilesDbConnection.MediaFileRow::genre)
                    .distinct()
                    .sorted()
                    .toList();
            List<Map<String, String>> words = new ArrayList<>();
            for (String genre : genres) {
                words.add(Map.of("text", genre));
            }
            ctx.json(Map.of("words", words));
        });

        app.get(p + "/wordcloud/artists", ctx -> {
            FilterArgs args = FilterArgs.from(ctx);
            List<String> artists = db.getArtists().stream()
                    .filter(args::matchesArtist)
                    .map(MediaFilesDbConnection.ArtistRow::name)
                    .distinct()
                    .sorted()
                    .toList();
            List<Map<String, String>> words = new ArrayList<>();
            for (String artist : artists) {
                words.add(Map.of("text", artist));
            }
            ctx.json(Map.of("words", words));
        });

        app.get(p + "/random-track", ctx -> {
            FilterArgs args = FilterArgs.from(ctx);
            List<MediaFilesDbConnection.MediaFileRow> filtered = db.filterFiles(args);
            if (filtered.isEmpty()) {
                throw new NotFoundResponse();
            }
            MediaFilesDbConnection.MediaFileRow row =
                    filtered.get(new java.util.Random().nextInt(filtered.size()));
            MediaFile mediaFile = row.toMediaFile();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("path", mediaFile.path());
            body.put("coverPath", db.getCoverPath(mediaFile.path()));
            body.put("artist", mediaFile.artist());
            body.put("album", mediaFile.album());
            body.put("title", mediaFile.title());
            body.put("genre", mediaFile.genre());
            body.put("year", mediaFile.year());
            body.put("countryCode", mediaFile.countryCode());
            body.put("regionCode", mediaFile.regionCode());
            body.put("city", mediaFile.city());
            body.put("languageCode", mediaFile.languageCode());
            ctx.json(body);
        });
    }

    private List<AlbumInfo> getAlbums(List<MediaFilesDbConnection.AlbumRow> albums, FilterArgs args) {
        Map<String, AlbumInfo> albumSet = new LinkedHashMap<>();
        for (MediaFilesDbConnection.AlbumRow row : albums) {
            String artist = row.albumartist().isEmpty() ? row.artist() : row.albumartist();
            String coverPath = db.getAlbumCoverPath(row.path());
            albumSet.putIfAbsent(row.path(),
                    new AlbumInfo(artist, row.name(), row.year(), coverPath, row.explicitCover()));
        }
        List<AlbumInfo> ret = new ArrayList<>(albumSet.values());
        String sort = args.sort() == null ? "random" : args.sort();
        switch (sort) {
            case "artist" -> ret.sort(Comparator.comparing(AlbumInfo::artist));
            case "album" -> ret.sort(Comparator.comparing(AlbumInfo::album));
            case "year" -> ret.sort(Comparator.comparingInt(AlbumInfo::year).reversed());
            default -> Collections.shuffle(ret); // random
        }
        // slice if necessary to keep the result count below the configured
        // max results limit for album covers
        if (config.maxResultsAlbumCovers > 0 && ret.size() > config.maxResultsAlbumCovers) {
            ret = new ArrayList<>(ret.subList(0, config.maxResultsAlbumCovers));
        }
        return ret;
    }

    private List<MediaFilesDbConnection.MediaFileRow> sortTracks(
            List<MediaFilesDbConnection.MediaFileRow> filtered, FilterArgs args) {
        List<MediaFilesDbConnection.MediaFileRow> ret = new ArrayList<>(filtered);
        // Don't reorder tracks if displaying tracks for single album or a specific artist
        if (!args.hasAlbumOrArtistFilter()) {
            String sort = args.sort() == null ? "random" : args.sort();
            if ("year".equals(sort)) {
                ret.sort(Comparator.comparingInt(MediaFilesDbConnection.MediaFileRow::year).reversed());
            } else if ("random".equals(sort)) {
                Collections.shuffle(ret);
            }
        }
        return ret;
    }

    private List<GeoItem> artistGeoCounts(String kind) {
        Map<String, String> countryMap = db.getCountryCodeNameMap();
        Map<String, String> regionMap = db.getRegionCodeNameMap();
        Map<String, String> languageMap = db.getLanguageCodeNameMap();

        String name = "";
        Map<String, GeoItem> counts = new LinkedHashMap<>();
        for (MediaFilesDbConnection.ArtistRow row : db.getArtists()) {
            String cc = row.countryCode();
            String rc = row.regionCode();
            String city = row.city();
            String lc = row.languageCode();
            String value;
            Map<String, String> criteria;
            switch (kind) {
                case "countries" -> {
                    name = "Country";
                    if (!countryMap.containsKey(cc)) {
                        continue;
                    }
                    value = countryMap.get(cc);
                    criteria = Map.of("countryCode", cc);
                }
                case "regions" -> {
                    name = "Region";
                    if (!regionMap.containsKey(rc)) {
                        continue;
                    }
                    value = regionMap.get(rc);
                    criteria = Map.of("regionCode", rc);
                }
                case "languages" -> {
                    name = "Language";
                    if (!languageMap.containsKey(lc)) {
                        continue;
                    }
                    value = languageMap.get(lc);
                    criteria = Map.of("languageCode", lc);
                }
                default -> { // cities
                    name = "City";
                    List<String> qualifiers = new ArrayList<>();
                    if (regionMap.containsKey(rc)) {
                        qualifiers.add(regionMap.get(rc));
                    }
                    if (countryMap.containsKey(cc)) {
                        qualifiers.add(countryMap.get(cc));
                    }
                    value = qualifiers.isEmpty() ? city : city + " (" + String.join(", ", qualifiers) + ")";
                    criteria = Map.of("city", city, "regionCode", rc, "countryCode", cc);
                }
            }
            String uniqKey = value + "|" + criteria.toString();
            GeoItem existing = counts.get(uniqKey);
            if (existing != null) {
                counts.put(uniqKey, new GeoItem(name, value, criteria, existing.count() + 1));
            } else {
                counts.put(uniqKey, new GeoItem(name, value, criteria, 1));
            }
        }
        // default sort: by count (descending)
        List<GeoItem> items = new ArrayList<>(counts.values());
        items.sort(Comparator.comparingInt(GeoItem::count).reversed());
        return items;
    }
}
