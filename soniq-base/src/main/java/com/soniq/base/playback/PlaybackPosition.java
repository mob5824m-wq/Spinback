package com.soniq.base.playback;

/**
 * Smooths the position reported by {@code MediaPlayer.getCurrentPosition()}
 * with a wall-clock estimate for the moments the player has nothing useful to
 * say — {@code 0} right after start, {@code -1} when unavailable, a stale
 * {@code 0} on some devices, or the pre-seek value while a seek is in flight —
 * so the seek bar never freezes at 0:00 or jumps backwards.
 *
 * <p>Ported in spirit from upstream Kobo-Arc-7-freedom commit 6e7b566 ("YouTube
 * Music seek bar stuck — add wall-clock position fallback"), where Android 4.2
 * Stagefright reported a stuck position for proxied streams. This version is
 * pure Java with an injected monotonic clock so it is unit-tested on the JVM.
 *
 * <p>Rules:
 * <ul>
 *   <li>A positive native reading is authoritative and re-anchors the estimate —
 *       except during the short window after a seek, when only a reading near
 *       the seek target is trusted (a player still reporting its old position
 *       must not undo the seek). After {@link #SEEK_GRACE_MS} reality wins.</li>
 *   <li>While playing, zero / unavailable readings are ignored and the estimate
 *       advances from the anchor at real-time speed.</li>
 *   <li>Anchors only ever move on a positive reading, so a stale zero never
 *       drags the timeline back.</li>
 *   <li>Pausing freezes the estimate; resuming continues from it.</li>
 *   <li>The result is clamped to the known duration.</li>
 * </ul>
 */
public final class PlaybackPosition {

    /** A native reading within this distance of the seek target confirms the seek. */
    static final long SEEK_TOLERANCE_MS = 1500L;
    /** After this long, trust whatever the player reports even if it is far from the target. */
    static final long SEEK_GRACE_MS = 5000L;

    private int anchorMs;
    private long anchorAtMs;
    private boolean playing;
    private boolean seekPending;
    private int seekTargetMs;
    private long seekAtMs;

    /** New track: back to zero, nothing pending. */
    public void reset() {
        anchorMs = 0;
        anchorAtMs = 0L;
        playing = false;
        seekPending = false;
        seekTargetMs = 0;
        seekAtMs = 0L;
    }

    /** Explicitly (re)anchors at a known position, e.g. on start or resume. */
    public void anchor(int positionMs, long nowMs) {
        anchorMs = Math.max(0, positionMs);
        anchorAtMs = nowMs;
    }

    /**
     * Records a seek that was just issued: the UI jumps to {@code targetMs}
     * immediately and stale pre-seek readings are ignored until the player
     * confirms the new position.
     */
    public void seekTo(int targetMs, long nowMs) {
        anchor(targetMs, nowMs);
        seekPending = true;
        seekTargetMs = anchorMs;
        seekAtMs = nowMs;
    }

    /** Records a play / pause transition at the instant it happens. */
    public void setPlaying(boolean isPlaying, long nowMs) {
        if (playing == isPlaying) {
            return;
        }
        if (!isPlaying) {
            anchorMs = estimate(nowMs);
        }
        playing = isPlaying;
        anchorAtMs = nowMs;
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
        if (nativeMs > 0) {
            if (!seekPending) {
                anchor(nativeMs, nowMs);
            } else {
                boolean nearTarget = Math.abs(nativeMs - seekTargetMs) <= SEEK_TOLERANCE_MS;
                boolean graceOver = nowMs - seekAtMs >= SEEK_GRACE_MS;
                if (nearTarget || graceOver) {
                    anchor(nativeMs, nowMs);
                    seekPending = false;
                }
                // otherwise: still the pre-seek reading — keep the estimate from the target
            }
        }
        int result = playing ? estimate(nowMs) : anchorMs;
        if (durationMs > 0 && result > durationMs) {
            result = durationMs;
        }
        return result;
    }

    private int estimate(long nowMs) {
        long advanced = Math.max(0L, nowMs - anchorAtMs);
        return (int) Math.min(Integer.MAX_VALUE, (long) anchorMs + advanced);
    }
}
