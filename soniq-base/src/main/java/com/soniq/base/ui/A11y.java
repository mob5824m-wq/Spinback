package com.soniq.base.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Accessibility helpers shared by the framework screens.
 *
 * <p>Rules this file encodes, so every screen behaves the same:
 * <ul>
 *   <li>Every tappable thing has a spoken label; icons inside a labelled button
 *       are hidden from TalkBack so it does not read "play" twice.</li>
 *   <li>Text that is drawn inside a row is not announced separately — the row
 *       carries one sentence.</li>
 *   <li>Decorative artwork has a description that names the track, because it
 *       is the only non-text cue on the row.</li>
 *   <li>State (playing, liked, selected) is expressed with
 *       {@code setSelected}/{@code setContentDescription} transitions that
 *       TalkBack announces, not with colour alone.</li>
 * </ul>
 *
 * <p>Host apps must still run their own pass — see {@code AGENT.md}, "what an
 * app must finish".
 */
public final class A11y {

    private A11y() {
    }

    /** Marks a view as one announcement and labels it; no-op on null/blank. */
    public static <T extends View> T label(T view, CharSequence description) {
        if (view != null && description != null && description.length() > 0) {
            view.setContentDescription(description);
        }
        return view;
    }

    /**
     * Collapses a whole row into one focusable announcement ("Play <title>, by
     * <artist>, 3:42"). Children stop being individually announced, which is
     * the difference between a usable list and 5 swipes per song.
     */
    public static void singleAnnouncement(View row, CharSequence description) {
        if (row == null) {
            return;
        }
        row.setFocusable(true);
        row.setClickable(true);
        row.setContentDescription(description);
        int children = row instanceof ViewGroup ? ((ViewGroup) row).getChildCount() : 0;
        for (int i = 0; i < children; i++) {
            View child = ((ViewGroup) row).getChildAt(i);
            child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    /** Hides a purely decorative view (an icon drawn next to a label). */
    public static void decorative(View view) {
        if (view != null) {
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    /** Announces a state change without stealing focus (TalkBack reads it when asked). */
    public static void announceState(View view, CharSequence state) {
        if (view != null && state != null) {
            view.setContentDescription(state);
        }
    }

    /**
     * Makes a label repeatable: TalkBack reads the title, then the subtitle, in
     * one focus stop instead of two.
     */
    public static void mergeText(View container, TextView title, TextView subtitle) {
        if (title == null) {
            return;
        }
        String text = title.getText() == null ? "" : title.getText().toString();
        if (subtitle != null && subtitle.getText() != null && subtitle.getText().length() > 0) {
            text = text + ", " + subtitle.getText().toString();
        }
        if (container != null) {
            container.setContentDescription(text);
        }
    }

    /** A play/pause button's label, including what will happen next. */
    public static CharSequence playPauseLabel(String state, String title) {
        String action = "Pause".contentEquals(state) ? "Pause" : state;
        return title == null || title.length() == 0 ? action : action + " " + title;
    }

    /**
     * Minimum touch target the framework guarantees (48 dp). Layouts that look
     * smaller use transparent padding up to this size — never a smaller target.
     */
    public static int minTouchPixels(android.content.Context context) {
        return Ui.dp(context, 48);
    }
}
