package com.soniq.base.ui.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;

/** Rounded filter chip with a selected accent state. */
public final class Chip extends TextView {

    private boolean selected;

    public Chip(Context context, String label, final Runnable onClick) {
        super(context);
        setText(label);
        setTextSize(13f);
        setGravity(Gravity.CENTER);
        setTextColor(Theme.TEXT);
        setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 14), Ui.dp(context, 8));
        setSingleLine(true);
        int height = Ui.dp(context, 48);
        setMinimumHeight(height);
        setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, height));
        setSelected(false);
        setBackground(makeBackground(false));
        setClickable(true);
        setFocusable(true);
        if (onClick != null) {
            setOnClickListener(v -> onClick.run());
        }
    }

    public void setSelected(boolean value) {
        selected = value;
        setBackground(makeBackground(value));
        setTextColor(value ? Color.WHITE : Theme.TEXT);
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    private GradientDrawable makeBackground(boolean is_selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(Ui.dp(getContext(), 17));
        drawable.setColor(is_selected ? Theme.ACCENT : Theme.SURFACE_HIGH);
        if (!is_selected) {
            drawable.setStroke(Ui.dp(getContext(), 1), Theme.OUTLINE);
        }
        return drawable;
    }
}
