import subprocess
import tempfile
import unittest
from pathlib import Path

from resolve_release_version import next_release_version, read_release_tags


class ReleaseVersionTests(unittest.TestCase):
    def test_first_version_follows_manual_upload(self):
        self.assertEqual(next_release_version(1, 0, 2, {}), (2, "1.0.0001"))

    def test_successful_deployments_advance_code_and_patch_once(self):
        tags = {"android-internal-2": "versionName=1.0.0001"}
        self.assertEqual(next_release_version(1, 0, 2, tags), (3, "1.0.0002"))
        tags["android-internal-3"] = "versionName=1.0.0002"
        self.assertEqual(next_release_version(1, 0, 2, tags), (4, "1.0.0003"))

    def test_major_minor_change_resets_patch_without_resetting_code(self):
        tags = {"android-internal-2": "versionName=1.0.0001", "android-internal-3": "versionName=1.1.0001"}
        self.assertEqual(next_release_version(1, 2, 2, tags), (4, "1.2.0001"))
        self.assertEqual(next_release_version(1, 0, 2, tags), (4, "1.0.0002"))

    def test_only_numeric_release_tags_count(self):
        self.assertEqual(next_release_version(1, 2, 2, {"android-internal-draft": "versionName=1.2.0009"}), (2, "1.2.0001"))

    def test_patch_overflow_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "version_minor"):
            next_release_version(1, 0, 2, {"android-internal-2": "versionName=1.0.9999"})

    def test_annotated_tag_subject_becomes_next_patch(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for command in (
                ["git", "init", "-q"],
                ["git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-q", "--allow-empty", "-m", "test"],
                ["git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "tag", "-a", "android-internal-2", "-m", "versionName=1.0.0001\n\n내부 테스트 배포"],
            ):
                subprocess.run(command, cwd=repository, check=True, capture_output=True)
            tags = read_release_tags(repository)
            self.assertEqual(tags, {"android-internal-2": "versionName=1.0.0001"})
            self.assertEqual(next_release_version(1, 0, 2, tags), (3, "1.0.0002"))


if __name__ == "__main__":
    unittest.main()
