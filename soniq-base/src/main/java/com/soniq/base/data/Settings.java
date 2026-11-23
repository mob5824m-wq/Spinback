package com.soniq.base.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Framework preference facade: stream quality hint, notification prompt state and
 * ListenBrainz scrobbling. Everything lives in one private SharedPreferences file.
 * Provider-specific settings belong to the app, not here.
 */
public final class Settings {

    public static final int QUALITY_128 = 128000;
    public static final int QUALITY_256 = 256000;

    private static final String PREFS = "soniq_settings";
    private static final String KEY_QUALITY = "target_bitrate";
    private static final String KEY_NOTIFICATIONS_ASKED = "notifications_asked";
    private static final String KEY_EQ_PRESET = "equalizer_preset";
    private static final String KEY_SCROBBLE_ENABLED = "scrobble_enabled";
    private static final String KEY_SCROBBLE_TOKEN = "scrobble_token";
    private static final String KEY_SCROBBLE_USER = "scrobble_user_name";
    private static final String KEY_SCROBBLE_NOW_PLAYING = "scrobble_announce_now_playing";
    private static final String KEY_INSIGHTS_USER = "insights_user_name";
    private static final String KEY_CROSSFADE = "crossfade_seconds";
    private static final String KEY_GAPLESS = "gapless_enabled";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    private static final String KEY_RESUME = "resume_enabled";
    private static final String KEY_SORT = "library_sort";
    private static final String KEY_WIDGET = "widget_enabled";

    /** Library ordering choices, shared by the Library and playlist screens. */
    public static final int SORT_RECENT = 0;
    public static final int SORT_TITLE = 1;
    public static final int SORT_ARTIST = 2;
    public static final int SORT_DURATION = 3;

    private final SharedPreferences prefs;

    public Settings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int targetBitrate() {
        return prefs.getInt(KEY_QUALITY, QUALITY_128);
    }

    public void setTargetBitrate(int bitrate) {
        prefs.edit().putInt(KEY_QUALITY, bitrate).apply();
    }

