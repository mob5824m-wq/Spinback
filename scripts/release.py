#!/usr/bin/env python3
"""Cut a Spinback release.

A release is a ``vX.Y.Z`` tag on ``main`` plus a ``CHANGELOG.md`` section —
nothing else. Every module's version is derived from that tag (root
``build.gradle.kts``), and pushing the tag runs ``.github/workflows/release.yml``,
which builds and tests both modules and creates the GitHub Release with the
release APK (and the library AAR + sources JAR) attached.

    python3 scripts/release.py 0.2.0                   # validate; show the plan
    python3 scripts/release.py 0.2.0 --yes             # commit changelog on main, tag, push
    python3 scripts/release.py 0.2.0 --pr              # open a "Release 0.2.0" PR instead
    python3 scripts/release.py 0.2.0 --tag-only --yes  # tag main once that PR is merged
    python3 scripts/release.py 0.2.0 --print-body      # GitHub Release body (used by CI)
    python3 scripts/release.py --bump patch            # auto-bump: next patch version
    python3 scripts/release.py --bump patch --yes      # auto-bump, commit, tag, push

Options:
    --bump KIND    auto-compute the version from the latest ``vX.Y.Z`` tag
                   (``patch`` / ``minor`` / ``major``); the version argument
                   is then optional. With no tags yet, any bump starts at
                   ``0.1.0`` (``major`` starts at ``1.0.0``). If there is
                   exactly one prepared changelog release and no tags, tag it first.
    --notes TEXT   one bullet for the new section; repeatable. Defaults to the
                   entries collected under ``## [Unreleased]``.
    --date ISO     release date recorded in the changelog (default: today)

Exit codes: 0 ok; 1 invalid arguments or state; 3 from ``--print-body`` when the
changelog has no section for the version (a placeholder is still printed).
"""

import argparse
import datetime
import difflib
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHANGELOG = os.path.join(ROOT, "CHANGELOG.md")
SEMVER = re.compile(r"^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$")
SECTION = re.compile(r"^## \[([^\]]+)\][^\n]*$", re.M)
LINK_REF = re.compile(r"^\[([^\]]+)\]:\s+(\S+)[^\n]*$", re.M)


def die(message, code=1):
    print("error: " + message, file=sys.stderr)
    sys.exit(code)


def git(*args, check=True):
    result = subprocess.run(["git", *args], cwd=ROOT, text=True, capture_output=True)
    if check and result.returncode != 0:
        die("`git %s` failed:\n%s" % (" ".join(args), result.stderr.strip()))
    return result.stdout.strip()


def has_tag(tag):
    if git("tag", "-l", tag):
        return True
    return bool(git("ls-remote", "--tags", "origin", "refs/tags/" + tag, check=False))


def repo_slug():
    """owner/repo for the origin remote, or None when it is not on GitHub."""
    url = git("remote", "get-url", "origin", check=False)
    match = re.search(r"github\.com[/:]([^/]+/[^/]+?)(?:\.git)?/?$", url)
    return match.group(1) if match else None


def latest_version():
    """Newest ``vX.Y.Z`` tag (without the ``v``), or None when there are none."""
    tag = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", check=False)
    if not tag:
        return None
    version = tag[1:] if tag.startswith("v") else tag
    # A pre-release tag (v1.2.3-rc.1) still counts as the latest release line.
    return version.split("-")[0] if SEMVER.match(version) else None


def bumped_version(kind):
    """Next version for a ``patch`` / ``minor`` / ``major`` bump from the latest tag."""
    latest = latest_version()
    if latest is None:
        # A prepared first release may have been committed without its tag.
        # Publish that section rather than trying to create an empty new one.
        if os.path.exists(CHANGELOG):
            with open(CHANGELOG, encoding="utf-8") as handle:
                changelog = Changelog(handle.read())
            versions = changelog.versions()
            unreleased = changelog.section_body("unreleased") or ""
            if len(versions) == 1 and SEMVER.fullmatch(versions[0]) and not unreleased.strip():
                return versions[0]
        return "1.0.0" if kind == "major" else "0.1.0"
    major, minor, patch = (int(n) for n in latest.split("."))
    if kind == "major":
        return "%d.0.0" % (major + 1)
    if kind == "minor":
        return "%d.%d.0" % (major, minor + 1)
    return "%d.%d.%d" % (major, minor, patch + 1)


