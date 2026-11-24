# Google Play Music theme — what this app is and is not

**Spinback** is an unofficial revival of **Google Play Music** (2011–2020),
built on the [Soniq Base Android](https://github.com/mob5824m-wq/Soniq-Base-Android)
framework. This document maps the original service — as described on
[Wikipedia](https://en.wikipedia.org/wiki/Google_Play_Music) — onto what
this app actually does, so the theme never overclaims.

> **Unofficial tribute.** Google Play Music and Google Play are trademarks of
> Google LLC. This project is not affiliated with, sponsored by, or endorsed
> by Google. No Google asset is used: the launcher icon is an original vector
> drawn in the spirit of the original headphones mark.

## Why the catalogue is YouTube Music

Google Play Music was announced on 10 May 2011, launched publicly on
16 November 2011, and **shut down in December 2020 after being replaced by
YouTube Music**. Google migrated users' libraries to the successor service.
There is no Google Play Music API left to call — the service, its upload
locker, its store, and its `play.google.com/music/…` links are all dead.

This revival therefore uses the successor's **public, unauthenticated
InnerTube endpoints** as its catalogue, behind the framework's
`MusicProvider` SPI (`GooglePlayMusicProvider` → `YouTubeMusicApi` →
`InnerTubeClient`). That is the same approach as the Soniq Base reference
app, re-themed. Consequences, stated plainly:

- **No sign-in.** Public endpoints only. Age-restricted, members-only, or
  region-locked videos refuse to play and the reason is shown as text.
- **No bypass.** No cookies, OAuth, bot-check solving, or signature
  deciphering. If Google changes the player response enough that audio URLs
  can no longer be read directly, playback breaks until `YouTubeMusicApi`
  is updated.
- **Honest scrobbling.** `musicService()` returns `music.youtube.com` and
  `originUrl()` returns the YouTube watch URL, so ListenBrainz listens never
  claim a Google Play Music origin.
- **Dead links stay dead.** `play.google.com/music/…` links are not handled:
  their ids cannot be resolved by any live catalogue. The app handles
  `music.youtube.com` / `youtu.be` links instead (see `MainActivity`).

Swapping the catalogue later means writing one new `MusicProvider` — the
framework, UI, playback, library, downloads, lyrics, and scrobbling do not
change.

## Theme mapping

| Google Play Music (2011–2020) | This app |
|---|---|
| Orange identity (`#FF5722` family), headphones mark | Same orange accent (`Theme.customize(0xFFFF5722, 0xFFFF9800)` in `SpinbackApp`), original white-headphones-on-orange adaptive icon |
| **Listen Now** (Home) | **Home** tab: greeting, tagline, quick actions, Quick picks, Recently played, Continue listening |
| **My Music** / library | **Library** tab: Liked songs, saved artists/albums, local playlists, downloads, recent — all on-device, no account |
| **Explore** / search | **Search** tab with Songs / Artists / Albums / Playlists filters and search history |
| Curated **radio** by mood/activity | **Start radio** from any track (provider `radio()`), plus queue shuffle/repeat |
| Offline playback, downloads | **Downloads** to `Music/Google Play Music` via MediaStore, offline index, Wi-Fi-only gate |
| Full player, queue, lyrics | Framework player sheet: artwork, scrubber, synced lyrics (tap-to-seek), like/download/queue/radio, up-next queue |
| Web player, Android TV, Wear OS | Android app + **Android Auto** browse (`SoniqMediaBrowserService`) + home-screen **widget**. No web/TV/Wear clients — not claimed |
| 50,000-song free locker, file matching, 320 kbps | **Not replicated.** No locker, no upload, no matching. Stream quality is a target bitrate setting; the actual stream is whatever the successor catalogue returns |
| All Access (40M songs, no ads, unlimited skips) | **Not replicated.** No subscription, no catalogue-size claim. Skips are unlimited because there is no ad tier, not because of a paid plan |
| Podcasts (2016) | **Not replicated.** The framework is music-only |
| Store purchases, 5 phones / 10 devices | **Not replicated.** Local-first: no account, no cloud sync, no device limits to claim |
| Light Material theme (2016 redesign) | Dark framework UI with the orange accent. The framework is dark-only; forking a light theme would mean forking the framework, so the revival keeps dark surfaces and the recognisable orange |

## What the framework gives this app (for free)

Everything below comes from `:soniq-base` and is provider-agnostic:

- Foreground `mediaPlayback` service, `MediaSession`, lock-screen /
  Bluetooth / headset controls, `MediaStyle` notification, audio focus with
  ducking, wake/Wi-Fi locks, unplug-to-pause.
- Queue with shuffle (current song stays first), repeat off/all/one, up-next
  sheet, per-item removal.
- Liked songs, saved artists/albums, local playlists in private storage.
- Offline downloads with progress/cancellation and an offline index.
- Synced lyrics from lrclib.net with plain-text fallback and honest
  instrumental / not-found / unavailable states.
- Opt-in ListenBrainz scrobbling (official listen rule, 1 req/s, offline
  queue) and a Wrapped-style **Year in review** from public statistics.
- Now-playing widget, Android Auto browse, crossfade/gapless, resume,
  search history, Wi-Fi-only switch, sleep timer, equalizer presets,
  accessibility (labels, 48 dp targets, one focus stop per row).

See `docs/REQUIRED-FEATURES.md` and `docs/WIDGETS.md` (vendored from Soniq
Base) for the framework contract.

## Repository layout

```
soniq-base/   Vendored Soniq Base framework (MIT). Provider-agnostic.
app/          Google Play Music themed app (com.spinback.gplaymusic).
  SpinbackApp.java              SoniqApp + orange Theme.customize
  GooglePlayMusicProvider.java  MusicProvider SPI → YouTubeMusicApi
  YouTubeMusicApi.java          Search / browse / resolve (successor catalogue)
  InnerTubeClient.java          Public InnerTube HTTP client
  MainActivity.java             MusicActivity + YouTube deep links
docs/         This file + vendored framework docs
scripts/      compile-check.py (android.jar-only sanity check)
```

## Build

```bash
./gradlew :soniq-base:assembleDebug   # library AAR
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (debug-signed, R8)
./gradlew :soniq-base:testDebugUnitTest
ANDROID_HOME=~/Android/Sdk python3 scripts/compile-check.py --tests
```

Requires JDK 17 and an Android SDK with platform 35. Gradle 8.9 / AGP 8.7.3
via the wrapper. Versions come from git tags (`vX.Y.Z`, or
`X.Y.Z-SNAPSHOT` after it, or `0.0.0-SNAPSHOT` with no tags).
