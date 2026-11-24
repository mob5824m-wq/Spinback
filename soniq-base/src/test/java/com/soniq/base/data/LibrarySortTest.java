package com.soniq.base.data;

import com.soniq.base.model.MediaItem;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Sorting rules: stable, predictable, and "The Beatles" files under B. */
public class LibrarySortTest {

    private static MediaItem track(String title, String artist, long seconds) {
        MediaItem item = new MediaItem();
        item.id = title + "-" + artist;
        item.title = title;
        item.author = artist;
        item.setDurationSec(seconds);
        return item;
    }

    private static List<MediaItem> sample() {
        List<MediaItem> items = new ArrayList<>();
        items.add(track("Zebra", "The Beatles", 210));
        items.add(track("alpha", "Adele", 0));
        items.add(track("Middle", "AFI", 180));
        return items;
    }

    @Test
    public void recentSortLeavesOrderAlone() {
        List<MediaItem> items = sample();
        List<MediaItem> sorted = LibrarySort.apply(items, Settings.SORT_RECENT);
        Assert.assertEquals("Zebra", sorted.get(0).title);
        Assert.assertEquals("alpha", sorted.get(1).title);
        Assert.assertEquals("Middle", sorted.get(2).title);
    }

    @Test
    public void titleSortIgnoresCaseAndLeadingThe() {
        List<MediaItem> sorted = LibrarySort.apply(sample(), Settings.SORT_TITLE);
        Assert.assertEquals("alpha", sorted.get(0).title);
        Assert.assertEquals("Middle", sorted.get(1).title);
        // "The Beatles" must not sort under T.
        Assert.assertEquals("Zebra", sorted.get(2).title);
    }

    @Test
    public void artistSortFoldsThePrefix() {
        List<MediaItem> sorted = LibrarySort.apply(sample(), Settings.SORT_ARTIST);
        Assert.assertEquals("Adele", sorted.get(0).author);
        Assert.assertEquals("AFI", sorted.get(1).author);
        Assert.assertEquals("The Beatles", sorted.get(2).author);
    }

    @Test
    public void durationSortPutsUnknownLengthsLast() {
        List<MediaItem> sorted = LibrarySort.apply(sample(), Settings.SORT_DURATION);
        Assert.assertEquals("Middle", sorted.get(0).title);
        Assert.assertEquals("Zebra", sorted.get(1).title);
        Assert.assertEquals("alpha", sorted.get(2).title);
    }

    @Test
    public void sortingACopyDoesNotMutateTheCallerList() {
        List<MediaItem> items = sample();
        LibrarySort.apply(items, Settings.SORT_TITLE);
        Assert.assertEquals("Zebra", items.get(0).title);
    }

    @Test
    public void nullsAreTolerated() {
        List<MediaItem> items = new ArrayList<>();
        items.add(null);
        items.add(track("Song", null, 100));
        List<MediaItem> sorted = LibrarySort.apply(items, Settings.SORT_TITLE);
        Assert.assertEquals(1, sorted.size());
        Assert.assertNull(LibrarySort.apply(null, Settings.SORT_TITLE).iterator().hasNext()
                ? "unexpected item" : null);
    }

    @Test
    public void describeMatchesTheSetting() {
        Assert.assertEquals("Recently played", LibrarySort.describe(Settings.SORT_RECENT));
        Assert.assertEquals("Title (A–Z)", LibrarySort.describe(Settings.SORT_TITLE));
        Assert.assertEquals("Artist (A–Z)", LibrarySort.describe(Settings.SORT_ARTIST));
        Assert.assertEquals("Duration (shortest first)",
                LibrarySort.describe(Settings.SORT_DURATION));
    }

    @Test
    public void keyStripsOnlyOneLeadingThe() {
        Assert.assertEquals("beatles", LibrarySort.key("The Beatles"));
        Assert.assertEquals("theatre", LibrarySort.key("Theatre"));
        Assert.assertEquals("", LibrarySort.key(null));
    }
}
