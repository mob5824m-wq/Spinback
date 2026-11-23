package com.soniq.base.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import com.soniq.base.data.ImageLoader;

/**
 * ImageView that loads artwork through {@link ImageLoader} with rounded corners.
 *
 * <p>Rounded corners use an outline clip instead of the AndroidX
 * {@code RoundedBitmapDrawable}, because this app deliberately has no
 * support-library dependency.
 */
public final class NetworkImage extends ImageView {

    private String url = "";
    private float radiusDp = 8f;

    public NetworkImage(Context context) {
        super(context);
        init();
    }

    public NetworkImage(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setScaleType(ScaleType.CENTER_CROP);
        setBackgroundColor(Theme.SURFACE_HIGH);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        Ui.dp(getContext(), radiusDp));
            }
        });
    }

    public void setRadiusDp(float value) {
        this.radiusDp = value;
        invalidateOutline();
    }

    public String getUrl() {
        return url;
    }

    public void load(String value) {
        String target = value == null ? "" : value;
        if (target.equals(url)) {
            return;
        }
        url = target;
        setImageDrawable(null);
        if (target.length() == 0) {
            return;
        }
        Bitmap cached = ImageLoader.cached(target);
        if (cached != null) {
            setImageBitmap(cached);
            return;
        }
        ImageLoader.load(target, new ImageLoader.Callback() {
            @Override
            public void onBitmap(Bitmap bitmap) {
                if (target.equals(url) && bitmap != null) {
                    setImageBitmap(bitmap);
                }
            }
        });
    }

    public void setPlaceholder(Drawable drawable) {
        if (url.length() == 0) {
            setImageDrawable(drawable);
        }
    }
}
