package com.soniq.base.data;

import com.soniq.base.model.MediaItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One ListenBrainz listen. Persisted as JSON so listens detected while offline
 * survive a process death and can be submitted later with listen_type=import.
 */
public final class Scrobble {

    /** Playback start time in unix seconds — what ListenBrainz expects. */
    public long listenedAtSec;
    public String artist = "";
    public String title = "";
    public String release = "";
    /** ListenBrainz music_service domain supplied by the provider, e.g. "music.youtube.com". */
    public String musicService = "";
    /** ListenBrainz origin_url supplied by the provider (where the track is available). */
    public String originUrl = "";
    public long durationMs;
    /** How long the user actually listened, for additional_info.duration_played_ms. */
    public long playedMs;

    public static Scrobble from(MediaItem item, long listenedAtSec, long durationMs, long playedMs,
                                String musicService, String originUrl) {
        Scrobble listen = new Scrobble();
        listen.listenedAtSec = listenedAtSec;
        listen.artist = ScrobblePolicy.clean(item.author);
        listen.title = ScrobblePolicy.clean(item.title);
        listen.release = ScrobblePolicy.clean(item.album);
        listen.musicService = musicService == null ? "" : musicService.trim();
        listen.originUrl = originUrl == null ? "" : originUrl.trim();
        listen.durationMs = Math.max(0L, durationMs);
        listen.playedMs = Math.max(0L, playedMs);
        return listen;
    }

    public boolean isSubmittable() {
        return artist.length() > 0 && title.length() > 0;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("at", listenedAtSec);
            o.put("ar", artist);
            o.put("ti", title);
            o.put("al", release);
            o.put("svc", musicService);
            o.put("url", originUrl);
            o.put("dur", durationMs);
            o.put("played", playedMs);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Scrobble fromJson(JSONObject o) {
        Scrobble listen = new Scrobble();
        if (o == null) {
            return listen;
        }
        listen.listenedAtSec = o.optLong("at", 0L);
        listen.artist = o.optString("ar", "");
        listen.title = o.optString("ti", "");
        listen.release = o.optString("al", "");
        listen.musicService = o.optString("svc", "");
        listen.originUrl = o.optString("url", "");
        listen.durationMs = o.optLong("dur", 0L);
        listen.playedMs = o.optLong("played", 0L);
        return listen;
    }

    public static JSONArray toJsonArray(List<Scrobble> listens) {
        JSONArray array = new JSONArray();
        for (Scrobble listen : listens) {
            if (listen != null && listen.isSubmittable()) {
                array.put(listen.toJson());
            }
        }
        return array;
    }

    public static List<Scrobble> fromJsonArray(JSONArray array) {
        List<Scrobble> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null) {
                Scrobble listen = fromJson(o);
                if (listen.isSubmittable()) {
                    out.add(listen);
                }
            }
        }
        return out;
    }
}
