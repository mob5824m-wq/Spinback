package com.soniq.base.data;

/** One LRC line. {@code timeMs < 0} means the line has no timestamp. */
public final class LyricLine {
    public final long timeMs;
    public final String text;

    public LyricLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text == null ? "" : text;
    }

    public boolean isTimed() {
        return timeMs >= 0L;
    }
}