class Changelog:
    """A Keep-a-Changelog file: ``## [Unreleased]`` on top, then versions, then link refs."""

    def __init__(self, text):
        self.text = text
        heads = list(SECTION.finditer(text))
        self.link_start = None
        for ref in LINK_REF.finditer(text):
            if not heads or ref.start() > heads[-1].end():
                self.link_start = ref.start()
                break
        body_end = self.link_start if self.link_start is not None else len(text)
        self.sections = []
        for index, head in enumerate(heads):
            end = heads[index + 1].start() if index + 1 < len(heads) else body_end
            self.sections.append({
                "title": head.group(1).strip().lower(),
                "header": head.group(0),
                "start": head.start(),
                "end": end,
                "body": text[head.end():end].strip("\n"),
            })

    def find(self, title):
        title = title.lower()
        return next((s for s in self.sections if s["title"] == title), None)

    def versions(self):
        return [s["title"] for s in self.sections if s["title"] != "unreleased"]

    def section_body(self, version):
        section = self.find(version)
        return section["body"] if section else None

    def release(self, version, date, notes):
        """New text with [Unreleased] moved into a ``[version]`` section; None if empty."""
        if self.find(version):
            die("CHANGELOG.md already has a [%s] section." % version)
        unreleased = self.find("unreleased")
        moved = unreleased["body"] if unreleased else ""
        bullets = [n if n.startswith(("- ", "* ")) else "- " + n for n in notes]
        content = "\n".join(part for part in (moved, "\n".join(bullets)) if part)
        if not content:
            return None
        new_section = "## [%s] - %s\n\n%s\n" % (version, date, content)
        if unreleased:
            rest = self.text[unreleased["end"]:].lstrip("\n")
            return "%s%s\n\n%s\n%s" % (
                self.text[:unreleased["start"]], unreleased["header"], new_section, rest)
        insert_at = self.sections[0]["start"] if self.sections else (
            self.link_start if self.link_start is not None else len(self.text))
        return "%s%s\n%s" % (
            self.text[:insert_at], new_section, self.text[insert_at:].lstrip("\n"))

    def update_links(self, text, version, slug):
        """Point the [unreleased] compare link at the new tag and add the version's link."""
        if not slug:
            return text
        base = "https://github.com/%s" % slug
        previous = next((v for v in self.versions() if v != version), None)
        old_unreleased = re.search(r"^\[unreleased\]:\s+(\S+)", text, re.M)
        if previous:
            new_line = "[%s]: %s/compare/v%s...v%s" % (version, base, previous, version)
        elif old_unreleased:
            # First release after adopting this script: keep the old compare base.
            old_base = re.search(r"compare/(.+)\.\.\.", old_unreleased.group(1))
            new_line = "[%s]: %s/compare/%s...v%s" % (
                version, base, old_base.group(1) if old_base else version, version)
        else:
            new_line = "[%s]: %s/releases/tag/v%s" % (version, base, version)
        unreleased_line = "[unreleased]: %s/compare/v%s...HEAD" % (base, version)
        if old_unreleased:
            return "%s%s\n%s" % (
                text[:old_unreleased.start()], unreleased_line,
                new_line + text[old_unreleased.end():])
        return text.rstrip("\n") + "\n\n" + unreleased_line + "\n" + new_line + "\n"


def release_body(version, body, slug):
    return "%s\n\n## Install\n\nDownload `spinback-google-play-music-%s-release.apk` below and\ninstall it on Android 8.0+ (API 26). The APK is debug-signed (this is a\nrevival client, not a store release), so Android may ask you to allow\ninstalls from your browser or file manager.\n\n## Assets\n\n- `spinback-google-play-music-%s-release.apk` — the app (debug-signed)\n- `soniq-base-release.aar` — the vendored Soniq Base library\n- `soniq-base-*-sources.jar` — library sources\n" % (body, version, version)


def require_clean_tree():
    if git("status", "--porcelain"):
        die("working tree is not clean; commit or stash your changes first.")


def current_branch():
    return git("rev-parse", "--abbrev-ref", "HEAD")


def sync_main(allow_ahead):
    """Fetch origin/main and make sure main is not behind it. Returns commits ahead."""
    git("fetch", "--quiet", "origin", "main")
    ahead, behind = (int(n) for n in git(
        "rev-list", "--left-right", "--count", "main...origin/main").split())
    if behind:
        die("main is %d commit(s) behind origin/main; run `git switch main && git pull --ff-only`."
            % behind)
    if ahead and not allow_ahead:
        die("main has %d unpushed commit(s); push them before tagging." % ahead)
    return ahead


