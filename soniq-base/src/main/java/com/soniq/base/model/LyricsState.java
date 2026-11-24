package com.soniq.base.model;

/**
 * Provider-reported lyrics availability for a track. The framework never
 * guesses: it only distinguishes "the provider says this has no words" from
 * "we have not looked yet".
 */
public final class LyricsState {

    /** The provider did not say anything; a lookup decides. */
    public static final String UNKNOWN = "";
    /** The provider says this track has no vocals. */
    public static final String INSTRUMENTAL = "instrumental";
    /** The provider says lyrics exist but are not exposed to us. */
    public static final String EXTERNAL = "external";

    private LyricsState() {
    }

    public static String normalize(String raw) {
        if (INSTRUMENTAL.equals(raw) || EXTERNAL.equals(raw)) {
            return raw;
        }
        return UNKNOWN;
    }

    public static boolean isInstrumental(String raw) {
        return INSTRUMENTAL.equals(normalize(raw));
    }
}
