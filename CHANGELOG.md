# Changelog

All notable changes to Spinback are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[SemVer](https://semver.org/). A version is a `vX.Y.Z` git tag; cut one with
`python3 scripts/release.py --bump patch --yes` (see README → Releases).

## [Unreleased]

- Fixed the Release workflow failing on `error: nothing under [Unreleased] and
  no --notes` when **Actions → Release → Run workflow** was used with an empty
  `[Unreleased]` section: auto-bumps now fall back to the commit subjects since
  the last release tag (`scripts/release.py --from-commits`), so a bump never
  needs a hand-written changelog entry. Hand-written entries still win.
- Auto-bump releases now build and test **before** publishing: `--prepare`
  writes the section and creates the tag locally, and `--publish-prepared`
  pushes the `Release X.Y.Z` commit and tag only after Gradle and the unit tests
  pass. A broken build no longer leaves a tag or a half-cut release behind.
  `scripts/release.py` gained tests for both paths.

## [1.0.0] - 2026-11-24

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

[unreleased]: https://github.com/mob5824m-wq/Spinback/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/mob5824m-wq/Spinback/releases/tag/v1.0.0
