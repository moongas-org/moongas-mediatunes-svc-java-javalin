package org.moongas.datatypes;

import java.util.Map;

/**
 * One item of an /artist-geo/{kind} response: a user-facing name/value pair
 * plus the filter criteria the client needs to build its own router links.
 */
public record GeoItem(String name, String value, Map<String, String> criteria, int count) {
}
