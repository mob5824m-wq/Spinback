package com.soniq.base.ui.screens;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.data.LyricLine;
import com.soniq.base.data.LyricsApi;
import com.soniq.base.data.LyricsRepository;
import com.soniq.base.data.LyricsResult;
import com.soniq.base.model.LyricsState;
import com.soniq.base.data.ScrobbleQueue;
import com.soniq.base.model.MediaItem;
import com.soniq.base.playback.PlaybackQueue;
import com.soniq.base.playback.PlaybackService;
import com.soniq.base.ui.Host;
import com.soniq.base.ui.NetworkImage;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;
import com.soniq.base.ui.views.LyricsView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Full-screen now-playing sheet with artwork, transport, lyrics and actions. */
public final class PlayerSheet {

    private final Host host;
    private final FrameLayout root;
    private final ScrollView scroll;
    private final NetworkImage art;
    private final TextView title;
    private final TextView artist;
    private final TextView album;
    private final TextView lyricBanner;
    private final TextView positionLabel;
    private final TextView durationLabel;
    private final TextView statusLabel;
    private final SeekBar seekBar;
    private final ImageView playIcon;
    private final ImageView shuffleIcon;
    private final ImageView repeatIcon;
    private final ImageView likeIcon;
    private final LyricsView lyricsView;
    private final LinearLayout lyricsPanel;
    private final List<LyricLine> lyrics = new ArrayList<>();
    private final ExecutorService lyricsExecutor = Executors.newSingleThreadExecutor();
    private final LyricsRepository lyricsRepository;

    private boolean scrubbing;
    private boolean lyricsVisible;
    private String lyricsKey = "";
    private int lyricsRequest;
    private Future<?> lyricsTask;
    private LyricsApi.CancellationSignal lyricsCancellation;

