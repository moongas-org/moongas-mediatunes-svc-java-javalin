package org.moongas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TestFilterArgs {
    private static FilterArgs args(List<String> artists) {
        return new FilterArgs(List.of("Rock"), artists, List.of("U2, Muse"),
                List.of("Album"), List.of("Track"), List.of("2000"),
                List.of("US"), List.of("US-AL"), List.of("Huntsville"), List.of("en"),
                1990, 2010, "year", true);
    }

    @Test
    void expandsCommaSeparatedArtistsWithoutModifyingInputOrOtherFilters() {
        Map<List<String>, List<String>> cases = Map.of(
                List.of("A Tribe Called Quest, Busta Rhymes, Redman"),
                List.of("A Tribe Called Quest, Busta Rhymes, Redman",
                        "A Tribe Called Quest", "Busta Rhymes", "Redman"),
                List.of("A Tribe Called Quest,Busta Rhymes,Redman"),
                List.of("A Tribe Called Quest,Busta Rhymes,Redman",
                        "A Tribe Called Quest", "Busta Rhymes", "Redman"),
                List.of("Coldplay"), List.of("Coldplay"),
                List.of("Coldplay", "U2, Muse"), List.of("Coldplay", "U2, Muse", "U2", "Muse"),
                List.of(",Coldplay,, "), List.of(",Coldplay,, ", "Coldplay"),
                List.of("Coldplay, coldplay", "Coldplay"),
                List.of("Coldplay, coldplay", "Coldplay", "coldplay"),
                List.of(""), List.of(""),
                List.of(",, ,"), List.of(",, ,"));
        for (var entry : cases.entrySet()) {
            FilterArgs original = args(entry.getKey());
            FilterArgs expanded = original.expandArtistArgs();
            assertEquals(args(entry.getValue()), expanded);
            assertEquals(entry.getKey(), original.artist());
            assertEquals(expanded, expanded.expandArtistArgs());
        }
    }

    @Test
    void leavesAbsentOrEmptyArtistFiltersUnchanged() {
        FilterArgs absent = args(null);
        assertSame(absent, absent.expandArtistArgs());
        FilterArgs empty = args(List.of());
        assertSame(empty, empty.expandArtistArgs());
    }
}
