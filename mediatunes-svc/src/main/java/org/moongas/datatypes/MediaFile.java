package org.moongas.datatypes;

/**
 * Track DTO returned by the /tracks endpoint (like mediascan.MediaFile but
 * with the addition of the artist geo data), mirroring the Python MediaFile
 * dataclass and its JSON field names.
 */
public record MediaFile(
        String path,
        int size,
        String format,
        String title,
        String artist,
        String albumartist,
        String album,
        String genre,
        int year,
        int duration,
        String countryCode,
        String regionCode,
        String city,
        String languageCode) {
}
