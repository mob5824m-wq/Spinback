package com.soniq.base.ui.views;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.soniq.base.ui.Tab;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;

/** Four-destination bottom navigation with a pill highlight. */
public final class BottomNav extends LinearLayout {

    public interface Listener {
        void onTabSelected(Tab tab);
    }

    private final Tab[] tabs = Tab.values();
    private final LinearLayout[] items = new LinearLayout[tabs.length];
    private final ImageView[] icons = new ImageView[tabs.length];
    private final TextView[] labels = new TextView[tabs.length];
    private Tab selected = Tab.HOME;

    public BottomNav(Context context, final Listener listener) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackgroundColor(Theme.SURFACE);
        setPadding(Ui.dp(context, 8), Ui.dp(context, 6), Ui.dp(context, 8), Ui.dp(context, 6));

        for (int i = 0; i < tabs.length; i++) {
            final Tab tab = tabs[i];
            LinearLayout item = Ui.column(context);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            item.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 2));
            item.setBackground(Ui.ripple(context, 0x00000000, 14f));
            item.setClickable(true);
            item.setFocusable(true);
            item.setMinimumHeight(Ui.dp(context, 48));
            item.setContentDescription(tab.label);

            FrameLayout iconWrap = new FrameLayout(context);
            iconWrap.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ImageView icon = new ImageView(context);
            icon.setImageResource(tab.icon);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = Ui.dp(context, 22);
            icon.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            iconWrap.addView(icon);
            item.addView(iconWrap);

            TextView label = Ui.text(context, tab.label, 10f, Theme.TEXT_DIM, false);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, Ui.dp(context, 2), 0, 0);
            item.addView(label);

            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        listener.onTabSelected(tab);
                    }
                }
            });
            addView(item);
            items[i] = item;
            icons[i] = icon;
            labels[i] = label;
        }
        setSelected(Tab.HOME);
    }

    public void setSelected(Tab tab) {
        selected = tab == null ? Tab.HOME : tab;
        for (int i = 0; i < tabs.length; i++) {
            boolean active = tabs[i] == selected;
            icons[i].setColorFilter(active ? Theme.ACCENT : Theme.TEXT_DIM);
            labels[i].setTextColor(active ? Theme.ACCENT : Theme.TEXT_DIM);
            labels[i].setTypeface(null, active ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
        }
    }

    public Tab selected() {
        return selected;
    }
}