def tag_main(version, tag, slug, apply):
    """Tag an up-to-date main whose changelog already has the version's section."""
    if current_branch() != "main":
        die("run this on main (you are on %s)." % current_branch())
    sync_main(allow_ahead=False)
    if Changelog(open(CHANGELOG, encoding="utf-8").read()).section_body(version) is None:
        die("CHANGELOG.md on main has no [%s] section. Add it with "
            "`scripts/release.py %s --pr`, or tag the commit that has it." % (version, version))
    kind = "pre-release" if "-" in version else "release"
    print("  tag %s  →  %s  (%s)" % (tag, git("rev-parse", "--short", "HEAD"), kind))
    print("  push origin %s" % tag)
    print("  → Release workflow: build + tests, GitHub Release with APK")
    if not apply:
        print("\nDry run. Re-run with --yes to tag and push.")
        return
    git("tag", "-a", tag, "-m", "Spinback %s" % version)
    git("push", "origin", tag)
    print("Tagged %s and pushed it; the Release workflow is running." % tag)
    urls(tag, slug)


def show_diff(old, new):
    for line in difflib.unified_diff(old.splitlines(), new.splitlines(),
                                     "CHANGELOG.md", "CHANGELOG.md", lineterm="", n=1):
        print(line)


def urls(tag, slug):
    if not slug:
        return
    print("\n  Actions:  https://github.com/%s/actions/workflows/release.yml" % slug)
    print("  Release:  https://github.com/%s/releases/tag/%s  (once CI finishes)" % (slug, tag))


