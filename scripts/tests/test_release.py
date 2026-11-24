import importlib.util
import pathlib
import tempfile
import unittest
from unittest.mock import patch

script = pathlib.Path(__file__).resolve().parents[1] / "release.py"
spec = importlib.util.spec_from_file_location("release", script)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class BumpTests(unittest.TestCase):
    def bump(self, changelog, latest=None, kind="patch"):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "CHANGELOG.md"
            path.write_text(changelog, encoding="utf-8")
            with patch.object(release, "CHANGELOG", str(path)), patch.object(release, "latest_version", return_value=latest):
                return release.bumped_version(kind)

    def test_prepared_first_release(self):
        self.assertEqual(self.bump("## [Unreleased]\n\n## [1.0.0] - 2026-11-24\n\n- Initial\n"), "1.0.0")

    def test_unreleased_changes_keep_normal_initial_bump(self):
        self.assertEqual(self.bump("## [Unreleased]\n\n- New\n\n## [1.0.0]\n\n- Old\n"), "0.1.0")

    def test_no_prepared_section(self):
        self.assertEqual(self.bump("## [Unreleased]\n\n- Initial\n", kind="major"), "1.0.0")

    def test_existing_tag_bumps_normally(self):
        self.assertEqual(self.bump("## [Unreleased]\n\n## [1.0.0]\n\n- Initial\n", latest="1.0.0"), "1.0.1")


class LatestVersionTests(unittest.TestCase):
    def test_unreachable_tags_still_count(self):
        """A shallow clone cannot describe HEAD; the newest tag is still the base."""
        def fake_git(*args, **kwargs):
            if args[0] == "describe":
                return ""
            return "v1.3.0\nv1.2.0\n"
        with patch.object(release, "git", side_effect=fake_git):
            self.assertEqual(release.latest_version(), "1.3.0")

    def test_no_tags_at_all(self):
        with patch.object(release, "git", return_value=""):
            self.assertIsNone(release.latest_version())


class NoteTests(unittest.TestCase):
    """The note fallback that keeps an unattended bump from failing."""

    def test_bullets_are_capitalised(self):
        self.assertEqual(release.note_bullets(["fix sources JAR task"]),
                         ["- Fix sources JAR task"])

    def test_bullets_keep_existing_markers(self):
        self.assertEqual(release.note_bullets(["- already a bullet"]),
                         ["- already a bullet"])

    def test_long_histories_are_capped(self):
        bullets = release.note_bullets(["change %d" % n for n in range(45)])
        self.assertEqual(len(bullets), release.MAX_COMMIT_NOTES + 1)
        self.assertIn("15 more change(s)", bullets[-1])

    def test_commits_skip_plumbing_and_merges(self):
        log = "\n".join([
            "Fix sources JAR task",
            "Merge pull request #4 from mob5824m-wq/arena/x",
            "Release 1.0.0",
            "Bump version to 1.1.0",
            "Fix sources JAR task",
            "Refresh Play Music theme",
        ])
        with patch.object(release, "git", return_value=log):
            self.assertEqual(release.commits_since_release("2.0.0"),
                             ["Fix sources JAR task", "Refresh Play Music theme"])

    def test_release_range_uses_last_tag(self):
        with patch.object(release, "latest_version", return_value="1.0.0"):
            self.assertEqual(release.release_range("2.0.0"), ["v1.0.0..HEAD"])
            # Re-releasing an existing version or a first release: whole history.
            self.assertEqual(release.release_range("1.0.0"), ["HEAD"])
        with patch.object(release, "latest_version", return_value=None):
            self.assertEqual(release.release_range("1.0.0"), ["HEAD"])


class ReleaseSectionTests(unittest.TestCase):
    CHANGELOG = (
        "# Changelog\n\n## [Unreleased]\n\n- Hand written note\n\n"
        "## [1.0.0] - 2026-11-24\n\n- Initial\n\n"
        "[unreleased]: https://github.com/o/r/compare/v1.0.0...HEAD\n"
        "[1.0.0]: https://github.com/o/r/releases/tag/v1.0.0\n"
    )

    def test_unreleased_entries_take_precedence_over_commits(self):
        changelog = release.Changelog(self.CHANGELOG)
        section = changelog.release("2.0.0", "2026-11-24", [])
        self.assertIn("## [2.0.0] - 2026-11-24\n\n- Hand written note\n", section)
        self.assertNotIn("- Initial\n- Hand", section)

    def test_notes_fill_an_empty_unreleased(self):
        empty = "# Changelog\n\n## [Unreleased]\n\n## [1.0.0] - 2026-11-24\n\n- Initial\n"
        changelog = release.Changelog(empty)
        self.assertIsNone(changelog.release("2.0.0", "2026-11-24", []))
        section = changelog.release("2.0.0", "2026-11-24", ["- Fix sources JAR task"])
        self.assertIn("## [2.0.0] - 2026-11-24\n\n- Fix sources JAR task", section)

    def test_links_point_at_the_new_tag(self):
        changelog = release.Changelog(self.CHANGELOG)
        updated = changelog.update_links(self.CHANGELOG, "2.0.0", "o/r")
        self.assertIn("[unreleased]: https://github.com/o/r/compare/v2.0.0...HEAD", updated)
        self.assertIn("[2.0.0]: https://github.com/o/r/compare/v1.0.0...v2.0.0", updated)


class PrepareOutputTests(unittest.TestCase):
    def test_prepare_reports_the_workflow_outputs(self):
        with patch("sys.stdout") as stdout:
            stdout.isatty.return_value = False
            release.print_prepare("2.0.0-rc.1", "v2.0.0-rc.1")
        printed = "".join(call.args[0] for call in stdout.write.call_args_list)
        self.assertIn("version=2.0.0-rc.1\n", printed)
        self.assertIn("tag=v2.0.0-rc.1\n", printed)
        self.assertIn("prerelease=true\n", printed)

    def test_stable_releases_are_not_prereleases(self):
        with patch("sys.stdout") as stdout:
            stdout.isatty.return_value = False
            release.print_prepare("2.0.0", "v2.0.0")
        printed = "".join(call.args[0] for call in stdout.write.call_args_list)
        self.assertIn("prerelease=false\n", printed)


if __name__ == "__main__":
    unittest.main()
