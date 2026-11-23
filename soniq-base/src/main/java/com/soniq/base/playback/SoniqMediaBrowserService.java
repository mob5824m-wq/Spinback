package com.soniq.base.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import com.soniq.base.data.Library;
import com.soniq.base.model.MediaItem;
import com.soniq.base.provider.MusicProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Android Auto / Android Automotive / Wear browse endpoint.
 *
 * <p>This is the framework <em>backbone</em>: it exposes the library the
 * framework already owns — liked songs, downloads, playlists and recently
 * played — as a browsable tree, and routes playback through
 * {@link PlaybackService} exactly like the in-app UI. It deliberately does not
 * model the provider's catalogue: a car head unit needs a small, predictable
 * tree, and the app knows its own browsing better than the framework does.
 *
 * <p>What a host app must still do is listed in {@code docs/WIDGETS.md} and
 * {@code AGENT.md}; in short: declare this service with the
 * {@code android.media.browse.MediaBrowserService} intent filter and
 * {@code MediaBrowserService} meta-data in <em>its</em> manifest, provide
 * automotive/car app metadata, and add provider-specific browse nodes (mixes,
 * charts, "downloaded albums") by overriding {@link #browseProviderNode}.
 *
 * <p>Note: the platform {@code MediaBrowserService} is used rather than
 * {@code androidx.media3}; Soniq Base has no runtime dependencies, so the
 * androidx media stack (which would give richer browse flags and a built-in
 * MediaBrowserCompat binder) is out of scope. A host app that wants those can
 * keep this class for the platform API and wrap it with
 * {@code MediaBrowserServiceCompat} itself.
 */
public class SoniqMediaBrowserService extends MediaBrowserService {

    /** The single root id clients browse from. */
    public static final String ROOT_ID = "soniq_root";
    public static final String NODE_LIKED = "soniq_liked";
    public static final String NODE_DOWNLOADS = "soniq_downloads";
    public static final String NODE_RECENT = "soniq_recent";
    public static final String NODE_PLAYLISTS = "soniq_playlists";
    /** Prefix for a local playlist's children; the rest of the id is the playlist id. */
    public static final String PLAYLIST_PREFIX = "soniq_playlist:";
    /** Prefix for a provider-supplied browse node; the rest is the MediaItem id. */
    public static final String PROVIDER_PREFIX = "soniq_provider:";

    private MediaSession.Token sessionToken;
    private Library library;
    private MusicProvider provider;

    /** Host hook: extra browse nodes for the provider's own catalogue. */
    protected List<MediaItem> providerChildren() {
        return new ArrayList<>();
    }

    /** Host hook: "Play from <node>" for a node id this framework does not know. */
    protected List<MediaItem> browseProviderNode(String providerId) {
        return new ArrayList<>();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        library = new Library(this);
        try {
            provider = com.soniq.base.SoniqApp.providerOf(this);
        } catch (RuntimeException notSoniqApp) {
            provider = null;
        }
        // The browser needs the session token the notification also uses.
        sessionToken = PlaybackService.sessionToken();
        setSessionToken(sessionToken);
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle hints) {
        // Everything this service exposes is the user's own library on their own
        // device, so any client may browse it. Playback still needs the app.
        return new BrowserRoot(ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        result.detach();
        List<MediaBrowser.MediaItem> items = new ArrayList<>();
        try {
            loadChildren(parentId, items);
        } catch (RuntimeException error) {
            // A car head unit must never see a crash: return what we have.
            items.clear();
        }
        result.sendResult(items);
    }

    private void loadChildren(String parentId, List<MediaBrowser.MediaItem> out) {
        if (parentId == null || ROOT_ID.equals(parentId)) {
            out.add(node(NODE_LIKED, "Liked songs", "Your liked tracks", true));
            out.add(node(NODE_RECENT, "Recently played", "Where you left off", true));
            out.add(node(NODE_DOWNLOADS, "Downloads", "Available offline", true));
            out.add(node(NODE_PLAYLISTS, "Playlists", "Playlists saved in this app", true));
            for (MediaItem extra : providerChildren()) {
                if (extra != null) {
                    out.add(treeItem(extra));
                }
            }
            return;
        }
        if (NODE_LIKED.equals(parentId)) {
            addTracks(library.list(Library.LIKED), out);
            return;
        }
        if (NODE_RECENT.equals(parentId)) {
            addTracks(library.recent(20), out);
            return;
        }
        if (NODE_DOWNLOADS.equals(parentId)) {
            addTracks(new com.soniq.base.data.Downloads(this).all(), out);
            return;
        }
        if (NODE_PLAYLISTS.equals(parentId)) {
            for (MediaItem playlist : library.playlists()) {
                out.add(node(PLAYLIST_PREFIX + playlist.id, playlist.title,
                        library.playlistTracks(playlist.id).size() + " songs", true));
            }
            return;
        }
        if (parentId.startsWith(PLAYLIST_PREFIX)) {
            addTracks(library.playlistTracks(parentId.substring(PLAYLIST_PREFIX.length())), out);
            return;
        }
        if (parentId.startsWith(PROVIDER_PREFIX)) {
            addTracks(browseProviderNode(parentId.substring(PROVIDER_PREFIX.length())), out);
        }
    }

    private void addTracks(List<MediaItem> tracks, List<MediaBrowser.MediaItem> out) {
        if (tracks == null) {
            return;
        }
        for (MediaItem track : tracks) {
            if (track != null && track.isPlayable()) {
                out.add(treeItem(track));
            }
        }
    }

    /** A browsable folder node. */
    private MediaBrowser.MediaItem node(String id, String title, String subtitle,
                                        boolean browsable) {
        MediaDescription description = new MediaDescription.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build();
        return new MediaBrowser.MediaItem(description,
                browsable ? MediaBrowser.MediaItem.FLAG_BROWSABLE
                        : MediaBrowser.MediaItem.FLAG_PLAYABLE);
    }

    /** A playable leaf backed by a framework {@link MediaItem}. */
    private MediaBrowser.MediaItem treeItem(MediaItem item) {
        MediaDescription.Builder description = new MediaDescription.Builder()
                .setMediaId(item.identity())
                .setTitle(item.title)
                .setSubtitle(item.author);
        if (item.durationSec > 0) {
            description.setExtras(durationExtras(item.durationSec * 1000L));
        }
        boolean browsable = item.isBrowsable();
        return new MediaBrowser.MediaItem(description.build(),
                browsable ? MediaBrowser.MediaItem.FLAG_BROWSABLE
                        : MediaBrowser.MediaItem.FLAG_PLAYABLE);
    }

    private static Bundle durationExtras(long durationMs) {
        Bundle extras = new Bundle();
        extras.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
        return extras;
    }

    /** Turns a browsed id back into the queue the app would have played. */
    public List<MediaItem> queueFor(String mediaId) {
        if (mediaId != null && mediaId.startsWith(PLAYLIST_PREFIX)) {
            return library.playlistTracks(mediaId.substring(PLAYLIST_PREFIX.length()));
        }
        if (NODE_LIKED.equals(mediaId)) {
            return library.list(Library.LIKED);
        }
        if (NODE_DOWNLOADS.equals(mediaId)) {
            return new com.soniq.base.data.Downloads(this).all();
        }
        if (NODE_RECENT.equals(mediaId)) {
            return library.recent(20);
        }
        List<MediaItem> providerItems = mediaId != null && mediaId.startsWith(PROVIDER_PREFIX)
                ? browseProviderNode(mediaId.substring(PROVIDER_PREFIX.length()))
                : new ArrayList<MediaItem>();
        return providerItems;
    }

    /** PendingIntent a car client can use to open the full app. */
    protected PendingIntent openAppIntent() {
        Intent intent = new Intent(this, com.soniq.base.ui.MusicActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 7, intent, flags);
    }
}
