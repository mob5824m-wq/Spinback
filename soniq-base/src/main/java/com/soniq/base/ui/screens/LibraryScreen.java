package com.soniq.base.ui.screens;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.data.Library;
import com.soniq.base.model.MediaItem;
import com.soniq.base.ui.Host;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;
import com.soniq.base.ui.views.TrackRow;

import java.util.ArrayList;
import java.util.List;

/** Liked songs, saved artists, saved collections, local playlists and downloads. */
public final class LibraryScreen extends Screen {

    private MediaItem openPlaylist;
    private List<MediaItem> playlistTracks = new ArrayList<>();

    public LibraryScreen(Host host) {
        super(host);
    }

    /** Opens a local playlist directly (used when a local playlist is tapped elsewhere). */
    public void openLocalPlaylist(MediaItem item) {
        if (item == null || !item.isLocalPlaylist()) {
            return;
        }
        openPlaylist = item;
        playlistTracks = host.library().playlistTracks(item.id);
        refresh();
    }

    @Override
    protected void build(LinearLayout container) {
        if (openPlaylist != null) {
            buildPlaylist(container);
            return;
        }
        container.addView(Ui.text(host.context(), "Library", 28f, Theme.TEXT, true));
        container.addView(Ui.text(host.context(),
                "Local only · no account · no sync", 12f, Theme.TEXT_DIM, false));

        List<MediaItem> liked = com.soniq.base.data.LibrarySort.apply(
                host.library().list(Library.LIKED), host.settings().librarySort());
        container.addView(Ui.sectionHeader(host.context(), "Liked songs · " + liked.size()));
        container.addView(Ui.caption(host.context(),
                "Sorted by " + host.settings().librarySortLabel().toLowerCase(
                        java.util.Locale.ROOT)
                        + " · change this in Settings"));
        if (liked.isEmpty()) {
            container.addView(Ui.caption(host.context(),
                    "Tap the heart on a song to save it here."));
        } else {
            addTrackList(container, liked, Math.min(10, liked.size()));
        }

        List<MediaItem> artists = host.library().list(Library.ARTISTS);
        container.addView(Ui.sectionHeader(host.context(), "Saved artists · " + artists.size()));
        if (artists.isEmpty()) {
            container.addView(Ui.caption(host.context(),
                    "Search for an artist, then long-press to save them."));
        } else {
            addBrowsableList(container, artists);
        }

        List<MediaItem> collections = host.library().list(Library.COLLECTIONS);
        container.addView(Ui.sectionHeader(host.context(),
                "Saved albums & playlists · " + collections.size()));
        if (collections.isEmpty()) {
            container.addView(Ui.caption(host.context(),
                    "Long-press an album or playlist to save it."));
        } else {
            addBrowsableList(container, collections);
        }

        List<MediaItem> playlists = host.library().playlists();
        container.addView(Ui.sectionHeader(host.context(),
                "Your playlists · " + playlists.size()));
        container.addView(newPlaylistRow());
        for (int i = 0; i < playlists.size(); i++) {
            final MediaItem playlist = playlists.get(i);
            int count = host.library().playlistTracks(playlist.id).size();
            TrackRow row = new TrackRow(host.context(), playlist, i, true, false,
                    new TrackRow.Callback() {
                        @Override
                        public void onClick(MediaItem clicked, int position) {
                            openPlaylist = clicked;
                            playlistTracks = host.library().playlistTracks(clicked.id);
                            refresh();
                        }

                        @Override
                        public void onMore(View anchor, MediaItem clicked, int position) {
                            confirmDeletePlaylist(clicked);
                        }

                        @Override
                        public void onLongClick(MediaItem clicked, int position) {
                            confirmDeletePlaylist(clicked);
                        }
                    });
            container.addView(row);
            // Show track count as a caption line under each playlist row.
            container.addView(captionRight(count + " tracks"));
        }

        List<MediaItem> downloads = host.downloads().all();
        LinearLayout downloadHeader = Ui.row(host.context());
        downloadHeader.setPadding(dp(4), dp(14), dp(4), dp(8));
        downloadHeader.addView(Ui.icon(host.context(), R.drawable.soniq_ic_download_done,
                Theme.ACCENT, 18));
        TextView downloadTitle = Ui.text(host.context(),
                "Downloads · " + downloads.size(), 17f, Theme.TEXT, true);
        downloadTitle.setPadding(dp(8), 0, 0, 0);
        downloadHeader.addView(downloadTitle);
        container.addView(downloadHeader);
        if (downloads.isEmpty()) {
            container.addView(Ui.caption(host.context(),
                    "Download a song from the player to listen offline."));
        } else {
            addTrackList(container, downloads, Math.min(20, downloads.size()));
        }
    }

