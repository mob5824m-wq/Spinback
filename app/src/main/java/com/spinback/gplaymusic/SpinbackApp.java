package com.spinback.gplaymusic;

import com.soniq.base.SoniqApp;
import com.soniq.base.provider.MusicProvider;
import com.soniq.base.ui.Theme;

/**
 * Application entry point: wires the Google Play Music themed provider into
 * the Soniq Base framework and applies the Google Play Music palette.
 *
 * <p>Uses warm light surfaces and an orange accent inspired by the original
 * Google Play Music redesign. The catalogue remains YouTube Music.
 */
public final class SpinbackApp extends SoniqApp {

    /** Google Play Music orange (2016–2020 identity). */
    public static final int GPLAY_ORANGE = 0xFFBF360C;
    /** Warm amber companion from the same orange family. */
    public static final int GPLAY_AMBER = 0xFFFF9800;

    @Override
    public void onCreate() {
        super.onCreate();
        // Must run before any view is built; views read Theme fields when
        // they are constructed.
        Theme.customize(GPLAY_ORANGE, GPLAY_AMBER);
        Theme.BG = 0xFFF5F5F5;
        Theme.SURFACE = 0xFFFFFFFF;
        Theme.SURFACE_HIGH = 0xFFF0EFED;
        Theme.OUTLINE = 0xFFD8D6D3;
        Theme.TEXT = 0xFF212121;
        Theme.TEXT_DIM = 0xFF5F5F5F;
        Theme.TEXT_FAINT = 0xFF707070;
        Theme.LIGHT_SYSTEM_BARS = true;
        Theme.HOME_LABEL = "Listen Now";
        Theme.HOME_TITLE = "Listen Now";
        Theme.SEARCH_LABEL = "Explore";
        Theme.LIBRARY_LABEL = "My Music";
    }

    @Override
    protected MusicProvider createProvider() {
        return new GooglePlayMusicProvider(this);
    }
}
