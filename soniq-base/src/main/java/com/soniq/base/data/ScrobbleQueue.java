package com.soniq.base.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import com.soniq.base.SoniqApp;
import com.soniq.base.model.MediaItem;
import com.soniq.base.provider.MusicProvider;

import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects finished listens, submits them to ListenBrainz and keeps an
 * on-disk queue so listens made offline are sent later.
 *
 * <p>Play-time accounting is driven by the playback service on the main
 * thread ({@link #trackChanged}, {@link #playbackStarted}, {@link #playbackTick},
 * {@link #playbackPaused}); all network work happens on one background thread,
 * spaced to respect ListenBrainz' one-call-per-second limit.
 */
public final class ScrobbleQueue {

    private static final String PREFS = "soniq_scrobbles";
    private static final String KEY_PENDING = "pending";
    /** Bound on the offline backlog; oldest entries are dropped beyond this. */
    private static final int MAX_PENDING = 1000;
    /** Listens per import request (server allows 1000; keep requests small). */
    private static final int BATCH_SIZE = 50;
    private static final long REQUEST_SPACING_MS = 1100L;
    private static final int MAX_RATE_LIMIT_RETRIES = 3;

    private static final int TRACK_STATUS_NONE = 0;
    private static final int TRACK_STATUS_QUEUED = 1;
    private static final int TRACK_STATUS_SUBMITTED = 2;
    private static final int TRACK_STATUS_MISSING_METADATA = 3;
    private static final int TRACK_STATUS_DISCARDED = 4;

    private static ScrobbleQueue instance;

    public static synchronized ScrobbleQueue get(Context context) {
        if (instance == null) {
            instance = new ScrobbleQueue(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final Settings settings;
    private final MusicProvider provider;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private final ArrayList<Scrobble> pending = new ArrayList<>();
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final Object nowPlayingLock = new Object();
    private volatile boolean flushRequested;
    private volatile String lastError = "";
    private volatile String nowPlayingError = "";
    private volatile int currentTrackStatus = TRACK_STATUS_NONE;
    private volatile String currentTrackError = "";
    private volatile Scrobble currentListen;
    private Scrobble pendingNowPlaying;
    private long pendingNowPlayingGeneration;
    private long nowPlayingGeneration;
    private boolean nowPlayingWorkerScheduled;
    private long lastApiRequestAtMs;
    private ListenBrainzApi api;

    // Play-time accounting; only touched on the main thread.
    private String currentKey = "";
    private long playedMs;
    private long startedAtMs;
    private boolean thresholdReached;

    private ScrobbleQueue(Context context) {
        this.context = context;
        this.settings = new Settings(context);
        this.provider = SoniqApp.providerOf(context);
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        synchronized (lock) {
            pending.addAll(Scrobble.fromJsonArray(
                    parse(prefs.getString(KEY_PENDING, "[]"))));
        }
    }

    private static JSONArray parse(String text) {
        try {
            return new JSONArray(text);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    /* ======================================================== playback hooks */

    /**
     * A track is about to load: reset the accounting even when it is the same
     * track again (re-playing a song from the queue is a new listen).
     */
    public void trackChanged(MediaItem item) {
        synchronized (nowPlayingLock) {
            nowPlayingGeneration++;
            pendingNowPlaying = null;
        }
        synchronized (lock) {
            currentKey = item == null ? "" : item.identity();
            playedMs = 0L;
            thresholdReached = false;
            currentListen = null;
            currentTrackStatus = TRACK_STATUS_NONE;
            currentTrackError = "";
            startedAtMs = System.currentTimeMillis();
        }
    }

    /** Drops any playing_now request that has not started yet. */
    public void playbackPaused() {
        synchronized (nowPlayingLock) {
            nowPlayingGeneration++;
            pendingNowPlaying = null;
        }
    }

    /**
     * Playback actually started for {@code item}: announce playing_now. Called
     * for fresh starts and for resume-from-pause, so an unchanged track keeps
     * its accumulated play time.
     */
    public void playbackStarted(MediaItem item) {
        if (item == null) {
            return;
        }
        if (!item.identity().equals(currentKey)) {
            trackChanged(item);
        } else if (playedMs == 0L) {
            // First actual start (not a resume): stamp the real playback start time.
            startedAtMs = System.currentTimeMillis();
        }
        if (settings.isListenBrainzConfigured() && settings.announceNowPlaying()
                && ScrobblePolicy.isSubmittable(item.author, item.title)) {
            queuePlayingNow(scrobbleOf(item, nowSec(), durationOf(item, 0L), 0L));
        }
        flush();
    }

    /** Keeps only the freshest playing_now update and serializes it with all other API calls. */
    private void queuePlayingNow(Scrobble listen) {
        boolean schedule = false;
        synchronized (nowPlayingLock) {
            pendingNowPlaying = listen;
            pendingNowPlayingGeneration = ++nowPlayingGeneration;
            if (!nowPlayingWorkerScheduled) {
                nowPlayingWorkerScheduled = true;
                schedule = true;
            }
        }
        if (schedule) {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        drainPlayingNow();
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                synchronized (nowPlayingLock) {
                    nowPlayingWorkerScheduled = false;
                }
                nowPlayingError = "ListenBrainz playing-now update could not be scheduled";
            }
        }
    }

    private void drainPlayingNow() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Scrobble listen;
                final long requestGeneration;
                synchronized (nowPlayingLock) {
                    listen = pendingNowPlaying;
                    requestGeneration = pendingNowPlayingGeneration;
                    pendingNowPlaying = null;
                }
                if (listen == null) {
                    return;
                }
                if (!settings.isListenBrainzConfigured() || !settings.announceNowPlaying()) {
                    continue;
                }
                if (!awaitApiRequestSlot()) {
                    return;
                }
                synchronized (nowPlayingLock) {
                    // A track change or pause while rate-limited drops the stale announcement.
                    if (pendingNowPlaying != null || requestGeneration != nowPlayingGeneration) {
                        continue;
                    }
                }
                if (!settings.isListenBrainzConfigured() || !settings.announceNowPlaying()) {
                    continue;
                }
                String token = settings.listenBrainzToken();
                recordApiRequest();
                try {
                    api().submitPlayingNow(token, listen);
                    nowPlayingError = "";
                } catch (IOException error) {
                    nowPlayingError = describe(error);
                } catch (RuntimeException error) {
                    nowPlayingError = error.getMessage() == null
                            ? "ListenBrainz playing-now update failed"
                            : describe(new IOException(error.getMessage(), error));
                }
            }
        } finally {
            boolean reschedule = false;
            synchronized (nowPlayingLock) {
                nowPlayingWorkerScheduled = false;
                if (pendingNowPlaying != null && !Thread.currentThread().isInterrupted()) {
                    nowPlayingWorkerScheduled = true;
                    reschedule = true;
                }
            }
            if (reschedule) {
                try {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            drainPlayingNow();
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException rejected) {
                    synchronized (nowPlayingLock) {
                        nowPlayingWorkerScheduled = false;
                    }
                    nowPlayingError = "ListenBrainz playing-now update could not be scheduled";
                }
            }
        }
    }

    /**
     * Called about once a second while playing. Accumulates real listening time
     * (seeking cannot inflate it) and enqueues a listen once the threshold is met.
     *
     * @param deltaMs    wall-clock time since the previous tick
     * @param durationMs track duration from the player, or 0 if unknown
     */
    public void playbackTick(MediaItem item, long deltaMs, long durationMs) {
        if (item == null) {
            return;
        }
        if (!item.identity().equals(currentKey)) {
            trackChanged(item);
        }
        if (thresholdReached || !settings.isListenBrainzConfigured()) {
            return;
        }
        if (deltaMs <= 0L || deltaMs > ScrobblePolicy.MAX_TICK_MS) {
            return;
        }
        if (!ScrobblePolicy.isSubmittable(item.author, item.title)) {
            thresholdReached = true;
            currentTrackStatus = TRACK_STATUS_MISSING_METADATA;
            return;
        }
        playedMs += deltaMs;
        long duration = durationOf(item, durationMs);
        if (ScrobblePolicy.shouldScrobble(playedMs, duration)) {
            thresholdReached = true;
            Scrobble listen = scrobbleOf(item, startedAtMs / 1000L, duration, playedMs);
            currentListen = listen;
            currentTrackStatus = TRACK_STATUS_QUEUED;
            currentTrackError = "";
            enqueue(listen);
        }
    }

    /** True only after ListenBrainz accepted the current listen, not merely when queued. */
    public boolean isCurrentTrackScrobbled() {
        return currentTrackStatus == TRACK_STATUS_SUBMITTED;
    }

    /** Honest user-facing status for the current track; an offline listen remains queued. */
    public String currentTrackStatusMessage() {
        switch (currentTrackStatus) {
            case TRACK_STATUS_QUEUED:
                String error = currentTrackError;
                if (error.length() > 0) {
                    return "Listen queued for retry · " + error;
                }
                return settings.isListenBrainzConfigured()
                        ? "Listen queued for ListenBrainz" : "Listen queued · scrobbling is off";
            case TRACK_STATUS_SUBMITTED:
                return "Scrobbled to ListenBrainz ✓";
            case TRACK_STATUS_MISSING_METADATA:
                return "Not scrobbled · artist or title unavailable";
            case TRACK_STATUS_DISCARDED:
                return currentTrackError.length() > 0
                        ? "Listen discarded · " + currentTrackError
                        : "Listen removed before submission";
            default:
                return "";
        }
    }

    /** Last playing_now error, separate from queued listen submission errors. */
    public String nowPlayingError() {
        return nowPlayingError;
    }

    private Scrobble scrobbleOf(MediaItem item, long listenedAtSec, long durationMs, long playedMs) {
        String service = "";
        String origin = "";
        try {
            service = provider.musicService();
            origin = provider.originUrl(item);
        } catch (RuntimeException ignored) {
            // A provider bug must not stop listen accounting.
        }
        return Scrobble.from(item, listenedAtSec, durationMs, playedMs, service, origin);
    }

    private static long durationOf(MediaItem item, long playerDurationMs) {
        if (playerDurationMs > 0L) {
            return playerDurationMs;
        }
        return item.durationSec > 0L ? item.durationSec * 1000L : 0L;
    }

    private static long nowSec() {
        return System.currentTimeMillis() / 1000L;
    }

    /* ================================================================== queue */

    /** Number of listens detected but not yet accepted by ListenBrainz. */
    public int pendingCount() {
        synchronized (lock) {
            return pending.size();
        }
    }

    /** Last submission error, empty when the most recent request succeeded. */
    public String lastError() {
        return lastError;
    }

    public void clearLastError() {
        lastError = "";
    }

    /** Drops every queued listen (Settings → Pending listens, long-press style safety valve). */
    public void clearPending() {
        synchronized (lock) {
            if (currentListen != null && pending.contains(currentListen)) {
                currentTrackStatus = TRACK_STATUS_DISCARDED;
                currentTrackError = "";
            }
            pending.clear();
            persistLocked();
        }
        lastError = "";
    }

    private void enqueue(Scrobble listen) {
        if (listen == null || !listen.isSubmittable()) {
            return;
        }
        synchronized (lock) {
            pending.add(listen);
            while (pending.size() > MAX_PENDING) {
                Scrobble discarded = pending.remove(0);
                if (discarded == currentListen) {
                    currentTrackStatus = TRACK_STATUS_DISCARDED;
                    currentTrackError = "Offline listen queue is full";
                }
            }
            persistLocked();
        }
        flush();
    }

    /** Sends queued listens in the background; safe to call often. */
    public void flush() {
        if (!settings.isListenBrainzConfigured() || pendingCount() == 0) {
            return;
        }
        if (!flushing.compareAndSet(false, true)) {
            flushRequested = true;
            return;
        }
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        do {
                            flushRequested = false;
                            drain();
                        } while (flushRequested && pendingCount() > 0
                                && settings.isListenBrainzConfigured()
                                && !Thread.currentThread().isInterrupted());
                    } finally {
                        flushing.set(false);
                        if (flushRequested && pendingCount() > 0
                                && settings.isListenBrainzConfigured()) {
                            flush();
                        }
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            flushing.set(false);
        }
    }

    private void drain() {
        int rateLimitRetries = 0;
        while (!Thread.currentThread().isInterrupted()) {
            final List<Scrobble> batch;
            final boolean single;
            synchronized (lock) {
                if (pending.isEmpty()) {
                    break;
                }
                single = pending.size() == 1;
                int count = Math.min(pending.size(), BATCH_SIZE);
                batch = new ArrayList<>(pending.subList(0, count));
            }
            if (!settings.isListenBrainzConfigured() || !awaitApiRequestSlot()) {
                break;
            }
            synchronized (lock) {
                // Do not submit a listen the user cleared while this batch was rate-limited.
                if (!pending.containsAll(batch)) {
                    continue;
                }
            }
            if (!settings.isListenBrainzConfigured()) {
                break;
            }
            String token = settings.listenBrainzToken();
            recordApiRequest();
            try {
                if (single) {
                    api().submitSingle(token, batch.get(0));
                } else {
                    api().submitImport(token, batch);
                }
                synchronized (lock) {
                    pending.removeAll(batch);
                    persistLocked();
                    lastError = "";
                    Scrobble tracked = currentListen;
                    if (tracked != null && batch.contains(tracked)) {
                        currentTrackError = "";
                        currentTrackStatus = TRACK_STATUS_SUBMITTED;
                    }
                }
                rateLimitRetries = 0;
            } catch (ListenBrainzApi.HttpFailure failure) {
                if (failure.code == 429 && rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                    rateLimitRetries++;
                    if (!sleep(failure.retryAfterSec * 1000L)) {
                        break;
                    }
                    continue;
                }
                recordQueueFailure(batch, failure);
                break;
            } catch (IOException error) {
                recordQueueFailure(batch, error);
                break;
            } catch (RuntimeException error) {
                recordQueueFailure(batch, new IOException(
                        "ListenBrainz submission failed", error));
                break;
            }
        }
    }

    private void recordQueueFailure(List<Scrobble> batch, IOException error) {
        String message = describe(error);
        synchronized (lock) {
            boolean retained = false;
            for (Scrobble listen : batch) {
                if (pending.contains(listen)) {
                    retained = true;
                    break;
                }
            }
            if (!retained) {
                return;
            }
            lastError = message;
            Scrobble tracked = currentListen;
            if (tracked != null && batch.contains(tracked)
                    && currentTrackStatus == TRACK_STATUS_QUEUED) {
                currentTrackError = message;
            }
        }
    }

    private boolean awaitApiRequestSlot() {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        long elapsed = SystemClock.elapsedRealtime() - lastApiRequestAtMs;
        long wait = REQUEST_SPACING_MS - elapsed;
        return sleep(wait) && !Thread.currentThread().isInterrupted();
    }

    private void recordApiRequest() {
        lastApiRequestAtMs = SystemClock.elapsedRealtime();
    }

    private ListenBrainzApi api() {
        if (api == null) {
            api = new ListenBrainzApi(context);
        }
        return api;
    }

    private void persistLocked() {
        prefs.edit().putString(KEY_PENDING, Scrobble.toJsonArray(pending).toString()).apply();
    }

    private static boolean sleep(long millis) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (millis <= 0L) {
            return true;
        }
        try {
            Thread.sleep(Math.min(millis, 125_000L));
            return !Thread.currentThread().isInterrupted();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String describe(IOException error) {
        if (error == null || error.getMessage() == null) {
            return "ListenBrainz submission failed";
        }
        String message = error.getMessage();
        return message.length() > 160 ? message.substring(0, 160) + "…" : message;
    }
}
