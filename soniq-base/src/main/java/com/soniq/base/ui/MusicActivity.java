package com.soniq.base.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.soniq.base.data.Downloads;
import com.soniq.base.data.Library;
import com.soniq.base.model.MediaItem;
import com.soniq.base.data.Settings;
import com.soniq.base.SoniqApp;
import com.soniq.base.provider.MusicProvider;
import com.soniq.base.playback.PlaybackService;
import com.soniq.base.ui.screens.CollectionSheet;
import com.soniq.base.ui.screens.HomeScreen;
import com.soniq.base.ui.screens.LibraryScreen;
import com.soniq.base.ui.screens.PlayerSheet;
import com.soniq.base.ui.screens.QueueSheet;
import com.soniq.base.ui.screens.RecapSheet;
import com.soniq.base.ui.screens.Screen;
import com.soniq.base.ui.screens.SearchScreen;
import com.soniq.base.ui.screens.SettingsScreen;
import com.soniq.base.ui.views.BottomNav;
import com.soniq.base.ui.views.MiniPlayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The framework's single-activity UI: Home / Search / Library / Settings tabs,
 * the mini player and the full-screen player / queue / collection sheets. All
 * catalogue access goes through the app's {@link MusicProvider} (see
 * {@link SoniqApp#createProvider()}).
 *
 * <p>Apps subclass this as their launcher activity and override
 * {@link #onHandleViewIntent(Uri)} for provider-specific deep links.
 *
 * <p>Edge-to-edge is enabled and insets are applied manually; targetSdk 35
 * otherwise forces content under the system bars.
 */
public class MusicActivity extends android.app.Activity implements Host {

    private static final int REQUEST_NOTIFICATIONS = 4101;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);

    private Library library;
    private Settings settings;
    private Downloads downloads;
    private MusicProvider provider;

    private LinearLayout column;
    private FrameLayout contentContainer;
    private FrameLayout overlay;
    private MiniPlayer miniPlayer;
    private BottomNav bottomNav;
    private PlayerSheet playerSheet;
    private QueueSheet queueSheet;
    private CollectionSheet collectionSheet;
    private RecapSheet recapSheet;

    private HomeScreen homeScreen;
    private SearchScreen searchScreen;
    private LibraryScreen libraryScreen;
    private SettingsScreen settingsScreen;
    private Screen currentScreen;
    private Tab tab = Tab.HOME;

    private PlaybackService service;
    private boolean bound;
    private volatile boolean destroyed;
    private Downloads.CancellationSignal activeDownloadSignal;
    private Future<?> activeDownloadTask;
    private List<MediaItem> pendingQueue;
    private int pendingIndex;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (destroyed) {
                return;
            }
            if (miniPlayer != null) {
                miniPlayer.update(service);
            }
            if (playerSheet != null && playerSheet.isShowing()) {
                playerSheet.update();
            }
            main.postDelayed(this, 500L);
        }
    };

    private final PlaybackService.Listener playbackListener = new PlaybackService.Listener() {
        @Override
        public void onPlaybackChanged() {
            if (destroyed) {
                return;
            }
            if (miniPlayer != null) {
                miniPlayer.update(service);
            }
            if (playerSheet != null && playerSheet.isShowing()) {
                playerSheet.update();
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlaybackService.LocalBinder) binder).service();
            bound = true;
            if (destroyed) {
                service.removeListener(playbackListener);
                return;
            }
            service.addListener(playbackListener);
            if (pendingQueue != null) {
                service.play(pendingQueue, pendingIndex);
                pendingQueue = null;
            }
            if (miniPlayer != null) {
                miniPlayer.update(service);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (service != null) {
                service.removeListener(playbackListener);
            }
            service = null;
            bound = false;
        }
    };

    /* ================================================================ lifecycle */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        library = new Library(this);
        settings = new Settings(this);
        downloads = new Downloads(this);
        provider = SoniqApp.providerOf(this);

        enableEdgeToEdge();
        setContentView(buildRoot());

        Intent serviceIntent = new Intent(this, PlaybackService.class);
        startService(serviceIntent);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    new android.window.OnBackInvokedCallback() {
                        @Override
                        public void onBackInvoked() {
                            handleBack();
                        }
                    });
        }
        switchTab(Tab.HOME);
        main.postDelayed(ticker, 500L);
        requestNotificationPermissionOnce();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (miniPlayer != null) {
            miniPlayer.update(service);
        }
        if (playerSheet != null && playerSheet.isShowing()) {
            playerSheet.update();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacks(ticker);
        if (activeDownloadSignal != null) {
            activeDownloadSignal.cancel();
        }
        if (activeDownloadTask != null) {
            activeDownloadTask.cancel(true);
        }
        if (service != null) {
            service.removeListener(playbackListener);
        }
        if (playerSheet != null) {
            playerSheet.release();
        }
        if (collectionSheet != null) {
            collectionSheet.release();
        }
        if (recapSheet != null) {
            recapSheet.release();
        }
        if (homeScreen != null) {
            homeScreen.release();
        }
        if (searchScreen != null) {
            searchScreen.release();
        }
        if (libraryScreen != null) {
            libraryScreen.release();
        }
        if (settingsScreen != null) {
            settingsScreen.release();
        }
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (recapSheet != null && recapSheet.isShowing()) {
            recapSheet.hide();
            return;
        }
        if (queueSheet != null && queueSheet.isShowing()) {
            queueSheet.hide();
            return;
        }
        if (collectionSheet != null && collectionSheet.isShowing()) {
            collectionSheet.hide();
            return;
        }
        if (playerSheet != null && playerSheet.isShowing()) {
            playerSheet.hide();
            return;
        }
        if (tab != Tab.HOME) {
            switchTab(Tab.HOME);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    private void enableEdgeToEdge() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        column = Ui.column(this);
        contentContainer = new FrameLayout(this);
        column.addView(contentContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        miniPlayer = new MiniPlayer(this, new MiniPlayer.Listener() {
            @Override
            public void onOpenPlayer() {
                openPlayer();
            }

            @Override
            public void onToggle() {
                if (service != null) {
                    service.toggle();
                }
            }

            @Override
            public void onNext() {
                if (service != null) {
                    service.next();
                }
            }
        });
        miniPlayer.setVisibility(View.GONE);
        column.addView(miniPlayer);

        bottomNav = new BottomNav(this, new BottomNav.Listener() {
            @Override
            public void onTabSelected(Tab selected) {
                switchTab(selected);
            }
        });
        column.addView(bottomNav);

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        playerSheet = new PlayerSheet(this);
        overlay.addView(playerSheet.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        collectionSheet = new CollectionSheet(this);
        overlay.addView(collectionSheet.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        queueSheet = new QueueSheet(this);
        overlay.addView(queueSheet.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        recapSheet = new RecapSheet(this);
        overlay.addView(recapSheet.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                column.setPadding(0, top, 0, bottom);
                overlay.setPadding(0, top, 0, bottom);
                return insets;
            }
        });
        root.requestApplyInsets();
        return root;
    }

    private void switchTab(Tab selected) {
        tab = selected;
        currentScreen = screenFor(selected);
        contentContainer.removeAllViews();
        currentScreen.refresh();
        contentContainer.addView(currentScreen.view());
        bottomNav.setSelected(selected);
    }

    private Screen screenFor(Tab selected) {
        switch (selected) {
            case SEARCH:
                if (searchScreen == null) {
                    searchScreen = new SearchScreen(this);
                }
                return searchScreen;
            case LIBRARY:
                if (libraryScreen == null) {
                    libraryScreen = new LibraryScreen(this);
                }
                return libraryScreen;
            case SETTINGS:
                if (settingsScreen == null) {
                    settingsScreen = new SettingsScreen(this);
                }
                return settingsScreen;
            case HOME:
            default:
                if (homeScreen == null) {
                    homeScreen = new HomeScreen(this);
                }
                return homeScreen;
        }
    }

    private void requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (settings.notificationsAsked()) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            settings.setNotificationsAsked(true);
            return;
        }
        settings.setNotificationsAsked(true);
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    /* ==================================================================== Host */

    @Override
    public Context context() {
        return this;
    }

    @Override
    public boolean isAlive() {
        return !destroyed;
    }

    @Override
    public PlaybackService service() {
        return service;
    }

    @Override
    public Library library() {
        return library;
    }

    @Override
    public Settings settings() {
        return settings;
    }

    @Override
    public Downloads downloads() {
        return downloads;
    }

    @Override
    public MusicProvider provider() {
        return provider;
    }

    @Override
    public Handler main() {
        return main;
    }

    @Override
    public ExecutorService io() {
        return io;
    }

    @Override
    public void play(List<MediaItem> items, int index) {
        List<MediaItem> playable = new ArrayList<>();
        int mapped = 0;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                MediaItem item = items.get(i);
                if (item != null && !item.isBrowsable() && item.isPlayable()) {
                    if (i <= index) {
                        mapped = playable.size();
                    }
                    playable.add(item);
                }
            }
        }
        if (playable.isEmpty()) {
            showToast("Open an album or playlist to load its songs first.");
            return;
        }
        if (mapped >= playable.size()) {
            mapped = playable.size() - 1;
        }
        if (service == null) {
            pendingQueue = playable;
            pendingIndex = mapped;
            showToast("Preparing playback…");
            return;
        }
        service.play(playable, mapped);
    }

    @Override
    public void playSingle(MediaItem item) {
        if (item == null) {
            return;
        }
        List<MediaItem> single = new ArrayList<>();
        single.add(item);
        play(single, 0);
    }

    @Override
    public void addToQueue(MediaItem item) {
        if (item == null) {
            return;
        }
        if (service == null) {
            showToast("Playback is still starting…");
            return;
        }
        service.addToQueue(item);
        showToast("Added to queue: " + item.title);
    }

    @Override
    public void quickPlay() {
        List<MediaItem> liked = library.list(Library.LIKED);
        if (!liked.isEmpty()) {
            play(liked, 0);
            return;
        }
        showToast("Finding something to play…");
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<MediaItem> found;
                try {
                    found = provider.search("popular music official audio",
                            MusicProvider.FILTER_SONGS);
                } catch (final Exception error) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            showToast("Quick play unavailable: " + error.getMessage());
                        }
                    });
                    return;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAlive()) {
                            return;
                        }
                        if (found.isEmpty()) {
                            showToast("No quick picks were returned.");
                            return;
                        }
                        play(found, 0);
                    }
                });
            }
        });
    }

    @Override
    public void openCollection(MediaItem item) {
        if (item == null) {
            return;
        }
        if (item.isArtist()) {
            collectionSheet.show(item);
            return;
        }
        if (item.isLocalPlaylist()) {
            switchTab(Tab.LIBRARY);
            if (libraryScreen != null) {
                libraryScreen.openLocalPlaylist(item);
            }
            return;
        }
        collectionSheet.show(item);
    }

    @Override
    public void openArtist(MediaItem item) {
        if (item != null) {
            collectionSheet.show(item);
        }
    }

    @Override
    public void openPlayer() {
        playerSheet.show();
    }

    @Override
    public void openInsights() {
        recapSheet.show();
    }

    @Override
    public void openLibrary() {
        switchTab(Tab.LIBRARY);
    }

    @Override
    public void openSearch(String query) {
        switchTab(Tab.SEARCH);
        if (searchScreen != null && query != null && query.trim().length() > 0) {
            searchScreen.submit(query.trim());
        }
    }

    @Override
    public void showQueue() {
        queueSheet.show();
    }

    @Override
    public void showTrackMenu(final MediaItem item) {
        if (item == null) {
            return;
        }
        if (item.isBrowsable()) {
            boolean saved = library.contains(Library.COLLECTIONS, item)
                    || library.contains(Library.ARTISTS, item);
            String[] options = {saved ? "Remove from library" : "Save to library",
                    item.isArtist() ? "Open artist" : "Open"};
            new AlertDialog.Builder(this)
                    .setTitle(item.title)
                    .setItems(options, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                if (item.isArtist()) {
                                    library.toggle(Library.ARTISTS, item);
                                    showToast(library.contains(Library.ARTISTS, item)
                                            ? "Artist saved" : "Artist removed");
                                } else {
                                    library.toggle(Library.COLLECTIONS, item);
                                    showToast(library.contains(Library.COLLECTIONS, item)
                                            ? "Saved to library" : "Removed from library");
                                }
                                refreshCurrentScreen();
                            } else if (item.isArtist()) {
                                openArtist(item);
                            } else {
                                openCollection(item);
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        final boolean liked = library.contains(Library.LIKED, item);
        final boolean offline = downloads.isDownloaded(item);
        String[] options = {liked ? "Remove from Liked Songs" : "Add to Liked Songs",
                "Add to playlist", offline ? "Downloaded" : "Download offline",
                "Add to queue", "Go to artist", "Start radio"};
        new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                toggleLike(item);
                                break;
                            case 1:
                                addToPlaylist(item);
                                break;
                            case 2:
                                if (!offline) {
                                    download(item);
                                } else {
                                    showToast("Already downloaded.");
                                }
                                break;
                            case 3:
                                addToQueue(item);
                                break;
                            case 4:
                                if (item.author.length() > 0) {
                                    openArtistByName(item.author);
                                } else {
                                    showToast("No artist is listed for this track.");
                                }
                                break;
                            case 5:
                                startRadio(item);
                                break;
                            default:
                                break;
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void toggleLike(MediaItem item) {
        if (item == null) {
            return;
        }
        boolean liked = library.toggle(Library.LIKED, item);
        showToast(liked ? "Added to Liked Songs" : "Removed from Liked Songs");
        refreshCurrentScreen();
        if (playerSheet != null) {
            playerSheet.update();
        }
    }

    @Override
    public boolean isLiked(MediaItem item) {
        return item != null && library.contains(Library.LIKED, item);
    }

    @Override
    public void download(final MediaItem item) {
        if (item == null || !isAlive()) {
            return;
        }
        if (activeDownloadSignal != null) {
            showToast(activeDownloadSignal.isCancelled()
                    ? "The cancelled download is still stopping. Try again in a moment."
                    : "A download is already running. Cancel it before starting another.");
            return;
        }
        if (item.localUri.length() > 0) {
            showToast("This song is already offline.");
            return;
        }
        if (item.id.length() == 0) {
            showToast("Play an online song first.");
            return;
        }
        if (downloads.isDownloaded(item)) {
            showToast("Already downloaded.");
            return;
        }

        final Downloads.CancellationSignal cancellation = new Downloads.CancellationSignal();
        activeDownloadSignal = cancellation;
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Downloading")
                .setMessage(item.title)
                .setView(bar)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        cancellation.cancel();
                        showToast("Cancelling download…");
                    }
                })
                .setCancelable(true)
                .create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                cancellation.cancel();
            }
        });
        dialog.show();

        try {
            activeDownloadTask = io.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        final MediaItem saved = downloads.downloadSync(item,
                                new Downloads.Callback() {
                                    @Override
                                    public void onProgress(final int percent, final long done,
                                                           final long total) {
                                        if (!isAlive() || cancellation.isCancelled()) {
                                            return;
                                        }
                                        main.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!isAlive() || cancellation.isCancelled()
                                                        || !dialog.isShowing()) {
                                                    return;
                                                }
                                                if (percent >= 0) {
                                                    bar.setProgress(percent);
                                                } else if (total > 0) {
                                                    bar.setProgress((int) Math.min(99L,
                                                            done * 100L / total));
                                                }
                                            }
                                        });
                                    }
                                }, cancellation);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAlive()) {
                                    return;
                                }
                                clearDownloadRequest(cancellation);
                                if (dialog.isShowing()) {
                                    dialog.dismiss();
                                }
                                if (cancellation.isCancelled()) {
                                    showToast("Download finished as cancellation arrived; kept offline.");
                                    refreshCurrentScreen();
                                    return;
                                }
                                showToast("Saved offline: " + saved.title);
                                refreshCurrentScreen();
                            }
                        });
                    } catch (final Exception error) {
                        final boolean cancelled = cancellation.isCancelled()
                                || Thread.currentThread().isInterrupted();
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAlive()) {
                                    return;
                                }
                                clearDownloadRequest(cancellation);
                                if (dialog.isShowing()) {
                                    dialog.dismiss();
                                }
                                if (cancelled) {
                                    showToast("Download cancelled.");
                                } else {
                                    String message = error.getMessage();
                                    showToast("Download failed: "
                                            + (message == null ? "invalid audio or network error" : message));
                                }
                            }
                        });
                    }
                }
            });
        } catch (RuntimeException rejected) {
            cancellation.cancel();
            clearDownloadRequest(cancellation);
            dialog.dismiss();
            showToast("A download could not be started.");
        }
    }

    private void clearDownloadRequest(Downloads.CancellationSignal signal) {
        if (activeDownloadSignal == signal) {
            activeDownloadSignal = null;
            activeDownloadTask = null;
        }
    }

    @Override
    public void addToPlaylist(final MediaItem item) {
        if (item == null) {
            return;
        }
        if (item.isBrowsable()) {
            String key = item.isArtist() ? Library.ARTISTS : Library.COLLECTIONS;
            boolean saved = library.toggle(key, item);
            showToast(saved ? "Saved to library" : "Removed from library");
            refreshCurrentScreen();
            return;
        }
        final List<MediaItem> playlists = library.playlists();
        String[] labels = new String[playlists.size() + 1];
        labels[0] = "+ New playlist…";
        for (int i = 0; i < playlists.size(); i++) {
            labels[i + 1] = playlists.get(i).title;
        }
        new AlertDialog.Builder(this)
                .setTitle("Add to playlist")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            createPlaylistDialog(item);
                        } else {
                            MediaItem playlist = playlists.get(which - 1);
                            boolean added = library.addToPlaylist(playlist.id, item);
                            showToast(added
                                    ? "Added to " + playlist.title
                                    : "Already in " + playlist.title);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void createPlaylist() {
        createPlaylistDialog(null);
    }

    private void createPlaylistDialog(final MediaItem firstTrack) {
        final EditText input = new EditText(this);
        input.setHint("Playlist name");
        input.setSingleLine(true);
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        new AlertDialog.Builder(this)
                .setTitle("New local playlist")
                .setView(input)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0 || name.length() > 100) {
                            showToast("Use a name between 1 and 100 characters.");
                            return;
                        }
                        MediaItem playlist = library.createPlaylist(name, firstTrack);
                        if (playlist != null) {
                            showToast("Created " + playlist.title);
                        }
                        refreshCurrentScreen();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openArtistByName(final String name) {
        showToast("Finding " + name + "…");
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<MediaItem> artists;
                try {
                    artists = provider.search(name, MusicProvider.FILTER_ARTISTS);
                } catch (final Exception error) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            showToast("Artist unavailable: " + error.getMessage());
                        }
                    });
                    return;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAlive()) {
                            return;
                        }
                        if (artists.isEmpty()) {
                            showToast("No artist profile was found.");
                        } else {
                            openArtist(artists.get(0));
                        }
                    }
                });
            }
        });
    }

    @Override
    public void startRadio(final MediaItem item) {
        if (item == null) {
            return;
        }
        showToast("Building radio from " + item.title + "…");
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<MediaItem> radio = new ArrayList<>();
                final String[] failure = new String[1];
                radio.add(item);
                try {
                    // Provider mix first; a title search is the generic fallback.
                    List<MediaItem> seeds = provider.radio(item);
                    if (seeds == null || seeds.isEmpty()) {
                        String seed = (item.author.length() > 0 ? item.author + " " : "")
                                + item.title;
                        seeds = provider.search(seed, MusicProvider.FILTER_SONGS);
                    }
                    for (MediaItem seedItem : seeds) {
                        if (seedItem == null) {
                            continue;
                        }
                        if (!seedItem.isBrowsable()
                                && !seedItem.identity().equals(item.identity())) {
                            radio.add(seedItem);
                        }
                        if (radio.size() >= 30) {
                            break;
                        }
                    }
                } catch (Exception error) {
                    failure[0] = error.getMessage();
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAlive()) {
                            return;
                        }
                        if (failure[0] != null) {
                            showToast("Radio unavailable: " + failure[0]);
                            return;
                        }
                        if (radio.size() == 1) {
                            showToast("No similar tracks were found; playing only this song.");
                        }
                        play(radio, 0);
                        if (service != null) {
                            service.setShuffle(true);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void showToast(final String message) {
        if (!isAlive() || message == null || message.length() == 0) {
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                if (isAlive()) {
                    Toast.makeText(MusicActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void shareText(final String chooserTitle, final String text) {
        if (!isAlive() || text == null || text.length() == 0) {
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                if (!isAlive()) {
                    return;
                }
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(share,
                        chooserTitle == null ? "Share" : chooserTitle));
            }
        });
    }

    @Override
    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        } else {
            showToast("Notifications are already allowed.");
        }
    }

    @Override
    public void refreshCurrentScreen() {
        if (currentScreen != null) {
            currentScreen.refresh();
        }
        if (queueSheet != null && queueSheet.isShowing()) {
            queueSheet.render();
        }
        if (playerSheet != null && playerSheet.isShowing()) {
            playerSheet.update();
        }
    }

    /* ============================================================== deep links */

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        try {
            onHandleViewIntent(intent.getData());
        } catch (Exception error) {
            showToast("Could not open link: " + error.getMessage());
        }
    }

    /**
     * Hook for provider-specific links. The framework declares no URL filters;
     * an app adds {@code VIEW} intent filters for its service and turns the
     * {@link Uri} into a track ({@link #playSingle}) or a collection
     * ({@link #openCollection}) here. Called on the main thread.
     */
    protected void onHandleViewIntent(Uri data) {
    }
}
