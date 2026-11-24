package com.soniq.base.playback;

import android.content.Context;
import android.content.SharedPreferences;

import com.soniq.base.model.MediaItem;

/**
 * Persists the queue so a cold start can offer "Continue listening" instead of
 * an empty player, and stores the single resume point the Home screen shows.
 *
 * <p>Deliberately small: the whole queue up to a cap, plus one resume point.
 * Written on pause/track change, cleared when the queue finishes.
 */
public final class PlaybackSession {

    /** Enough history for a meaningful resume without bloating preferences. */
    public static final int MAX_QUEUE = 50;

    private static final String PREFS = "soniq_playback_session";
    private static final String KEY_QUEUE = "queue";
    private static final String KEY_INDEX = "index";
    private static final String KEY_RESUME = "resume";
    private static final String KEY_POSITION = "position";

    private final SharedPreferences prefs;

    public PlaybackSession(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Saves the queue, the current index and the position inside it. */
    public void saveQueue(java.util.List<MediaItem> items, int index, int positionMs) {
        if (items == null || items.isEmpty()) {
            clearQueue();
            return;
        }
        int from = 0;
        int to = Math.min(items.size(), MAX_QUEUE);
        int current = index;
        if (index >= to) {
            // Keep the playing track inside the window instead of dropping it.
            from = Math.max(0, index - MAX_QUEUE + 1);
            to = Math.min(items.size(), from + MAX_QUEUE);
            current = index - from;
        }
        org.json.JSONArray array = new org.json.JSONArray();
        for (int i = from; i < to; i++) {
            MediaItem item = items.get(i);
            if (item != null) {
                array.put(item.toJson());
            }
        }
        prefs.edit()
                .putString(KEY_QUEUE, array.toString())
                .putInt(KEY_INDEX, Math.max(0, current))
                .putInt(KEY_POSITION, Math.max(0, positionMs))
                .apply();
    }

    public java.util.List<MediaItem> queue() {
        String raw = prefs.getString(KEY_QUEUE, "");
        if (raw.length() == 0) {
            return new java.util.ArrayList<>();
        }
        try {
            return MediaItem.fromJsonArray(new org.json.JSONArray(raw));
        } catch (Exception ignored) {
            return new java.util.ArrayList<>();
        }
    }

    public int index() {
        return Math.max(0, prefs.getInt(KEY_INDEX, 0));
    }

    public int positionMs() {
        return Math.max(0, prefs.getInt(KEY_POSITION, 0));
    }

    public boolean hasQueue() {
        return prefs.getString(KEY_QUEUE, "").length() > 0;
    }

    public void clearQueue() {
        prefs.edit().remove(KEY_QUEUE).remove(KEY_INDEX).remove(KEY_POSITION).apply();
    }

    /** Stores where playback should continue, or clears it when null. */
    public void saveResumePoint(ResumePoint point) {
        if (point == null) {
            prefs.edit().remove(KEY_RESUME).apply();
        } else {
            prefs.edit().putString(KEY_RESUME, point.encode()).apply();
        }
    }

    public ResumePoint resumePoint() {
        return ResumePoint.decode(prefs.getString(KEY_RESUME, ""));
    }

    public void clearResumePoint() {
        prefs.edit().remove(KEY_RESUME).apply();
    }
}