    public boolean notificationsAsked() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false);
    }

    public void setNotificationsAsked(boolean asked) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ASKED, asked).apply();
    }

    /** Last selected system equalizer preset index; the effect is attached when playback is ready. */
    public int equalizerPreset() {
        return Math.max(0, prefs.getInt(KEY_EQ_PRESET, 0));
    }

    public void setEqualizerPreset(int preset) {
        prefs.edit().putInt(KEY_EQ_PRESET, Math.max(0, preset)).apply();
    }

    public String qualityLabel() {
        return (targetBitrate() / 1000) + " kbps";
    }

    /* ============================================================ ListenBrainz */

    /** Master switch. Off by default: scrobbling sends listening history off-device. */
    public boolean scrobblingEnabled() {
        return prefs.getBoolean(KEY_SCROBBLE_ENABLED, false);
    }

    public void setScrobblingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCROBBLE_ENABLED, enabled).apply();
    }

    /** ListenBrainz user token (from listenbrainz.org/settings). Never log this value. */
    public String listenBrainzToken() {
        String token = prefs.getString(KEY_SCROBBLE_TOKEN, "");
        return token == null ? "" : token.trim();
    }

    public void setListenBrainzToken(String token) {
        String value = token == null ? "" : token.trim();
        prefs.edit().putString(KEY_SCROBBLE_TOKEN, value).apply();
        if (value.length() == 0) {
            prefs.edit().putString(KEY_SCROBBLE_USER, "").apply();
        }
    }

    /** MusicBrainz user name cached from the last successful validate-token call. */
    public String scrobbleUserName() {
        String name = prefs.getString(KEY_SCROBBLE_USER, "");
        return name == null ? "" : name;
    }

    public void setScrobbleUserName(String name) {
        prefs.edit().putString(KEY_SCROBBLE_USER, name == null ? "" : name).apply();
    }

    /** Whether to send a playing_now update when a track starts (on by default). */
    public boolean announceNowPlaying() {
        return prefs.getBoolean(KEY_SCROBBLE_NOW_PLAYING, true);
    }

    public void setAnnounceNowPlaying(boolean announce) {
        prefs.edit().putBoolean(KEY_SCROBBLE_NOW_PLAYING, announce).apply();
    }

    /**
     * ListenBrainz user whose public statistics "Year in review" reads. Falls
     * back to the scrobbling user name, so a listener normally needs to set
     * nothing; anyone can point the recap at another public profile.
     */
    public String insightsUserName() {
        String value = prefs.getString(KEY_INSIGHTS_USER, "");
        return value == null ? "" : value.trim();
    }

    public void setInsightsUserName(String name) {
        prefs.edit().putString(KEY_INSIGHTS_USER,
                name == null ? "" : name.trim()).apply();
    }

    /** The ListenBrainz user the recap reads: the override, else the scrobbling user. */
    public String recapUserName() {
        String override = insightsUserName();
        return override.length() > 0 ? override : scrobbleUserName();
    }

    /* =========================================================== Playback extras */

    /**
     * Crossfade length in seconds, {@code 0..12}; 0 means off. The service
     * clamps again on read, so a hand-edited preference cannot break playback.
     */
    public int crossfadeSeconds() {
        return Math.max(0, Math.min(12, prefs.getInt(KEY_CROSSFADE, 0)));
    }

    public void setCrossfadeSeconds(int seconds) {
        prefs.edit().putInt(KEY_CROSSFADE, Math.max(0, Math.min(12, seconds))).apply();
    }

    /** Gapless hand-off between consecutive tracks (off by default: it costs a second decoder). */
    public boolean gaplessEnabled() {
        return prefs.getBoolean(KEY_GAPLESS, false);
    }

    public void setGaplessEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GAPLESS, enabled).apply();
    }

    /**
     * "Wi-Fi only": no downloads and no streaming on metered connections.
     * Off by default because a listener on unlimited data should not have to
     * discover a switch before music works.
     */
    public boolean wifiOnly() {
        return prefs.getBoolean(KEY_WIFI_ONLY, false);
    }

    public void setWifiOnly(boolean wifiOnly) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply();
    }

    /**
     * Whether the framework widget publishes playback state. Off only when a
     * host app ships its own widget and does not want two updating.
     */
    public boolean widgetEnabled() {
        return prefs.getBoolean(KEY_WIDGET, true);
    }

    public void setWidgetEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WIDGET, enabled).apply();
    }

    /** Whether the Home screen offers "Continue listening". On by default. */
    public boolean resumeEnabled() {
        return prefs.getBoolean(KEY_RESUME, true);
    }

    public void setResumeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_RESUME, enabled).apply();
    }

    public int librarySort() {
        return Math.max(0, Math.min(3, prefs.getInt(KEY_SORT, SORT_RECENT)));
    }

    public void setLibrarySort(int sort) {
        prefs.edit().putInt(KEY_SORT, Math.max(0, Math.min(3, sort))).apply();
    }

    public String librarySortLabel() {
        switch (librarySort()) {
            case SORT_TITLE:
                return "Title (A–Z)";
            case SORT_ARTIST:
                return "Artist (A–Z)";
            case SORT_DURATION:
                return "Duration (shortest first)";
            default:
                return "Recently played";
        }
    }

    /** True when scrobbling is switched on and a token is present. */
    public boolean isListenBrainzConfigured() {
        return scrobblingEnabled() && listenBrainzToken().length() > 0;
    }

    /** Masked token for UI display, e.g. "••••••1a2b". */
    public String maskedListenBrainzToken() {
        String token = listenBrainzToken();
        if (token.length() == 0) {
            return "";
        }
        String tail = token.length() <= 4 ? token : token.substring(token.length() - 4);
        return "••••••" + tail;
    }
}
