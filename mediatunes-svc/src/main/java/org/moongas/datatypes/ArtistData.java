package org.moongas.datatypes;

/**
 * Artist geo data, mirroring the artist table of the mediascan database.
 */
public record ArtistData(
        String path,
        String name,
        String city,
        String countryCode,
        String regionCode,
        String languageCode) {
}
