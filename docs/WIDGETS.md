# What the framework provides — and what each app must finish

Soniq Base is a **backbone**. It always does the framework half of a feature and
never pretends to do the app half. This page lists the eight capabilities that
have a deliberately unfinished part, what the framework already handles, and the
concrete checklist a hosting app must complete.

Read this before porting a new music app, and keep it in sync when you do.

| Capability | Framework ships | The app must finish |
|---|---|---|
| [Home-screen widget](#1-home-screen-widget) | Default now-playing widget + push channel | Branding, sizes, extra controls |
| [Android Auto / MediaBrowserService](#2-android-auto--mediabrowserservice) | Browse tree for the local library, session wiring | Manifest declaration, car metadata, catalogue nodes |
| [Crossfade / gapless](#3-crossfade--gapless) | Fade engine, prebuffering, hand-off | Advertise it honestly, test real streams |
| [Search history](#4-search-history) | Storage, chips, clear action | Placement, per-provider scopes |
| [Wi-Fi only](#5-wi-fi-only--metered-switch) | Policy, enforcement, Settings row | Foreground-only policy, roaming rules |
| [No lyrics vs instrumental](#6-no-lyrics-vs-instrumental) | Four-state result + cache | Provider answer (`MusicProvider.lyricsState`) |
| [Resume where you left off](#7-resume-where-you-left-off) | Persistence, card, restore | Queue identity rules, deep links |
| [Accessibility](#8-accessibility) | Labels, one-stop rows, contrast tokens | Screens not built by the framework, translations |

A short version of this list also lives in `AGENT.md`; this file is the long form
with code pointers. Everything below is verified by JVM tests in
`soniq-base/src/test/java` where the logic is pure.

---

## 1. Home-screen widget

**Framework:** `playback/SoniqWidgetProvider` renders
`res/layout/soniq_widget.xml` (artwork, title, artist, play/next) and is
declared in the library manifest. `PlaybackService` pushes state through
`SoniqWidgetProvider.publish(...)` whenever the track or play state changes, so
one process owns the truth. `Settings.widgetEnabled()` lets an app switch the
framework widget off.

**App:**
- **Branding.** The default widget uses framework colours
  (`@color/soniq_*`) and the app label from the manifest. If the app has its own
  palette, ship your own layout + provider rather than theming this one.
- **Sizes.** `res/xml/soniq_widget_info.xml` declares 3×1 cells with a 250 dp
  minimum. Launchers differ; if you need 4×1, 2×2 or a lock-screen widget,
  declare a second provider and give it its own `minWidth`/`targetCellWidth`.
- **Extra controls.** Seek bar, like, download, shuffle — `RemoteViews` cannot
  hold the framework's programmatic widgets, so these need new XML and new
  `PendingIntent` actions on `PlaybackService`.
- **Deep link.** Tapping the widget opens `MusicActivity` with
  `ACTION_TOGGLE`; change `activityIntent()` if your launcher activity is a
  subclass with different behaviour.
- **Lock screen / AOD.** Not supported by `AppWidgetProvider` before API 31 in
  the way users expect; if the app targets that, use the media session's
  `MediaStyle` notification instead of a widget.

## 2. Android Auto / MediaBrowserService

**Framework:** `playback/SoniqMediaBrowserService` exposes the library the
framework already owns — Liked songs, Recently played, Downloads, Playlists —
as a browsable tree, and hands the browser the live `MediaSession.Token` from
`PlaybackService.sessionToken()`. Browsing is bounded and safe: every
`onLoadChildren` returns what it has rather than crashing a head unit.

**App — required for the feature to appear at all:**
- Declare the service **in your manifest** with the browse action and metadata
  (the library cannot declare it usefully for you):
  ```xml
  <service
      android:name="com.soniq.base.playback.SoniqMediaBrowserService"
      android:exported="true"
      android:foregroundServiceType="mediaPlayback">
      <intent-filter>
          <action android:name="android.media.browse.MediaBrowserService" />
      </intent-filter>
      <meta-data
          android:name="android.media.browse.MediaBrowserService"
          android:resource="@xml/automotive_app_desc" />
  </service>
  ```
  with `res/xml/automotive_app_desc.xml`:
  ```xml
  <automotiveApp><uses name="media"/></automotiveApp>
  ```
- Pass the Android Auto "media app" desktop/QA checklist: the car needs a
  launcher icon, a `MediaSession` that reports play state, and no phone-only
  dialogs on the browse path.
- **Catalogue nodes.** The framework deliberately does not guess your
  provider's browsing. Extend the service (or your own subclass) and override
  `providerChildren()` to add roots ("Mixes", "Charts", "Albums you saved") and
  `browseProviderNode(id)` to expand them. Every id you mint must round-trip
  through `queueFor(mediaId)`.
- **Playback from the car** must start without touching the phone UI. Test with
  the Desktop Head Unit (DHU); the emulator's phone UI is not a substitute.
- **Android Automotive OS (car as a separate device)** needs a second app
  module with `<uses-feature android:name="android.hardware.type.automotive"/>`
  and a car launcher activity — that is app work, not library work.
- **Cold start.** `PlaybackService.sessionToken()` is null until playback has
  started once in the process. The browse tree still works (the car shows the
  library), but transport controls appear only after the first track. If your
  product needs controls immediately, start the service from your
  `Application`/launcher so the session exists early.
- **Dependency note.** This uses the platform `MediaBrowserService`, not
  `androidx.media3`, because Soniq Base has no runtime dependencies. If you want
  `MediaBrowserServiceCompat`, richer browse flags or media3's session wrapper,
  add that dependency in the app and bridge to this service.

## 3. Crossfade / gapless

**Framework:** `playback/PlaybackEngine` (pure, fully tested) decides clamping,
fade curves, prebuffer eligibility and the crossfade start position.
`PlaybackService` prepares the next track on the IO thread, swaps players at the
exact end for gapless, or ramps volumes over 2–12 s for crossfade. Both are off
by default; Settings has rows for each. The service also refuses to report
`BUFFERING` while a fade is audible (`PlaybackState` truthfulness).

**App:**
- **Advertise, never imply.** If you hide the Settings rows, do not describe the
  app as having crossfade.
- **Test constraints:** crossfade cannot apply to live streams (no duration) or
  to tracks shorter than the fade window; gapless needs two decoders alive,
  which some low-RAM devices dislike — the app should consider defaulting
  gapless on only for capable devices.
- **Provider cooperation:** gapless only sounds seamless when the provider
  returns the *next* track quickly. If your resolver is slow or rate-limited,
  prebuffering will miss and users will hear the normal gap — fix it in
  `resolve()`, not by guessing.
- **Equal-power curves**: the backbone is linear. Apps that care about
  perceptual loudness should replace `startFade` with their own curve.

## 4. Search history

**Framework:** `data/SearchHistory` stores up to 12 queries with counts, dedupes
case-insensitively, and renders chips with per-entry removal ("Recent
searches" row in `SearchScreen`) plus a confirmed "Clear" in Settings.

**App:**
- Decide the scope. The framework is device-wide; an app with profiles or
  provider-scoped search should key the history per profile/provider instead of
  sharing it.
- If the app has a search suggestions endpoint, merge it with these chips rather
  than replacing history — users still expect their own past queries first.
- Never record a query the user did not run (no keystroke logging).

## 5. Wi-Fi only / metered switch

**Framework:** `data/NetworkPolicy` (pure, tested) is the single rule;
`PlaybackService` checks it before resolving a stream and `Downloads` checks it
before writing a file. Settings shows the current connection next to the switch.
Unknown networks are treated as **metered for downloads** and allowed for
streaming — an honest default in both directions.

**App:**
- Report the connection type if your app can do so more accurately (VPN,
  tethered, roaming). `NetworkStatus.currentType` is the seam.
- Add app policy on top: roaming rules, "stream only on Wi-Fi in the car",
  per-account data limits. These are product decisions, not framework rules.
- Handle the blocked path in your UI: download buttons should show the reason
  (`NetworkPolicy.blockedReason`) instead of failing silently.

## 6. No lyrics vs instrumental

**Framework:** `data/LyricsRepository` returns one of four outcomes —
`FOUND`, `INSTRUMENTAL`, `NOT_FOUND`, `UNAVAILABLE` — and caches only the
permanent ones. `PlayerSheet` shows a distinct sentence for each, and marks plain
(non-time-synced) lyrics as such instead of faking a sync.

**App:**
- **Answer the hook.** Implement `MusicProvider.lyricsState(track)` returning
  `LyricsState.INSTRUMENTAL` when your catalogue says a track has no vocals.
  That answer wins over the search and is never overwritten.
- Do **not** guess "instrumental" from the title ("Intro", "Interlude") — a
  wrong label is worse than "no lyrics found", which is why the framework keeps
  the three negative states apart.
- If your catalogue exposes lyrics directly, keep returning lyrics from your own
  API and leave the lrclib path unused; the states still apply.

## 7. Resume where you left off

**Framework:** `playback/PlaybackSession` stores the queue (up to 50 items), the
index and the position; `playback/ResumePoint` (pure, tested) decides whether a
resume is worth offering — at least 20 s in, at least 10 s from the end, and no
more than 14 days old. Home shows a "Continue listening" card that restores the
queue and seeks back.

**App:**
- **Identity rules.** The saved point is keyed by `MediaItem.identity()`. If your
  provider mints new ids between sessions (e.g. a fresh search id), resume will
  find the item but the queue copy may not resolve — use stable ids.
- **Expiring streams.** If your stream URLs expire, resume must re-resolve:
  the framework restores the *items*, not the URLs, which is the right shape —
  keep it that way.
- **Deep links.** If the app can be opened from a share/link, decide whether a
  deep link overrides the resume card (usually yes) and clear it then.
- Live streams deliberately never produce a resume point.

## 8. Accessibility

**Framework:** `ui/A11y` + the widget/rows adopt: spoken labels on every
icon button (`Ui.iconButton` takes a description), one focus stop per list row
("Title, artist, 3:42" instead of five swipes), artwork described by track name,
48 dp minimum touch targets, decorative icons hidden from TalkBack, and state
announced through `contentDescription` rather than colour alone.

**App — the part the framework cannot do:**
- **Screens you write** need the same pass: every custom view, dialog and
  overlay gets a label; every gesture has a button equivalent (long-press to
  forget a search is a shortcut, not the only way).
- **Translations.** Strings are English by default; `Ui` and the screens take
  text, so provide `res/values-xx/strings.xml` in your app for your own strings
  and override framework strings by name where you need different wording.
- **Contrast and theming.** If you rebrand with `Theme`, verify body text at
  4.5:1 and large text at 3:1; the default palette is chosen for that.
- **Large fonts.** `AdaptiveUi.scaledTextSize` caps growth at 1.6× so layouts
  do not break, but any custom row must still be checked at 200 % font scale.
- **TalkBack pass.** Run through Home → Search → Library → Settings → Player →
  Queue with TalkBack on before shipping; the framework's own labels are only
  as good as the custom screens around them.

---

## Adding a ninth capability

If a new shared capability needs an app half, add it here with the same three
columns (framework ships / app must finish / code pointers) and a row in the
table at the top. `AGENT.md` should gain the same sentence, and
`docs/REQUIRED-FEATURES.md` gains the requirement only if **every** app must
have it.
