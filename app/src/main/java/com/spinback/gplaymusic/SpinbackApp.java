package com.spinback.gplaymusic;

import com.soniq.base.SoniqApp;
import com.soniq.base.provider.MusicProvider;
import com.soniq.base.ui.Theme;

/**
 * Application entry point: wires the Google Play Music themed provider into
 * the Soniq Base framework and applies the Google Play Music palette.
 *
 * <p>Google Play Music (2011–2020) used Google's signature orange. The
 * framework's UI is dark, so this app keeps the dark surfaces and rebrands
 * the accent to that orange — the most recognisable part of the original
 * identity — rather than forking the framework's light theme. See
 * {@code docs/GOOGLE-PLAY-MUSIC.md} for the full theme mapping.
 */
public final class SpinbackApp extends SoniqApp {

    /** Google Play Music orange (2016–2020 identity). */
    public static final int GPLAY_ORANGE = 0xFFFF5722;
    /** Warm amber companion from the same orange family. */
    public static final int GPLAY_AMBER = 0xFFFF9800;

    @Override
    public void onCreate() {
        super.onCreate();
        // Must run before any view is built; views read Theme fields when
        // they are constructed.
        Theme.customize(GPLAY_ORANGE, GPLAY_AMBER);
    }

    @Override
    protected MusicProvider createProvider() {
        return new GooglePlayMusicProvider(this);
    }
}
