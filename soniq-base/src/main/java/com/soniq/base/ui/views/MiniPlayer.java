package com.soniq.base.ui.views;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.model.MediaItem;
import com.soniq.base.playback.PlaybackService;
import com.soniq.base.ui.NetworkImage;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;

/** Compact now-playing bar pinned above the bottom navigation. */
public final class MiniPlayer extends LinearLayout {

    public interface Listener {
        void onOpenPlayer();

        void onToggle();

        void onNext();
    }

    private final NetworkImage art;
    private final TextView title;
    private final TextView artist;
    private final ImageView playIcon;
    private final Listener listener;

    public MiniPlayer(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int padding = Ui.dp(context, 8);
        setPadding(padding, padding, padding, padding);
        setClickable(true);
        setFocusable(true);
        setBackground(Ui.ripple(context, Theme.SURFACE_HIGH, 0f));

        art = new NetworkImage(context);
        art.setRadiusDp(6f);
        int artSize = Ui.dp(context, 42);
        addView(art, new LinearLayout.LayoutParams(artSize, artSize));

        LinearLayout info = Ui.column(context);
        info.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 6), 0);
        title = Ui.text(context, "Nothing playing", 13f, Theme.TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        artist = Ui.text(context, Theme.formatDuration(0), 11f, Theme.TEXT_DIM, false);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(artist);
        addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        playIcon = new ImageView(context);
        playIcon.setImageResource(R.drawable.soniq_ic_play);
        playIcon.setColorFilter(Theme.TEXT);
        playIcon.setContentDescription("Play");
        playIcon.setFocusable(true);
        int buttonSize = Ui.dp(context, 48);
        playIcon.setLayoutParams(new LinearLayout.LayoutParams(buttonSize, buttonSize));
        playIcon.setPadding(Ui.dp(context, 10), Ui.dp(context, 10),
                Ui.dp(context, 10), Ui.dp(context, 10));
        playIcon.setBackground(Ui.ripple(context, 0x00000000, 20f));
        playIcon.setClickable(true);
        playIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) {
                    listener.onToggle();
                }
            }
        });
        addView(playIcon);

        ImageView next = new ImageView(context);
        next.setImageResource(R.drawable.soniq_ic_next);
        next.setColorFilter(Theme.TEXT_DIM);
        next.setContentDescription("Next track");
        next.setFocusable(true);
        next.setLayoutParams(new LinearLayout.LayoutParams(buttonSize, buttonSize));
        next.setPadding(Ui.dp(context, 10), Ui.dp(context, 10),
                Ui.dp(context, 10), Ui.dp(context, 10));
        next.setBackground(Ui.ripple(context, 0x00000000, 20f));
        next.setClickable(true);
        next.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) {
                    listener.onNext();
                }
            }
        });
        addView(next);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) {
                    listener.onOpenPlayer();
                }
            }
        });
    }

    public void update(PlaybackService service) {
        MediaItem item = service == null ? null : service.current();
        if (item == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        title.setText(item.title);
        String artistName = item.author.length() > 0 ? item.author : "Unknown artist";
        artist.setText(artistName);
        String accessibilityState = service.isPlaying() ? "Now playing "
                : service.isPreparing() && service.isPlaybackRequested() ? "Loading "
                : service.getStatus().length() > 0 ? "Playback status: " + service.getStatus()
                + ". Track " : "Paused on ";
        setContentDescription(accessibilityState + item.title + " by " + artistName
                + ". Open the full player.");
        art.setContentDescription("Artwork for " + item.title);
        art.load(item.bestThumb());
        boolean playbackRequested = service.isPlaybackRequested();
        playIcon.setImageResource(playbackRequested
                ? R.drawable.soniq_ic_pause : R.drawable.soniq_ic_play);
        playIcon.setContentDescription(service.isPreparing() && playbackRequested
                ? "Pause loading playback" : playbackRequested ? "Pause" : "Play");
        String status = service.getStatus();
        if (status.length() > 0) {
            artist.setText(status);
        } else if (service.getOutputWarning().length() > 0) {
            artist.setText(service.getOutputWarning());
        }
    }
}
