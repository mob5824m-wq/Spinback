package com.soniq.base.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Synced lyrics from the public lrclib.net API. Falls back to plain lyrics. */
public final class LyricsApi {

    private static final String USER_AGENT = "SoniqMusic/1.0 (Android; +https://github.com/mob5824m-wq/Soniq-Base)";

    /** Cancellation handle that disconnects an in-flight lyrics request. */
    public static final class CancellationSignal {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;

        public void cancel() {
            cancelled = true;
            HttpURLConnection active = connection;
            if (active != null) {
                active.disconnect();
            }
        }

        private void check() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Lyrics request cancelled");
            }
        }

        private void attach(HttpURLConnection active) {
            connection = active;
            if (cancelled) {
                active.disconnect();
            }
        }

        private void detach(HttpURLConnection active) {
            if (connection == active) {
                connection = null;
            }
        }
    }

    public List<LyricLine> fetch(String rawTitle, String rawArtist) throws IOException {
        return fetch(rawTitle, rawArtist, null);
    }

    public List<LyricLine> fetch(String rawTitle, String rawArtist,
                                 CancellationSignal cancellation) throws IOException {
        checkCancelled(cancellation);
        String title = cleanTitle(rawTitle);
        String artist = rawArtist == null ? "" : rawArtist.replace(" - Topic", "").trim();
        if (title.length() == 0) {
            return new ArrayList<>();
        }
        try {
            String url = "https://lrclib.net/api/search?track_name="
                    + URLEncoder.encode(title, "UTF-8")
                    + (artist.length() > 0
                    ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : "");
            String raw = get(url, cancellation);
            checkCancelled(cancellation);
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                checkCancelled(cancellation);
                JSONObject item = array.optJSONObject(i);
                if (item != null && item.optString("syncedLyrics", "").length() > 10) {
                    return LyricsParser.parse(item.optString("syncedLyrics"));
                }
            }
            JSONObject first = array.optJSONObject(0);
            if (first != null && first.optString("plainLyrics", "").length() > 0) {
                return LyricsParser.parse(first.optString("plainLyrics"));
            }
        } catch (java.io.IOException error) {
            throw error;
        } catch (Exception error) {
            throw new java.io.IOException("Could not parse the lyrics response.", error);
        }
        return new ArrayList<>();
    }

    static String cleanTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.replaceAll("(?i)\\((official\\s+video|official\\s+music\\s+video|"
                        + "official\\s+audio|lyrics?|visualizer|hd|4k)\\)", "")
                .replaceAll("(?i)\\[(official\\s+video|official\\s+audio|lyrics?|4k)\\]", "")
                .trim();
    }

    private static String get(String url, CancellationSignal cancellation) throws IOException {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            checkCancelled(cancellation);
            connection = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
            if (cancellation != null) {
                cancellation.attach(connection);
            }
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            checkCancelled(cancellation);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            input = connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) > 0) {
                checkCancelled(cancellation);
                buffer.write(chunk, 0, read);
                if (buffer.size() > 2 * 1024 * 1024) {
                    break;
                }
            }
            checkCancelled(cancellation);
            return new String(buffer.toByteArray(), "UTF-8");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                if (cancellation != null) {
                    cancellation.detach(connection);
                }
                connection.disconnect();
            }
        }
    }

    private static void checkCancelled(CancellationSignal cancellation) throws InterruptedIOException {
        if (cancellation != null) {
            cancellation.check();
        } else if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Lyrics request cancelled");
        }
    }
}
