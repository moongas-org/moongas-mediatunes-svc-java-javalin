/*
 * Copyright 2026
 * moongas.org
 */
package org.moongas.server;

import java.util.List;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

/**
 * Filter query arguments, parsed like the Python get_request_args().
 * List-type args are repeatable and also accept the "[]" suffix
 * (e.g. ?genre=Rock&amp;genre=Pop or ?genre[]=Rock); matching is exact,
 * case-insensitive. Scalar args are minYear, maxYear, sort and explicitCover.
 */
public record FilterArgs(
        List<String> genre,
        List<String> artist,
        List<String> albumartist,
        List<String> album,
        List<String> title,
        List<String> year,
        List<String> countryCode,
        List<String> regionCode,
        List<String> city,
        List<String> languageCode,
        Integer minYear,
        Integer maxYear,
        String sort,
        Boolean explicitCover) {

    public static FilterArgs from(Context ctx) {
        return new FilterArgs(
                listArg(ctx, "genre"),
                listArg(ctx, "artist"),
                listArg(ctx, "albumartist"),
                listArg(ctx, "album"),
                listArg(ctx, "title"),
                listArg(ctx, "year"),
                listArg(ctx, "countryCode"),
                listArg(ctx, "regionCode"),
                listArg(ctx, "city"),
                listArg(ctx, "languageCode"),
                intArg(ctx, "minYear"),
                intArg(ctx, "maxYear"),
                ctx.queryParam("sort"),
                booleanArg(ctx, "explicitCover"));
    }

    private static Boolean booleanArg(Context ctx, String name) {
        String value = ctx.queryParam(name);
        if (value == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        throw new BadRequestResponse("Invalid " + name + " value: expected true, false, 1, or 0.");
    }

    private static List<String> listArg(Context ctx, String name) {
        List<String> values = ctx.queryParams(name);
        if (values == null || values.isEmpty()) {
            values = ctx.queryParams(name + "[]");
        }
        return values == null || values.isEmpty() ? null : values;
    }

    private static Integer intArg(Context ctx, String name) {
        String value = ctx.queryParam(name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True if any of the album/artist/albumartist filters are present. */
    public boolean hasAlbumOrArtistFilter() {
        return album != null || artist != null || albumartist != null;
    }

    private static boolean notInListIgnoreCase(String value, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        for (String item : allowed) {
            if (value != null && value.equalsIgnoreCase(item)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(MediaFilesDbConnection.MediaFileRow row) {
        int rowYear = row.year();
        if (minYear != null && rowYear < minYear) {
            return false;
        }
        if (maxYear != null && rowYear > maxYear) {
            return false;
        }
        return !(notInListIgnoreCase(row.artist(), artist)
                || notInListIgnoreCase(row.albumartist(), albumartist)
                || notInListIgnoreCase(row.album(), album)
                || notInListIgnoreCase(row.genre(), genre)
                || notInListIgnoreCase(row.title(), title)
                || notInListIgnoreCase(row.yearStr(), year)
                || notInListIgnoreCase(row.countryCode(), countryCode)
                || notInListIgnoreCase(row.regionCode(), regionCode)
                || notInListIgnoreCase(row.city(), city)
                || notInListIgnoreCase(row.languageCode(), languageCode));
    }

    public boolean matchesArtist(MediaFilesDbConnection.ArtistRow row) {
        return !(notInListIgnoreCase(row.name(), artist)
                || notInListIgnoreCase(row.countryCode(), countryCode)
                || notInListIgnoreCase(row.regionCode(), regionCode)
                || notInListIgnoreCase(row.city(), city)
                || notInListIgnoreCase(row.languageCode(), languageCode));
    }

    public boolean matchesAlbum(MediaFilesDbConnection.AlbumRow row) {
        if (minYear != null && row.year() < minYear
                || maxYear != null && row.year() > maxYear
                || explicitCover != null && explicitCover != row.explicitCover()) {
            return false;
        }
        return !(notInListIgnoreCase(row.name(), album)
                || notInListIgnoreCase(Integer.toString(row.year()), year)
                || notInListIgnoreCase(row.artist(), artist)
                || notInListIgnoreCase(row.albumartist(), albumartist)
                || notInListIgnoreCase(row.title(), title)
                || notInListIgnoreCase(row.genre(), genre)
                || notInListIgnoreCase(row.countryCode(), countryCode)
                || notInListIgnoreCase(row.regionCode(), regionCode)
                || notInListIgnoreCase(row.city(), city)
                || notInListIgnoreCase(row.languageCode(), languageCode));
    }
}
