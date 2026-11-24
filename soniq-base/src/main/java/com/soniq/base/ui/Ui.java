package com.soniq.base.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Programmatic Material-ish widgets; the app ships no layout XML on purpose. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        return rounded(color, dp(context, radiusDp));
    }

    public static RippleDrawable ripple(Context context, int color, float radiusDp) {
        GradientDrawable content = rounded(context, color, radiusDp);
        GradientDrawable mask = rounded(context, 0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

    public static TextView text(Context context, CharSequence value, float sp, int color,
                                boolean bold) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView singleLine(TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static LinearLayout.LayoutParams fixed(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static ImageView icon(Context context, int resource, int tintColor, int sizeDp) {
        ImageView image = new ImageView(context);
        image.setImageResource(resource);
        image.setColorFilter(tintColor);
        int size = dp(context, sizeDp);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return image;
    }

    /**
     * Icon-only action with a minimum 48dp target. New framework UI should use
     * the overload with a human-readable accessibility description.
     */
    public static FrameLayout iconButton(Context context, int resource, int tintColor,
                                         int sizeDp, int iconDp, final Runnable onClick) {
        return iconButton(context, resource, tintColor, sizeDp, iconDp, null, onClick);
    }

    public static FrameLayout iconButton(Context context, int resource, int tintColor,
                                         int sizeDp, int iconDp, CharSequence description,
                                         final Runnable onClick) {
        FrameLayout button = new FrameLayout(context);
        int size = dp(context, Math.max(48, sizeDp));
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        button.setBackground(ripple(context, 0x00000000, Math.max(24, sizeDp / 2f)));
        button.setClickable(true);
        button.setFocusable(true);
        if (description != null && description.length() > 0) {
            button.setContentDescription(description);
        }
        ImageView image = icon(context, resource, tintColor, iconDp);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams imageParams =
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        imageParams.gravity = Gravity.CENTER;
        button.addView(image, imageParams);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onClick != null) {
                    onClick.run();
                }
            }
        });
        return button;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(Theme.withAlpha(Theme.OUTLINE, 0xFF));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.5f))));
        return view;
    }

    public static TextView sectionHeader(Context context, String title) {
        TextView view = text(context, title, 17f, Theme.TEXT, true);
        view.setPadding(dp(context, 4), dp(context, 14), dp(context, 4), dp(context, 8));
        return view;
    }

    public static TextView caption(Context context, String value) {
        TextView view = text(context, value, 12f, Theme.TEXT_DIM, false);
        view.setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 8));
        return view;
    }

    public static View emptyState(Context context, String title, String body) {
        LinearLayout wrapper = column(context);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setPadding(dp(context, 24), dp(context, 40), dp(context, 24), dp(context, 40));
        TextView heading = text(context, title, 16f, Theme.TEXT, true);
        heading.setGravity(Gravity.CENTER);
        wrapper.addView(heading);
        TextView detail = text(context, body, 13f, Theme.TEXT_DIM, false);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(context, 8), 0, 0);
        wrapper.addView(detail);
        return wrapper;
    }
}
