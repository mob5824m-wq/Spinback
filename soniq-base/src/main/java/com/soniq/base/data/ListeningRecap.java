package com.soniq.base.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One user's complete listening year as scrobbled to ListenBrainz: totals, top
 * artists / tracks / albums / genres, a day and era highlight, and a daily
 * activity series the UI folds into a monthly chart.
 *
 * <p>This is the framework's year-in-review ("Wrapped"-style) recap. It always
 * covers the <b>whole year</b>, never a single month, and can be produced at any
 * time of year — a year still in progress is reported as partial instead of
 * being treated as finished. Past years can be revisited whenever the service
 * still publishes data for them.
 *
 * <p>Plain Java (no Android types) so the aggregation and formatting helpers
 * are unit-tested on the JVM. Every value is optional: ListenBrainz may know
 * only some of them, and {@link #isEmpty()} tells the UI when there is nothing
 * trustworthy to show. Nothing here is computed on the device except sums and
 * labels — the numbers are the user's own public scrobble statistics.
 */
public final class ListeningRecap {

    /** How many entries each top list keeps. */
    public static final int TOP_LIMIT = 10;

    /** One row of the top-recordings list. */
    public static final class Track {
        public String title = "";
        public String artist = "";
        public String album = "";
        public String coverUrl = "";
        public long listenCount;
    }

    /** One row of the top-artists list. */
    public static final class Artist {
        public String name = "";
        public String coverUrl = "";
        public long listenCount;
    }

    /** One row of the top-albums (release) list. */
    public static final class Release {
        public String title = "";
        public String artist = "";
        public String coverUrl = "";
        public long listenCount;
    }

    /** One row of the genre share list; {@link #percent} is 0–100. */
    public static final class Genre {
        public String name = "";
        public long listenCount;
        public double percent;
    }

    /** How many listens a release year received, for the "most played era" line. */
    public static final class Era {
        public int year;
        public long listenCount;
    }

    /** One day of the activity series; {@link #fromTs} is unix seconds (UTC). */
    public static final class ActivityDay {
        public long fromTs;
        public String label = "";
        public long listenCount;
    }

    /** User the recap belongs to (ListenBrainz user name). */
    public final String userName;
    /** Calendar year the recap describes. */
    public final int year;
    /** Human-readable period, e.g. {@code "1 Jan – 23 Nov 2026"}. */
    public String rangeLabel = "";
    /** True when the year is still in progress, so the totals are partial. */
    public boolean partial;

    public long totalListens;
    /** Total listening time in seconds; 0 when the service did not report it. */
    public long totalListeningTimeSec;
    /** Distinct artists heard; 0 when unknown. */
    public long totalArtists;
    /** Distinct tracks heard; 0 when unknown. */
    public long totalTracks;
    /** Distinct albums heard; 0 when unknown. */
    public long totalReleases;
    /** Artists heard for the first time this year; 0 when unknown. */
    public long newArtists;
    /** Busiest day of the week, e.g. {@code "Friday"}; "" when unknown. */
    public String busiestDayOfWeek = "";

    public final List<Track> topTracks = new ArrayList<>();
    public final List<Artist> topArtists = new ArrayList<>();
    public final List<Release> topReleases = new ArrayList<>();
    public final List<Genre> topGenres = new ArrayList<>();
    public final List<Era> eras = new ArrayList<>();
    public final List<ActivityDay> activity = new ArrayList<>();

    /** Explanation shown when the recap has no data (never a generic "error"). */
    public String note = "";

    public ListeningRecap(String userName, int year) {
        this.userName = userName == null ? "" : userName.trim();
        this.year = year;
    }

    /** A recap with no statistics, carrying the reason in {@link #note}. */
    public static ListeningRecap empty(String userName, int year, String note) {
        ListeningRecap recap = new ListeningRecap(userName, year);
        recap.note = note == null ? "" : note;
        return recap;
    }

    /** True when ListenBrainz returned nothing worth rendering. */
    public boolean isEmpty() {
        return totalListens <= 0L
                && topArtists.isEmpty() && topTracks.isEmpty() && topReleases.isEmpty()
                && totalListeningTimeSec <= 0L;
    }

    /* ============================================================ aggregation */

    /** The calendar year of a unix-seconds timestamp in the device time zone. */
    public static int yearOf(long unixSeconds) {
        if (unixSeconds <= 0L) {
            return 0;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(unixSeconds * 1000L);
        return calendar.get(Calendar.YEAR);
    }

    /** Sums the activity days that fall inside {@code year}; ignores anything else. */
    public static long listensInYear(List<ActivityDay> days, int year) {
        if (days == null || year <= 0) {
            return 0L;
        }
        long total = 0L;
        for (ActivityDay day : days) {
            if (day != null && yearOf(day.fromTs) == year) {
                total += Math.max(0L, day.listenCount);
            }
        }
        return total;
    }

    /** Twelve listen totals (January → December) for one calendar year. */
    public long[] monthlyTotals(int year) {
        long[] months = new long[12];
        if (year <= 0) {
            return months;
        }
        for (ActivityDay day : activity) {
            if (day == null || day.fromTs <= 0L) {
                continue;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(day.fromTs * 1000L);
            if (calendar.get(Calendar.YEAR) != year) {
                continue;
            }
            months[calendar.get(Calendar.MONTH)] += Math.max(0L, day.listenCount);
        }
        return months;
    }

    /** Name of the month with the most listens in {@code year}, or "" when unknown. */
    public String busiestMonth(int year) {
        long[] months = monthlyTotals(year);
        int best = -1;
        long bestCount = 0L;
        for (int i = 0; i < months.length; i++) {
            if (months[i] > bestCount) {
                bestCount = months[i];
                best = i;
            }
        }
        if (best < 0) {
            return "";
        }
        java.text.DateFormatSymbols symbols = java.text.DateFormatSymbols.getInstance(Locale.US);
        return symbols.getMonths()[best];
    }

    /**
     * The decade that received the most listens, e.g. {@code "1990s"} — the
     * "most played era" of the recap. Empty when no release years are known.
     */
    public String topEraLabel() {
        TreeMap<Integer, Long> decades = new TreeMap<>();
        for (Era era : eras) {
            if (era == null || era.year <= 0 || era.listenCount <= 0L) {
                continue;
            }
            int decade = (era.year / 10) * 10;
            Long current = decades.get(decade);
            decades.put(decade, (current == null ? 0L : current) + era.listenCount);
        }
        int bestDecade = -1;
        long bestCount = 0L;
        for (Map.Entry<Integer, Long> entry : decades.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestDecade = entry.getKey();
            }
        }
        return bestDecade < 0 ? "" : bestDecade + "s";
    }

    /* ============================================================== formatting */

    /** Groups thousands, e.g. {@code 12480 → "12,480"}. */
    public static String formatCount(long value) {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }

    /** Hours for large totals, minutes for small ones; "" for unknown. */
    public static String formatListeningTime(long seconds) {
        if (seconds <= 0L) {
            return "";
        }
        if (seconds < 3600L) {
            long minutes = Math.max(1L, seconds / 60L);
            return formatCount(minutes) + " min";
        }
        long hours = Math.round(seconds / 3600.0);
        return formatCount(hours) + " h";
    }

    /** A share text summary: totals, the top three of each list, the era. */
    public String summaryText() {
        StringBuilder text = new StringBuilder();
        text.append(year > 0 ? "My " + year + " in music" : "My year in music");
        if (totalListens > 0L) {
            text.append(": ").append(formatCount(totalListens)).append(" listens");
        }
        String time = formatListeningTime(totalListeningTimeSec);
        if (time.length() > 0) {
            text.append(" · ").append(time);
        }
        if (partial) {
            text.append(" · so far");
        }
        text.append('\n');
        appendList(text, "Top artists", artistNames(3));
        appendList(text, "Top songs", trackNames(3));
        appendList(text, "Top albums", releaseNames(3));
        appendList(text, "Top genres", genreNames(3));
        String era = topEraLabel();
        if (era.length() > 0) {
            text.append("Most played era: ").append(era).append('\n');
        }
        if (busiestDayOfWeek.length() > 0) {
            text.append("Busiest day: ").append(busiestDayOfWeek).append('\n');
        }
        if (userName.length() > 0) {
            text.append("From @").append(userName).append(" on ListenBrainz");
        } else {
            text.append("From my ListenBrainz scrobbles");
        }
        return text.toString().trim();
    }

    private static void appendList(StringBuilder text, String heading, List<String> names) {
        if (names.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder(heading).append(": ");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append(names.get(i));
        }
        text.append(line).append('\n');
    }

    private List<String> artistNames(int limit) {
        List<String> names = new ArrayList<>();
        for (Artist artist : topArtists) {
            if (artist != null && artist.name.length() > 0 && names.size() < limit) {
                names.add(artist.name);
            }
        }
        return names;
    }

    private List<String> trackNames(int limit) {
        List<String> names = new ArrayList<>();
        for (Track track : topTracks) {
            if (track != null && track.title.length() > 0 && names.size() < limit) {
                names.add(track.artist.length() > 0
                        ? track.title + " — " + track.artist : track.title);
            }
        }
        return names;
    }

    private List<String> releaseNames(int limit) {
        List<String> names = new ArrayList<>();
        for (Release release : topReleases) {
            if (release != null && release.title.length() > 0 && names.size() < limit) {
                names.add(release.artist.length() > 0
                        ? release.title + " — " + release.artist : release.title);
            }
        }
        return names;
    }

    private List<String> genreNames(int limit) {
        List<String> names = new ArrayList<>();
        for (Genre genre : topGenres) {
            if (genre != null && genre.name.length() > 0 && names.size() < limit) {
                names.add(genre.name);
            }
        }
        return names;
    }

    /* ================================================================== covers */

    /**
     * Cover Art Archive front cover for a MusicBrainz release id; "" when the
     * id is not a full MBID, so the UI falls back to its placeholder instead
     * of requesting a malformed URL.
     */
    public static String coverArtUrl(String releaseMbid) {
        String clean = releaseMbid == null ? "" : releaseMbid.trim().toLowerCase(Locale.US);
        if (!isMbid(clean)) {
            return "";
        }
        return "https://coverartarchive.org/release/" + clean + "/front-500";
    }

    /** Cover Art Archive front cover for a release-group id; "" when malformed. */
    public static String coverArtGroupUrl(String releaseGroupMbid) {
        String clean = releaseGroupMbid == null
                ? "" : releaseGroupMbid.trim().toLowerCase(Locale.US);
        if (!isMbid(clean)) {
            return "";
        }
        return "https://coverartarchive.org/release-group/" + clean + "/front-500";
    }

    private static boolean isMbid(String value) {
        if (value.length() != 36) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean dash = i == 8 || i == 13 || i == 18 || i == 23;
            if (dash ? c != '-' : !isHex(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
