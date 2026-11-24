package com.soniq.base.ui.screens;

import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.data.Library;
import com.soniq.base.model.MediaItem;
import com.soniq.base.playback.PlaybackSession;
import com.soniq.base.playback.ResumePoint;
import com.soniq.base.playback.PlaybackService;
import com.soniq.base.ui.A11y;
import com.soniq.base.ui.Host;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;
import com.soniq.base.ui.views.Chip;
import com.soniq.base.ui.views.TrackRow;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Landing tab: quick actions, quick picks and recently played. */
public final class HomeScreen extends Screen {

    private LinearLayout picksContainer;
    private final List<MediaItem> picks = new ArrayList<>();
    private boolean loadingPicks;

    public HomeScreen(Host host) {
        super(host);
    }

    @Override
    protected void build(LinearLayout container) {
        container.addView(Ui.text(host.context(), greeting(), 28f, Theme.TEXT, true));
        String tagline = host.provider().tagline();
        container.addView(Ui.text(host.context(),
                tagline.length() > 0 ? tagline : host.provider().displayName(),
                12f, Theme.TEXT_DIM, false));

        LinearLayout resumeCard = buildResumeCard();
        if (resumeCard != null) {
            container.addView(resumeCard);
        }

        HorizontalScrollView chipScroll = new HorizontalScrollView(host.context());
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(0, dp(16), 0, dp(4));
        LinearLayout chipRow = Ui.row(host.context());
        chipRow.setPadding(0, 0, 0, 0);
        chipRow.addView(new Chip(host.context(), "Quick play", new Runnable() {
            @Override
            public void run() {
                host.quickPlay();
            }
        }), chipParams());
        chipRow.addView(new Chip(host.context(), "Liked songs", new Runnable() {
            @Override
            public void run() {
                List<MediaItem> liked = host.library().list(Library.LIKED);
                if (liked.isEmpty()) {
                    host.showToast("Like a song first — tap the heart in the player.");
                } else {
                    host.play(liked, 0);
                }
            }
        }), chipParams());
        chipRow.addView(new Chip(host.context(), "Downloads", new Runnable() {
            @Override
            public void run() {
                List<MediaItem> offline = host.downloads().all();
                if (offline.isEmpty()) {
                    host.showToast("No offline songs yet.");
                } else {
                    host.play(offline, 0);
                }
            }
        }), chipParams());
        chipRow.addView(new Chip(host.context(), "Year in review", new Runnable() {
            @Override
            public void run() {
                host.openInsights();
            }
        }), chipParams());
        chipRow.addView(new Chip(host.context(), "Library", new Runnable() {
            @Override
            public void run() {
                host.openLibrary();
            }
        }), chipParams());
        chipScroll.addView(chipRow);
        container.addView(chipScroll);

        container.addView(Ui.sectionHeader(host.context(), "Quick picks"));
        picksContainer = Ui.column(host.context());
        container.addView(picksContainer);
        if (picks.isEmpty()) {
            if (!loadingPicks) {
                loadPicks();
            }
            TextView loading = Ui.text(host.context(),
                    loadingPicks ? "Searching " + host.provider().displayName() + "…"
                            : "No quick picks yet.",
                    13f, Theme.TEXT_DIM, false);
            loading.setPadding(dp(4), dp(8), dp(4), dp(8));
            picksContainer.addView(loading);
        } else {
            renderPicks();
        }

        container.addView(Ui.sectionHeader(host.context(), "Recently played"));
        List<MediaItem> recent = host.library().recent(6);
        if (recent.isEmpty()) {
            container.addView(Ui.caption(host.context(),
                    "Songs you play will show up here."));
        } else {
            for (int i = 0; i < recent.size(); i++) {
                final int index = i;
                final MediaItem item = recent.get(i);
                container.addView(new TrackRow(host.context(), item, i, true, false,
                        new TrackRow.Callback() {
                            @Override
                            public void onClick(MediaItem clicked, int position) {
                                host.play(recent, index);
                            }

                            @Override
                            public void onMore(android.view.View anchor,
                                                MediaItem clicked, int position) {
                                host.showTrackMenu(clicked);
                            }

                            @Override
                            public void onLongClick(MediaItem clicked, int position) {
                                host.addToPlaylist(clicked);
                            }
                        }));
            }
        }
    }

