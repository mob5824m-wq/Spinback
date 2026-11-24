package com.soniq.base.ui.views;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.soniq.base.R;
import com.soniq.base.model.MediaItem;
import com.soniq.base.ui.A11y;
import com.soniq.base.ui.NetworkImage;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;

/** One song / artist / album / playlist row with artwork, metadata and overflow. */
public final class TrackRow extends LinearLayout {

    public interface Callback {
        void onClick(MediaItem item, int position);

        void onMore(View anchor, MediaItem item, int position);

        void onLongClick(MediaItem item, int position);
    }

    public TrackRow(Context context, MediaItem item, int position, boolean showArt,
                    boolean active, Callback callback) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int padding = Ui.dp(context, 8);
        setPadding(padding, padding, Ui.dp(context, 4), padding);
        setBackground(Ui.ripple(context, active ? Theme.ACCENT_SOFT : Theme.SURFACE, 12f));
        setClickable(true);
        setFocusable(true);

        if (showArt) {
            NetworkImage art = new NetworkImage(context);
            art.setRadiusDp(8f);
            // The row itself announces title/artist; artwork describes itself.
            A11y.label(art, item.title.length() > 0
                    ? "Artwork for " + item.title : "Album artwork");
            int size = Ui.dp(context, 48);
            LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(size, size);
            artParams.rightMargin = Ui.dp(context, 12);
            addView(art, artParams);
            art.load(item.bestThumb());
        }

        LinearLayout info = Ui.column(context);
        info.setPadding(0, 0, Ui.dp(context, 8), 0);

        TextView title = Ui.text(context, item.title, 14f, Theme.TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);

        String subtitle = item.subtitle();
        TextView detail = Ui.text(context, subtitle, 12f, active ? Theme.ACCENT : Theme.TEXT_DIM, false);
        detail.setMaxLines(1);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(detail);

        addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String trailing = trailingLabel(item);
        if (trailing.length() > 0) {
            TextView label = Ui.text(context, trailing, 11f, Theme.TEXT_FAINT, false);
            label.setGravity(Gravity.CENTER);
            label.setPadding(Ui.dp(context, 6), 0, Ui.dp(context, 2), 0);
            addView(label);
        }

        addView(Ui.iconButton(context, R.drawable.soniq_ic_more, Theme.TEXT_DIM, 48, 18,
                "More actions for " + item.title, new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onMore(TrackRow.this, item, position);
                        }
                    }
                }));

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (callback != null) {
                    callback.onClick(item, position);
                }
            }
        });
        // One focus stop per row: "Title, artist, 3:42" instead of five swipes.
        StringBuilder announcement = new StringBuilder(item.title);
        if (item.author.length() > 0) {
            announcement.append(", ").append(item.author);
        }
        if (item.durationText.length() > 0) {
            announcement.append(", ").append(item.durationText);
        }
        if (active) {
            announcement.append(" (now playing)");
        }
        A11y.singleAnnouncement(this, announcement.toString());
        // Keep the overflow button reachable: it is the one interactive child.
        View moreButton = getChildAt(getChildCount() - 1);
        if (moreButton != null) {
            moreButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (callback != null) {
                    callback.onLongClick(item, position);
                    return true;
                }
                return false;
            }
        });
    }

    private static String trailingLabel(MediaItem item) {
        if (item.isArtist()) {
            return "ARTIST";
        }
        if (item.isCollection()) {
            return "OPEN";
        }
        if (item.durationText.length() > 0) {
            return item.durationText;
        }
        if (item.isOffline()) {
            return "OFFLINE";
        }
        return "";
    }
}
