"""The page a reader lands on when `pm disable-user` answers `Unknown package`.

#1880 is a Konka B50U where `pm disable-user --user 0 com.vendor.recommendations` printed
`Error: java.lang.IllegalArgumentException: Unknown package: com.vendor.recommendations`, and the owner
read it as the install script having broken: the name was not a package at all, it had been guessed from
the app's look. Nothing in docs/TROUBLESHOOTING.md explained the line, and nothing in tools/ prints it
either - it is the TV's own answer for a name that is not installed for that user - so the one thing the
page could not tell the reader was whether anything had gone wrong.

The bullet says what the line means, that the rest of the list still runs, the two causes (a list written
on another build, or a name that is not a package) and the command that finds the real name. Each of those
claims is checked against the code rather than asserted in prose:

  - `tools/tv.py --batch` runs every line and carries on past a failed one, and still exits 0: run here
    against a stub adb_shell that answers the way a TV does, so the page's "the exit status of a run that
    worked" is observed, not assumed.
  - `tools/disable.sh` really hands the list to that client, and no script in tools/ prints the message
    itself: if one starts to, the page has to say which.
  - `tools/recovery.py` records such a package as `absent`, which is what the bullet's pointer to the
    uninstall bullet below it promises.
  - the list header the bullet names for the "written on another build" case still calls its own two
    `Unknown package` errors harmless.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "TROUBLESHOOTING.md"
TVPY = ROOT / "tools" / "tv.py"
DISABLE = ROOT / "tools" / "disable.sh"
SECTION = "## Working on the TV"
# The line the TV prints, as #1880 quotes it: `pm` catches the exception and prints "Error: " before it.
TV_LINE = "Error: java.lang.IllegalArgumentException: Unknown package: "
UK_BUILD_LIST = ROOT / "examples" / "tcl-c845x2-google-tv" / "debloat.txt"

sys.path.insert(0, str(ROOT / "tools"))

STUB_DEVICE = '''\
import os


class AdbDeviceTcp:
    """Stands in for adb_shell's client: records each command and answers like a TV with one bad name."""

    def __init__(self, host, port, **kwargs):
        pass

    def connect(self, **kwargs):
        pass

    def shell(self, command, **kwargs):
        with open(os.environ["STUB_CMDS"], "a") as f:
            f.write(command + "\\n")
        if "disable-user" in command:
            name = command.split()[-1]
            return ("Error: java.lang.IllegalArgumentException: Unknown package: %s\\n" % name)
        return "package:com.example.player\\n"

    def close(self):
        pass
