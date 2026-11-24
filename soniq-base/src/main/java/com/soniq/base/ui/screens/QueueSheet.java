package com.soniq.base.ui.screens;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.model.MediaItem;
import com.soniq.base.playback.PlaybackService;
import com.soniq.base.ui.Host;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;

import java.util.List;

/** "Up next" queue overlay with tap-to-play and per-item remove. */
public final class QueueSheet {

    private final Host host;
    private final FrameLayout root;
    private final LinearLayout list;
    private final TextView subtitle;

    public QueueSheet(Host host) {
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
        top.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_close,
                Theme.TEXT, 44, 22, "Close queue", new Runnable() {
                    @Override
                    public void run() {
                        hide();
                    }
                }));
        LinearLayout titles = Ui.column(host.context());
        titles.addView(Ui.text(host.context(), "Up next", 22f, Theme.TEXT, true));
        subtitle = Ui.text(host.context(), "", 12f, Theme.TEXT_DIM, false);
        titles.addView(subtitle);
        top.addView(titles, Ui.weight(1f));
        top.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_close,
                Theme.TEXT_DIM, 48, 18, "Clear playback and queue", new Runnable() {
                    @Override
                    public void run() {
                        if (host.service() != null) {
                            host.service().stop();
                        }
                        hide();
                    }
                }));
        column.addView(top);
        column.addView(Ui.caption(host.context(),
                "Tap a song to jump. The X clears playback and the queue."));
        list = Ui.column(host.context());
        column.addView(list);
    }

    public View view() {
        return root;
    }

    public void show() {
        root.setVisibility(View.VISIBLE);
        render();
    }

    public void hide() {
        root.setVisibility(View.GONE);
    }

    public boolean isShowing() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void render() {
        list.removeAllViews();
        PlaybackService service = host.service();
        if (service == null) {
            subtitle.setText("Playback service is not ready.");
            return;
        }
        List<MediaItem> items = service.orderedQueueItems();
        subtitle.setText(items.size() + (items.size() == 1 ? " track" : " tracks"));
        if (items.isEmpty()) {
            list.addView(Ui.emptyState(host.context(), "Queue is empty",
                    "Play a song or tap + Queue on a search result."));
            return;
        }
        int currentIndex = service.queueIndex();
        for (int i = 0; i < items.size(); i++) {
            final MediaItem item = items.get(i);
            boolean active = service.queueIndexOf(item) == currentIndex;
            LinearLayout row = Ui.row(host.context());
            row.setPadding(Ui.dp(host.context(), 8), Ui.dp(host.context(), 10),
                    Ui.dp(host.context(), 4), Ui.dp(host.context(), 10));
            row.setBackground(Ui.ripple(host.context(),
                    active ? Theme.ACCENT_SOFT : Theme.SURFACE, 12f));
            row.setClickable(true);

            TextView index = Ui.text(host.context(), String.valueOf(i + 1), 11f,
                    active ? Theme.ACCENT : Theme.TEXT_FAINT, false);
            index.setGravity(Gravity.CENTER);
            index.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(host.context(), 22),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(index);

            com.soniq.base.ui.NetworkImage art = new com.soniq.base.ui.NetworkImage(host.context());
            art.setRadiusDp(6f);
            int artSize = Ui.dp(host.context(), 38);
            LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(artSize, artSize);
            artParams.leftMargin = Ui.dp(host.context(), 6);
            artParams.rightMargin = Ui.dp(host.context(), 10);
            row.addView(art, artParams);
            art.load(item.bestThumb());

            LinearLayout info = Ui.column(host.context());
            info.addView(Ui.text(host.context(), item.title, 13f,
                    Theme.TEXT, true));
            info.addView(Ui.text(host.context(),
                    item.author.length() > 0 ? item.author : "Soniq Music",
                    11f, Theme.TEXT_DIM, false));
            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(Ui.iconButton(host.context(), R.drawable.soniq_ic_close,
                    Theme.TEXT_DIM, 48, 16, "Remove " + item.title + " from queue", new Runnable() {
                        @Override
                        public void run() {
                            int index = host.service().queueIndexOf(item);
                            if (index >= 0) {
                                host.service().removeQueueItem(index);
                            }
                            render();
                        }
                    }));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    host.service().playQueueItem(item);
                }
            });
            list.addView(row);
        }
    }
}
