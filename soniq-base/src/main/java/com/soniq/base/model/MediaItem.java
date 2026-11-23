package com.soniq.base.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One entry in a provider's music graph: a playable track, an artist, an album
 * or a playlist. Providers fill public fields while parsing their own
 * responses, so this class deliberately has no builder.
 *
 * <p>Ids are opaque to the framework: whatever the provider uses to identify
 * the item (a track id, an artist id, an album id…). Local playlists created
 * in-app use {@link #LOCAL_PLAYLIST_PREFIX} followed by a UUID.
 */
public final class MediaItem {

    public static final String KIND_TRACK = "track";
    public static final String KIND_ARTIST = "artist";
    public static final String KIND_ALBUM = "album";
    public static final String KIND_PLAYLIST = "playlist";

    /** Prefix of {@link #id} for playlists that exist only in this app's library. */
    public static final String LOCAL_PLAYLIST_PREFIX = "local:";

    /** Provider-scoped identifier of this item. */
    public String id = "";
    /** One of {@code KIND_TRACK}, {@code KIND_ARTIST}, {@code KIND_ALBUM}, {@code KIND_PLAYLIST}. */
    public String kind = KIND_TRACK;
    public String title = "";
    public String author = "";
    public String album = "";
    public String durationText = "";
    public long durationSec = 0L;
    public String thumbnailUrl = "";
    public boolean isLive = false;
    /** Content URI of an offline copy; when set the track plays without the provider. */
    public String localUri = "";

    /**
     * Lyrics availability as reported by the provider, or
     * {@link LyricsState#UNKNOWN} when the provider has no opinion. A provider
     * that knows a track is an instrumental sets this so the UI can say
     * "instrumental" instead of pretending the lookup failed.
     */
    public String lyricsState = LyricsState.UNKNOWN;

    /** Sets the duration in seconds and refreshes {@link #durationText}. */
    public void setDurationSec(long seconds) {
        durationSec = Math.max(0L, seconds);
        durationText = formatDuration(durationSec * 1000L);
    }

    /** {@code "m:ss"} (or {@code "h:mm:ss"} over an hour) for a duration in milliseconds. */
    public static String formatDuration(long millis) {
        if (millis <= 0L) {
            return "0:00";
        }
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + ":" + two(minutes) + ":" + two(seconds);
        }
        return minutes + ":" + two(seconds);
    }

    private static String two(long value) {
        return value < 10L ? "0" + value : String.valueOf(value);
    }

    public boolean isArtist() {
        return KIND_ARTIST.equals(kind) && id.length() > 0;
    }

    public boolean isAlbum() {
        return KIND_ALBUM.equals(kind) && id.length() > 0;
    }

    public boolean isPlaylist() {
        return KIND_PLAYLIST.equals(kind) && id.length() > 0;
    }

    public boolean isCollection() {
        return isAlbum() || isPlaylist();
    }

    /** Items the UI opens instead of playing. */
    public boolean isBrowsable() {
        return isArtist() || isCollection();
    }

    public boolean isPlayable() {
        if (isBrowsable()) {
            return false;
        }
        return id.length() > 0 || localUri.length() > 0;
    }

    public boolean isOffline() {
        return localUri.length() > 0;
    }

    /** True for playlists this app created (as opposed to provider playlists). */
    public boolean isLocalPlaylist() {
        return KIND_PLAYLIST.equals(kind) && id.startsWith(LOCAL_PLAYLIST_PREFIX);
    }

    /** Stable identity used for liked songs, playlists and de-duplication. */
    public String identity() {
        if (id.length() > 0) {
            return kind + ":" + id;
        }
        return localUri.length() > 0 ? "local:" + localUri : "";
    }

    public String bestThumb() {
        return thumbnailUrl == null ? "" : thumbnailUrl;
    }

    public String subtitle() {
        if (isBrowsable()) {
            String kindLabel = kind.toUpperCase(Locale.US);
            return author.length() > 0 ? kindLabel + " · " + author : kindLabel;
        }
        StringBuilder sb = new StringBuilder();
        if (author.length() > 0) {
            sb.append(author);
        }
        if (album.length() > 0) {
            if (sb.length() > 0) {
                sb.append("  •  ");
            }
            sb.append(album);
        }
        if (durationText.length() > 0) {
            if (sb.length() > 0) {
                sb.append("  •  ");
            }
            sb.append(durationText);
        }
        return sb.toString();
    }

    public MediaItem copy() {
        MediaItem out = new MediaItem();
        out.id = id;
        out.kind = kind;
        out.title = title;
        out.author = author;
        out.album = album;
        out.durationText = durationText;
        out.durationSec = durationSec;
        out.thumbnailUrl = thumbnailUrl;
        out.isLive = isLive;
        out.localUri = localUri;
        out.lyricsState = lyricsState;
        return out;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("kind", kind);
            o.put("t", title);
            o.put("a", author);
            o.put("al", album);
            o.put("d", durationText);
            o.put("sec", durationSec);
            o.put("th", thumbnailUrl);
            o.put("live", isLive);
            o.put("local", localUri);
            if (lyricsState != null && !LyricsState.UNKNOWN.equals(lyricsState)) {
                o.put("lyr", lyricsState);
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    public static MediaItem fromJson(JSONObject o) {
        MediaItem m = new MediaItem();
        if (o == null) {
            return m;
        }
        m.id = o.optString("id", "");
        m.kind = o.optString("kind", KIND_TRACK);
        m.title = o.optString("t", "");
        m.author = o.optString("a", "");
        m.album = o.optString("al", "");
        m.durationText = o.optString("d", "");
        m.durationSec = o.optLong("sec", 0L);
        m.thumbnailUrl = o.optString("th", "");
        m.isLive = o.optBoolean("live", false);
        m.localUri = o.optString("local", "");
        m.lyricsState = LyricsState.normalize(o.optString("lyr", LyricsState.UNKNOWN));
        return m;
    }

    public static List<MediaItem> fromJsonArray(JSONArray array) {
        List<MediaItem> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null) {
                out.add(fromJson(o));
            }
        }
        return out;
    }

    public static JSONArray toJsonArray(List<MediaItem> items) {
        JSONArray array = new JSONArray();
        if (items == null) {
            return array;
        }
        for (MediaItem item : items) {
            if (item != null) {
                array.put(item.toJson());
            }
        }
        return array;
    }

    public static List<MediaItem> copy(List<MediaItem> source) {
        List<MediaItem> out = new ArrayList<>();
        if (source != null) {
            for (MediaItem item : source) {
                if (item != null) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return title + " (" + identity() + ")";
    }
}
