# Spinback — R8 keep rules.
#
# The app is a thin shell over :soniq-base: SpinbackApp creates the
# GooglePlayMusicProvider, MainActivity extends MusicActivity, and the
# framework instantiates PlaybackService / MediaButtonReceiver /
# SoniqWidgetProvider / SoniqMediaBrowserService from the merged manifest.
# Android keeps manifest-declared components automatically, but R8 full
# mode (AGP 8 default) can still strip members it thinks are unused, so
# the entry points and the provider SPI are pinned explicitly.

# Application and launcher activity.
-keep class com.spinback.gplaymusic.SpinbackApp { *; }
-keep class com.spinback.gplaymusic.MainActivity { *; }

# The catalogue provider: created directly in SpinbackApp.createProvider(),
# but keep its public surface stable for diagnostics and settings.
-keep class com.spinback.gplaymusic.GooglePlayMusicProvider { *; }
-keep class com.spinback.gplaymusic.YouTubeMusicApi { *; }
-keep class com.spinback.gplaymusic.InnerTubeClient { *; }
-keep class com.spinback.gplaymusic.InnerTubeClient$Client { *; }

# Framework entry points instantiated by the system from the manifest.
-keep class com.soniq.base.SoniqApp { *; }
-keep class com.soniq.base.ui.MusicActivity { *; }
-keep class com.soniq.base.playback.PlaybackService { *; }
-keep class com.soniq.base.playback.PlaybackService$LocalBinder { *; }
-keep class com.soniq.base.playback.MediaButtonReceiver { *; }
-keep class com.soniq.base.playback.SoniqWidgetProvider { *; }
-keep class com.soniq.base.playback.SoniqMediaBrowserService { *; }

# The provider SPI: implementations are looked up through the interface,
# so keep the interface and its constants intact.
-keep interface com.soniq.base.provider.MusicProvider { *; }
-keep class com.soniq.base.model.MediaItem { *; }
-keep class com.soniq.base.model.StreamSource { *; }

# org.json is part of the Android framework, but R8 full mode sometimes
# strips its package-private helpers when the app has no explicit rule.
# Keep the public API surface the YouTube client relies on.
-keep class org.json.JSONObject { *; }
-keep class org.json.JSONArray { *; }
-keep class org.json.JSONTokener { *; }
-keep class org.json.JSONException { *; }

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