    public PlayerSheet(Host host) {
        this.host = host;
        this.lyricsRepository = new LyricsRepository();
        this.root = new FrameLayout(host.context());
        root.setBackgroundColor(Theme.BG);
        root.setVisibility(View.GONE);

        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.withAlpha(Theme.ACCENT, 0x38), Theme.BG, Theme.BG});
        root.setBackground(gradient);

        scroll = new ScrollView(host.context());
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout column = Ui.column(host.context());
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(Ui.dp(host.context(), 24), Ui.dp(host.context(), 12),
                Ui.dp(host.context(), 24), Ui.dp(host.context(), 32));
        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = Ui.row(host.context());
        topBar.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_close,
                Theme.TEXT_DIM, 44, 22, "Close player", new Runnable() {
                    @Override
                    public void run() {
                        hide();
                    }
                }));
        TextView nowPlaying = Ui.text(host.context(), "NOW PLAYING", 11f,
                Theme.TEXT_DIM, true);
        nowPlaying.setGravity(Gravity.CENTER);
        topBar.addView(nowPlaying, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topBar.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_more,
                Theme.TEXT_DIM, 44, 20, "More track actions", new Runnable() {
                    @Override
                    public void run() {
                        MediaItem item = service() == null ? null : service().current();
                        if (item != null) {
                            host.showTrackMenu(item);
                        }
                    }
                }));
        column.addView(topBar);

        art = new NetworkImage(host.context());
        art.setRadiusDp(20f);
        int artSize = Ui.dp(host.context(), 300);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(artSize, artSize);
        artParams.topMargin = Ui.dp(host.context(), 8);
        artParams.bottomMargin = Ui.dp(host.context(), 20);
        column.addView(art, artParams);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int availableWidth = scroll.getWidth()
                        - column.getPaddingLeft() - column.getPaddingRight();
                int size = Math.max(1, Math.min(Ui.dp(host.context(), 300), availableWidth));
                ViewGroup.LayoutParams params = art.getLayoutParams();
                if (params.width != size || params.height != size) {
                    params.width = size;
                    params.height = size;
                    art.setLayoutParams(params);
                }
            }
        });

        lyricBanner = Ui.text(host.context(), "", 12f, Theme.ACCENT, true);
        lyricBanner.setGravity(Gravity.CENTER);
        lyricBanner.setSingleLine(true);
        lyricBanner.setEllipsize(android.text.TextUtils.TruncateAt.END);
        lyricBanner.setPadding(0, 0, 0, Ui.dp(host.context(), 8));
        column.addView(lyricBanner);

        LinearLayout titleRow = Ui.row(host.context());
        LinearLayout titles = Ui.column(host.context());
        title = Ui.text(host.context(), "Nothing playing", 22f, Theme.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(title);
        artist = Ui.text(host.context(), "Pick a song to start", 14f, Theme.TEXT_DIM, false);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(artist);
        album = Ui.text(host.context(), "", 12f, Theme.TEXT_FAINT, false);
        album.setSingleLine(true);
        album.setEllipsize(android.text.TextUtils.TruncateAt.END);
        album.setVisibility(View.GONE);
        titles.addView(album);
        titleRow.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        likeIcon = new ImageView(host.context());
        likeIcon.setImageResource(R.drawable.soniq_ic_favorite_border);
        likeIcon.setColorFilter(Theme.TEXT_DIM);
        likeIcon.setPadding(Ui.dp(host.context(), 12), Ui.dp(host.context(), 12),
                Ui.dp(host.context(), 12), Ui.dp(host.context(), 12));
        likeIcon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(host.context(), 48),
                Ui.dp(host.context(), 48)));
        likeIcon.setContentDescription("Like this song");
        likeIcon.setFocusable(true);
        likeIcon.setClickable(true);
        likeIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaItem item = service() == null ? null : service().current();
                if (item != null) {
                    host.toggleLike(item);
                }
            }
        });
        titleRow.addView(likeIcon);
        column.addView(titleRow);

        seekBar = new SeekBar(host.context());
        seekBar.setMax(1);
        seekBar.setProgress(0);
        seekBar.setContentDescription("Track position");
        seekBar.setPadding(Ui.dp(host.context(), 4), 0, Ui.dp(host.context(), 4), 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    positionLabel.setText(Theme.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                scrubbing = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scrubbing = false;
                if (service() != null) {
                    service().seekTo(seekBar.getProgress());
                }
            }
        });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host.context(), 48));
        seekParams.topMargin = Ui.dp(host.context(), 8);
        column.addView(seekBar, seekParams);

        LinearLayout times = Ui.row(host.context());
        positionLabel = Ui.text(host.context(), "0:00", 11f, Theme.TEXT_DIM, false);
        durationLabel = Ui.text(host.context(), "0:00", 11f, Theme.TEXT_DIM, false);
        times.addView(positionLabel, Ui.weight(1f));
        times.addView(durationLabel);
        column.addView(times);

        statusLabel = Ui.text(host.context(), "", 12f, Theme.ACCENT, false);
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setPadding(0, Ui.dp(host.context(), 6), 0, 0);
        column.addView(statusLabel);

        LinearLayout transport = Ui.row(host.context());
        transport.setGravity(Gravity.CENTER);
        transport.setPadding(0, Ui.dp(host.context(), 10), 0, Ui.dp(host.context(), 4));
        shuffleIcon = transportIcon(R.drawable.soniq_ic_shuffle, Theme.TEXT_DIM,
                "Toggle shuffle", new Runnable() {
                    @Override
                    public void run() {
                        if (service() != null) {
                            service().setShuffle(!service().isShuffle());
                        }
                    }
                });
        transport.addView(shuffleIcon);
        transport.addView(transportIcon(R.drawable.soniq_ic_prev, Theme.TEXT,
                "Previous track", new Runnable() {
                    @Override
                    public void run() {
                        if (service() != null) {
                            service().previous();
                        }
                    }
                }));
        playIcon = new ImageView(host.context());
        playIcon.setImageResource(R.drawable.soniq_ic_play);
        playIcon.setColorFilter(Color.WHITE);
        playIcon.setContentDescription("Play");
        playIcon.setFocusable(true);
        int playSize = Ui.dp(host.context(), 72);
        playIcon.setPadding(Ui.dp(host.context(), 22), Ui.dp(host.context(), 22),
                Ui.dp(host.context(), 22), Ui.dp(host.context(), 22));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(playSize, playSize);
        playParams.leftMargin = Ui.dp(host.context(), 8);
        playParams.rightMargin = Ui.dp(host.context(), 8);
        playIcon.setLayoutParams(playParams);
        GradientDrawable playBackground = Ui.rounded(host.context(), Theme.ACCENT, 36f);
        playIcon.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                playBackground, Ui.rounded(host.context(), 0xFFFFFFFF, 36f)));
        playIcon.setClickable(true);
        playIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (service() != null) {
                    service().toggle();
                }
            }
        });
        transport.addView(playIcon);
        transport.addView(transportIcon(R.drawable.soniq_ic_next, Theme.TEXT,
                "Next track", new Runnable() {
                    @Override
                    public void run() {
                        if (service() != null) {
                            service().next();
                        }
                    }
                }));
        repeatIcon = transportIcon(R.drawable.soniq_ic_repeat, Theme.TEXT_DIM,
                "Cycle repeat mode", new Runnable() {
                    @Override
                    public void run() {
                        if (service() != null) {
                            service().cycleRepeat();
                        }
                    }
                });
        transport.addView(repeatIcon);
        HorizontalScrollView transportScroll = new HorizontalScrollView(host.context());
        transportScroll.setHorizontalScrollBarEnabled(false);
        transportScroll.setFillViewport(true);
        transportScroll.addView(transport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(transportScroll, Ui.matchWrap());

        LinearLayout actions = Ui.row(host.context());
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, Ui.dp(host.context(), 8), 0, Ui.dp(host.context(), 8));
        actions.addView(action("Lyrics", R.drawable.soniq_ic_lyrics, new Runnable() {
            @Override
            public void run() {
                toggleLyrics();
            }
        }));
        actions.addView(action("Save", R.drawable.soniq_ic_download, new Runnable() {
            @Override
            public void run() {
                MediaItem item = service() == null ? null : service().current();
                if (item != null) {
                    host.download(item);
                }
            }
        }));
        actions.addView(action("Queue", R.drawable.soniq_ic_queue, new Runnable() {
            @Override
            public void run() {
                host.showQueue();
            }
        }));
        actions.addView(action("Radio", R.drawable.soniq_ic_radio, new Runnable() {
            @Override
            public void run() {
                MediaItem item = service() == null ? null : service().current();
                if (item != null) {
                    host.startRadio(item);
                }
            }
        }));
        HorizontalScrollView actionScroll = new HorizontalScrollView(host.context());
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.setFillViewport(true);
        actionScroll.addView(actions, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(actionScroll, Ui.matchWrap());

        lyricsPanel = Ui.column(host.context());
        lyricsPanel.setVisibility(View.GONE);
        lyricsView = new LyricsView(host.context());
        lyricsView.setSeekCallback(new LyricsView.SeekCallback() {
            @Override
            public void onSeekTo(long positionMs) {
                if (service() != null) {
                    service().seekTo((int) positionMs);
                }
            }
        });
        LinearLayout.LayoutParams lyricsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host.context(), 260));
        lyricsParams.topMargin = Ui.dp(host.context(), 8);
        lyricsPanel.addView(lyricsView, lyricsParams);
        column.addView(lyricsPanel);
    }

    private ImageView transportIcon(int resource, int tint, String description,
                                    final Runnable onClick) {
        ImageView image = new ImageView(host.context());
        image.setImageResource(resource);
        image.setColorFilter(tint);
        image.setContentDescription(description);
        image.setFocusable(true);
        int size = Ui.dp(host.context(), 48);
        image.setPadding(Ui.dp(host.context(), 12), Ui.dp(host.context(), 12),
                Ui.dp(host.context(), 12), Ui.dp(host.context(), 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.leftMargin = Ui.dp(host.context(), 2);
        params.rightMargin = Ui.dp(host.context(), 2);
        image.setLayoutParams(params);
        image.setBackground(Ui.ripple(host.context(), 0x00000000, 24f));
        image.setClickable(true);
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClick.run();
            }
        });
        return image;
    }

    private LinearLayout action(String label, int icon, final Runnable onClick) {
        LinearLayout button = Ui.column(host.context());
        button.setGravity(Gravity.CENTER);
        button.setPadding(Ui.dp(host.context(), 12), Ui.dp(host.context(), 8),
                Ui.dp(host.context(), 12), Ui.dp(host.context(), 8));
        button.setBackground(Ui.ripple(host.context(), 0x00000000, 14f));
        button.setContentDescription(label);
        button.setClickable(true);
        button.setFocusable(true);
        ImageView image = new ImageView(host.context());
        image.setImageResource(icon);
        image.setColorFilter(Theme.TEXT_DIM);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(host.context(), 22),
                Ui.dp(host.context(), 22)));
        button.addView(image);
        TextView text = Ui.text(host.context(), label, 10f, Theme.TEXT_DIM, false);
        text.setPadding(0, Ui.dp(host.context(), 4), 0, 0);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.addView(text);
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

    public void show() {
        root.setVisibility(View.VISIBLE);
        update();
    }

    public void hide() {
        root.setVisibility(View.GONE);
    }

    public boolean isShowing() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void toggleLyrics() {
        lyricsVisible = !lyricsVisible;
        lyricsPanel.setVisibility(lyricsVisible ? View.VISIBLE : View.GONE);
        if (lyricsVisible) {
            updateLyrics(true);
        }
    }

    public void update() {
        PlaybackService service = service();
        MediaItem item = service == null ? null : service.current();
        if (item == null) {
            title.setText("Nothing playing");
            artist.setText("Pick a song to start");
            album.setText("");
            album.setVisibility(View.GONE);
            art.setContentDescription("No album artwork");
            playIcon.setImageResource(R.drawable.soniq_ic_play);
            playIcon.setContentDescription("Play");
            likeIcon.setContentDescription("Like this song");
            seekBar.setProgress(0);
            positionLabel.setText("0:00");
            durationLabel.setText("0:00");
            lyricBanner.setText("");
            statusLabel.setText("");
            return;
        }
        if (!item.title.equals(title.getText().toString())) {
            title.setText(item.title);
        }
        String artistText = item.author.length() > 0 ? item.author : "Soniq Music";
        if (!artistText.equals(artist.getText().toString())) {
            artist.setText(artistText);
        }
        String albumText = item.album == null ? "" : item.album.trim();
        album.setText(albumText);
        album.setVisibility(albumText.length() > 0 ? View.VISIBLE : View.GONE);
        art.setContentDescription("Artwork for " + item.title);
        art.load(item.bestThumb());
        boolean playbackRequested = service.isPlaybackRequested();
        playIcon.setImageResource(playbackRequested
                ? R.drawable.soniq_ic_pause : R.drawable.soniq_ic_play);
        playIcon.setContentDescription(service.isPreparing() && playbackRequested
                ? "Pause loading playback" : playbackRequested ? "Pause" : "Play");
        shuffleIcon.setColorFilter(service.isShuffle() ? Theme.ACCENT : Theme.TEXT_DIM);
        shuffleIcon.setContentDescription(service.isShuffle() ? "Shuffle on" : "Turn shuffle on");
        int repeat = service.repeatMode();
        repeatIcon.setImageResource(repeat == PlaybackQueue.REPEAT_ONE
                ? R.drawable.soniq_ic_repeat_one : R.drawable.soniq_ic_repeat);
        repeatIcon.setColorFilter(repeat == PlaybackQueue.REPEAT_OFF
                ? Theme.TEXT_DIM : Theme.ACCENT);
        repeatIcon.setContentDescription(repeat == PlaybackQueue.REPEAT_OFF
                ? "Repeat off; cycle repeat mode" : repeat == PlaybackQueue.REPEAT_ONE
                ? "Repeat one; cycle repeat mode" : "Repeat all; cycle repeat mode");
        boolean liked = host.isLiked(item);
        likeIcon.setImageResource(liked ? R.drawable.soniq_ic_favorite : R.drawable.soniq_ic_favorite_border);
        likeIcon.setColorFilter(liked ? Theme.ACCENT : Theme.TEXT_DIM);
        likeIcon.setContentDescription(liked ? "Remove from liked songs" : "Like this song");

        int duration = service.getDuration();
        int position = service.getPosition();
        if (duration > 0 && seekBar.getMax() != duration) {
            seekBar.setMax(duration);
        }
        if (!scrubbing) {
            seekBar.setProgress(Math.max(0, Math.min(duration, position)));
            positionLabel.setText(Theme.formatDuration(position));
        }
        durationLabel.setText(duration > 0 ? Theme.formatDuration(duration) : "--:--");
        String playbackStatus = service.getStatus();
        String outputWarning = service.getOutputWarning();
        String message = playbackStatus;
        if (outputWarning.length() > 0) {
            message = message.length() > 0
                    ? message + " · " + outputWarning : outputWarning;
        }
        if (message.length() == 0) {
            message = scrobbleBadge(item);
        }
        statusLabel.setText(message);

        updateLyrics(item, service, position);
    }

    private void updateLyrics(boolean force) {
        PlaybackService service = service();
        MediaItem item = service == null ? null : service.current();
        if (item == null) {
            return;
        }
        updateLyrics(item, service, service.getPosition());
    }

    private void updateLyrics(MediaItem item, PlaybackService service, int position) {
        String key = item.identity() + "\n" + item.title + "\n" + item.author;
        if (!key.equals(lyricsKey)) {
            lyricsKey = key;
            lyrics.clear();
            lyricsRequest++;
            if (lyricsCancellation != null) {
                lyricsCancellation.cancel();
                lyricsCancellation = null;
            }
            if (lyricsTask != null) {
                lyricsTask.cancel(true);
                lyricsTask = null;
            }
            lyricsView.setMessage("Looking for lyrics…");
            final int request = lyricsRequest;
            final String trackTitle = item.title;
            final String trackArtist = item.author;
            // Provider hook wins over the search: an instrumental stays instrumental.
            final String providerLyricsState = resolveLyricsState(item);
            final LyricsApi.CancellationSignal cancellation = new LyricsApi.CancellationSignal();
            lyricsCancellation = cancellation;
            lyricsTask = lyricsExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    // The repository never throws: every outcome is a stated result.
                    final LyricsResult result = lyricsRepository.lookup(
                            trackTitle, trackArtist, providerLyricsState);
                    host.main().post(new Runnable() {
                        @Override
                        public void run() {
                            if (!host.isAlive() || request != lyricsRequest
                                    || host.service() != service) {
                                return;
                            }
                            lyricsTask = null;
                            if (lyricsCancellation == cancellation) {
                                lyricsCancellation = null;
                            }
                            lyrics.clear();
                            if (result == null) {
                                lyricsView.setMessage(host.context()
                                        .getString(R.string.soniq_lyrics_not_found));
                                lyricBanner.setText("");
                                return;
                            }
                            if (result.hasLyrics()) {
                                lyrics.addAll(result.lines);
                                lyricsView.setLyrics(lyrics);
                                if (!result.synced) {
                                    // Plain lyrics: say so instead of faking a sync.
                                    lyricsView.setMessage("Lyrics · not time-synced");
                                }
                                updateLyricHighlight(service.getPosition());
                                return;
                            }
                            // The three "no lyrics" cases are deliberately distinct.
                            lyricsView.setMessage(result.status == LyricsResult.Status.INSTRUMENTAL
                                    ? host.context().getString(R.string.soniq_lyrics_instrumental)
                                    : result.message());
                            lyricBanner.setText("");
                        }
                    });
                }
            });
            return;
        }
        updateLyricHighlight(position);
    }

    /** Provider's lyrics state, falling back to the item's own field. */
    private String resolveLyricsState(MediaItem item) {
        if (item == null) {
            return LyricsState.UNKNOWN;
        }
        String declared = LyricsState.normalize(item.lyricsState);
        if (declared.length() > 0) {
            return declared;
        }
        try {
            return LyricsState.normalize(host.provider().lyricsState(item));
        } catch (RuntimeException ignored) {
            return LyricsState.UNKNOWN;
        }
    }

    private void updateLyricHighlight(int position) {
        int active = com.soniq.base.data.LyricsParser.activeIndex(lyrics, position);
        lyricsView.setActive(active);
        if (active >= 0 && active < lyrics.size()) {
            lyricBanner.setText(lyrics.get(active).text);
        } else {
            lyricBanner.setText("");
        }
    }

    /** Shows whether the current ListenBrainz listen is queued, submitted, or unavailable. */
    private String scrobbleBadge(MediaItem item) {
        if (item == null) {
            return "";
        }
        return ScrobbleQueue.get(host.context()).currentTrackStatusMessage();
    }

    private PlaybackService service() {
        return host.service();
    }

    public void release() {
        lyricsRequest++;
        if (lyricsCancellation != null) {
            lyricsCancellation.cancel();
            lyricsCancellation = null;
        }
        if (lyricsTask != null) {
            lyricsTask.cancel(true);
            lyricsTask = null;
        }
        lyricsExecutor.shutdownNow();
    }
}
