package com.soniq.base.provider;

import com.soniq.base.model.LyricsState;
import com.soniq.base.model.MediaItem;
import com.soniq.base.model.StreamSource;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * The service Provider SPI. An app implements this for its music source and
 * hands it to the framework through {@code SoniqApp.createProvider()}; the
 * framework supplies the player, queue, library, downloads, lyrics, scrobbling
 * and UI.
 *
 * <p>All methods are called on background threads and may block. Return an
 * empty list for "nothing found"; throw {@link IOException} with a message the
 * user can read for failures. Implementations must be safe to call from
 * several threads concurrently.
 */
public interface MusicProvider {

    /** Standard search filters. Unknown filters should be treated as songs. */
    String FILTER_SONGS = "songs";
    String FILTER_ARTISTS = "artists";
    String FILTER_ALBUMS = "albums";
    String FILTER_PLAYLISTS = "playlists";

    /** Stable identifier, e.g. {@code "youtube-music"}. Used for diagnostics only. */
    String id();

    /** Human-readable name shown in the UI, e.g. {@code "YouTube Music"}. */
    String displayName();

    /** Optional one-line description shown under the Home heading. */
    default String tagline() {
        return "";
    }

    /**
     * Searches the catalogue. Results may mix tracks, artists, albums and
     * playlists; the framework routes each by {@link MediaItem#isBrowsable()}.
     *
     * @param filter one of the {@code FILTER_*} constants
     */
    List<MediaItem> search(String query, String filter) throws IOException;

    /**
     * Children of an artist, album or playlist. Artists may return a mix of
     * albums and tracks; albums and playlists return tracks.
     */
    List<MediaItem> browse(MediaItem item) throws IOException;

    /** Resolves a playable stream for a track. Called off the main thread. */
    StreamSource resolve(MediaItem track) throws IOException;

    /**
     * Optional: the tracks shown as "Quick picks" on the Home screen. The
     * default runs a song search for "popular music"; providers with a real
     * charts/feed endpoint should override this.
     */
    default List<MediaItem> quickPicks() throws IOException {
        return search("popular music", FILTER_SONGS);
    }

    /**
     * Optional: a mix seeded by a track (used by "Start radio"). The default
     * returns nothing and the framework falls back to a title search.
     */
    default List<MediaItem> radio(MediaItem seed) throws IOException {
        return Collections.emptyList();
    }

    /**
     * Optional: the ListenBrainz {@code music_service} domain for this
     * provider's tracks (e.g. {@code "music.youtube.com"}), or "" to omit.
     */
    default String musicService() {
        return "";
    }

    /** Optional: the ListenBrainz {@code origin_url} for a track, or "" to omit. */
    default String originUrl(MediaItem track) {
        return "";
    }

    /**
     * Optional: what this provider knows about a track's lyrics, before any
     * lookup happens.
     *
     * <p>Return {@link LyricsState#INSTRUMENTAL} when the catalogue marks a
     * track as having no vocals, or {@link LyricsState#EXTERNAL} when lyrics
     * exist but are only available outside the app. Return
     * {@link LyricsState#UNKNOWN} (the default) when the provider has no
     * opinion — the framework then searches lrclib and reports honestly
     * whether it found nothing or the request failed. Never return
     * INSTRUMENTAL for a guess: a wrong "instrumental" label is worse than
     * "no lyrics found".
     */
    default String lyricsState(MediaItem track) {
        return LyricsState.UNKNOWN;
    }
}