    private void loadPicks() {
        loadingPicks = true;
        runIo(new Runnable() {
            @Override
            public void run() {
                final List<MediaItem> result;
                try {
                    result = host.provider().quickPicks();
                } catch (Exception error) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            loadingPicks = false;
                            if (picksContainer != null) {
                                picksContainer.removeAllViews();
                                picksContainer.addView(Ui.caption(host.context(),
                                        "Quick picks are unavailable: " + error.getMessage()));
                            }
                        }
                    });
                    return;
                }
                post(new Runnable() {
                    @Override
                    public void run() {
                        loadingPicks = false;
                        picks.clear();
                        for (MediaItem item : result) {
                            if (!item.isBrowsable()) {
                                picks.add(item);
                            }
                        }
                        if (picksContainer != null) {
                            picksContainer.removeAllViews();
                            renderPicks();
                        }
                    }
                });
            }
        });
    }

    private void renderPicks() {
        if (picksContainer == null) {
            return;
        }
        if (picks.isEmpty()) {
            picksContainer.addView(Ui.caption(host.context(),
                    "No quick picks yet. Search for a song to get started."));
            return;
        }
        int limit = Math.min(8, picks.size());
        for (int i = 0; i < limit; i++) {
            final int index = i;
            final MediaItem item = picks.get(i);
            picksContainer.addView(new TrackRow(host.context(), item, i, true, false,
                    new TrackRow.Callback() {
                        @Override
                        public void onClick(MediaItem clicked, int position) {
                            host.play(picks, index);
                        }

                        @Override
                        public void onMore(android.view.View anchor,
                                            MediaItem clicked, int position) {
                            host.showTrackMenu(clicked);
                        }

                        @Override
                        public void onLongClick(MediaItem clicked, int position) {
                            host.addToPlaylist(clicked);
                        }
                    }));
        }
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        return params;
    }

    private static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) {
            return "Late night listening";
        }
        if (hour < 12) {
            return "Good morning";
        }
        if (hour < 18) {
            return "Good afternoon";
        }
        return "Good evening";
    }


    /**
     * "Continue listening": one tap resumes the exact track and position. Only
     * shown when the saved point is still meaningful — a resume from last month,
     * or one 4 seconds into a song, is not offered.
     */
    private LinearLayout buildResumeCard() {
        if (!host.settings().resumeEnabled() || host.service() == null) {
            return null;
        }
        final ResumePoint point;
        try {
            point = new PlaybackSession(host.context()).resumePoint();
        } catch (RuntimeException unavailable) {
            return null;
        }
        if (point == null || !point.isUsable(System.currentTimeMillis())) {
            return null;
        }
        LinearLayout card = Ui.row(host.context());
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(Ui.ripple(host.context(), Theme.SURFACE_HIGH, 14f));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout info = Ui.column(host.context());
        info.addView(Ui.text(host.context(), "Continue listening", 11f, Theme.ACCENT, true));
        info.addView(Ui.text(host.context(), point.title, 15f, Theme.TEXT, true));
        info.addView(Ui.text(host.context(), point.summary(), 12f, Theme.TEXT_DIM, false));
        card.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(Ui.icon(host.context(), R.drawable.soniq_ic_play, Theme.TEXT, 26));
        A11y.label(card, "Continue listening: " + point.title + ", " + point.summary());
        card.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                PlaybackService service = host.service();
                if (service == null) {
                    host.showToast("Playback is still starting…");
                    return;
                }
                service.restoreQueue();
                host.openPlayer();
            }
        });

        LinearLayout wrapper = Ui.column(host.context());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        wrapper.addView(card, params);
        return wrapper;
    }
}
