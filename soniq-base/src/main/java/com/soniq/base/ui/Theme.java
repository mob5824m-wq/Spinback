package com.soniq.base.ui;

import com.soniq.base.model.MediaItem;

/**
 * Palette and small formatting helpers for the dark, music-first UI.
 *
 * <p>Colours are non-final so an app can rebrand in {@code Application.onCreate()},
 * before any view is built: {@code Theme.customize(0xFF00A86B)}. Views read the
 * fields when they are constructed, so later changes only affect new views.
 */
public final class Theme {

    public static final int DEFAULT_ACCENT = 0xFFFF3B5C;
    public static final int DEFAULT_SECONDARY = 0xFF7C5CFF;

    public static int BG = 0xFF0B0B0F;
    public static int SURFACE = 0xFF15151B;
    public static int SURFACE_HIGH = 0xFF1E1E26;
    public static int OUTLINE = 0xFF2C2C36;
    public static int ACCENT = DEFAULT_ACCENT;
    /** Accent at ~20 % alpha for the active-row highlight; derived by {@link #customize}. */
    public static int ACCENT_SOFT = 0x33FF3B5C;
    public static int SECONDARY = DEFAULT_SECONDARY;
    public static int TEXT = 0xFFF5F5F7;
    public static int TEXT_DIM = 0xFF9A9AA6;
    public static int TEXT_FAINT = 0xFF6E6E7A;

    /** Optional presentation overrides set before constructing the UI. */
    public static boolean LIGHT_SYSTEM_BARS = false;
    public static String HOME_LABEL = "Home";
    public static String SEARCH_LABEL = "Search";
    public static String LIBRARY_LABEL = "Library";
    public static String HOME_TITLE = null;

    private Theme() {
    }

    /** Replaces the accent (and derives the soft highlight) and secondary colour. */
    public static void customize(int accent, int secondary) {
        ACCENT = accent;
        ACCENT_SOFT = withAlpha(accent, 0x33);
        SECONDARY = secondary;
    }

    /** Replaces only the accent colour. */
    public static void customize(int accent) {
        customize(accent, SECONDARY);
    }

    /** Restores the default dark palette. */
    public static void reset() {
        BG = 0xFF0B0B0F; SURFACE = 0xFF15151B; SURFACE_HIGH = 0xFF1E1E26;
        OUTLINE = 0xFF2C2C36; TEXT = 0xFFF5F5F7; TEXT_DIM = 0xFF9A9AA6;
        TEXT_FAINT = 0xFF6E6E7A; LIGHT_SYSTEM_BARS = false;
        HOME_LABEL = "Home"; SEARCH_LABEL = "Search"; LIBRARY_LABEL = "Library";
        HOME_TITLE = null;
        customize(DEFAULT_ACCENT, DEFAULT_SECONDARY);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** {@code "m:ss"} / {@code "h:mm:ss"} for a duration in milliseconds. */
    public static String formatDuration(long millis) {
        return MediaItem.formatDuration(millis);
    }
}
