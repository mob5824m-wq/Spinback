package com.soniq.base.playback;

import com.soniq.base.model.MediaItem;

/**
 * Where the listener left off: one track, one position, one moment in time.
 *
 * <p>Pure data with an explicit text encoding, so "was this resume offered
 * honestly?" is testable without Android. The service writes it through
 * {@link PlaybackSession}; the Home screen reads it back.
 */
public final class ResumePoint {

    /** A resume older than this is stale — the listener has moved on. */
    public static final int MAX_AGE_DAYS = 14;

    private static final char SEPARATOR = '\u0001';

    public final String title;
    public final String author;
    public final String identity;
    public final int positionMs;
    public final int durationMs;
    public final long savedAt;

    public ResumePoint(String identity, String title, String author,
                       int positionMs, int durationMs, long savedAt) {
        this.identity = identity == null ? "" : identity;
        this.title = title == null ? "" : title;
        this.author = author == null ? "" : author;
        this.positionMs = Math.max(0, positionMs);
        this.durationMs = Math.max(0, durationMs);
        this.savedAt = Math.max(0L, savedAt);
    }

    /** Builds a point for the item, or null when there is nothing worth resuming. */
    public static ResumePoint of(MediaItem item, int positionMs, int durationMs, long now) {
        if (item == null || item.identity().length() == 0) {
            return null;
        }
        ResumePoint point = new ResumePoint(item.identity(), item.title, item.author,
                positionMs, durationMs, now);
        return point.isWorthOffering() ? point : null;
    }

    /** True when the saved position is far enough in, and not at the very end. */
    public boolean isWorthOffering() {
        if (positionMs < MIN_MS) {
            return false;
        }
        return durationMs <= 0 || positionMs < durationMs - END_GUARD;
    }

    /** True when the point should still be shown, given "now". */
    public boolean isFresh(long now) {
        if (savedAt <= 0L || now < savedAt) {
            return false;
        }
        return now - savedAt <= MAX_AGE_DAYS * 24L * 60L * 60L * 1000L;
    }

    public boolean isUsable(long now) {
        return isWorthOffering() && isFresh(now);
    }

    /** Human summary: "3:42 of 5:01". Blank when the duration is unknown. */
    public String positionLabel() {
        if (positionMs <= 0) {
            return "";
        }
        String position = MediaItem.formatDuration(positionMs);
        return durationMs > 0 ? position + " of " + MediaItem.formatDuration(durationMs)
                : position;
    }

    /** One-line label for the Home card. */
    public String summary() {
        String position = positionLabel();
        return author.length() > 0
                ? author + (position.length() > 0 ? " · " + position : "")
                : position;
    }

    /** Encodes to a single preferences-safe string. */
    public String encode() {
        StringBuilder out = new StringBuilder();
        out.append(escape(identity)).append(SEPARATOR)
                .append(escape(title)).append(SEPARATOR)
                .append(escape(author)).append(SEPARATOR)
                .append(positionMs).append(SEPARATOR)
                .append(durationMs).append(SEPARATOR)
                .append(savedAt);
        return out.toString();
    }

    /** Decodes {@link #encode()}; returns null for blank or malformed input. */
    public static ResumePoint decode(String encoded) {
        if (encoded == null || encoded.length() == 0) {
            return null;
        }
        String[] parts = split(encoded);
        if (parts == null || parts.length != 6) {
            return null;
        }
        try {
            int position = Integer.parseInt(parts[3]);
            int duration = Integer.parseInt(parts[4]);
            long savedAt = Long.parseLong(parts[5]);
            if (parts[0].length() == 0) {
                return null;
            }
            return new ResumePoint(unescape(parts[0]), unescape(parts[1]),
                    unescape(parts[2]), position, duration, savedAt);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /**
     * Backslash-escapes the separator and the backslash itself, so a title that
     * literally contains U+0001 still round-trips instead of corrupting the row.
     */
    static String escape(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == SEPARATOR || c == '\\') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    static String unescape(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                out.append(value.charAt(++i));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Splits on unescaped separators, keeping empty fields (titles can be
     * blank). A backslash escapes the next character, so a title containing the
     * separator survives.
     */
    private static String[] split(String encoded) {
        String[] out = new String[6];
        int field = 0;
        int from = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '\\') {
                i++; // skip the escaped character
                continue;
            }
            if (c == SEPARATOR) {
                if (field >= 5) {
                    return null;
                }
                out[field++] = encoded.substring(from, i);
                from = i + 1;
            }
        }
        if (field != 5) {
            return null;
        }
        out[5] = encoded.substring(from);
        return out;
    }

    private static final int MIN_MS = PlaybackEngine.MIN_RESUME_MS;
    private static final int END_GUARD = PlaybackEngine.END_GUARD_MS;
}
