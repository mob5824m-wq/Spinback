package com.soniq.base.data;

import com.soniq.base.model.LyricsState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lyrics lookups with a small in-memory cache and an honest four-way result:
 * found, instrumental, not found, unavailable.
 *
 * <p>Why the distinction matters: "we searched and the service has nothing"
 * (NOT_FOUND) is a permanent answer for that track and can be cached; a failed
 * request (UNAVAILABLE) is temporary and must not be remembered as "no lyrics".
 * Before, three different situations produced the same empty panel.
 *
 * <p>The provider hook wins over the search: if the catalogue marks a track
 * {@link LyricsState#INSTRUMENTAL}, we never claim a network lookup failed.
 */
public final class LyricsRepository {

    /** Small: a full lyrics response is a few KB, and stale entries are useless. */
    static final int CACHE_LIMIT = 24;

    private final LyricsApi api;
    private final List<String> keys = new ArrayList<>();
    private final List<LyricsResult> values = new ArrayList<>();

    public LyricsRepository() {
        this(new LyricsApi());
    }

    public LyricsRepository(LyricsApi api) {
        this.api = api == null ? new LyricsApi() : api;
    }

    /**
     * Looks up lyrics for a track. Never throws: every failure is one of the
     * {@link LyricsResult.Status} values, so the UI has something to say.
     *
     * @param providerState {@link LyricsState} value the provider reported
     */
    public LyricsResult lookup(String title, String artist, String providerState) {
        if (LyricsState.isInstrumental(providerState)) {
            return LyricsResult.instrumental();
        }
        String key = cacheKey(title, artist);
        if (key.length() == 0) {
            return LyricsResult.notFound();
        }
        LyricsResult cached = get(key);
        if (cached != null) {
            return cached;
        }
        LyricsResult result;
        try {
            List<LyricLine> lines = api.fetch(title, artist);
            if (lines == null || lines.isEmpty()) {
                result = LyricsResult.notFound();
            } else {
                result = LyricsResult.found(lines, hasTimings(lines));
            }
        } catch (java.io.InterruptedIOException cancelled) {
            return LyricsResult.unavailable("Lyrics request cancelled");
        } catch (java.io.IOException error) {
            String message = error.getMessage();
            return LyricsResult.unavailable(
                    message == null || message.trim().length() == 0 ? "Lyrics unavailable" : message);
        } catch (RuntimeException error) {
            return LyricsResult.unavailable("Could not read the lyrics response");
        }
        put(key, result);
        return result;
    }

    /** True when at least one line carries a timestamp the player can follow. */
    public static boolean hasTimings(List<LyricLine> lines) {
        if (lines == null) {
            return false;
        }
        for (LyricLine line : lines) {
            if (line != null && line.timeMs >= 0) {
                return true;
            }
        }
        return false;
    }

    static String cacheKey(String title, String artist) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.length() == 0) {
            return "";
        }
        String cleanArtist = artist == null ? "" : artist.trim();
        return (cleanTitle + "\n" + cleanArtist).toLowerCase(java.util.Locale.ROOT);
    }

    /** Cached result, or null. */
    public LyricsResult get(String key) {
        synchronized (values) {
            int index = keys.indexOf(key);
            return index < 0 ? null : values.get(index);
        }
    }

    /**
     * Remembers a result. Only FOUND and NOT_FOUND are cached: an UNAVAILABLE
     * answer must be retried next time, and INSTRUMENTAL comes from the provider,
     * not from this cache.
     */
    public void put(String key, LyricsResult result) {
        if (key == null || key.length() == 0 || result == null) {
            return;
        }
        if (result.status == LyricsResult.Status.UNAVAILABLE
                || result.status == LyricsResult.Status.INSTRUMENTAL) {
            return;
        }
        synchronized (values) {
            int index = keys.indexOf(key);
            if (index >= 0) {
                keys.remove(index);
                values.remove(index);
            }
            keys.add(0, key);
            values.add(0, result);
            while (keys.size() > CACHE_LIMIT) {
                keys.remove(keys.size() - 1);
                values.remove(values.size() - 1);
            }
        }
    }

    public void clear() {
        synchronized (values) {
            keys.clear();
            values.clear();
        }
    }

    public int size() {
        synchronized (values) {
            return keys.size();
        }
    }

    /** Read-only snapshot, newest first — used by tests and diagnostics. */
    public List<LyricsResult> snapshot() {
        synchronized (values) {
            return Collections.unmodifiableList(new ArrayList<>(values));
        }
    }
}
