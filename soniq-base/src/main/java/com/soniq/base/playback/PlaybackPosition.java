package com.soniq.base.playback;

/**
 * Smooths the position reported by {@code MediaPlayer.getCurrentPosition()}
 * with a wall-clock estimate for the moments the player has nothing useful to
 * say — {@code 0} right after start, {@code -1} when unavailable, a reading
 * frozen on one sample (some players stick at {@code 0}, others at a small
 * positive buffer sample such as {@code 23} ms), a stale {@code 0} on some
 * devices, or the pre-seek value while a seek is in flight — so the seek bar
 * never freezes at 0:00 or jumps backwards.
 *
 * <p>Ported in spirit from upstream Kobo-Arc-7-freedom commits 6e7b566
 * ("YouTube Music seek bar stuck — add wall-clock position fallback") and
 * eb270d0 ("Fix YT Music seek/elapsed time and disabled alarms firing"),
 * where Android 4.2 Stagefright reported stuck positions for proxied streams.
 * This version is pure Java with an injected monotonic clock so it is
 * unit-tested on the JVM.
 *
 * <p>Rules:
 * <ul>
 *   <li>Only a <em>changed</em> native reading is authoritative and re-anchors
 *       the estimate. A reading frozen on one sample (whatever that sample is)
 *       is extrapolated past instead, so a player stuck at 0 ms or at 23 ms
 *       cannot freeze the timeline.</li>
 *   <li>Except during the short window after a seek, when only a reading near
 *       the seek target is trusted (a player still reporting its old position
 *       must not undo the seek). After {@link #SEEK_GRACE_MS} a <em>changed</em>
 *       reading wins even if it is far from the target — but a reading frozen
 *       on one sample never cancels the seek.</li>
 *   <li>A stale {@code 0} (a zero after positive samples) is ignored entirely:
 *       it neither drags the timeline back nor confirms a nonzero seek.</li>
 *   <li>While playing, zero / unavailable / unchanged readings are ignored and
 *       the estimate advances from the anchor at real-time speed.</li>
 *   <li>Pausing or rebuffering freezes the estimate; resuming continues from
 *       it.</li>
 *   <li>The result is clamped to the known duration.</li>
 * </ul>
 */
public final class PlaybackPosition {

    /** A native reading within this distance of the seek target confirms the seek. */
    static final long SEEK_TOLERANCE_MS = 1500L;
    /** After this long, a changing reading wins even if it is far from the seek target. */
    static final long SEEK_GRACE_MS = 5000L;

    private int anchorMs;
    private long anchorAtMs;
    private boolean playing;
    private boolean buffering;
    private boolean seekPending;
    private int seekTargetMs;
    private long seekAtMs;
    /** Last native sample seen, or -1 when there is none yet. */
    private int lastNativeMs = -1;

    /** New track: back to zero, nothing pending, no native baseline. */
    public void reset() {
        anchorMs = 0;
        anchorAtMs = 0L;
        playing = false;
        buffering = false;
        seekPending = false;
        seekTargetMs = 0;
        seekAtMs = 0L;
        lastNativeMs = -1;
    }

    /**
     * Explicitly (re)anchors at a known position, e.g. on start or resume. The
     * value becomes the native baseline, so a later reading equal to it is
     * treated as a stuck sample and extrapolated past — this is the seeding
     * step of upstream's {@code resetPositionTracking}.
     */
    public void anchor(int positionMs, long nowMs) {
        place(positionMs, nowMs);
        lastNativeMs = anchorMs;
    }

    /**
     * Records a seek that was just issued: the UI jumps to {@code targetMs}
     * immediately and stale pre-seek readings are ignored until the player
     * confirms the new position. The native baseline is left alone so a player
     * frozen on its pre-seek sample can neither confirm nor cancel the seek.
     */
    public void seekTo(int targetMs, long nowMs) {
        place(targetMs, nowMs);
        seekPending = true;
        seekTargetMs = anchorMs;
        seekAtMs = nowMs;
    }

