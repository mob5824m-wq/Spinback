package com.soniq.base.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny in-process artwork loader with a bounded memory cache. */
public final class ImageLoader {

    /** Generic browser-like agent; artwork hosts reject empty or library UAs. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36";

    public interface Callback {
        void onBitmap(Bitmap bitmap);
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ImageLoader() {
    }

    public static Bitmap cached(String url) {
        return url == null ? null : CACHE.get(url);
    }

    /** Always invokes {@code callback} on the main thread, with null on failure. */
    public static void load(final String url, final Callback callback) {
        if (url == null || url.length() == 0) {
            post(callback, null);
            return;
        }
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            post(callback, hit);
            return;
        }
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = fetch(url);
                if (bitmap != null) {
                    CACHE.put(url, bitmap);
                }
                post(callback, bitmap);
            }
        });
    }

    public static void clear() {
        CACHE.evictAll();
    }

    private static void post(final Callback callback, final Bitmap bitmap) {
        if (callback == null) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onBitmap(bitmap);
            }
        });
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 400) {
                return null;
            }
            input = connection.getInputStream();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            // Decode twice: first to measure, then with a sane sample size.
            byte[] raw = readAll(input);
            input.close();
            input = null;
            bounds.inSampleSize = 1;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        } catch (IOException | OutOfMemoryError ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[16384];
        int read;
        while ((read = input.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > 8 * 1024 * 1024) {
                break;
            }
        }
        return buffer.toByteArray();
    }
}
