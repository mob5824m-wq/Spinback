package com.soniq.base.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of a lyrics lookup that distinguishes the two states users care
 * about: the service has no match, and the track genuinely has no words.
 *
 * <p>Before this class the UI showed the same "nothing here" for a network
 * failure, a missing song and an instrumental. Each now has its own
 * {@link Status} and its own sentence.
 */
public final class LyricsResult {

    public enum Status {
        /** We found lyrics (word timings, or plain lines when unsynced). */
        FOUND,
        /** The provider says this track is an instrumental. */
        INSTRUMENTAL,
        /** The service answered, but has nothing for this track. */
        NOT_FOUND,
        /** The request failed — network, rate limit or parse error. */
        UNAVAILABLE
    }

    public final Status status;
    public final List<LyricLine> lines;
    public final String message;
    /** True when {@link #lines} carry word timings the player can follow. */
    public final boolean synced;

    private LyricsResult(Status status, List<LyricLine> lines, boolean synced, String message) {
        this.status = status;
        this.lines = Collections.unmodifiableList(
                lines == null ? new ArrayList<LyricLine>() : new ArrayList<>(lines));
        this.synced = synced;
        this.message = message == null ? "" : message;
    }

    public static LyricsResult found(List<LyricLine> lines, boolean synced) {
        return new LyricsResult(Status.FOUND, lines, synced, "");
    }

    public static LyricsResult instrumental() {
        return new LyricsResult(Status.INSTRUMENTAL, null, false,
                "Instrumental · no lyrics for this track");
    }

    public static LyricsResult notFound() {
        return new LyricsResult(Status.NOT_FOUND, null, false,
                "No lyrics found for this track");
    }

    public static LyricsResult unavailable(String message) {
        String clean = message == null ? "" : message.trim();
        return new LyricsResult(Status.UNAVAILABLE, null, false,
                clean.length() == 0 ? "Lyrics unavailable" : clean);
    }

    public boolean hasLyrics() {
        return status == Status.FOUND && !lines.isEmpty();
    }

    /** True when retrying the same request could plausibly succeed. */
    public boolean isRetryable() {
        return status == Status.UNAVAILABLE;
    }

    /** The sentence to show under the lyrics panel. */
    public String message() {
        return message;
    }
}