    private void buildPlaylist(LinearLayout container) {
        LinearLayout header = Ui.row(host.context());
        header.setPadding(0, dp(4), 0, dp(8));
        header.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_arrow_back,
                Theme.TEXT, 40, 22, "Back", new Runnable() {
                    @Override
                    public void run() {
                        openPlaylist = null;
                        playlistTracks = new ArrayList<>();
                        refresh();
                    }
                }));
        LinearLayout titles = Ui.column(host.context());
        titles.addView(Ui.text(host.context(), openPlaylist.title, 22f, Theme.TEXT, true));
        titles.addView(Ui.text(host.context(),
                playlistTracks.size() + " tracks · local playlist", 12f, Theme.TEXT_DIM, false));
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(header);

        LinearLayout actions = Ui.row(host.context());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(4), 0, dp(12));
        actions.addView(actionButton("Play all", new Runnable() {
            @Override
            public void run() {
                if (!playlistTracks.isEmpty()) {
                    host.play(playlistTracks, 0);
                }
            }
        }));
        actions.addView(actionButton("Shuffle", new Runnable() {
            @Override
            public void run() {
                if (!playlistTracks.isEmpty()) {
                    host.play(playlistTracks, 0);
                    if (host.service() != null) {
                        host.service().setShuffle(true);
                    }
                }
            }
        }));
        container.addView(actions);

        if (playlistTracks.isEmpty()) {
            container.addView(Ui.emptyState(host.context(), "Empty playlist",
                    "Long-press a song anywhere and choose this playlist."));
            return;
        }
        playlistTracks = com.soniq.base.data.LibrarySort.apply(playlistTracks,
                host.settings().librarySort());
        for (int i = 0; i < playlistTracks.size(); i++) {
            final int index = i;
            final MediaItem item = playlistTracks.get(i);
            container.addView(new TrackRow(host.context(), item, i, true, false,
                    new TrackRow.Callback() {
                        @Override
                        public void onClick(MediaItem clicked, int position) {
                            host.play(playlistTracks, index);
                        }

                        @Override
                        public void onMore(View anchor, MediaItem clicked, int position) {
                            host.library().removeFromPlaylist(openPlaylist.id, clicked);
                            playlistTracks = host.library().playlistTracks(openPlaylist.id);
                            refresh();
                            host.showToast("Removed from " + openPlaylist.title);
                        }

                        @Override
                        public void onLongClick(MediaItem clicked, int position) {
                            host.addToPlaylist(clicked);
                        }
                    }));
        }
    }

    private View newPlaylistRow() {
        LinearLayout row = Ui.row(host.context());
        row.setPadding(dp(8), dp(10), dp(8), dp(10));
        row.setBackground(Ui.ripple(host.context(), Theme.SURFACE, 12f));
        row.setClickable(true);
        row.addView(Ui.icon(host.context(), R.drawable.soniq_ic_add, Theme.ACCENT, 22));
        LinearLayout info = Ui.column(host.context());
        info.setPadding(dp(12), 0, 0, 0);
        info.addView(Ui.text(host.context(), "New playlist", 14f, Theme.TEXT, true));
        info.addView(Ui.text(host.context(),
                "Create an empty local playlist", 11f, Theme.TEXT_DIM, false));
        row.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                host.createPlaylist();
            }
        });
        return row;
    }

    private LinearLayout actionButton(String label, final Runnable onClick) {
        LinearLayout button = Ui.row(host.context());
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.rounded(host.context(), Theme.ACCENT, 18f));
        button.setPadding(dp(18), dp(9), dp(18), dp(9));
        button.addView(Ui.text(host.context(), label, 13f, 0xFFFFFFFF, true));
        button.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        button.setLayoutParams(params);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClick.run();
            }
        });
        return button;
    }

    private android.widget.TextView captionRight(String value) {
        android.widget.TextView caption = Ui.text(host.context(), value, 11f,
                Theme.TEXT_FAINT, false);
        caption.setGravity(Gravity.END);
        caption.setPadding(0, 0, dp(44), dp(4));
        return caption;
    }

    private void addTrackList(LinearLayout container, final List<MediaItem> items, int limit) {
        for (int i = 0; i < limit; i++) {
            final int index = i;
            final MediaItem item = items.get(i);
            container.addView(new TrackRow(host.context(), item, i, true, false,
                    new TrackRow.Callback() {
                        @Override
                        public void onClick(MediaItem clicked, int position) {
                            host.play(items, index);
                        }

                        @Override
                        public void onMore(View anchor, MediaItem clicked, int position) {
                            host.showTrackMenu(clicked);
                        }

                        @Override
                        public void onLongClick(MediaItem clicked, int position) {
                            host.addToPlaylist(clicked);
                        }
                    }));
        }
    }

    private void addBrowsableList(LinearLayout container, List<MediaItem> items) {
        for (int i = 0; i < items.size(); i++) {
            final MediaItem item = items.get(i);
            container.addView(new TrackRow(host.context(), item, i, true, false,
                    new TrackRow.Callback() {
                        @Override
                        public void onClick(MediaItem clicked, int position) {
                            if (clicked.isArtist()) {
                                host.openArtist(clicked);
                            } else if (clicked.isCollection()) {
                                if (clicked.isLocalPlaylist()) {
                                    openPlaylist = clicked;
                                    playlistTracks =
                                            host.library().playlistTracks(clicked.id);
                                    refresh();
                                } else {
                                    host.openCollection(clicked);
                                }
                            } else {
                                host.playSingle(clicked);
                            }
                        }

                        @Override
                        public void onMore(View anchor, MediaItem clicked, int position) {
                            if (clicked.isCollection()) {
                                host.library().remove(Library.COLLECTIONS, clicked);
                                host.showToast("Removed from saved collections");
                                refresh();
                            } else if (clicked.isArtist()) {
                                host.library().remove(Library.ARTISTS, clicked);
                                host.showToast("Removed from saved artists");
                                refresh();
                            }
                        }

                        @Override
                        public void onLongClick(MediaItem clicked, int position) {
                            host.addToPlaylist(clicked);
                        }
                    }));
        }
    }

    private void confirmDeletePlaylist(final MediaItem playlist) {
        if (!playlist.isLocalPlaylist()) {
            return;
        }
        new AlertDialog.Builder(host.context())
                .setTitle("Delete playlist?")
                .setMessage("\"" + playlist.title + "\" will be removed from this device. "
                        + "Downloaded songs are not deleted.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.library().deletePlaylist(playlist);
                        host.showToast("Playlist deleted");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