    /** Records a play / pause transition at the instant it happens. */
    public void setPlaying(boolean isPlaying, long nowMs) {
        transition(isPlaying, buffering, nowMs);
    }

    /**
     * Records a rebuffering stall at the instant it starts or ends. The
     * estimate freezes while the player is stalled, even if it is nominally
     * playing, and continues afterwards from where the audio really is.
     */
    public void setBuffering(boolean isBuffering, long nowMs) {
        transition(playing, isBuffering, nowMs);
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isSeekPending() {
        return seekPending;
    }

    /**
     * @param nativeMs    position reported by the player, or -1 when unavailable
     * @param nowMs       monotonic now, from the caller's clock
     * @param durationMs  known duration, or 0 when unknown
     * @return the position to show to the user
     */
    public int update(int nativeMs, long nowMs, int durationMs) {
        if (nativeMs >= 0) {
            boolean changed = nativeMs != lastNativeMs;
            if (seekPending) {
                long low = Math.max(0L, (long) seekTargetMs - SEEK_TOLERANCE_MS);
                long high = Math.min((long) Integer.MAX_VALUE,
                        Math.max((long) seekTargetMs, current(nowMs)) + SEEK_TOLERANCE_MS);
                // A stale zero must not confirm or undo a nonzero seek on the
                // affected ROMs; a zero does confirm a seek to zero.
                boolean nearTarget = nativeMs >= low && nativeMs <= high
                        && (nativeMs != 0 || seekTargetMs == 0);
                if (nearTarget && (changed || nativeMs == seekTargetMs)) {
                    place(nativeMs, nowMs);
                    seekPending = false;
                } else if (lastNativeMs >= 0 && changed && nativeMs > 0
                        && nowMs - seekAtMs >= SEEK_GRACE_MS) {
                    // A player that keeps reporting a *different* position may
                    // have rejected the seek. Eventually show reality rather
                    // than a false jump — but a reading frozen on one sample
                    // never cancels the seek.
                    place(nativeMs, nowMs);
                    seekPending = false;
                }
                lastNativeMs = nativeMs;
            } else if (!(nativeMs == 0 && lastNativeMs > 0)) {
                // A changing reading is authoritative; a frozen one is
                // extrapolated past. Some broken players intermittently return
                // zero after valid samples — those must not drag the timeline
                // back to the beginning.
                if (changed) {
                    place(nativeMs, nowMs);
                }
                lastNativeMs = nativeMs;
            }
        }
        int result = current(nowMs);
        if (durationMs > 0 && result > durationMs) {
            result = durationMs;
        }
        return result;
    }

    private void place(int positionMs, long nowMs) {
        anchorMs = Math.max(0, positionMs);
        anchorAtMs = nowMs;
    }

    /** Applies a play / pause / buffering transition at the instant it happens. */
    private void transition(boolean newPlaying, boolean newBuffering, long nowMs) {
        if (playing == newPlaying && buffering == newBuffering) {
            return;
        }
        boolean wasAdvancing = playing && !buffering;
        boolean nowAdvancing = newPlaying && !newBuffering;
        if (wasAdvancing) {
            // Freeze: fold the elapsed time into the anchor before the clock stops.
            anchorMs = (int) Math.min((long) Integer.MAX_VALUE,
                    (long) anchorMs + Math.max(0L, nowMs - anchorAtMs));
        }
        playing = newPlaying;
        buffering = newBuffering;
        if (wasAdvancing != nowAdvancing) {
            anchorAtMs = nowMs;
        }
    }

    /** The position right now: the anchored estimate while advancing, the frozen anchor otherwise. */
    private int current(long nowMs) {
        if (playing && !buffering) {
            long advanced = Math.max(0L, nowMs - anchorAtMs);
            return (int) Math.min((long) Integer.MAX_VALUE, (long) anchorMs + advanced);
        }
        return anchorMs;
    }
}
