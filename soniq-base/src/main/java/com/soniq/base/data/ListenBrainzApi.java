package com.soniq.base.data;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.util.List;

/**
 * Minimal ListenBrainz API client (https://api.listenbrainz.org/1).
 *
 * <p>Only two endpoints are used: {@code validate-token} and
 * {@code submit-listens}. Everything is plain {@link HttpURLConnection} +
 * {@code org.json}, matching the rest of the app's zero-dependency rule.
 *
 * <p>Contract (from the listenbrainz-server docs):
 * <ul>
 *   <li>{@code single}: exactly one finished listen, with {@code listened_at}.</li>
 *   <li>{@code playing_now}: exactly one track, <b>without</b> {@code listened_at}.</li>
 *   <li>{@code import}: previously saved listens, several per request.</li>
 *   <li>{@code listened_at} is the playback <i>start</i> time in unix seconds.</li>
 *   <li>Rate limit is one call per second; 429 responses carry Retry-After /
 *       X-RateLimit-Reset-In headers.</li>
 * </ul>
 */
public final class ListenBrainzApi {

    public static final String ROOT = "https://api.listenbrainz.org/1/";
    public static final String CLIENT_NAME = "Soniq Base";
    public static final String CONTACT_URL = "https://github.com/mob5824m-wq/Soniq-Base";

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final long MIN_REQUEST_SPACING_MS = 1100L;
    private static final Object REQUEST_SPACING_LOCK = new Object();
    private static long lastRequestStartedMs;

    /** Result of {@code GET /validate-token}. */
    public static final class TokenResult {
        public final boolean valid;
        public final String userName;
        public final String message;

        TokenResult(boolean valid, String userName, String message) {
            this.valid = valid;
            this.userName = userName == null ? "" : userName;
            this.message = message == null ? "" : message;
        }
    }

    /** Non-2xx response from ListenBrainz, carrying the HTTP code and any back-off hint. */
    public static final class HttpFailure extends IOException {
        private static final long serialVersionUID = 1L;

        public final int code;
        /** Seconds the server asked us to wait (0 when it did not say). */
        public final long retryAfterSec;

        HttpFailure(int code, String message, long retryAfterSec) {
            super(message);
            this.code = code;
            this.retryAfterSec = Math.max(0L, retryAfterSec);
        }
    }

    private final String version;
    private final String userAgent;

    public ListenBrainzApi(Context context) {
        this(readVersion(context));
    }

    ListenBrainzApi(String version) {
        String safe = version == null || version.trim().length() == 0 ? "dev" : version.trim();
        this.version = safe;
        this.userAgent = userAgentFor(safe);
    }

    /**
     * The User-Agent ListenBrainz requires ("Name/version ( contact-url )");
     * anonymous clients are blocked. Shared with {@link ListeningRecapApi}.
     */
    static String userAgentFor(Context context) {
        return userAgentFor(readVersion(context));
    }

    private static String userAgentFor(String version) {
        String safe = version == null || version.trim().length() == 0 ? "dev" : version.trim();
        return "SoniqMusic/" + safe + " ( " + CONTACT_URL + " )";
    }

    public String clientVersion() {
        return version;
    }

