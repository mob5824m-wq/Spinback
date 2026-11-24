package com.soniq.base.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses LRC text (synced) or plain lyrics into timestamped lines. */
public final class LyricsParser {

    private static final Pattern TIME_TAG = Pattern.compile(
            "^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)$");

    private LyricsParser() {
    }

    public static List<LyricLine> parse(String raw) {
        List<LyricLine> lines = new ArrayList<>();
        if (raw == null || raw.trim().length() == 0) {
            return lines;
        }
        String[] rows = raw.split("\\r?\\n");
        for (String row : rows) {
            String trimmed = row.trim();
            if (trimmed.length() == 0) {
                continue;
            }
            Matcher matcher = TIME_TAG.matcher(trimmed);
            if (matcher.find()) {
                try {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    String fraction = matcher.group(3) == null ? "0" : matcher.group(3);
                    while (fraction.length() < 3) {
                        fraction = fraction + "0";
                    }
                    long millis = minutes * 60000L + seconds * 1000L
                            + Long.parseLong(fraction.substring(0, 3));
                    String text = matcher.group(4) == null ? "" : matcher.group(4).trim();
                    if (text.length() > 0) {
                        lines.add(new LyricLine(millis, text));
                    }
                } catch (Exception ignored) {
                    // Malformed timestamp: fall through as an untimed line.
                    if (trimmed.length() > 0) {
                        lines.add(new LyricLine(-1L, trimmed));
                    }
                }
            } else {
                lines.add(new LyricLine(-1L, trimmed));
            }
        }
        return lines;
    }

    /** Index of the line active at {@code positionMs}, or -1 when none is timed. */
    public static int activeIndex(List<LyricLine> lines, long positionMs) {
        int active = -1;
        if (lines == null) {
            return active;
        }
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            if (line.isTimed() && positionMs >= line.timeMs) {
                active = i;
            }
        }
        return active;
    }

    public static boolean isSynced(List<LyricLine> lines) {
        if (lines == null) {
            return false;
        }
        for (LyricLine line : lines) {
            if (line.isTimed()) {
                return true;
            }
        }
        return false;
    }
}
