package com.spinback.gplaymusic;

import android.content.Context;

import com.soniq.base.model.MediaItem;
import com.soniq.base.model.StreamSource;
import com.soniq.base.provider.MusicProvider;

import java.io.IOException;
import java.util.List;

/**
 * Adapts the YouTube Music public client ({@link YouTubeMusicApi}) to the
 * framework's {@link MusicProvider} SPI, under a Google Play Music theme.
 *
 * <p><b>Why YouTube Music underneath?</b> Google Play Music was shut down in
 * December 2020 and replaced by YouTube Music; Google migrated users'
 * libraries to the successor service. There is no Google Play Music API left
 * to call, so this revival uses the successor's public, unauthenticated
 * endpoints as its catalogue. Everything YouTube-specific stays behind this
 * class, exactly as the Soniq Base SPI intends — swapping the catalogue
 * later means writing one new provider, not touching the framework.
 *
 * <p>Honesty notes (also in {@code docs/GOOGLE-PLAY-MUSIC.md}):
 * <ul>
 *   <li>{@link #displayName()} is the theme name. The tagline and README say
 *       plainly that this is an unofficial revival and where the catalogue
 *       actually comes from.</li>
 *   <li>{@link #musicService()} / {@link #originUrl(MediaItem)} report the
 *       real source ({@code music.youtube.com}), so ListenBrainz scrobbles
 *       never claim a Google Play Music origin that does not exist.</li>
 *   <li>No sign-in, no cookies, no bypass of age / member / region
 *       restrictions. Provider refusals are surfaced as text.</li>
 * </ul>
 */
public final class GooglePlayMusicProvider implements MusicProvider {

    public static final String ID = "google-play-music";
    public static final String MUSIC_SERVICE = "music.youtube.com";
    private static final String ORIGIN_PREFIX = "https://music.youtube.com/watch?v=";

    private final YouTubeMusicApi api;

    public GooglePlayMusicProvider(Context context) {
        this.api = new YouTubeMusicApi(context);
    }

    /** Exposed for app-level settings (preferred InnerTube client) and diagnostics. */
    public YouTubeMusicApi api() {
        return api;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Google Play Music";
    }

    @Override
    public String tagline() {
        return "Unofficial revival · Google Play Music 2011–2020 · "
                + "catalogue via YouTube Music, its successor · no sign-in";
    }

    @Override
    public List<MediaItem> search(String query, String filter) throws IOException {
        return api.search(query, modeFor(filter));
    }

    @Override
    public List<MediaItem> quickPicks() throws IOException {
        // "official audio" keeps results to proper tracks rather than
        // music videos, interviews and fan uploads.
        return api.search("popular music official audio", YouTubeMusicApi.MODE_SONGS);
    }

    @Override
    public List<MediaItem> browse(MediaItem item) throws IOException {
        if (item == null) {
            throw new IOException("Nothing to browse.");
        }
        return item.isArtist() ? api.browseArtist(item) : api.browseCollection(item);
    }

    @Override
    public StreamSource resolve(MediaItem track) throws IOException {
        if (track == null || track.id.length() == 0) {
            throw new IOException("This track has no video id.");
        }
        return api.resolve(track.id);
    }

    @Override
    public String musicService() {
        return MUSIC_SERVICE;
    }

    @Override
    public String originUrl(MediaItem track) {
        return track != null && track.id.length() > 0 ? ORIGIN_PREFIX + track.id : "";
    }

    /**
     * YouTube Music returns lyrics separately (and only for some tracks), so
     * this provider does not claim to know anything here: the framework
     * searches lrclib and reports honestly. A provider that knows a track is
     * an instrumental should return
     * {@link com.soniq.base.model.LyricsState#INSTRUMENTAL}.
     */
    @Override
    public String lyricsState(MediaItem track) {
        return com.soniq.base.model.LyricsState.UNKNOWN;
    }

    /** Maps the framework's standard filters onto this API's search modes. */
    public static String modeFor(String filter) {
        if (FILTER_ARTISTS.equals(filter)) {
            return YouTubeMusicApi.MODE_ARTISTS;
        }
        if (FILTER_ALBUMS.equals(filter)) {
            return YouTubeMusicApi.MODE_ALBUMS;
        }
        if (FILTER_PLAYLISTS.equals(filter)) {
            return YouTubeMusicApi.MODE_PLAYLISTS;
        }
        return YouTubeMusicApi.MODE_SONGS;
    }

    /** Placeholder track for a watch link; the resolver fills in the real metadata. */
    public static MediaItem trackForVideoId(String videoId) {
        MediaItem item = new MediaItem();
        item.id = videoId;
        item.kind = MediaItem.KIND_TRACK;
        item.title = "Opening link…";
        item.thumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        return item;
    }

    /** Placeholder playlist for a {@code list=} link; ids are kept in browse ("VL…") form. */
    public static MediaItem playlistForId(String playlistId) {
        MediaItem item = new MediaItem();
        item.id = playlistId.startsWith("VL") ? playlistId : "VL" + playlistId;
        item.kind = MediaItem.KIND_PLAYLIST;
        item.title = "YouTube playlist";
        return item;
    }
}
