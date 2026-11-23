package com.soniq.base.ui.views;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.soniq.base.data.LyricLine;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Scrollable synced lyrics with tap-to-seek and an active-line highlight. */
public final class LyricsView extends ScrollView {

    public interface SeekCallback {
        void onSeekTo(long positionMs);
    }

    private final LinearLayout list;
    private final TextView empty;
    private List<LyricLine> lines = new ArrayList<>();
    private TextView[] rows = new TextView[0];
    private SeekCallback callback;
    private int active = -1;

    public LyricsView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        list = Ui.column(context);
        int padding = Ui.dp(context, 20);
        list.setPadding(padding, padding, padding, padding);
        empty = Ui.text(context, "", 13f, Theme.TEXT_DIM, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(context, 16), Ui.dp(context, 32),
                Ui.dp(context, 16), Ui.dp(context, 32));
        addView(list);
    }

    public void setSeekCallback(SeekCallback value) {
        callback = value;
    }

    /** Shows a visible fetch error instead of incorrectly describing it as no lyrics. */
    public void setMessage(String message) {
        lines = new ArrayList<>();
        active = -1;
        rows = new TextView[0];
        list.removeAllViews();
        empty.setText(message == null ? "Lyrics unavailable." : message);
        list.addView(empty);
    }

    public void setLyrics(List<LyricLine> value) {
        lines = value == null ? new ArrayList<LyricLine>() : value;
        active = -1;
        list.removeAllViews();
        if (lines.isEmpty()) {
            empty.setText("No lyrics available for this song.\nLyrics depend on provider coverage.");
            list.addView(empty);
            rows = new TextView[0];
            return;
        }
        rows = new TextView[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            final LyricLine line = lines.get(i);
            TextView row = Ui.text(getContext(), line.text, 14f, Theme.TEXT_DIM, false);
            row.setContentDescription(line.isTimed()
                    ? "Seek to " + Theme.formatDuration(line.timeMs) + ": " + line.text
                    : line.text);
            row.setPadding(Ui.dp(getContext(), 4), Ui.dp(getContext(), 8),
                    Ui.dp(getContext(), 4), Ui.dp(getContext(), 8));
            if (line.isTimed()) {
                row.setClickable(true);
                row.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (callback != null) {
                            callback.onSeekTo(line.timeMs);
                        }
                    }
                });
            }
            list.addView(row);
            rows[i] = row;
        }
        setActive(0);
    }

    public void setActive(int index) {
        if (index < 0) {
            index = -1;
        }
        if (index == active) {
            return;
        }
        active = index;
        for (int i = 0; i < rows.length; i++) {
            boolean isActive = i == active;
            rows[i].setTextColor(isActive ? Theme.ACCENT : Theme.TEXT_DIM);
            rows[i].setTextSize(isActive ? 17f : 14f);
            rows[i].setTypeface(isActive ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (active >= 0 && active < rows.length) {
            final View target = rows[active];
            post(new Runnable() {
                @Override
                public void run() {
                    int y = Math.max(0, target.getTop() - getHeight() / 3);
                    smoothScrollTo(0, y);
                }
            });
        }
    }

    public int activeIndex() {
        return active;
    }

    public boolean hasLyrics() {
        return !lines.isEmpty();
    }
}