    private static String readVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return "dev";
        }
    }

    /* =================================================================== calls */

    /** Checks a user token; never throws for an invalid token, only for network errors. */
    public TokenResult validateToken(String token) throws IOException {
        try {
            JSONObject body = new JSONObject(request(token, "GET", "validate-token", null));
            return new TokenResult(body.optBoolean("valid", false),
                    body.optString("user_name", ""), body.optString("message", ""));
        } catch (JSONException error) {
            throw new IOException("Unexpected answer from ListenBrainz: " + error.getMessage());
        } catch (HttpFailure failure) {
            if (failure.code == 401 || failure.code == 400) {
                return new TokenResult(false, "", failure.getMessage());
            }
            throw failure;
        }
    }

    public void submitPlayingNow(String token, Scrobble listen) throws IOException {
        JSONArray payload = new JSONArray().put(trackMetadataOnly(listen));
        request(token, "POST", "submit-listens", envelope("playing_now", payload));
    }

    public void submitSingle(String token, Scrobble listen) throws IOException {
        JSONArray payload = new JSONArray().put(withTimestamp(listen));
        request(token, "POST", "submit-listens", envelope("single", payload));
    }

    /** Submits saved listens in one {@code import} request. */
    public void submitImport(String token, List<Scrobble> listens) throws IOException {
        JSONArray payload = new JSONArray();
        for (Scrobble listen : listens) {
            if (listen != null && listen.isSubmittable()) {
                payload.put(withTimestamp(listen));
            }
        }
        if (payload.length() == 0) {
            return;
        }
        request(token, "POST", "submit-listens", envelope("import", payload));
    }

    /* ================================================================= payload */

    private static JSONObject envelope(String type, JSONArray payload) throws IOException {
        try {
            return new JSONObject().put("listen_type", type).put("payload", payload);
        } catch (JSONException error) {
            throw new IOException("Could not build listen payload: " + error.getMessage());
        }
    }

    /** Listen object for single/import: track_metadata plus the start timestamp. */
    private JSONObject withTimestamp(Scrobble listen) throws IOException {
        JSONObject o = trackMetadataOnly(listen);
        try {
            o.put("listened_at", Math.max(0L, listen.listenedAtSec));
        } catch (JSONException error) {
            throw new IOException("Could not build listen payload: " + error.getMessage());
        }
        return o;
    }

    /** Top-level listen object for playing_now — must not contain listened_at. */
    private JSONObject trackMetadataOnly(Scrobble listen) throws IOException {
        if (listen == null || !listen.isSubmittable()) {
            throw new IOException("Listen is missing artist or title.");
        }
        try {
            JSONObject additional = new JSONObject()
                    .put("submission_client", CLIENT_NAME)
                    .put("submission_client_version", version)
                    .put("media_player", CLIENT_NAME)
                    .put("media_player_version", version);
            // Both come from the app's MusicProvider; omit whatever it does not know.
            if (listen.musicService.length() > 0) {
                additional.put("music_service", listen.musicService);
            }
            if (listen.originUrl.length() > 0) {
                additional.put("origin_url", listen.originUrl);
            }
            if (listen.durationMs > 0L) {
                additional.put("duration_ms", listen.durationMs);
            }
            if (listen.playedMs > 0L) {
                additional.put("duration_played_ms", listen.playedMs);
            }
            JSONObject track = new JSONObject()
                    .put("artist_name", listen.artist)
                    .put("track_name", listen.title)
                    .put("additional_info", additional);
            if (listen.release.length() > 0) {
                track.put("release_name", listen.release);
            }
            return new JSONObject().put("track_metadata", track);
        } catch (JSONException error) {
            throw new IOException("Could not build listen payload: " + error.getMessage());
        }
    }

    /* ===================================================================== HTTP */

    private String request(String token, String method, String path, JSONObject body)
            throws IOException {
        String cleanToken = token == null ? "" : token.trim();
        if (cleanToken.length() == 0) {
            throw new HttpFailure(401, "No ListenBrainz token is configured.", 0L);
        }
        waitForRequestSlot();
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) URI.create(ROOT + path).toURL()
                    .openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Token " + cleanToken);
            connection.setRequestProperty("Accept", "application/json");
            // ListenBrainz requires "Name/version ( contact-url )" and blocks anonymous clients.
            connection.setRequestProperty("User-Agent", userAgent);
            if (body != null) {
                byte[] payload = body.toString().getBytes("UTF-8");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(payload.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(payload);
                    output.flush();
                } finally {
                    output.close();
                }
            }

            int code = connection.getResponseCode();
            input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = new String(readFully(input), "UTF-8");
            if (code < 200 || code >= 300) {
                String detail = summarize(text);
                if (code == 429) {
                    throw new HttpFailure(code, "ListenBrainz rate limit reached"
                            + (detail.length() > 0 ? ": " + detail : ""), retryAfter(connection));
                }
                if (code == 401) {
                    throw new HttpFailure(code, "ListenBrainz rejected the token"
                            + (detail.length() > 0 ? ": " + detail : ""), 0L);
                }
                throw new HttpFailure(code, "ListenBrainz HTTP " + code
                        + (detail.length() > 0 ? ": " + detail : ""), 0L);
            }
            return text;
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

    /** Shared one-call-per-second spacer; also used by {@link ListeningRecapApi}. */
    static void waitForRequestSlot() throws InterruptedIOException {
        synchronized (REQUEST_SPACING_LOCK) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("ListenBrainz request cancelled");
            }
            long elapsed = SystemClock.elapsedRealtime() - lastRequestStartedMs;
            long wait = MIN_REQUEST_SPACING_MS - elapsed;
            if (wait > 0L) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException cancelled =
                            new InterruptedIOException("ListenBrainz request cancelled");
                    cancelled.initCause(interrupted);
                    throw cancelled;
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("ListenBrainz request cancelled");
            }
            lastRequestStartedMs = SystemClock.elapsedRealtime();
        }
    }

    private static long retryAfter(HttpURLConnection connection) {
        long seconds = parseSeconds(connection.getHeaderField("Retry-After"));
        if (seconds <= 0L) {
            seconds = parseSeconds(connection.getHeaderField("X-RateLimit-Reset-In"));
        }
        return seconds > 0L ? Math.min(seconds, 120L) : 2L;
    }

    private static long parseSeconds(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    static String summarize(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.trim().replaceAll("\\s+", " ");
        if (compact.length() > 200) {
            compact = compact.substring(0, 200) + "…";
        }
        return compact;
    }

    static byte[] readFully(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
            if (buffer.size() >= MAX_RESPONSE_BYTES) {
                break;
            }
        }
        return buffer.toByteArray();
    }
}
