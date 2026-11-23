package com.soniq.base;

import android.app.Application;
import android.content.Context;

import com.soniq.base.provider.MusicProvider;

/**
 * Base {@code Application} for apps built on Soniq Base. Subclasses create
 * their {@link MusicProvider} once; the framework's service, activity, downloads
 * and scrobbling all read it from here.
 *
 * <pre>{@code
 * public final class MyApp extends SoniqApp {
 *     @Override protected MusicProvider createProvider() {
 *         return new MyProvider(this);
 *     }
 * }
 * }</pre>
 *
 * Declare the subclass with {@code android:name} in the app manifest.
 */
public abstract class SoniqApp extends Application {

    private MusicProvider provider;

    /**
     * Creates the provider. Override to do your own start-up work, but call
     * {@code super.onCreate()} first: the framework reads the provider from
     * here for the rest of the process lifetime.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        provider = createProvider();
        if (provider == null) {
            throw new IllegalStateException("createProvider() returned null");
        }
    }

    /** Creates the app's music source. Called once, on the main thread, at startup. */
    protected abstract MusicProvider createProvider();

    public final MusicProvider provider() {
        return provider;
    }

    /** The provider for the process; fails loudly if the app is not a {@link SoniqApp}. */
    public static MusicProvider providerOf(Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof SoniqApp) {
            MusicProvider provider = ((SoniqApp) app).provider();
            if (provider != null) {
                return provider;
            }
        }
        throw new IllegalStateException("The Application must extend com.soniq.base.SoniqApp "
                + "and return a MusicProvider from createProvider().");
    }
}
