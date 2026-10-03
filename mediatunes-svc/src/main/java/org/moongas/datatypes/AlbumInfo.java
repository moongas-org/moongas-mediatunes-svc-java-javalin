package org.moongas.datatypes;

/**
 * One entry per unique album, returned by the /albums endpoint.
 */
public record AlbumInfo(String artist, String album, int year, String coverPath) {
}
