package com.soniq.base.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;

/**
 * Headset / Bluetooth media button entry point.
 *
 * <p>While the app is playing, the active {@link android.media.session.MediaSession}
 * receives buttons directly. This receiver only matters when playback is idle, so
 * it forwards the key event to the foreground playback service.
 */
public final class MediaButtonReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            return;
        }
        KeyEvent event = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class)
                : intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }
        Intent service = new Intent(context, PlaybackService.class);
        service.setAction(PlaybackService.ACTION_MEDIA_BUTTON);
        service.putExtra(Intent.EXTRA_KEY_EVENT, event);
        // minSdk 26: startForegroundService is always available.
        context.startForegroundService(service);
    }
}
