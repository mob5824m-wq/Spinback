package com.soniq.base.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.soniq.base.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Search history: what the listener typed, newest first, with the number of
 * times they typed it so frequent searches can rank above one-offs.
 *
 * <p>Pure text storage with a hard cap. No network, no account. Clearing the
 * history is one call, and a cleared history stays cleared: results are never
 * silently re-added, only explicit {@link #record(String)} calls count.
 */
public final class SearchHistory {

    public static final int MAX_ENTRIES = 12;
    public static final int MAX_QUERY_LENGTH = 120;

    private static final String PREFS = "soniq_search_history";
    private static final String KEY_ENTRIES = "entries";
    private static final char FIELD = '\u0001';
    private static final char ROW = '\u0002';

    private final SharedPreferences prefs;

    public SearchHistory(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** One stored query and how many times it was searched. */
    public static final class Entry {
        public final String query;
        public final int count;
        public final long lastUsed;

        public Entry(String query, int count, long lastUsed) {
            this.query = query == null ? "" : query;
            this.count = Math.max(1, count);
            this.lastUsed = Math.max(0L, lastUsed);
        }
    }

    /** Records a search. Blank or absurdly long queries are ignored. */
    public void record(String rawQuery, long now) {
        String query = normalize(rawQuery);
        if (query.length() == 0) {
            return;
        }
        List<Entry> entries = entries();
        List<Entry> next = new ArrayList<>();
        int count = 1;
        for (Entry entry : entries) {
            if (entry.query.equalsIgnoreCase(query)) {
                count = entry.count + 1;
            } else {
                next.add(entry);
            }
        }
        next.add(0, new Entry(query, count, now));
        while (next.size() > MAX_ENTRIES) {
            next.remove(next.size() - 1);
        }
        write(next);
    }

    /** Entries newest first. */
    public List<Entry> entries() {
        String raw = prefs.getString(KEY_ENTRIES, "");
        return decode(raw);
    }

    /** Just the queries, newest first. */
    public List<String> queries() {
        List<String> out = new ArrayList<>();
        for (Entry entry : entries()) {
            out.add(entry.query);
        }
        return out;
    }

    /**
     * Queries for the "recent" chips: newest first, but a query searched many
     * times only once. Keeps the chip row short and truthful.
     */
    public List<String> recentQueries(int max) {
        List<String> out = new ArrayList<>();
        for (Entry entry : entries()) {
            if (out.size() >= Math.max(0, max)) {
                break;
            }
            out.add(entry.query);
        }
        return out;
    }

    public void remove(String query) {
        String normalized = normalize(query);
        List<Entry> next = new ArrayList<>();
        for (Entry entry : entries()) {
            if (!entry.query.equalsIgnoreCase(normalized)) {
                next.add(entry);
            }
        }
        write(next);
    }

    /** Wipes everything; the caller shows the confirmation. */
    public void clear() {
        prefs.edit().remove(KEY_ENTRIES).apply();
    }

    public boolean isEmpty() {
        return prefs.getString(KEY_ENTRIES, "").length() == 0;
    }

    public int size() {
        return entries().size();
    }

    /** Remembers which result the listener opened, so search can be resumed. */
    public void recordLastResult(MediaItem item) {
        if (item == null || item.identity().length() == 0) {
            return;
        }
        prefs.edit().putString("last_result", item.toJson().toString()).apply();
    }

    public MediaItem lastResult() {
        String raw = prefs.getString("last_result", "");
        if (raw.length() == 0) {
            return null;
        }
        try {
            return MediaItem.fromJson(new org.json.JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    public void clearLastResult() {
        prefs.edit().remove("last_result").apply();
    }

    /* ------------------------------------------------------------- encoding */

    static String normalize(String query) {
        if (query == null) {
            return "";
        }
        String trimmed = query.replace('\n', ' ').replace('\r', ' ').trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH).trim();
        }
        return trimmed;
    }

    static String encode(List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (out.length() > 0) {
                out.append(ROW);
            }
            out.append(entry.query).append(FIELD)
                    .append(entry.count).append(FIELD)
                    .append(entry.lastUsed);
        }
        return out.toString();
    }

    static List<Entry> decode(String raw) {
        List<Entry> out = new ArrayList<>();
        if (raw == null || raw.length() == 0) {
            return out;
        }
        String[] rows = raw.split(String.valueOf(ROW));
        for (String row : rows) {
            String[] fields = row.split(String.valueOf(FIELD));
            if (fields.length != 3) {
                continue;
            }
            try {
                out.add(new Entry(fields[0], Integer.parseInt(fields[1]),
                        Long.parseLong(fields[2])));
            } catch (NumberFormatException ignored) {
                // Skip a corrupt row rather than losing the whole history.
            }
        }
        return out;
    }

    private void write(List<Entry> entries) {
        prefs.edit().putString(KEY_ENTRIES, encode(entries)).apply();
    }
}
