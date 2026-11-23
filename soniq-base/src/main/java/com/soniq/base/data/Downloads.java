package com.soniq.base.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.soniq.base.SoniqApp;
import com.soniq.base.model.MediaItem;
import com.soniq.base.model.StreamSource;
import com.soniq.base.provider.MusicProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Offline audio downloads.
 *
 * <p>On Android 10+ the file is written into MediaStore under
 * {@code Music/Soniq Music}, so it appears in the system music apps. On Android
 * 8-9 it goes to the app's external music directory (no storage permission).
 * An index in SharedPreferences maps a track identity to its local URI.
 */
public final class Downloads {

    public interface Callback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
    }

    /** Cancellation handle for a single resolution/download/copy operation. */
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

        public boolean isCancelled() {
            return cancelled;
        }

        private void check() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Download cancelled");
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

    private static final String PREFS = "soniq_downloads";
    private static final String KEY_INDEX = "index";
    /** MediaStore folder, e.g. {@code Music/<app label>}; apps can override it. */

    private final Context context;
    private final MusicProvider provider;
    private final SharedPreferences prefs;
    private final String mediaRelativePath;

    /** Stores downloads under {@code Music/<application label>}. */
    public Downloads(Context context) {
        this(context, SoniqApp.providerOf(context), defaultRelativePath(context));
    }

    public Downloads(Context context, MusicProvider provider, String mediaRelativePath) {
        this.context = context.getApplicationContext();
        this.provider = provider;
        this.mediaRelativePath = mediaRelativePath == null || mediaRelativePath.trim().length() == 0
                ? "Music/Soniq Base" : mediaRelativePath.trim();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String defaultRelativePath(Context context) {
        try {
            CharSequence label = context.getApplicationInfo().loadLabel(context.getPackageManager());
            String name = sanitize(label == null ? "" : label.toString());
            if (name.length() > 0) {
                return "Music/" + name;
            }
        } catch (RuntimeException ignored) {
        }
        return "Music/Soniq Base";
    }

    public List<MediaItem> all() {
        List<MediaItem> out = new ArrayList<>();
        List<MediaItem> stored = readIndex();
        boolean changed = false;
        for (MediaItem item : stored) {
            if (item.localUri.length() == 0) {
                changed = true;
                continue;
            }
            if (exists(item.localUri)) {
                out.add(item);
            } else {
                changed = true;
            }
        }
        if (changed) {
            writeIndex(out);
        }
        return out;
    }

    public boolean isDownloaded(MediaItem item) {
        return item != null && find(item.identity()) != null;
    }

    public MediaItem find(String identity) {
        if (identity == null || identity.length() == 0) {
            return null;
        }
        for (MediaItem item : readIndex()) {
            if (identity.equals(item.identity()) && item.localUri.length() > 0
                    && exists(item.localUri)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Resolves, downloads and registers a track. Blocking; call from a worker
     * thread. Returns a copy of the item with {@link MediaItem#localUri} set.
     */
    public MediaItem downloadSync(MediaItem item, Callback callback) throws IOException {
        return downloadSync(item, callback, null);
    }

    /**
     * Resolves, downloads, validates and registers a track. Blocking; call from a
     * worker thread. Cancellation interrupts stream I/O and prevents publishing
     * or indexing an incomplete result.
     */
    public MediaItem downloadSync(MediaItem item, Callback callback,
                                  CancellationSignal cancellation) throws IOException {
        checkCancelled(cancellation);
        if (item == null || item.id.length() == 0) {
            throw new IOException("Play an online song before downloading it.");
        }
        MediaItem existing = find(item.identity());
        if (existing != null) {
            return existing;
        }
        if (networkBlocked()) {
            throw new IOException(new NetworkPolicy(true)
                    .blockedReason(com.soniq.base.playback.NetworkStatus.currentType(context), true)
                    + ". Downloads resume when Wi-Fi is available.");
        }
        StreamSource track = provider.resolve(item);
        checkCancelled(cancellation);
        if (track == null || !track.hasStream()) {
            throw new IOException("No downloadable audio stream was returned.");
        }
        if (track.hls) {
            throw new IOException("This stream is HLS-only and cannot be saved offline.");
        }

        File partial = new File(context.getCacheDir(), "download-" + UUID.randomUUID() + ".part");
        Uri publishedUri = null;
        boolean indexed = false;
        try {
            downloadTo(track, partial, callback, cancellation);
            checkCancelled(cancellation);
            validateMedia(partial, cancellation);
            checkCancelled(cancellation);

            String mimeType = track.mimeType == null ? "" : track.mimeType.trim();
            if (mimeType.length() == 0) {
                mimeType = "audio/mp4";
            }
            String lowerMime = mimeType.toLowerCase(java.util.Locale.US);
            String extension = lowerMime.startsWith("audio/webm") ? "webm"
                    : lowerMime.startsWith("audio/mpeg") || lowerMime.startsWith("audio/mp3") ? "mp3"
                    : lowerMime.startsWith("audio/ogg") ? "ogg"
                    : lowerMime.startsWith("audio/flac") ? "flac"
                    : lowerMime.startsWith("audio/aac") ? "aac" : "m4a";
            String trackTitle = track.title == null ? "" : track.title.trim();
            String trackAuthor = track.author == null ? "" : track.author.trim();
            String displayName = sanitize(trackAuthor.length() > 0
                    ? trackAuthor + " - " : trackTitle.length() > 0
                    ? trackTitle : item.title) + "." + extension;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    publishedUri = insertIntoMediaStore(displayName, mimeType, partial, cancellation);
                } catch (InterruptedIOException cancelled) {
                    throw cancelled;
                } catch (IOException | RuntimeException mediaStoreFailure) {
                    checkCancelled(cancellation);
                    publishedUri = copyToAppMusic(displayName, partial, mimeType, cancellation);
                }
            } else {
                publishedUri = copyToAppMusic(displayName, partial, mimeType, cancellation);
            }
            checkCancelled(cancellation);

            MediaItem saved = item.copy();
            saved.title = track.title != null && track.title.length() > 0 ? track.title : item.title;
            saved.author = track.author != null && track.author.length() > 0
                    ? track.author : item.author;
            saved.album = track.album != null && track.album.length() > 0 ? track.album : item.album;
            saved.thumbnailUrl = track.artworkUrl != null && track.artworkUrl.length() > 0
                    ? track.artworkUrl : item.thumbnailUrl;
            saved.durationSec = track.durationSec > 0 ? track.durationSec : item.durationSec;
            saved.localUri = publishedUri.toString();

            checkCancelled(cancellation);
            List<MediaItem> index = readIndex();
            index.add(0, saved);
            writeIndex(index);
            indexed = true;
            return saved;
        } finally {
            if (!indexed && publishedUri != null) {
                MediaItem unpublished = new MediaItem();
                unpublished.localUri = publishedUri.toString();
                deleteStoredFile(unpublished);
            }
            if (partial.exists() && !partial.delete()) {
                partial.deleteOnExit();
            }
        }
    }

    /** True when "Wi-Fi only" is on and the current connection is metered. */
    private boolean networkBlocked() {
        try {
            return new com.soniq.base.data.Settings(context).wifiOnly()
                    && !new NetworkPolicy(true).allowsDownload(
                    com.soniq.base.playback.NetworkStatus.currentType(context));
        } catch (RuntimeException unavailable) {
            // Never block a download because a preference could not be read.
            return false;
        }
    }

    /** Bytes used by downloaded audio, for the Storage settings row. */
    public long totalBytes() {
        long total = 0L;
        for (MediaItem item : all()) {
            total += byteCount(item);
        }
        return total;
    }

    /** Size of one downloaded file in bytes, or 0 when it is missing. */
    public long byteCount(MediaItem item) {
        if (item == null || item.localUri == null || item.localUri.length() == 0) {
            return 0L;
        }
        try {
            java.io.InputStream input = context.getContentResolver()
                    .openInputStream(android.net.Uri.parse(item.localUri));
            if (input == null) {
                return 0L;
            }
            try {
                long size = 0L;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    size += read;
                }
                return size;
            } finally {
                input.close();
            }
        } catch (Exception missing) {
            return 0L;
        }
    }

    /** Human total for Settings: "1.2 GB". */
    public String totalLabel() {
        return formatBytes(totalBytes());
    }

    /** Compact byte size; "" for zero so the UI can say "no downloads". */
    public static String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0
                ? ((long) value) + " " + units[unit]
                : String.format(java.util.Locale.US, "%.1f %s", value, units[unit]);
    }

    /** Deletes every downloaded file; returns how many were removed. */
    public int deleteAll() {
        int removed = 0;
        for (MediaItem item : all()) {
            if (delete(item)) {
                removed++;
            }
        }
        return removed;
    }

    public boolean delete(MediaItem item) {
        if (item == null) {
            return false;
        }
        List<MediaItem> index = readIndex();
        boolean removed = false;
        for (int i = 0; i < index.size(); i++) {
            if (item.identity().equals(index.get(i).identity())) {
                MediaItem stored = index.remove(i);
                removed = true;
                deleteStoredFile(stored);
                break;
            }
        }
        if (removed) {
            writeIndex(index);
        }
        return removed;
    }

    private void deleteStoredFile(MediaItem item) {
        if (item == null || item.localUri.length() == 0) {
            return;
        }
        try {
            Uri uri = Uri.parse(item.localUri);
            if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                context.getContentResolver().delete(uri, null, null);
            } else if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                File file = new File(uri.getPath());
                if (file.exists() && !file.delete()) {
                    file.deleteOnExit();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void downloadTo(StreamSource track, File target, Callback callback,
                            CancellationSignal cancellation) throws IOException {
        HttpURLConnection connection = null;
        InputStream input = null;
        OutputStream output = null;
        try {
            checkCancelled(cancellation);
            connection = (HttpURLConnection) new URL(track.url).openConnection(Proxy.NO_PROXY);
            if (cancellation != null) {
                cancellation.attach(connection);
            }
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            for (java.util.Map.Entry<String, String> header : track.headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            int code = connection.getResponseCode();
            checkCancelled(cancellation);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " while downloading audio");
            }
            long total = connection.getContentLengthLong();
            input = connection.getInputStream();
            output = new FileOutputStream(target);
            byte[] buffer = new byte[64 * 1024];
            long done = 0L;
            int read;
            while ((read = input.read(buffer)) > 0) {
                checkCancelled(cancellation);
                output.write(buffer, 0, read);
                done += read;
                if (callback != null) {
                    int percent = total > 0 ? (int) Math.min(99L, done * 100L / total) : -1;
                    callback.onProgress(percent, done, total);
                }
            }
            checkCancelled(cancellation);
            if (done <= 0L || (total > 0L && done != total)) {
                throw new IOException("The downloaded audio is empty or incomplete.");
            }
            output.flush();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
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

    private Uri insertIntoMediaStore(String displayName, String mimeType, File source,
                                     CancellationSignal cancellation) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, mediaRelativePath);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Could not create a MediaStore entry for the download");
        }
        boolean success = false;
        try {
            InputStream input = null;
            OutputStream output = null;
            try {
                input = new FileInputStream(source);
                output = resolver.openOutputStream(uri);
                if (output == null) {
                    throw new IOException("Could not open the MediaStore output stream");
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    checkCancelled(cancellation);
                    output.write(buffer, 0, read);
                }
                checkCancelled(cancellation);
                output.flush();
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException ignored) {
                    }
                }
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Audio.Media.IS_PENDING, 0);
            if (resolver.update(uri, done, null, null) <= 0) {
                throw new IOException("Could not publish the completed download in MediaStore");
            }
            success = true;
            return uri;
        } finally {
            if (!success) {
                try {
                    resolver.delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Uri copyToAppMusic(String displayName, File source, String mimeType,
                               CancellationSignal cancellation) throws IOException {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (directory == null) {
            throw new IOException("Download folder is unavailable");
        }
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Download folder is unavailable");
        }
        int dot = displayName.lastIndexOf('.');
        String base = dot > 0 ? displayName.substring(0, dot) : displayName;
        String extension = dot > 0 ? displayName.substring(dot) : "";
        File target = null;
        for (int suffix = 0; suffix < 10000; suffix++) {
            checkCancelled(cancellation);
            String candidateName = suffix == 0 ? displayName
                    : base + " (" + suffix + ")" + extension;
            File candidate = new File(directory, candidateName);
            if (candidate.createNewFile()) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IOException("Could not choose a unique download filename");
        }

        FileInputStream input = null;
        FileOutputStream output = null;
        boolean success = false;
        try {
            checkCancelled(cancellation);
            input = new FileInputStream(source);
            output = new FileOutputStream(target, false);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                checkCancelled(cancellation);
                output.write(buffer, 0, read);
            }
            checkCancelled(cancellation);
            output.flush();
            success = true;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (!success && target.exists() && !target.delete()) {
                target.deleteOnExit();
            }
        }
        MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()},
                new String[]{mimeType == null || mimeType.length() == 0 ? "audio/mp4" : mimeType},
                null);
        return Uri.fromFile(target);
    }

    private static void checkCancelled(CancellationSignal cancellation) throws InterruptedIOException {
        if (cancellation != null) {
            cancellation.check();
        } else if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Download cancelled");
        }
    }

    /** Verifies the downloaded bytes using the same platform decoder used for playback. */
    private static void validateMedia(File file, CancellationSignal cancellation) throws IOException {
        checkCancelled(cancellation);
        if (!file.isFile() || file.length() <= 0L) {
            throw new IOException("The downloaded audio file is empty.");
        }
        MediaPlayer validator = new MediaPlayer();
        try {
            validator.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            validator.setDataSource(file.getAbsolutePath());
            validator.prepare();
            checkCancelled(cancellation);
            if (validator.getDuration() <= 0) {
                throw new IOException("The downloaded file has no playable audio duration.");
            }
        } catch (InterruptedIOException cancelled) {
            throw cancelled;
        } catch (IOException error) {
            throw new IOException("The downloaded file could not be decoded as audio.", error);
        } catch (RuntimeException error) {
            throw new IOException("The downloaded file could not be decoded as audio.", error);
        } finally {
            try {
                validator.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static boolean exists(String localUri) {
        try {
            Uri uri = Uri.parse(localUri);
            if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                return new File(uri.getPath()).exists();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static String sanitize(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "")
                .trim();
        if (cleaned.length() == 0 || ".".equals(cleaned) || "..".equals(cleaned)) {
            cleaned = "track";
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
    }

    /* --------------------------------------------------------------- index */

    private List<MediaItem> readIndex() {
        List<MediaItem> out = new ArrayList<>();
        String raw = prefs.getString(KEY_INDEX, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    out.add(MediaItem.fromJson(object));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void writeIndex(List<MediaItem> items) {
        prefs.edit().putString(KEY_INDEX, MediaItem.toJsonArray(items).toString()).apply();
    }
}
