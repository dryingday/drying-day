"""Tests for check_before_stop.py. Run: python3 -m unittest discover -s .claude/hooks"""

import os
import subprocess
import tempfile
import unittest

from check_before_stop import tree_key


def git(cwd, *args):
    subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True)


class TreeKey(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        git(self.dir, "init", "-q")
        git(self.dir, "config", "user.email", "t@example.org")
        git(self.dir, "config", "user.name", "t")
        self.write("a.kt", "one")
        git(self.dir, "add", "a.kt")
        git(self.dir, "commit", "-q", "-m", "init")

    def write(self, name, text):
        with open(os.path.join(self.dir, name), "w") as f:
            f.write(text)

    def test_same_tree_same_key(self):
        self.assertEqual(tree_key(self.dir), tree_key(self.dir))

    def test_edit_changes_key(self):
        before = tree_key(self.dir)
        self.write("a.kt", "two")
        self.assertNotEqual(before, tree_key(self.dir))

    def test_new_untracked_file_changes_key(self):
        before = tree_key(self.dir)
        self.write("b.kt", "new")
        self.assertNotEqual(before, tree_key(self.dir))

    def test_untracked_file_edit_changes_key(self):
        self.write("b.kt", "new")
        before = tree_key(self.dir)
        self.write("b.kt", "newer")
        self.assertNotEqual(before, tree_key(self.dir))

    def test_ignored_files_dont_change_key(self):
        self.write(".gitignore", "build/\n")
        before = tree_key(self.dir)
        os.makedirs(os.path.join(self.dir, "build"))
        self.write("build/out.txt", "x")
        self.assertEqual(before, tree_key(self.dir))


if __name__ == "__main__":
    unittest.main()
