package com.soniq.base.data;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** History encoding, caps and de-duplication — testable without Android. */
public class SearchHistoryTest {

    @Test
    public void encodesAndDecodesEntries() {
        List<SearchHistory.Entry> entries = new ArrayList<>();
        entries.add(new SearchHistory.Entry("daft punk", 3, 1_700_000_000_000L));
        entries.add(new SearchHistory.Entry("bjork", 1, 1_600_000_000_000L));
        String raw = SearchHistory.encode(entries);

        List<SearchHistory.Entry> decoded = SearchHistory.decode(raw);
        Assert.assertEquals(2, decoded.size());
        Assert.assertEquals("daft punk", decoded.get(0).query);
        Assert.assertEquals(3, decoded.get(0).count);
        Assert.assertEquals(1_700_000_000_000L, decoded.get(0).lastUsed);
        Assert.assertEquals("bjork", decoded.get(1).query);
    }

    @Test
    public void corruptRowsAreSkippedNotFatal() {
        String raw = "good\u00013\u0001100\u0002bad\u0001x\u0001100\u0002also-good\u00011\u0001200";
        List<SearchHistory.Entry> decoded = SearchHistory.decode(raw);
        Assert.assertEquals(2, decoded.size());
        Assert.assertEquals("good", decoded.get(0).query);
        Assert.assertEquals("also-good", decoded.get(1).query);
    }

    @Test
    public void blankAndOversizeQueriesAreNormalized() {
        Assert.assertEquals("", SearchHistory.normalize(null));
        Assert.assertEquals("", SearchHistory.normalize("   "));
        Assert.assertEquals("a b", SearchHistory.normalize("a\nb"));
        String longQuery = new String(new char[SearchHistory.MAX_QUERY_LENGTH + 40]).replace('\0', 'x');
        Assert.assertEquals(SearchHistory.MAX_QUERY_LENGTH,
                SearchHistory.normalize(longQuery).length());
    }

    @Test
    public void emptyHistoryDecodesToNothing() {
        Assert.assertTrue(SearchHistory.decode("").isEmpty());
        Assert.assertTrue(SearchHistory.decode(null).isEmpty());
    }

    @Test
    public void entriesKeepInsertionOrderThroughEncoding() {
        List<SearchHistory.Entry> entries = new ArrayList<>();
        for (int i = 0; i < SearchHistory.MAX_ENTRIES; i++) {
            entries.add(new SearchHistory.Entry("q" + i, 1, i));
        }
        List<SearchHistory.Entry> decoded = SearchHistory.decode(SearchHistory.encode(entries));
        Assert.assertEquals(SearchHistory.MAX_ENTRIES, decoded.size());
        Assert.assertEquals("q0", decoded.get(0).query);
        Assert.assertEquals("q" + (SearchHistory.MAX_ENTRIES - 1),
                decoded.get(decoded.size() - 1).query);
    }

    @Test
    public void countsAreAtLeastOne() {
        SearchHistory.Entry entry = new SearchHistory.Entry("q", 0, 0L);
        Assert.assertEquals(1, entry.count);
        Assert.assertEquals(0L, entry.lastUsed);
    }
}
