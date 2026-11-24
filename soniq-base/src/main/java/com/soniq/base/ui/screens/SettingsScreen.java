package com.soniq.base.ui.screens;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.soniq.base.R;
import com.soniq.base.data.ImageLoader;
import com.soniq.base.data.ListenBrainzApi;
import com.soniq.base.data.ScrobbleQueue;
import com.soniq.base.data.Settings;
import com.soniq.base.ui.Host;
import com.soniq.base.ui.Theme;
import com.soniq.base.ui.Ui;

import java.io.IOException;

/** Stream quality, notifications, sleep timer, EQ, ListenBrainz and about. */
public final class SettingsScreen extends Screen {

    public SettingsScreen(Host host) {
        super(host);
    }

    @Override
    protected void build(LinearLayout container) {
        container.addView(Ui.text(host.context(), "Settings", 28f, Theme.TEXT, true));
        container.addView(Ui.text(host.context(),
                "Playback behaviour on this device", 12f, Theme.TEXT_DIM, false));

        container.addView(Ui.sectionHeader(host.context(), "Playback"));
        container.addView(settingRow("Stream quality",
                "Target audio bitrate · currently " + host.settings().qualityLabel(),
                new Runnable() {
                    @Override
                    public void run() {
                        chooseQuality();
                    }
                }));
        container.addView(settingRow("Notifications",
                "Allow the now-playing media notification",
                new Runnable() {
                    @Override
                    public void run() {
                        host.requestNotificationPermission();
                    }
                }));
        container.addView(settingRow("Sleep timer",
                describeSleepTimer(),
                new Runnable() {
                    @Override
                    public void run() {
                        chooseSleepTimer();
                    }
                }));
        String equalizerSummary = host.service() == null
                ? "Selected preset applies when playback is ready"
                : host.service().equalizerStatus();
        container.addView(settingRow("Equalizer", equalizerSummary,
                new Runnable() {
                    @Override
                    public void run() {
                        chooseEqualizer();
                    }
                }));

        container.addView(settingRow("Crossfade", crossfadeSummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        chooseCrossfade();
                    }
                }));
        container.addView(settingRow("Gapless playback", gaplessSummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        host.settings().setGaplessEnabled(!host.settings().gaplessEnabled());
                        if (host.service() != null) {
                            host.service().applyEngineSettings();
                        }
                        refresh();
                    }
                }));
        container.addView(settingRow("Continue listening", resumeSummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        host.settings().setResumeEnabled(!host.settings().resumeEnabled());
                        refresh();
                    }
                }));

        container.addView(Ui.sectionHeader(host.context(), "Data"));
        container.addView(settingRow("Wi-Fi only", wifiOnlySummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        host.settings().setWifiOnly(!host.settings().wifiOnly());
                        refresh();
                    }
                }));
        container.addView(settingRow("Library order", host.settings().librarySortLabel(),
                new Runnable() {
                    @Override
                    public void run() {
                        chooseLibrarySort();
                    }
                }));
        container.addView(settingRow("Search history", searchHistorySummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        confirmClearSearchHistory();
                    }
                }));

        container.addView(Ui.sectionHeader(host.context(), "ListenBrainz"));
        container.addView(settingRow("Scrobble to ListenBrainz", scrobbleSummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        toggleScrobbling();
                    }
                }));
        container.addView(settingRow("User token", tokenSummary(), new Runnable() {
            @Override
            public void run() {
                showTokenDialog();
            }
        }));
        container.addView(settingRow("Now playing updates", nowPlayingSummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        host.settings().setAnnounceNowPlaying(!host.settings().announceNowPlaying());
                        refresh();
                    }
                }));
        container.addView(settingRow("Pending listens", pendingSummary(), new Runnable() {
            @Override
            public void run() {
                ScrobbleQueue queue = ScrobbleQueue.get(host.context());
                queue.clearLastError();
                int pending = queue.pendingCount();
                queue.flush();
                if (pending == 0) {
                    host.showToast("Nothing waiting to be submitted");
                } else if (!host.settings().isListenBrainzConfigured()) {
                    host.showToast(pending + " listen(s) remain queued until scrobbling is enabled.");
                } else {
                    host.showToast("Retrying " + pending + " queued listen(s)…");
                }
                host.main().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (host.isAlive()) {
                            refresh();
                        }
                    }
                }, 1500L);
            }
        }));

        container.addView(Ui.sectionHeader(host.context(), "Year in review"));
        container.addView(settingRow("Open recap",
                "Monthly chart, top artists, songs, albums and genres for any year",
                new Runnable() {
                    @Override
                    public void run() {
                        host.openInsights();
                    }
                }));
        container.addView(settingRow("Statistics profile", recapProfileSummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        showRecapProfileDialog();
                    }
                }));

        container.addView(Ui.sectionHeader(host.context(), "Storage"));
        container.addView(settingRow("Downloaded music", downloadsSummary(),
                new Runnable() {
                    @Override
                    public void run() {
                        confirmDeleteDownloads();
                    }
                }));
        container.addView(settingRow("Clear artwork cache",
                "Removes decoded cover art from memory; playback is not interrupted",
                new Runnable() {
                    @Override
                    public void run() {
                        ImageLoader.clear();
                        host.showToast("Artwork cache cleared");
                    }
                }));

        container.addView(Ui.sectionHeader(host.context(), "About"));
        container.addView(settingRow("Version", versionName(), null));
        container.addView(settingRow("Music source",
                host.provider().id() + " · " + host.provider().displayName(), null));
        container.addView(settingRow("Framework",
                "Soniq Base · playback, library, downloads, lyrics and scrobbling; "
                        + "the catalogue comes from the app's music source.",
                null));

        android.widget.TextView footer = Ui.text(host.context(),
                appLabel() + " · powered by Soniq Base · Android 8.0+ (API 26)",
                11f, Theme.TEXT_FAINT, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(16), dp(28), dp(16), dp(8));
        container.addView(footer);
    }

    private String describeSleepTimer() {
        if (host.service() == null) {
            return "Pause playback after a delay · off";
        }
        int minutes = host.service().sleepTimerMinutes();
        return minutes > 0
                ? ("Pause playback after " + minutes + " minutes")
                : "Pause playback after a delay · off";
    }

    private String crossfadeSummary() {
        int seconds = host.settings().crossfadeSeconds();
        return seconds <= 0
                ? "Off · tracks change with a short gap"
                : "Fade the last " + seconds + " s into the next track";
    }

    private String gaplessSummary() {
        return host.settings().gaplessEnabled()
                ? "On · the next track is prepared ahead for a seamless change"
                : "Off · saves memory and one decoder";
    }

    private String resumeSummary() {
        return host.settings().resumeEnabled()
                ? "Home offers the exact track and position you left"
                : "Off · Home opens without a resume card";
    }

    private String wifiOnlySummary() {
        boolean wifiOnly = host.settings().wifiOnly();
        String network = com.soniq.base.playback.NetworkStatus.label(host.context());
        return wifiOnly
                ? "On · streaming and downloads wait for Wi-Fi · now " + network
                : "Off · streams and downloads on any connection · now " + network;
    }

    private String searchHistorySummary() {
        int count = new com.soniq.base.data.SearchHistory(host.context()).size();
        return count == 0 ? "No searches saved" : count + " saved · tap to clear";
    }

    private String downloadsSummary() {
        String size = host.downloads().totalLabel();
        int count = host.downloads().all().size();
        if (count == 0) {
            return "No downloads · nothing taking up space";
        }
        return count + (count == 1 ? " song · " : " songs · ")
                + (size.length() > 0 ? size : "unknown size");
    }

    private void chooseCrossfade() {
        final int[] values = {0, 2, 4, 6, 8, 12};
        String[] labels = {"Off", "2 seconds", "4 seconds", "6 seconds",
                "8 seconds", "12 seconds"};
        int current = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == host.settings().crossfadeSeconds()) {
                current = i;
            }
        }
        new AlertDialog.Builder(host.context())
                .setTitle("Crossfade")
                .setSingleChoiceItems(labels, current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.settings().setCrossfadeSeconds(values[which]);
                        if (host.service() != null) {
                            host.service().applyEngineSettings();
                        }
                        dialog.dismiss();
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseLibrarySort() {
        final int[] values = {Settings.SORT_RECENT, Settings.SORT_TITLE,
                Settings.SORT_ARTIST, Settings.SORT_DURATION};
        int current = host.settings().librarySort();
        new AlertDialog.Builder(host.context())
                .setTitle("Library order")
                .setSingleChoiceItems(new String[]{
                                com.soniq.base.data.LibrarySort.describe(values[0]),
                                com.soniq.base.data.LibrarySort.describe(values[1]),
                                com.soniq.base.data.LibrarySort.describe(values[2]),
                                com.soniq.base.data.LibrarySort.describe(values[3])},
                        current, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                host.settings().setLibrarySort(values[which]);
                                dialog.dismiss();
                                host.refreshCurrentScreen();
                                refresh();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearSearchHistory() {
        final com.soniq.base.data.SearchHistory history =
                new com.soniq.base.data.SearchHistory(host.context());
        if (history.size() == 0) {
            host.showToast("There is no search history to clear.");
            return;
        }
        new AlertDialog.Builder(host.context())
                .setTitle("Clear search history?")
                .setMessage("The queries saved on this device are removed. "
                        + "Your music and account are not affected.")
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        history.clear();
                        host.showToast("Search history cleared");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteDownloads() {
        final int count = host.downloads().all().size();
        if (count == 0) {
            host.showToast("There are no downloads to remove.");
            return;
        }
        new AlertDialog.Builder(host.context())
                .setTitle("Remove " + count + " download" + (count == 1 ? "" : "s") + "?")
                .setMessage("Frees " + downloadsSummary() + ". You can download them again "
                        + "while online.")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int removed = host.downloads().deleteAll();
                        host.showToast(removed + " download" + (removed == 1 ? "" : "s")
                                + " removed");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout settingRow(String title, String subtitle, final Runnable onClick) {
        LinearLayout row = Ui.row(host.context());
        row.setPadding(dp(8), dp(12), dp(8), dp(12));
        row.setBackground(Ui.ripple(host.context(), Theme.SURFACE, 12f));
        row.setClickable(onClick != null);
        if (onClick != null) {
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onClick.run();
                }
            });
        }
        LinearLayout info = Ui.column(host.context());
        info.addView(Ui.text(host.context(), title, 14f, Theme.TEXT, true));
        info.addView(Ui.text(host.context(), subtitle, 11f, Theme.TEXT_DIM, false));
        row.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (onClick != null) {
            row.addView(Ui.icon(host.context(), R.drawable.soniq_ic_next, Theme.TEXT_FAINT, 16));
        }
        return row;
    }

    private void chooseQuality() {
        final int[] values = {Settings.QUALITY_128, Settings.QUALITY_256};
        String[] labels = {"128 kbps · data saver", "256 kbps · higher quality"};
        int current = host.settings().targetBitrate() == Settings.QUALITY_256 ? 1 : 0;
        new AlertDialog.Builder(host.context())
                .setTitle("Stream quality")
                .setSingleChoiceItems(labels, current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.settings().setTargetBitrate(values[which]);
                        dialog.dismiss();
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseSleepTimer() {
        if (host.service() == null) {
            host.showToast("Playback service is not ready yet.");
            return;
        }
        final int[] minutes = {0, 15, 30, 45, 60, 90};
        String[] labels = {"Off", "15 minutes", "30 minutes", "45 minutes",
                "60 minutes", "90 minutes"};
        int current = host.service().sleepTimerMinutes();
        int selected = 0;
        for (int i = 0; i < minutes.length; i++) {
            if (minutes[i] == current) {
                selected = i;
            }
        }
        new AlertDialog.Builder(host.context())
                .setTitle("Sleep timer")
                .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.service().setSleepTimer(minutes[which]);
                        dialog.dismiss();
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseEqualizer() {
        if (host.service() == null) {
            host.showToast("Playback service is not ready yet.");
            return;
        }
        if (!host.service().isEqualizerSupported()) {
            host.showToast("No usable equalizer effect is available on this device.");
            return;
        }
        final String[] presets = host.service().equalizerPresets();
        if (presets.length == 0) {
            host.showToast("No equalizer presets are available on this device.");
            return;
        }
        int current = host.service().equalizerPreset();
        new AlertDialog.Builder(host.context())
                .setTitle("Equalizer preset")
                .setSingleChoiceItems(presets, Math.min(current, presets.length - 1),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                host.service().setEqualizerPreset(which);
                                dialog.dismiss();
                                refresh();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ============================================================ ListenBrainz */

    private String scrobbleSummary() {
        Settings settings = host.settings();
        boolean hasToken = settings.listenBrainzToken().length() > 0;
        if (settings.scrobblingEnabled() && hasToken) {
            String user = settings.scrobbleUserName();
            return "On — listening as " + (user.length() > 0 ? user : "your ListenBrainz user");
        }
        if (hasToken) {
            return "Off — token saved, scrobbling paused";
        }
        return "Off — add your user token to scrobble";
    }

    private String tokenSummary() {
        String masked = host.settings().maskedListenBrainzToken();
        return masked.length() > 0
                ? masked + " · tap to change"
                : "Not set · get one at listenbrainz.org/settings";
    }

    private String recapProfileSummary() {
        Settings settings = host.settings();
        String profile = settings.recapUserName();
        String saved = "Uses your saved ListenBrainz user; no token is needed for statistics";
        if (settings.insightsUserName().length() > 0) {
            return profile + " · tap to change or clear the override";
        }
        return profile.length() > 0 ? profile + " · " + saved : "Not set · " + saved;
    }

    private void showRecapProfileDialog() {
        final Settings settings = host.settings();
        final EditText input = new EditText(host.context());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("ListenBrainz user name");
        input.setSingleLine(true);
        String existing = settings.recapUserName();
        if (existing.length() > 0) {
            input.setText(existing);
            input.setSelection(existing.length());
        }
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(host.context())
                .setTitle("Statistics profile")
                .setMessage("Year in review reads public ListenBrainz statistics for this "
                        + "user name. It falls back to the user your scrobbling token belongs to, "
                        + "and never sends the token.")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() > 0 && (name.length() < 2 || name.length() > 64)) {
                            host.showToast("Use the ListenBrainz user name, 2–64 characters.");
                            return;
                        }
                        settings.setInsightsUserName(name);
                        host.showToast(name.length() > 0
                                ? "Recap will read " + name : "Recap follows your scrobbling user");
                        refresh();
                    }
                })
                .setNeutralButton("Clear override", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settings.setInsightsUserName("");
                        host.showToast("Recap follows your scrobbling user");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String pendingSummary() {
        ScrobbleQueue queue = ScrobbleQueue.get(host.context());
        int count = queue.pendingCount();
        String base = count == 0
                ? "Nothing queued"
                : count + (count == 1 ? " listen" : " listens") + " waiting to be submitted";
        String error = queue.lastError();
        return error.length() > 0 ? base + " · " + error : base;
    }

    private String nowPlayingSummary() {
        Settings settings = host.settings();
        if (!settings.announceNowPlaying()) {
            return "Off — only finished listens are submitted";
        }
        if (!settings.isListenBrainzConfigured()) {
            return "Enabled — updates are sent when ListenBrainz is connected and scrobbling is on";
        }
        String error = ScrobbleQueue.get(host.context()).nowPlayingError();
        return error.length() > 0
                ? "On — latest update failed: " + error
                : "On — tell ListenBrainz when a song starts";
    }

    private void toggleScrobbling() {
        Settings settings = host.settings();
        if (settings.listenBrainzToken().length() == 0) {
            showTokenDialog();
            return;
        }
        boolean enable = !settings.scrobblingEnabled();
        settings.setScrobblingEnabled(enable);
        if (enable) {
            ScrobbleQueue.get(host.context()).flush();
            String user = settings.scrobbleUserName();
            host.showToast("Scrobbling to ListenBrainz" 
                    + (user.length() > 0 ? " as " + user : ""));
        } else {
            host.showToast("Scrobbling paused");
        }
        refresh();
    }

    private void showTokenDialog() {
        final Settings settings = host.settings();
        final EditText input = new EditText(host.context());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("User token from listenbrainz.org/settings");
        input.setSingleLine(true);
        String existing = settings.listenBrainzToken();
        if (existing.length() > 0) {
            input.setText(existing);
            input.setSelection(existing.length());
        }
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        final AlertDialog dialog = new AlertDialog.Builder(host.context())
                .setTitle("ListenBrainz user token")
                .setMessage("Create a token at listenbrainz.org/settings and paste it here. "
                        + "It is stored only on this device and sent only to api.listenbrainz.org.")
                .setView(input)
                .setPositiveButton("Validate & save", null)
                .setNeutralButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        settings.setListenBrainzToken("");
                        settings.setScrobblingEnabled(false);
                        host.showToast("ListenBrainz token removed");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final String token = input.getText().toString().trim();
                        if (token.length() < 16) {
                            host.showToast("That does not look like a ListenBrainz user token.");
                            return;
                        }
                        positive.setEnabled(false);
                        positive.setText("Checking…");
                        runIo(new Runnable() {
                            @Override
                            public void run() {
                                ListenBrainzApi.TokenResult resultValue = null;
                                String failureValue = null;
                                try {
                                    resultValue = new ListenBrainzApi(host.context()).validateToken(token);
                                } catch (IOException error) {
                                    failureValue = error.getMessage();
                                }
                                final ListenBrainzApi.TokenResult result = resultValue;
                                final String failure = failureValue;
                                host.main().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!host.isAlive() || !dialog.isShowing()) {
                                            return;
                                        }
                                        positive.setEnabled(true);
                                        positive.setText("Validate & save");
                                        if (result != null && result.valid) {
                                            settings.setListenBrainzToken(token);
                                            settings.setScrobbleUserName(result.userName);
                                            settings.setScrobblingEnabled(true);
                                            ScrobbleQueue.get(host.context()).flush();
                                            host.showToast("Connected to ListenBrainz"
                                                    + (result.userName.length() > 0
                                                    ? " as " + result.userName : ""));
                                            dialog.dismiss();
                                            refresh();
                                        } else if (result != null) {
                                            host.showToast("Token rejected: " + result.message);
                                        } else {
                                            host.showToast("Could not reach ListenBrainz: "
                                                    + (failure == null ? "network error" : failure));
                                        }
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
        dialog.show();
    }

    private String appLabel() {
        try {
            CharSequence label = host.context().getApplicationInfo()
                    .loadLabel(host.context().getPackageManager());
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (RuntimeException ignored) {
        }
        return "Soniq Base";
    }

    private String versionName() {
        try {
            PackageInfo info = host.context().getPackageManager()
                    .getPackageInfo(host.context().getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ") · Android "
                    + Build.VERSION.RELEASE;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "Unknown";
        }
    }
}
