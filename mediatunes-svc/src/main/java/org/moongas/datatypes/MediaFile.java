package org.moongas.datatypes;

/** Track DTO returned by the /tracks endpoint. */
public record MediaFile(
        int id,
        String path,
        String albumPath,
        String artistPath,
        int size,
        String format,
        String title,
        String artist,
        String albumartist,
        String album,
        String genre,
        Integer year,
        int duration,
        String countryCode,
        String regionCode,
        String city,
        String languageCode) {
}