'''
STUB_EXCEPTIONS = ("class AdbTimeoutError(Exception):\\n    pass\\n\\n\\n"
                   "class InvalidCommandError(Exception):\\n    pass\\n\\n\\n"
                   "class TcpTimeoutException(Exception):\\n    pass\\n")
STUB_KEYGEN = "def keygen(path):\n    pass\n"
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, pub, priv):\n        pass\n"


def section(text=None, heading=SECTION):
    """One section of the page, or an empty string when the heading is gone."""
    text = PAGE.read_text(encoding="utf-8") if text is None else text
    start = text.find(heading)
    if start == -1:
        return ""
    rest = text[start + len(heading):]
    end = re.search(r"^## ", rest, re.M)
    return rest if end is None else rest[:end.start()]


def bullets(text=None, heading=SECTION):
    """The section's bullets with their continuation lines joined: one string per bullet."""
    out = []
    for line in section(text, heading).splitlines():
        if line.startswith("- "):
            out.append(line[2:].strip())
        elif out and line.startswith("  ") and line.strip():
            out[-1] = out[-1] + " " + line.strip()
        elif not line.strip():
            continue
    return out


def bullet_naming(*needles, text=None):
    """The first bullet that names every needle, or None."""
    for b in bullets(text):
        if all(n in b for n in needles):
            return b
    return None


def stub_tree(base):
    """A package tree that answers tv.py's adb_shell imports."""
    (base / "adb_shell" / "auth").mkdir(parents=True)
    (base / "adb_shell" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "adb_device.py").write_text(STUB_DEVICE, encoding="utf-8")
    (base / "adb_shell" / "exceptions.py").write_text(STUB_EXCEPTIONS, encoding="utf-8")
    (base / "adb_shell" / "auth" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "auth" / "keygen.py").write_text(STUB_KEYGEN, encoding="utf-8")
    (base / "adb_shell" / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER, encoding="utf-8")
    return base


class TheBatchCarriesOnPastIt(unittest.TestCase):
    """The page's claim that the other lines still run, observed on the real client."""

    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        base = Path(cls._tmp.name)
        cls.stub = stub_tree(base / "stub")
        # A stock ADB key in a temporary HOME: tv.py reuses it, so no key is written into the checkout.
        cls.home = base / "home"
        (cls.home / ".android").mkdir(parents=True)
        (cls.home / ".android" / "adbkey").write_text("PRIVATE", encoding="utf-8")
        (cls.home / ".android" / "adbkey.pub").write_text("PUBLIC", encoding="utf-8")
        cls.cmds = base / "cmds.txt"

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def run_batch(self, lines):
        if self.cmds.exists():
            self.cmds.unlink()
        env = dict(os.environ)
        env["TV_HOST"] = "192.0.2.10"
        env["HOME"] = str(self.home)
        env["PYTHONPATH"] = str(self.stub)
        env["STUB_CMDS"] = str(self.cmds)
        return subprocess.run([sys.executable, str(TVPY), "--batch"],
                              input="\n".join(lines) + "\n", capture_output=True, text=True,
                              env=env, cwd=str(ROOT), timeout=120)

    def test_two_unknown_names_do_not_stop_the_line_after_them(self):
        result = self.run_batch([
            "pm disable-user --user 0 com.vendor.recommendations",
            "pm disable-user --user 0 com.vendor.alsoabsent",
            "pm list packages -d",
        ])
        self.assertEqual(result.returncode, 0,
                         "a batch with unknown names in it now fails: %r" % (result.stderr,))
        sent = self.cmds.read_text(encoding="utf-8").splitlines()
        self.assertEqual(sent, ["pm disable-user --user 0 com.vendor.recommendations",
                                "pm disable-user --user 0 com.vendor.alsoabsent",
                                "pm list packages -d"],
                         "the client no longer sends every line of the batch")
        self.assertIn(TV_LINE, result.stdout,
                      "the TV's own answer does not reach the reader any more: %r" % (result.stdout,))
        self.assertIn("package:com.example.player", result.stdout,
                      "the line after the failed ones did not run: %r" % (result.stdout,))

    def test_the_page_quotes_the_line_the_client_shows(self):
        # The page is the only place that explains this line, so the wording it quotes has to be the one
        # the client passes through rather than a paraphrase of it.
        result = self.run_batch(["pm disable-user --user 0 com.vendor.recommendations"])
        self.assertIn(TV_LINE, result.stdout)
        bullet = bullet_naming("Unknown package")
        self.assertIsNotNone(bullet, "no bullet in Working on the TV explains `Unknown package`")
        assert bullet is not None
        self.assertIn(TV_LINE.strip(), bullet,
                      "the bullet no longer quotes the line the client prints, so a reader searching for "
                      "it will not find the page: %r" % (bullet,))


class TheToolsAreWhatTheBulletSays(unittest.TestCase):
    def test_disable_sh_hands_the_list_to_the_batch_client(self):
        src = DISABLE.read_text(encoding="utf-8")
        self.assertRegex(src, r'"\$PY"\s+-I\s+tools/tv\.py\s+--batch',
                         "disable.sh no longer pipes the list into `tools/tv.py --batch`, which is the "
                         "reason the page gives for the other lines still running")
        self.assertIn("pm disable-user --user 0", src,
                      "disable.sh no longer turns a list line into `pm disable-user --user 0 <package>`")

    def test_no_script_in_tools_prints_the_message_itself(self):
        # The message is the TV's. The page says so by explaining it; if a script starts printing one of
        # its own, the page has to say which line comes from where.
        offenders = []
        for path in sorted(ROOT.joinpath("tools").iterdir()):
            if path.suffix not in (".py", ".sh", ".ps1") or not path.is_file():
                continue
            if "Unknown package" in path.read_text(encoding="utf-8", errors="replace"):
                offenders.append(path.name)
        self.assertEqual(offenders, [],
                         "a tool now says `Unknown package` itself: the page's explanation has to name it")

    def test_the_record_calls_such_a_package_absent(self):
        import recovery

        self.assertEqual(recovery.package_state("com.vendor.recommendations", set(), set()), "absent",
                         "recovery.py no longer records a listed package it cannot find as `absent`, "
                         "which is what the bullet's pointer to the uninstall bullet promises")
        self.assertEqual(recovery.package_state("com.example.player", {"com.example.player"}, set()),
                         "enabled")

    def test_the_list_the_bullet_names_still_says_its_errors_are_harmless(self):
        self.assertTrue(UK_BUILD_LIST.is_file(),
                        "the list the bullet names for a package missing from another regional build is "
                        "gone")
        text = UK_BUILD_LIST.read_text(encoding="utf-8")
        self.assertIn("Unknown package", text,
                      "examples/tcl-c845x2-google-tv/debloat.txt no longer calls its own Unknown package "
                      "errors harmless, which is the example the bullet points at")


class ThePageAnswersIt(unittest.TestCase):
    def test_the_section_has_bullets_to_read(self):
        found = bullets()
        self.assertGreaterEqual(len(found), 10,
                                "Working on the TV read back %d bullets: the reader is broken, not the "
                                "page" % len(found))

    def test_an_unknown_name_is_explained_where_a_list_runner_looks(self):
        bullet = bullet_naming(TV_LINE.strip())
        self.assertIsNotNone(
            bullet,
            "nothing in Working on the TV explains `Error: ... Unknown package: <package>`: an owner "
            "whose list has one line for another build reads it as the run having broken (#1880)")
        assert bullet is not None
        self.assertIn("pm list packages", bullet,
                      "the way to find the real name is `pm list packages`")
        self.assertIn("tools/tv.py", bullet)
        self.assertIn("-u --user 0", bullet,
                      "the command for a package removed earlier needs -u, or it names nothing")
        self.assertIn("com.vendor.recommendations", bullet,
                      "the name from #1880 belongs in the bullet: it is what someone searching the error "
                      "will paste")

    def test_the_bullet_says_the_rest_of_the_list_still_runs(self):
        bullet = bullet_naming(TV_LINE.strip())
        assert bullet is not None
        self.assertIn("--batch", bullet,
                      "the reason the other lines still run is the batch client, so the bullet has to "
                      "name it")
        self.assertIn("carries on", bullet,
                      "the bullet has to say the next line runs whatever this one answered")
        self.assertIn("exit status", bullet,
                      "a reader has to be told the run reports success anyway, or the line is read as a "
                      "failure of the whole list")

    def test_the_absent_case_below_it_is_pointed_at_and_still_there(self):
        found = bullets()
        unknown_at = [i for i, b in enumerate(found) if TV_LINE.strip() in b]
        # The "every way of disabling is refused" bullet names `pm uninstall --user 0` too, so the
        # uninstall bullet is the one that starts by saying the package stays gone.
        uninstall_at = [i for i, b in enumerate(found)
                        if "pm uninstall --user 0" in b and "stays gone" in b]
        self.assertTrue(unknown_at, "the Unknown package bullet did not read back out of the section")
        self.assertEqual(len(uninstall_at), 1,
                         "the bullet about a package removed with `pm uninstall --user 0` is not in the "
                         "section exactly once; the new bullet points at it for a name the TV does list")
        self.assertLess(unknown_at[0], uninstall_at[0],
                        "the Unknown package bullet says 'the bullet below' about the uninstall one, so it "
                        "has to come first")
        self.assertIn("absent", found[unknown_at[0]],
                      "the pointer has to name what the record calls such a package")


class TheReaderItself(unittest.TestCase):
    """The helpers above, on pages written here: so a quiet page test cannot be a reader that saw nothing."""

    def test_a_wrapped_bullet_is_read_as_one(self):
        text = (SECTION + "\n\n- **One** thing that runs over\n  two lines.\n- **Two** other.\n")
        self.assertEqual(bullets(text), ["**One** thing that runs over two lines.", "**Two** other."])

    def test_a_bullet_in_another_section_is_not_read(self):
        text = (SECTION + "\n\n- here\n\n## Another\n\n- there\n")
        self.assertEqual(bullets(text), ["here"])

    def test_a_page_without_the_section_answers_nothing(self):
        self.assertEqual(bullets("# Troubleshooting\n\n- nothing\n"), [])
        self.assertEqual(section("# Troubleshooting\n\n- nothing\n"), "")

    def test_a_bullet_naming_only_some_needles_is_not_matched(self):
        text = (SECTION + "\n\n- **Unknown package** on one line, and no more said about it.\n")
        self.assertIsNone(bullet_naming("Unknown package", "pm list packages", text=text))
        self.assertIsNotNone(bullet_naming("Unknown package", text=text))


if __name__ == "__main__":
    unittest.main()
