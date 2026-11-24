package com.soniq.base.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read-only ListenBrainz statistics client behind the year-in-review recap
 * ({@link ListeningRecap}).
 *
 * <p>It reads the user's <b>public</b> scrobble statistics — nothing is
 * submitted and no token is sent. The ListenBrainz user name comes from
 * {@link Settings#recapUserName()} (the scrobbling user, or a profile picked
 * for the recap) or from an explicit user name the caller passes in, so a
 * recap can be built for anyone who publishes listening data.
 *
 * <p>Two shapes of data are read, and both are optional:
 * <ul>
 *   <li>{@code /1/stats/user/{user}/year-in-music/{year}} — the service's
 *       complete year-in-review payload (top artists, tracks, albums, genres,
 *       daily listens, first-time artists, listening time, busiest weekday).</li>
 *   <li>{@code /1/stats/user/{user}/...} with {@code range=year} — the same
 *       statistics computed live, used while a year is still in progress
 *       (the annual payload is only published once a year is calculated).</li>
 * </ul>
 * Both are merged, and a missing or empty payload degrades to a
 * ({@link ListeningRecap#isEmpty() empty}) recap with a readable
 * {@link ListeningRecap#note} instead of an exception.
 *
 * <p>Requests are spaced through {@link ListenBrainzApi}'s one-call-per-second
 * throttle. Every method blocks: call it on a background executor, never on the
 * main thread.
 */
public final class ListeningRecapApi {

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    /** One high-count page per list is plenty for a top ten. */
    private static final int PAGE_SIZE = 100;
    /** Keeps the recap snapshot small; the chart only needs one year of days. */
    private static final int MAX_ACTIVITY_DAYS = 400;

    private final String userAgent;

    public ListeningRecapApi(Context context) {
        this.userAgent = ListenBrainzApi.userAgentFor(context);
    }

    /** Test seam: skips the Android {@code getPackageManager()} version lookup. */
    ListeningRecapApi(String version) {
        String safe = version == null || version.trim().length() == 0 ? "dev" : version.trim();
        this.userAgent = "SoniqMusic/" + safe + " ( " + ListenBrainzApi.CONTACT_URL + " )";
    }

    /** The ListenBrainz user the recap is built for, or "" when none is known. */
    public String userName(Settings settings) {
        return settings == null ? "" : settings.recapUserName();
    }

    /**
     * Builds the recap for one calendar year from whatever ListenBrainz
     * publishes. Never returns null; check {@link ListeningRecap#isEmpty()}.
     *
     * @param userName ListenBrainz user name; blank means "the saved one"
     * @param year     calendar year to recap, e.g. the current one
     */
    public ListeningRecap load(String userName, int year) throws IOException {
        String user = userName == null ? "" : userName.trim();
        if (user.length() == 0) {
            return ListeningRecap.empty("", year,
                    "Add your ListenBrainz user name in settings to see your year in review.");
        }
        if (year < 2002 || year > 9999) {
            throw new IOException("Pick a year between 2002 and today.");
        }
        String encoded = encode(user);
        ListeningRecap recap = new ListeningRecap(user, year);

        // 1. The published year-in-review payload, when the year has been calculated.
        JSONObject yearPayload = payloadOrNull("stats/user/" + encoded
                + "/year-in-music/" + year);
        // 2. Live statistics for the same year, for a year still in progress.
        String range = "\"" + year + "\"";
        JSONObject artists = payloadOrNull(
                "stats/user/" + encoded + "/artists?range=" + range + "&count=" + PAGE_SIZE);
        JSONObject tracks = payloadOrNull(
                "stats/user/" + encoded + "/recordings?range=" + range + "&count=" + PAGE_SIZE);
        JSONObject releases = payloadOrNull(
                "stats/user/" + encoded + "/releases?range=" + range + "&count=" + PAGE_SIZE);
        JSONObject activity = payloadOrNull(
                "stats/user/" + encoded + "/listening-activity?range=" + range);

        if (yearPayload == null && artists == null && tracks == null
                && releases == null && activity == null) {
            return ListeningRecap.empty(user, year,
                    "ListenBrainz has no statistics for " + user + " in " + year + " yet. "
                            + "Statistics need a day or two of listens to be calculated.");
        }

        // A year is "partial" when it is still running and no annual payload exists yet.
        recap.partial = yearPayload == null && year >= currentYear();

        JSONObject data = yearData(yearPayload);
        mergeTotals(recap, data);
        mergeTopLists(recap, data, artists, tracks, releases);
        mergeActivity(recap, data, activity);
        mergeHighlights(recap, data, year);

        if (recap.totalListens <= 0L) {
            recap.totalListens = ListeningRecap.listensInYear(recap.activity, year);
        }
        if (recap.isEmpty()) {
            return ListeningRecap.empty(user, year,
                    "ListenBrainz reported no listens for " + user + " in " + year + ".");
        }
        return recap;
    }

    private static int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    /* ================================================================ merging */

    /** The {@code data} object of a year-in-music payload, or null. */
    private static JSONObject yearData(JSONObject yearPayload) {
        if (yearPayload == null) {
            return null;
        }
        JSONObject payload = yearPayload.optJSONObject("payload");
        return payload == null ? null : payload.optJSONObject("data");
    }

    private static void mergeTotals(ListeningRecap recap, JSONObject data) {
        if (data == null) {
            return;
        }
        long listens = data.optLong("total_listen_count", 0L);
        if (listens > 0L) {
            recap.totalListens = listens;
        }
        double seconds = data.optDouble("total_listening_time", 0.0);
        if (seconds > 0.0) {
            recap.totalListeningTimeSec = Math.round(seconds);
        }
        recap.totalArtists = data.optLong("total_artists_count", 0L);
        recap.totalTracks = data.optLong("total_recordings_count", 0L);
        recap.totalReleases = data.optLong("total_release_groups_count", 0L);
        recap.newArtists = data.optLong("total_new_artists_discovered", 0L);
        String day = data.optString("day_of_week", "").trim();
        if (day.length() > 0) {
            recap.busiestDayOfWeek = day;
        }
    }

    private static void mergeTopLists(ListeningRecap recap, JSONObject data,
                                      JSONObject artists, JSONObject tracks,
                                      JSONObject releases) {
        mergeArtists(recap, data, artists);
        mergeTracks(recap, data, tracks);
        mergeReleases(recap, data, releases);
        mergeGenres(recap, data);
        mergeEras(recap, data);
        sortTopLists(recap);
    }

    private static void mergeArtists(ListeningRecap recap, JSONObject data, JSONObject live) {
        JSONArray source = data == null ? null : data.optJSONArray("top_artists");
        if (source == null && live != null) {
            source = live.optJSONArray("artists");
        }
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject o = source.optJSONObject(i);
            if (o == null) {
                continue;
            }
            ListeningRecap.Artist artist = new ListeningRecap.Artist();
            artist.name = firstNonEmpty(o.optString("artist_name", ""), o.optString("name", ""));
            artist.listenCount = o.optLong("listen_count", 0L);
            artist.coverUrl = ListeningRecap.coverArtUrl(o.optString("caa_release_mbid", ""));
            if (artist.name.length() > 0) {
                recap.topArtists.add(artist);
            }
        }
    }

    private static void mergeTracks(ListeningRecap recap, JSONObject data, JSONObject live) {
        JSONArray source = data == null ? null : data.optJSONArray("top_recordings");
        if (source == null && live != null) {
            source = live.optJSONArray("recordings");
        }
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject o = source.optJSONObject(i);
            if (o == null) {
                continue;
            }
            ListeningRecap.Track track = new ListeningRecap.Track();
            track.title = firstNonEmpty(o.optString("track_name", ""), o.optString("title", ""));
            track.artist = firstNonEmpty(o.optString("artist_name", ""),
                    o.optString("creator", ""));
            track.album = firstNonEmpty(o.optString("release_name", ""), o.optString("album", ""));
            track.listenCount = o.optLong("listen_count", 0L);
            track.coverUrl = ListeningRecap.coverArtUrl(
                    firstNonEmpty(o.optString("caa_release_mbid", ""),
                            o.optString("release_mbid", "")));
            if (track.title.length() > 0) {
                recap.topTracks.add(track);
            }
        }
    }

    private static void mergeReleases(ListeningRecap recap, JSONObject data, JSONObject live) {
        JSONArray source = data == null ? null : data.optJSONArray("top_release_groups");
        if (source == null && live != null) {
            source = live.optJSONArray("releases");
        }
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject o = source.optJSONObject(i);
            if (o == null) {
                continue;
            }
            ListeningRecap.Release release = new ListeningRecap.Release();
            release.title = firstNonEmpty(o.optString("release_group_name", ""),
                    o.optString("release_name", ""));
            release.artist = o.optString("artist_name", "");
            release.listenCount = o.optLong("listen_count", 0L);
            release.coverUrl = firstNonEmpty(
                    ListeningRecap.coverArtGroupUrl(o.optString("release_group_mbid", "")),
                    ListeningRecap.coverArtUrl(o.optString("caa_release_mbid", "")));
            if (release.title.length() > 0) {
                recap.topReleases.add(release);
            }
        }
    }

    private static void mergeGenres(ListeningRecap recap, JSONObject data) {
        JSONArray source = data == null ? null : data.optJSONArray("top_genres");
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject o = source.optJSONObject(i);
            if (o == null) {
                continue;
            }
            ListeningRecap.Genre genre = new ListeningRecap.Genre();
            genre.name = o.optString("genre", "");
            genre.listenCount = o.optLong("genre_count", 0L);
            genre.percent = o.optDouble("genre_count_percent", 0.0);
            if (genre.name.length() > 0) {
                recap.topGenres.add(genre);
            }
        }
    }

    private static void mergeEras(ListeningRecap recap, JSONObject data) {
        JSONObject years = data == null ? null : data.optJSONObject("most_listened_year");
        if (years == null) {
            return;
        }
        java.util.Iterator<String> keys = years.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                int year = Integer.parseInt(key.trim());
                long listens = years.optLong(key, 0L);
                if (year > 0 && listens > 0L) {
                    ListeningRecap.Era era = new ListeningRecap.Era();
                    era.year = year;
                    era.listenCount = listens;
                    recap.eras.add(era);
                }
            } catch (NumberFormatException ignored) {
                // "most_listened_year" is keyed by year; skip anything odd.
            }
        }
    }

    private static void mergeActivity(ListeningRecap recap, JSONObject data, JSONObject live) {
        JSONArray source = data == null ? null : data.optJSONArray("listens_per_day");
        if (source == null && live != null) {
            source = live.optJSONArray("listening_activity");
        }
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length() && recap.activity.size() < MAX_ACTIVITY_DAYS; i++) {
            JSONObject o = source.optJSONObject(i);
            if (o == null) {
                continue;
            }
            ListeningRecap.ActivityDay day = new ListeningRecap.ActivityDay();
            day.fromTs = o.optLong("from_ts", 0L);
            day.label = o.optString("time_range", "");
            day.listenCount = o.optLong("listen_count", 0L);
            if (day.fromTs > 0L) {
                recap.activity.add(day);
            }
        }
    }

    private static void mergeHighlights(ListeningRecap recap, JSONObject data, int year) {
        if (recap.busiestDayOfWeek.length() == 0 && data != null) {
            recap.busiestDayOfWeek = data.optString("day_of_week", "").trim();
        }
        if (recap.rangeLabel.length() == 0) {
            recap.rangeLabel = rangeLabel(year, !recap.partial);
        }
        if (recap.topTracks.isEmpty() && recap.topArtists.isEmpty() && !recap.isEmpty()) {
            recap.note = "ListenBrainz has totals for " + year
                    + " but has not published its detailed top lists.";
        }
    }

    private static void sortTopLists(ListeningRecap recap) {
        Collections.sort(recap.topArtists, new Comparator<ListeningRecap.Artist>() {
            @Override
            public int compare(ListeningRecap.Artist a, ListeningRecap.Artist b) {
                return Long.compare(b.listenCount, a.listenCount);
            }
        });
        Collections.sort(recap.topTracks, new Comparator<ListeningRecap.Track>() {
            @Override
            public int compare(ListeningRecap.Track a, ListeningRecap.Track b) {
                return Long.compare(b.listenCount, a.listenCount);
            }
        });
        Collections.sort(recap.topReleases, new Comparator<ListeningRecap.Release>() {
            @Override
            public int compare(ListeningRecap.Release a, ListeningRecap.Release b) {
                return Long.compare(b.listenCount, a.listenCount);
            }
        });
        Collections.sort(recap.topGenres, new Comparator<ListeningRecap.Genre>() {
            @Override
            public int compare(ListeningRecap.Genre a, ListeningRecap.Genre b) {
                return Long.compare(b.listenCount, a.listenCount);
            }
        });
        trim(recap.topArtists);
        trim(recap.topTracks);
        trim(recap.topReleases);
        trim(recap.topGenres);
    }

    /** Trims a top list to {@link ListeningRecap#TOP_LIMIT}. */
    private static <T> void trim(List<T> list) {
        while (list.size() > ListeningRecap.TOP_LIMIT) {
            list.remove(list.size() - 1);
        }
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && first.trim().length() > 0) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private static String rangeLabel(int year, boolean complete) {
        Calendar calendar = Calendar.getInstance();
        if (complete || year < calendar.get(Calendar.YEAR)) {
            return "1 Jan – 31 Dec " + year;
        }
        DateFormatSymbols symbols = DateFormatSymbols.getInstance(Locale.US);
        String month = symbols.getShortMonths()[calendar.get(Calendar.MONTH)];
        return "1 Jan – " + calendar.get(Calendar.DAY_OF_MONTH) + " " + month + " " + year
                + " · so far";
    }

    /* ===================================================================== HTTP */

    /** JSON body of a stats path, or null for "no statistics" answers (204 / 404). */
    private JSONObject payloadOrNull(String path) throws IOException {
        ListenBrainzApi.waitForRequestSlot();
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) URI.create(ListenBrainzApi.ROOT + path).toURL()
                    .openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            // ListenBrainz requires "Name/version ( contact-url )" and blocks anonymous clients.
            connection.setRequestProperty("User-Agent", userAgent);
            int code = connection.getResponseCode();
            if (code == 204 || code == 404) {
                return null;
            }
            input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = new String(readFully(input), "UTF-8");
            if (code < 200 || code >= 300) {
                if (code == 429) {
                    throw new ListenBrainzApi.HttpFailure(code,
                            "ListenBrainz rate limit reached", retryAfter(connection));
                }
                throw new ListenBrainzApi.HttpFailure(code, "ListenBrainz HTTP " + code
                        + (text.trim().length() > 0
                        ? ": " + ListenBrainzApi.summarize(text) : ""), 0L);
            }
            if (text.trim().length() == 0) {
                return null;
            }
            JSONObject body = new JSONObject(text);
            return body.has("payload") ? body : null;
        } catch (JSONException error) {
            throw new IOException("Unexpected answer from ListenBrainz: " + error.getMessage());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static long retryAfter(HttpURLConnection connection) {
        String value = connection.getHeaderField("Retry-After");
        if (value == null) {
            value = connection.getHeaderField("X-RateLimit-Reset-In");
        }
        if (value != null) {
            try {
                long seconds = Long.parseLong(value.trim());
                return seconds > 0L ? Math.min(seconds, 120L) : 2L;
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return 2L;
    }

    private static byte[] readFully(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
            if (buffer.size() >= MAX_RESPONSE_BYTES) {
                break;
            }
        }
        return buffer.toByteArray();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            return value;
        }
    }
}
