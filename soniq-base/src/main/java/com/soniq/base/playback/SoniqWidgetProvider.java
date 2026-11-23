package com.soniq.base.playback;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.soniq.base.R;
import com.soniq.base.model.MediaItem;

/**
 * Home-screen now-playing widget: artwork, title, artist, play/pause and next.
 *
 * <p>This is the framework's <em>default</em> widget — deliberately plain, one
 * control row, framework colours. It exists so every app built on Soniq Base
 * has a working widget with no extra code. Apps that need more (seek bar,
 * like/download toggles, custom artwork shape, themed colours, per-size
 * layouts) ship their own {@link AppWidgetProvider} and declare it in their
 * manifest instead — see {@code docs/WIDGETS.md} for the checklist.
 *
 * <p>The provider is read-only towards playback: buttons send the same intents
 * the notification uses, so there is one source of truth for transport.
 */
public class SoniqWidgetProvider extends AppWidgetProvider {

    /** Refresh hook the service calls on every state change. */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) {
            return;
        }
        ComponentName component = new ComponentName(context, SoniqWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) {
            return;
        }
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context, id, source(context)));
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        Source source = source(context);
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context, id, source));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
        manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId, source(context)));
    }

    /** Builds the RemoteViews for one widget instance. Exposed for host apps that subclass. */
    protected static RemoteViews buildViews(Context context, int appWidgetId, Source source) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.soniq_widget);
        boolean playing = source != null && source.playing;
        MediaItem item = source == null ? null : source.item;

        if (item == null) {
            views.setTextViewText(R.id.soniq_widget_title,
                    context.getString(R.string.soniq_widget_idle_title));
            views.setTextViewText(R.id.soniq_widget_subtitle,
                    context.getString(R.string.soniq_widget_idle_subtitle));
            views.setImageViewResource(R.id.soniq_widget_art, R.drawable.soniq_ic_music_note);
        } else {
            views.setTextViewText(R.id.soniq_widget_title, item.title);
            String subtitle = item.author == null || item.author.length() == 0
                    ? context.getString(R.string.soniq_widget_idle_subtitle) : item.author;
            views.setTextViewText(R.id.soniq_widget_subtitle, subtitle);
            Bitmap art = source.artwork;
            if (art != null) {
                views.setImageViewBitmap(R.id.soniq_widget_art, art);
            } else {
                views.setImageViewResource(R.id.soniq_widget_art, R.drawable.soniq_ic_music_note);
            }
        }

        views.setImageViewResource(R.id.soniq_widget_toggle,
                playing ? R.drawable.soniq_ic_pause : R.drawable.soniq_ic_play);
        views.setContentDescription(R.id.soniq_widget_toggle,
                context.getString(playing ? R.string.soniq_action_pause : R.string.soniq_action_play));
        views.setContentDescription(R.id.soniq_widget_next,
                context.getString(R.string.soniq_action_next));
        views.setContentDescription(R.id.soniq_widget_title,
                item == null ? context.getString(R.string.soniq_widget_idle_title)
                        : item.title + ", " + item.author);

        views.setOnClickPendingIntent(R.id.soniq_widget_root,
                activityIntent(context, PlaybackService.ACTION_TOGGLE));
        views.setOnClickPendingIntent(R.id.soniq_widget_toggle,
                serviceIntent(context, playing ? PlaybackService.ACTION_PAUSE
                        : PlaybackService.ACTION_PLAY));
        views.setOnClickPendingIntent(R.id.soniq_widget_next,
                serviceIntent(context, PlaybackService.ACTION_NEXT));
        return views;
    }

    /**
     * The widget's view of playback. Filled by
     * {@link #publish(Context, MediaItem, boolean, Bitmap)} from the service, so
     * the widget never binds or queries the player itself.
     */
    static final class Source {
        final MediaItem item;
        final boolean playing;
        final Bitmap artwork;

        Source(MediaItem item, boolean playing, Bitmap artwork) {
            this.item = item;
            this.playing = playing;
            this.artwork = artwork;
        }
    }

    private static volatile Source latest;

    /** Called by the playback service whenever what the widget shows changes. */
    public static void publish(Context context, MediaItem item, boolean playing, Bitmap artwork) {
        latest = new Source(item == null ? null : item.copy(), playing, artwork);
        updateAll(context);
    }

    /**
     * Last published state, or — after a process restart, where the static cache
     * is gone — whatever {@link PlaybackSession} last saved. Showing the saved
     * track (paused) is honest and useful; showing a stale "playing" would not be.
     */
    private static Source source(Context context) {
        if (latest != null) {
            return latest;
        }
        try {
            PlaybackSession session = new PlaybackSession(context);
            java.util.List<MediaItem> queue = session.queue();
            if (queue.isEmpty()) {
                return null;
            }
            MediaItem item = queue.get(Math.min(session.index(), queue.size() - 1));
            return new Source(item, false, null);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /**
     * Opens the app's own launcher activity. Using the package's launch intent
     * (instead of the framework activity) keeps the host app in control of
     * branding, deep links and which tab opens first.
     */
    private static PendingIntent activityIntent(Context context, String action) {
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) {
            intent = new Intent(context, com.soniq.base.ui.MusicActivity.class);
        }
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 1, intent, pendingFlags());
    }

    private static PendingIntent serviceIntent(Context context, String action) {
        Intent intent = new Intent(context, PlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, action.hashCode(), intent, pendingFlags());
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
