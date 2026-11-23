package com.soniq.base.data;

import com.soniq.base.model.MediaItem;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Local library: liked songs, saved artists, saved albums/playlists, local
 * playlists and the recent list. Stored as compact JSON in SharedPreferences so
 * the data survives app updates and Android backup without any account.
 */
public final class Library {

    public static final String LIKED = "liked";
    public static final String ARTISTS = "artists";
    public static final String COLLECTIONS = "collections";
    public static final String RECENT = "recent";
    public static final String PLAYLISTS = "playlists_index";

    private static final int MAX_LIST = 200;
    private static final int MAX_RECENT = 30;
    private static final String PREFS = "soniq_library";

    private final SharedPreferences prefs;

    public Library(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ------------------------------------------------------------- generic */

    public List<MediaItem> list(String key) {
        return MediaItem.fromJsonArray(readArray(key));
    }

    public void save(String key, List<MediaItem> items) {
        prefs.edit().putString(key, MediaItem.toJsonArray(items).toString()).apply();
    }

    public boolean contains(String key, MediaItem item) {
        if (item == null) {
            return false;
        }
        String identity = item.identity();
        if (identity.length() == 0) {
            return false;
        }
        for (MediaItem existing : list(key)) {
            if (identity.equals(existing.identity())) {
                return true;
            }
        }
        return false;
    }

    /** Adds an item at the front, or removes it when it is already present. */
    public boolean toggle(String key, MediaItem item) {
        if (item == null) {
            return false;
        }
        String identity = item.identity();
        List<MediaItem> current = list(key);
        for (int i = 0; i < current.size(); i++) {
            if (identity.length() > 0 && identity.equals(current.get(i).identity())) {
                current.remove(i);
                save(key, current);
                return false;
            }
        }
        current.add(0, item);
        while (current.size() > MAX_LIST) {
            current.remove(current.size() - 1);
        }
        save(key, current);
        return true;
    }

    public void add(String key, MediaItem item) {
        if (item == null || contains(key, item)) {
            return;
        }
        List<MediaItem> current = list(key);
        current.add(0, item);
        while (current.size() > MAX_LIST) {
            current.remove(current.size() - 1);
        }
        save(key, current);
    }

    public void remove(String key, MediaItem item) {
        if (item == null) {
            return;
        }
        String identity = item.identity();
        List<MediaItem> current = list(key);
        for (int i = 0; i < current.size(); i++) {
            if (identity.length() > 0 && identity.equals(current.get(i).identity())) {
                current.remove(i);
                save(key, current);
                return;
            }
        }
    }

    public void clear(String key) {
        save(key, new ArrayList<MediaItem>());
    }

    /* -------------------------------------------------------------- recent */

    public List<MediaItem> recent(int max) {
        List<MediaItem> all = list(RECENT);
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    public void addRecent(MediaItem item) {
        if (item == null || !item.isPlayable()) {
            return;
        }
        String identity = item.identity();
        List<MediaItem> current = list(RECENT);
        for (int i = current.size() - 1; i >= 0; i--) {
            if (identity.equals(current.get(i).identity())) {
                current.remove(i);
            }
        }
        current.add(0, item);
        while (current.size() > MAX_RECENT) {
            current.remove(current.size() - 1);
        }
        save(RECENT, current);
    }

    /* ------------------------------------------------------------ playlists */

    /** Playlists stored locally; their {@code id} is {@code "local:<uuid>"}. */
    public List<MediaItem> playlists() {
        List<MediaItem> out = new ArrayList<>();
        for (MediaItem item : list(PLAYLISTS)) {
            if (item.isLocalPlaylist()) {
                out.add(item);
            }
        }
        return out;
    }

    public MediaItem createPlaylist(String name, MediaItem firstTrack) {
        String title = name == null ? "" : name.trim();
        if (title.length() == 0) {
            return null;
        }
        MediaItem playlist = new MediaItem();
        playlist.title = title;
        playlist.id = MediaItem.LOCAL_PLAYLIST_PREFIX + UUID.randomUUID().toString();
        playlist.kind = MediaItem.KIND_PLAYLIST;
        List<MediaItem> index = list(PLAYLISTS);
        index.add(0, playlist);
        while (index.size() > MAX_LIST) {
            index.remove(index.size() - 1);
        }
        save(PLAYLISTS, index);
        List<MediaItem> tracks = new ArrayList<>();
        if (firstTrack != null) {
            tracks.add(firstTrack);
        }
        save(tracksKey(playlist.id), tracks);
        return playlist;
    }

    public boolean addToPlaylist(String playlistId, MediaItem track) {
        if (playlistId == null || track == null) {
            return false;
        }
        String key = tracksKey(playlistId);
        List<MediaItem> tracks = list(key);
        for (MediaItem existing : tracks) {
            if (track.identity().equals(existing.identity())) {
                return false;
            }
        }
        tracks.add(track);
        while (tracks.size() > MAX_LIST) {
            tracks.remove(tracks.size() - 1);
        }
        save(key, tracks);
        return true;
    }

    public boolean removeFromPlaylist(String playlistId, MediaItem track) {
        if (playlistId == null || track == null) {
            return false;
        }
        String key = tracksKey(playlistId);
        List<MediaItem> tracks = list(key);
        for (int i = 0; i < tracks.size(); i++) {
            if (track.identity().equals(tracks.get(i).identity())) {
                tracks.remove(i);
                save(key, tracks);
                return true;
            }
        }
        return false;
    }

    public List<MediaItem> playlistTracks(String playlistId) {
        return list(tracksKey(playlistId));
    }

    public void deletePlaylist(MediaItem playlist) {
        if (playlist == null || !playlist.isLocalPlaylist()) {
            return;
        }
        prefs.edit().remove(tracksKey(playlist.id)).apply();
        List<MediaItem> index = list(PLAYLISTS);
        for (int i = 0; i < index.size(); i++) {
            if (playlist.id.equals(index.get(i).id)) {
                index.remove(i);
                break;
            }
        }
        save(PLAYLISTS, index);
    }

    public static String tracksKey(String playlistId) {
        return "playlist_" + (playlistId == null ? "" : playlistId.replace(MediaItem.LOCAL_PLAYLIST_PREFIX, ""));
    }

    /* --------------------------------------------------------------- helpers */

    private JSONArray readArray(String key) {
        String raw = prefs.getString(key, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }
}
