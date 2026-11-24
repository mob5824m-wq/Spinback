package com.soniq.base.playback;

/**
 * Pure decisions behind crossfade, gapless playback and resume-where-you-left-off.
 *
 * <p>No Android types, so every rule here is unit-tested on the JVM. The
 * service owns the {@code MediaPlayer}s and asks this class <em>what</em> to do;
 * the actual fading and player swapping live in {@link PlaybackService}.
 *
 * <p>Scope note for host apps: this is the backbone. It implements a linear
 * volume crossfade and a prepared-next gapless hand-off with the platform
 * {@code MediaPlayer}. Apps that need equal-power curves, per-state overrides or
 * true sample-accurate gapless should read {@code docs/WIDGETS.md} and
 * {@code AGENT.md} ("what an app must finish").
 */
public final class PlaybackEngine {

    /** Crossfades longer than this are a gimmick and starve the audio callback. */
    public static final int MAX_CROSSFADE_SECONDS = 12;

    /** How often the service ramps volumes during a crossfade. */
    public static final int FADE_TICK_MS = 60;

    /** Resume is not offered this close to the start — it is noise, not a resume. */
    public static final int MIN_RESUME_MS = 20_000;

    /** Nor this close to the end: the track would just finish again. */
    public static final int END_GUARD_MS = 10_000;

    private int crossfadeSeconds;
    private boolean gaplessEnabled;
    private boolean resumeEnabled = true;
    private String bufferedIdentity = "";

    /** Clamps to {@code 0..MAX_CROSSFADE_SECONDS}; 0 disables crossfade. */
    public static int clampCrossfadeSeconds(int seconds) {
        return Math.max(0, Math.min(MAX_CROSSFADE_SECONDS, seconds));
    }

    public void setCrossfadeSeconds(int seconds) {
        crossfadeSeconds = clampCrossfadeSeconds(seconds);
    }

    public int crossfadeSeconds() {
        return crossfadeSeconds;
    }

    public boolean isCrossfadeEnabled() {
        return crossfadeSeconds > 0;
    }

    /** The fade length in milliseconds, or 0 when crossfade is off. */
    public int fadeMillis() {
        return crossfadeSeconds * 1000;
    }

    public void setGaplessEnabled(boolean enabled) {
        gaplessEnabled = enabled;
    }

    public boolean isGaplessEnabled() {
        return gaplessEnabled;
    }

    /** Both modes want the next track resolved and prepared early. */
    public boolean needsPrebuffer() {
        return gaplessEnabled || isCrossfadeEnabled();
    }

    public void setResumeEnabled(boolean enabled) {
        resumeEnabled = enabled;
    }

    public boolean isResumeEnabled() {
        return resumeEnabled;
    }

    /**
     * True when the service should start resolving the next track: a mode wants
     * it, there is a current item, and that item is not already buffered.
     */
    public boolean shouldPrebuffer(String currentIdentity) {
        if (!needsPrebuffer() || currentIdentity == null || currentIdentity.length() == 0) {
            return false;
        }
        return !currentIdentity.equals(bufferedIdentity);
    }

    /** True when the prepared player at the given identity can take over now. */
    public boolean isBufferReady(String identity, boolean prepared) {
        return prepared && identity != null && identity.length() > 0
                && identity.equals(bufferedIdentity);
    }

    public void onBufferPrepared(String identity) {
        bufferedIdentity = identity == null ? "" : identity;
    }

    public void onBufferReleased() {
        bufferedIdentity = "";
    }

    /**
     * Volume for the track that is fading out. {@code remainingMs} is how much
     * of the outgoing track is left. Returns 1 until the fade window opens.
     */
    public static float fadeOutVolume(long remainingMs, int fadeMs) {
        if (fadeMs <= 0) {
            return 1f;
        }
        if (remainingMs <= 0L) {
            return 0f;
        }
        if (remainingMs >= fadeMs) {
            return 1f;
        }
        return (float) remainingMs / (float) fadeMs;
    }

    /** Volume for the track that is fading in, {@code elapsedMs} after its start. */
    public static float fadeInVolume(long elapsedMs, int fadeMs) {
        if (fadeMs <= 0) {
            return 1f;
        }
        if (elapsedMs <= 0L) {
            return 0f;
        }
        if (elapsedMs >= fadeMs) {
            return 1f;
        }
        return (float) elapsedMs / (float) fadeMs;
    }

    /**
     * The point where the next track must start: {@code duration - fadeMillis},
     * or -1 when crossfade cannot apply (no duration, live stream, too short).
     */
    public int crossfadeStartPosition(int durationMs) {
        if (!isCrossfadeEnabled() || durationMs <= 0) {
            return -1;
        }
        int fadeMs = fadeMillis();
        if (durationMs <= fadeMs + 1000) {
            return -1;
        }
        return durationMs - fadeMs;
    }
}
