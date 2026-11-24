package com.soniq.base.ui;

import android.content.Context;
import android.os.Handler;

import com.soniq.base.data.Downloads;
import com.soniq.base.data.Library;
import com.soniq.base.model.MediaItem;
import com.soniq.base.data.Settings;
import com.soniq.base.provider.MusicProvider;
import com.soniq.base.playback.PlaybackService;

import java.util.List;
import java.util.concurrent.ExecutorService;

/** Everything a screen needs from the activity, without owning it. */
public interface Host {

    Context context();

    /** False once the host activity is being destroyed; async UI callbacks must stop then. */
    default boolean isAlive() {
        return true;
    }

    PlaybackService service();

    Library library();

    Settings settings();

    Downloads downloads();

    MusicProvider provider();

    Handler main();

    ExecutorService io();

    void play(List<MediaItem> items, int index);

    void playSingle(MediaItem item);

    void addToQueue(MediaItem item);

    void quickPlay();

    void openCollection(MediaItem item);

    void openArtist(MediaItem item);

    void openPlayer();

    /** Opens the year-in-review recap (listening statistics from ListenBrainz). */
    void openInsights();

    void openLibrary();

    void openSearch(String query);

    void showQueue();

    void showTrackMenu(MediaItem item);

    void toggleLike(MediaItem item);

    boolean isLiked(MediaItem item);

    void download(MediaItem item);

    void addToPlaylist(MediaItem item);

    void createPlaylist();

    void startRadio(MediaItem item);

    void showToast(String message);

    /** Hands plain text to the platform share sheet under {@code chooserTitle}. */
    void shareText(String chooserTitle, String text);

    void requestNotificationPermission();

    void refreshCurrentScreen();
}
