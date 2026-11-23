package com.soniq.base.ui;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.util.Locale;

/**
 * Layout and type decisions for phones, tablets, foldables and large-font users.
 *
 * <p>The framework draws with {@code dp}/{@code sp}; this class answers the
 * questions a screen actually asks: how wide can content get before it looks
 * absurd, how many columns does this device want, and should the tab bar move to
 * the side? Pure arithmetic, so the rules are deterministic and testable.
 *
 * <p>Host apps that want a fully bespoke tablet layout replace the screens
 * instead of tuning these numbers — see {@code AGENT.md}.
 */
public final class AdaptiveUi {

    /** Content stops widening here; past it text lines get unreadable. */
    public static final int MAX_CONTENT_DP = 720;

    /** Width at which a phone layout becomes a two-pane / side-nav layout. */
    public static final int TABLET_DP = 600;

    /** Below this the mini player drops a control instead of shrinking touch targets. */
    public static final int NARROW_DP = 360;

    private AdaptiveUi() {
    }

    /** Usable width in dp, from the current configuration. */
    public static int widthDp(Context context) {
        if (context == null) {
            return NARROW_DP;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.round(metrics.widthPixels / metrics.density);
    }

    /**
     * The width content should occupy: full width on phones, capped and centred
     * on tablets so a 10" screen does not stretch one column of text to 1000 dp.
     */
    public static int contentWidthDp(Context context) {
        return Math.min(widthDp(context), MAX_CONTENT_DP);
    }

    /** True when the device is wide enough for a two-pane / side-navigation layout. */
    public static boolean isTablet(Context context) {
        return widthDp(context) >= TABLET_DP;
    }

    /** True on phones narrow enough that a row must drop optional content. */
    public static boolean isNarrow(Context context) {
        return widthDp(context) < NARROW_DP;
    }

    /**
     * Columns for a grid of cards/tiles: 1 on phones, 2–3 on tablets, and never
     * so many that a single tile is under ~160 dp.
     */
    public static int gridColumns(Context context) {
        int usable = contentWidthDp(context) - 32;
        int columns = Math.max(1, usable / 160);
        return Math.min(columns, isTablet(context) ? 4 : 2);
    }

    /**
     * Largest horizontal margin the screen should use, keeping content within
     * {@link #MAX_CONTENT_DP} and centred.
     */
    public static int horizontalMargin(Context context) {
        int width = widthDp(context);
        int capped = contentWidthDp(context);
        return width <= capped ? 0 : (width - capped) / 2;
    }

    /**
     * {@code sp} for a base size, scaled for the user's font setting but capped
     * so a 2× font does not push a title off screen. Returns px via
     * {@link TypedValue}.
     */
    public static float scaledTextSize(Context context, float sp) {
        if (context == null) {
            return sp;
        }
        float scaled = context.getResources().getDisplayMetrics().scaledDensity
                / context.getResources().getDisplayMetrics().density * sp;
        // Cap at 1.6×: past that the framework layout (not the font) needs redesigning.
        return Math.min(scaled, sp * 1.6f);
    }
}
