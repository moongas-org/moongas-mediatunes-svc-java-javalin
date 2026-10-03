package org.moongas.server;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the Python tests/test_string_utils.py: case-insensitive list
 * membership (incl. null/empty list).
 */
class MoonGasServerTest {

    private static FilterArgs argsWithGenre(List<String> genres) {
        return new FilterArgs(genres, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    private static boolean genreMatches(String genre, List<String> filter) {
        MediaFilesDbConnection.MediaFileRow row = new MediaFilesDbConnection.MediaFileRow(
                1, "/data/Music/A/X/Al [2000]/01 - T.mp3", "", "/data/Music/A/X",
                "T", "X", "X", "Al", genre, "2000", "", "", "", "");
        return argsWithGenre(filter).matches(row);
    }

    @Test
    void strInListIgnoreCase() {
        assertTrue(genreMatches("Rock", List.of("rock")));
        assertTrue(genreMatches("ROCK", List.of("Rock")));
        assertFalse(genreMatches("Jazz", List.of("Rock")));
        // Python: `if len(arg_value_list) and not str_in_list_ignore_case(...)`
        // so empty and missing lists both mean "no filtering on this arg"
        assertTrue(genreMatches("Rock", List.of()));
        assertTrue(genreMatches("Rock", null));
    }
}
