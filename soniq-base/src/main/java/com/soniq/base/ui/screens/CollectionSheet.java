package com.soniq.base.ui.screens;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.model.MediaItem;
import com.soniq.base.ui.Host;
import com.soniq.base.ui.NetworkImage;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;
import com.soniq.base.ui.views.TrackRow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** Album / playlist / artist detail, loaded from the public Music browse API. */
public final class CollectionSheet {

    private final Host host;
    private final FrameLayout root;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView statusView;
    private final LinearLayout tracksContainer;
    private final NetworkImage cover;
    private final List<MediaItem> tracks = new ArrayList<>();
    private int request;
    private Future<?> browseTask;

    public CollectionSheet(Host host) {
        this.host = host;
        this.root = new FrameLayout(host.context());
        root.setBackgroundColor(Theme.BG);
        root.setVisibility(View.GONE);

        ScrollView scroll = new ScrollView(host.context());
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout column = Ui.column(host.context());
        column.setPadding(Ui.dp(host.context(), 16), Ui.dp(host.context(), 8),
                Ui.dp(host.context(), 16), Ui.dp(host.context(), 32));
        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = Ui.row(host.context());
        top.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_arrow_back,
                Theme.TEXT, 44, 22, "Back", new Runnable() {
                    @Override
                    public void run() {
                        hide();
                    }
                }));
        TextView label = Ui.text(host.context(), "", 11f, Theme.TEXT_DIM, true);
        label.setPadding(Ui.dp(host.context(), 4), 0, 0, 0);
        top.addView(label, Ui.weight(1f));
        column.addView(top);

        LinearLayout header = Ui.row(host.context());
        header.setPadding(0, Ui.dp(host.context(), 8), 0, Ui.dp(host.context(), 16));
        cover = new NetworkImage(host.context());
        cover.setRadiusDp(16f);
        header.addView(cover, new LinearLayout.LayoutParams(Ui.dp(host.context(), 132),
                Ui.dp(host.context(), 132)));
        LinearLayout titles = Ui.column(host.context());
        titles.setPadding(Ui.dp(host.context(), 14), 0, 0, 0);
        titleView = Ui.text(host.context(), "", 20f, Theme.TEXT, true);
        titleView.setMaxLines(2);
        titles.addView(titleView);
        subtitleView = Ui.text(host.context(), "", 12f, Theme.TEXT_DIM, false);
        subtitleView.setPadding(0, Ui.dp(host.context(), 6), 0, Ui.dp(host.context(), 12));
        titles.addView(subtitleView);

        LinearLayout buttons = Ui.row(host.context());
        buttons.addView(button("Play all", new Runnable() {
            @Override
            public void run() {
                if (!tracks.isEmpty()) {
                    host.play(tracks, 0);
                }
            }
        }));
        buttons.addView(button("Shuffle", new Runnable() {
            @Override
            public void run() {
                if (tracks.isEmpty()) {
                    return;
                }
                host.play(tracks, 0);
                if (host.service() != null) {
                    host.service().setShuffle(true);
                }
            }
        }));
        titles.addView(buttons);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(header);

        statusView = Ui.text(host.context(), "", 12f, Theme.TEXT_DIM, false);
        statusView.setPadding(Ui.dp(host.context(), 4), 0, Ui.dp(host.context(), 4),
                Ui.dp(host.context(), 8));
        column.addView(statusView);

        tracksContainer = Ui.column(host.context());
        column.addView(tracksContainer);
    }

    private LinearLayout button(String text, final Runnable onClick) {
        LinearLayout button = Ui.row(host.context());
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.rounded(host.context(), Theme.ACCENT, 17f));
        button.setPadding(Ui.dp(host.context(), 14), Ui.dp(host.context(), 8),
                Ui.dp(host.context(), 14), Ui.dp(host.context(), 8));
        button.addView(Ui.text(host.context(), text, 12f, 0xFFFFFFFF, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Ui.dp(host.context(), 8);
        button.setLayoutParams(params);
        button.setClickable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClick.run();
            }
        });
        return button;
    }

    public View view() {
        return root;
    }

    public void show(MediaItem item) {
        if (item == null || !host.isAlive()) {
            return;
        }
        if (browseTask != null) {
            browseTask.cancel(true);
            browseTask = null;
        }
        root.setVisibility(View.VISIBLE);
        titleView.setText(item.title);
        subtitleView.setText(item.subtitle());
        cover.load(item.bestThumb());
        tracks.clear();
        tracksContainer.removeAllViews();
        statusView.setText("Loading…");
        final int currentRequest = ++request;

        if (item.isLocalPlaylist()) {
            tracks.addAll(host.library().playlistTracks(item.id));
            render(currentRequest);
            return;
        }
        browseTask = host.io().submit(new Runnable() {
            @Override
            public void run() {
                List<MediaItem> loadedValue = null;
                Exception failureValue = null;
                try {
                    loadedValue = host.provider().browse(item);
                } catch (Exception error) {
                    failureValue = error;
                }
                final List<MediaItem> loaded = loadedValue;
                final Exception failure = failureValue;
                host.main().post(new Runnable() {
                    @Override
                    public void run() {
                        if (!host.isAlive() || currentRequest != request) {
                            return;
                        }
                        browseTask = null;
                        if (failure != null) {
                            statusView.setText("Unavailable: " + failure.getMessage());
                            return;
                        }
                        tracks.clear();
                        tracks.addAll(loaded);
                        render(currentRequest);
                    }
                });
            }
        });
    }

    private void render(int currentRequest) {
        if (currentRequest != request) {
            return;
        }
        statusView.setText(tracks.size() + (tracks.size() == 1 ? " track" : " tracks"));
        tracksContainer.removeAllViews();
        if (tracks.isEmpty()) {
            statusView.setText("No playable tracks were returned.");
            return;
        }
        for (int i = 0; i < tracks.size(); i++) {
            final int index = i;
            final MediaItem item = tracks.get(i);
            tracksContainer.addView(new TrackRow(host.context(), item, i, true, false,
                    new TrackRow.Callback() {
                        @Override
                        public void onClick(MediaItem clicked, int position) {
                            if (clicked.isArtist()) {
                                host.openArtist(clicked);
                            } else if (clicked.isCollection()) {
                                host.openCollection(clicked);
                            } else {
                                host.play(tracks, index);
                            }
                        }

                        @Override
                        public void onMore(View anchor, MediaItem clicked, int position) {
                            if (clicked.isBrowsable()) {
                                host.addToPlaylist(clicked);
                            } else {
                                host.showTrackMenu(clicked);
                            }
                        }

                        @Override
                        public void onLongClick(MediaItem clicked, int position) {
                            host.addToPlaylist(clicked);
                        }
                    }));
        }
    }

    public void hide() {
        root.setVisibility(View.GONE);
        request++;
        if (browseTask != null) {
            browseTask.cancel(true);
            browseTask = null;
        }
    }

    public void release() {
        request++;
        if (browseTask != null) {
            browseTask.cancel(true);
            browseTask = null;
        }
    }

    public boolean isShowing() {
        return root.getVisibility() == View.VISIBLE;
    }
}
