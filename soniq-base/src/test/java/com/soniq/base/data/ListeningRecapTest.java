package com.soniq.base.data;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class ListeningRecapTest {

    /** Unix seconds for a UTC calendar day, so month folding is timezone-stable in practice. */
    private static long ts(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, 12, 0, 0);
        return calendar.getTimeInMillis() / 1000L;
    }

    private static ListeningRecap.ActivityDay day(int year, int month, int day, long listens) {
        ListeningRecap.ActivityDay entry = new ListeningRecap.ActivityDay();
        entry.fromTs = ts(year, month, day);
        entry.listenCount = listens;
        return entry;
    }

    @Test
    public void emptyRecapIsEmptyAndKeepsItsNote() {
        ListeningRecap recap = ListeningRecap.empty("listener", 2026, "No statistics yet.");
        Assert.assertTrue(recap.isEmpty());
        Assert.assertEquals("listener", recap.userName);
        Assert.assertEquals(2026, recap.year);
        Assert.assertEquals("No statistics yet.", recap.note);
    }

    @Test
    public void recapWithArtistsButNoTotalsIsNotEmpty() {
        ListeningRecap recap = new ListeningRecap("listener", 2026);
        ListeningRecap.Artist artist = new ListeningRecap.Artist();
        artist.name = "Someone";
        artist.listenCount = 3L;
        recap.topArtists.add(artist);
        Assert.assertFalse(recap.isEmpty());
    }

    @Test
    public void listensInYearOnlyCountsThatYearsDays() {
        List<ListeningRecap.ActivityDay> days = new ArrayList<>();
        days.add(day(2025, Calendar.DECEMBER, 31, 40L));
        days.add(day(2026, Calendar.JANUARY, 1, 12L));
        days.add(day(2026, Calendar.JUNE, 15, 8L));
        Assert.assertEquals(20L, ListeningRecap.listensInYear(days, 2026));
        Assert.assertEquals(40L, ListeningRecap.listensInYear(days, 2025));
        Assert.assertEquals(0L, ListeningRecap.listensInYear(days, 2024));
        Assert.assertEquals(0L, ListeningRecap.listensInYear(null, 2026));
    }

    @Test
    public void monthlyTotalsFoldDaysIntoWholeYearBuckets() {
        ListeningRecap recap = new ListeningRecap("listener", 2026);
        recap.activity.add(day(2026, Calendar.JANUARY, 3, 5L));
        recap.activity.add(day(2026, Calendar.JANUARY, 4, 7L));
        recap.activity.add(day(2026, Calendar.MARCH, 1, 2L));
        recap.activity.add(day(2025, Calendar.MARCH, 1, 99L)); // previous year: ignored
        long[] months = recap.monthlyTotals(2026);
        Assert.assertEquals(12, months.length);
        Assert.assertEquals(12L, months[Calendar.JANUARY]);
        Assert.assertEquals(2L, months[Calendar.MARCH]);
        Assert.assertEquals(0L, months[Calendar.DECEMBER]);
        Assert.assertEquals("January", recap.busiestMonth(2026));
    }

    @Test
    public void busiestMonthIsEmptyWithoutActivity() {
        ListeningRecap recap = new ListeningRecap("listener", 2026);
        Assert.assertEquals("", recap.busiestMonth(2026));
        Assert.assertEquals("", new ListeningRecap("listener", 2026).topEraLabel());
    }

    @Test
    public void topEraGroupsReleaseYearsIntoDecades() {
        ListeningRecap recap = new ListeningRecap("listener", 2026);
        ListeningRecap.Era ninetiesA = new ListeningRecap.Era();
        ninetiesA.year = 1994;
        ninetiesA.listenCount = 10L;
        ListeningRecap.Era ninetiesB = new ListeningRecap.Era();
        ninetiesB.year = 1999;
        ninetiesB.listenCount = 5L;
        ListeningRecap.Era eighties = new ListeningRecap.Era();
        eighties.year = 1987;
        eighties.listenCount = 12L;
        recap.eras.add(ninetiesA);
        recap.eras.add(ninetiesB);
        recap.eras.add(eighties);
        // 1990s = 15 listens, 1980s = 12 listens.
        Assert.assertEquals("1990s", recap.topEraLabel());
    }

    @Test
    public void formattingGroupsThousandsAndShortensTime() {
        Assert.assertEquals("12,480", ListeningRecap.formatCount(12480L));
        Assert.assertEquals("0", ListeningRecap.formatCount(-5L));
        Assert.assertEquals("45 min", ListeningRecap.formatListeningTime(45L * 60L));
        Assert.assertEquals("3 h", ListeningRecap.formatListeningTime(3L * 3600L + 100L));
        Assert.assertEquals("", ListeningRecap.formatListeningTime(0L));
    }

    @Test
    public void coverUrlsAreOnlyBuiltFromWellFormedMbids() {
        String mbid = "f0d3e3bd-4e5a-4d3c-9e2c-1b2a3c4d5e6f";
        Assert.assertEquals("https://coverartarchive.org/release/" + mbid + "/front-500",
                ListeningRecap.coverArtUrl(mbid.toUpperCase()));
        Assert.assertEquals("https://coverartarchive.org/release-group/" + mbid + "/front-500",
                ListeningRecap.coverArtGroupUrl(mbid));
        Assert.assertEquals("", ListeningRecap.coverArtUrl(""));
        Assert.assertEquals("", ListeningRecap.coverArtUrl(null));
        Assert.assertEquals("", ListeningRecap.coverArtUrl("not-an-mbid"));
        Assert.assertEquals("", ListeningRecap.coverArtUrl(
                "f0d3e3bd-4e5a-4d3c-9e2c-1b2a3c4d5e6z")); // z is not hex
    }

    @Test
    public void summaryTextListsTheHighlightsItHas() {
        ListeningRecap recap = new ListeningRecap("listener", 2026);
        recap.totalListens = 12480L;
        recap.totalListeningTimeSec = 3L * 3600L;
        recap.busiestDayOfWeek = "Friday";
        ListeningRecap.Artist artist = new ListeningRecap.Artist();
        artist.name = "The Local Train";
        artist.listenCount = 385L;
        recap.topArtists.add(artist);
        ListeningRecap.Track track = new ListeningRecap.Track();
        track.title = "Aaoge Tum Kabhi";
        track.artist = "The Local Train";
        track.listenCount = 30L;
        recap.topTracks.add(track);
        ListeningRecap.Era era = new ListeningRecap.Era();
        era.year = 1998;
        era.listenCount = 4L;
        recap.eras.add(era);

        String summary = recap.summaryText();
        Assert.assertTrue(summary.startsWith("My 2026 in music: 12,480 listens · 3 h"));
        Assert.assertTrue(summary.contains("Top artists: The Local Train"));
        Assert.assertTrue(summary.contains("Top songs: Aaoge Tum Kabhi — The Local Train"));
        Assert.assertTrue(summary.contains("Most played era: 1990s"));
        Assert.assertTrue(summary.contains("Busiest day: Friday"));
        Assert.assertTrue(summary.contains("@listener on ListenBrainz"));
        Assert.assertFalse(summary.contains("Top albums:"));
    }

    @Test
    public void summaryMarksAPartialYearAndDoesNotCallItFinished() {
        ListeningRecap recap = new ListeningRecap("listener", 2026);
        recap.totalListens = 100L;
        recap.partial = true;
        String summary = recap.summaryText();
        Assert.assertTrue(summary.contains("so far"));
        Assert.assertFalse(summary.contains("December"));
    }
}
