package com.spinback.gplaymusic;

import android.net.Uri;

import com.soniq.base.model.MediaItem;
import com.soniq.base.ui.MusicActivity;

import java.util.List;

/**
 * Launcher activity. The whole UI comes from the framework; this subclass only
 * teaches it how to open YouTube Music links (see the manifest's VIEW filters).
 *
 * <p>Google Play Music links ({@code play.google.com/music/…}) are not
 * handled: the service shut down in 2020 and its ids cannot be resolved by
 * any live catalogue. Search for the song instead — see
 * {@code docs/GOOGLE-PLAY-MUSIC.md}.
 */
public final class MainActivity extends MusicActivity {

    @Override
    protected void onHandleViewIntent(Uri data) {
        String videoId = null;
        String playlistId = null;
        List<String> segments = data.getPathSegments();
        if ("youtu.be".equals(data.getHost()) && !segments.isEmpty()) {
            videoId = segments.get(0);
        } else {
            videoId = data.getQueryParameter("v");
            playlistId = data.getQueryParameter("list");
        }
        if (videoId != null && videoId.length() >= 8) {
            playSingle(GooglePlayMusicProvider.trackForVideoId(videoId));
        } else if (playlistId != null && playlistId.length() > 2) {
            openCollection(GooglePlayMusicProvider.playlistForId(playlistId));
        } else {
            showToast("That link does not contain a video or playlist id.");
        }
    }
}
