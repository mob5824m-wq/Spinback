# Changelog

All notable changes to Spinback are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[SemVer](https://semver.org/). A version is a `vX.Y.Z` git tag; cut one with
`python3 scripts/release.py --bump patch --yes` (see README → Releases).

## [Unreleased]

- Initial release: Google Play Music themed revival on Soniq Base Android.
  Vendored `:soniq-base` framework (playback, queue, library, downloads,
  lyrics, ListenBrainz scrobbling, widget, Android Auto, crossfade/gapless,
  resume, search history, Wi-Fi only, year in review) plus `:app`
  (`com.spinback.gplaymusic`) with Google Play Music orange branding,
  headphones launcher icon, and a `MusicProvider` backed by YouTube Music's
  public endpoints (Google Play Music's successor service).
- Added `assets/logo.png` (white headphones on Google Play Music orange,
  shown in the README) and `.github/workflows/release.yml`: tag-triggered
  releases with the versioned APK attached, plus auto version bumping via
  Actions → Release → Run workflow or a `[release]` commit message on `main`.
  `scripts/release.py` gained `--bump patch|minor|major`.
