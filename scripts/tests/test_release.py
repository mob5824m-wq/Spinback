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


if __name__ == "__main__":
    unittest.main()