def open_release_pr(version, branch, body):
    try:
        result = subprocess.run(
            ["gh", "pr", "create", "--base", "main", "--head", branch,
             "--title", "Release %s" % version, "--body", body],
            cwd=ROOT, text=True, capture_output=True)
    except FileNotFoundError:
        print("`gh` is not installed; open the pull request manually:")
        print("  https://github.com/%s/compare/main...%s" % (repo_slug() or "OWNER/REPO", branch))
        return
    if result.returncode != 0:
        die("`gh pr create` failed:\n%s" % result.stderr.strip())
    print("\nPull request: " + result.stdout.strip())


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("version", nargs="?", default=None,
                        help="X.Y.Z (an optional -suffix marks a pre-release); "
                             "omit with --bump to auto-compute it")
    parser.add_argument("--bump", choices=["patch", "minor", "major"], default=None,
                        metavar="KIND",
                        help="auto-compute the version from the latest vX.Y.Z tag")
    parser.add_argument("--yes", action="store_true", help="apply the plan (default: show it)")
    parser.add_argument("--pr", action="store_true",
                        help="open a release PR instead of committing to main")
    parser.add_argument("--tag-only", action="store_true",
                        help="only tag main (after a release PR was merged)")
    parser.add_argument("--print-body", action="store_true",
                        help="print the GitHub Release body and exit (used by CI)")
    parser.add_argument("--notes", action="append", default=[], metavar="TEXT",
                        help="bullet for the new section; repeatable")
    parser.add_argument("--date", help="release date for the changelog (default: today)")
    args = parser.parse_args()

    if args.bump:
        if args.version:
            die("pass either a version or --bump, not both.")
        git("fetch", "--quiet", "--tags", "origin")
        version = bumped_version(args.bump)
        print("Auto-bumped %s version: %s" % (args.bump, version))
    elif args.version:
        version = args.version.strip().lower()
        if version.startswith("v"):
            version = version[1:]
        if not SEMVER.match(version):
            die("%r is not X.Y.Z (optionally with a -suffix such as -rc.1)" % args.version)
    else:
        die("pass a version (X.Y.Z) or --bump patch|minor|major.")
    tag = "v" + version
    date = args.date or datetime.date.today().isoformat()
    slug = repo_slug()

    if not os.path.exists(CHANGELOG):
        die("CHANGELOG.md not found next to the repository root.")
    original = open(CHANGELOG, encoding="utf-8").read()
    changelog = Changelog(original)

    if args.print_body:
        body = changelog.section_body(version)
        if body is None:
            print("No CHANGELOG.md section for %s; see the changes below and "
                  "CHANGELOG.md on main." % version)
            print("::warning::CHANGELOG.md has no [%s] section; the release uses "
                  "auto-generated notes." % version, file=sys.stderr)
            sys.exit(3)
        print(release_body(version, body, slug))
        return

    if has_tag(tag):
        die("tag %s already exists." % tag)
    require_clean_tree()

    if args.tag_only:
        print("Plan:")
        tag_main(version, tag, slug, args.yes)
        return

    if args.pr:
        branch = "release/%s" % version
        if git("rev-parse", "--verify", "--quiet", "refs/heads/" + branch, check=False):
            die("branch %s already exists." % branch)
        main_changelog = git("show", "origin/main:CHANGELOG.md")
        if Changelog(main_changelog).section_body(version) is not None:
            die("origin/main already has a [%s] section; use --tag-only after merging it."
                % version)
        git("fetch", "--quiet", "origin", "main")
        base_changelog = Changelog(main_changelog)
        new_text = base_changelog.release(version, date, args.notes)
        if new_text is None:
            die("nothing under [Unreleased] on origin/main and no --notes; add changelog "
                "entries to main first.")
        new_text = base_changelog.update_links(new_text, version, slug)
        print("Plan:\n  branch %s from origin/main" % branch)
        print("  commit \"Release %s\" (CHANGELOG.md only)" % version)
        print("  push and open a pull request against main")
        print("  after merging: python3 scripts/release.py %s --tag-only --yes\n" % version)
        show_diff(main_changelog, new_text)
        if not args.yes:
            print("\nDry run. Re-run with --yes to create the branch and the PR.")
            return
        previous_branch = current_branch()
        git("switch", "--quiet", "-c", branch, "origin/main")
        with open(CHANGELOG, "w", encoding="utf-8") as handle:
            handle.write(new_text)
        git("add", "CHANGELOG.md")
        git("commit", "--quiet", "-m", "Release %s" % version)
        git("push", "--quiet", "-u", "origin", branch)
        open_release_pr(version, branch, (
            "Cuts **Spinback %s**: moves the `[Unreleased]` entries of `CHANGELOG.md` "
            "into the new version's section and updates the compare links.\n\n"
            "After merging, tag main from a clean checkout:\n\n"
            "    python3 scripts/release.py %s --tag-only --yes\n\n"
            "Pushing the tag runs the Release workflow: build + tests, create the "
            "GitHub Release with the APK attached."
            % (version, version)))
        if previous_branch != "HEAD":
            git("switch", "--quiet", previous_branch)
            print("Back on %s." % previous_branch)
        return

    # Direct release: commit the changelog on main, tag, push both.
    if changelog.section_body(version) is not None:
        # The section was added in a feature or release PR: nothing to commit.
        print("CHANGELOG.md already has a [%s] section — nothing to commit.\n" % version)
        print("Plan:")
        tag_main(version, tag, slug, args.yes)
        return
    new_text = changelog.release(version, date, args.notes)
    if new_text is None:
        die("nothing under [Unreleased] and no --notes; add changelog entries first.")
    new_text = changelog.update_links(new_text, version, slug)
    if current_branch() != "main":
        die("run this on main (you are on %s); use --pr to open a release pull request "
            "from a branch." % current_branch())
    ahead = sync_main(allow_ahead=True)
    print("Plan:\n  commit \"Release %s\" on main (CHANGELOG.md only)%s" % (
        version, ", pushing %d local commit(s) ahead of origin" % ahead if ahead else ""))
    print("  tag %s  →  %s  (%s)" % (tag, version, "pre-release" if "-" in version else "release"))
    print("  push origin main, then origin %s" % tag)
    print("  → Release workflow: build + tests, GitHub Release with APK\n")
    show_diff(original, new_text)
    if not args.yes:
        print("\nDry run. Re-run with --yes to commit, tag and push.")
        return
    with open(CHANGELOG, "w", encoding="utf-8") as handle:
        handle.write(new_text)
    git("add", "CHANGELOG.md")
    git("commit", "--quiet", "-m", "Release %s" % version)
    git("tag", "-a", tag, "-m", "Spinback %s" % version)
    git("push", "--quiet", "origin", "main")
    git("push", "--quiet", "origin", tag)
    print("Committed, tagged %s and pushed; the Release workflow is running." % tag)
    urls(tag, slug)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
