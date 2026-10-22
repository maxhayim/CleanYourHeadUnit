"""The MiTV-AFMU1 stick had two lists; this pins the one that is left (#740, #749, issue #855).

Both PRs were merged for the same stick - same board, product and firmware - so the two folders were
folded into `examples/xiaomi-mitv-afmu1/`. Consolidating by hand is easy to get wrong in a quiet way:
a package can disappear, or a fact that only one of the two owners recorded can go with it, and nothing
in the repo would notice. These tests compare the merged list against the two reports it came from, so
the merge is either exact or it fails here.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
MERGED = ROOT / "examples" / "xiaomi-mitv-afmu1" / "debloat.txt"
GONE = "xiaomi-mi-tv-stick-4k-gen2"
PACKAGE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")

# The packages each report disabled, as they stood on main before the merge.
UNIT_A = """
com.miui.tv.analytics
com.xiaomi.statistic
com.xiaomi.tvqs.overseas.y24
com.google.android.feedback
com.android.tv.feedbackconsent
com.mitv.tvhome.atv
com.mitv.tvhome.michannel
com.mitv.tvhome.mitvplus
com.mitv.tvhome.oemtab
com.xm.webcontent
android.autoinstalls.config.soc.amlogic
com.google.android.youtube.tvmusic
com.google.android.play.games
com.google.android.marvin.talkback
com.mitv.videoplayer
com.android.printspooler
com.google.android.tungsten.setupwraith
"""

# The packages the first unit left enabled and commented out (personal choice, plus the stock launcher,
# which build-and-install.sh disables itself).
UNIT_A_KEPT = """
com.google.android.katniss
com.google.android.tv.remote.service
com.xiaomi.mitv.smartshare
com.xiaomi.wfdsinkhelperservice
com.xiaomi.mi_connect_service
com.google.android.apps.tv.dreamx
com.google.android.apps.tv.launcherx
com.netflix.ninja
com.google.android.youtube.tv
com.amazon.amazonvideo.livingroom
"""

UNIT_B = """
com.miui.tv.analytics
com.xiaomi.statistic
com.xiaomi.tvqs.overseas.y24
com.google.android.feedback
com.android.tv.feedbackconsent
com.android.adservices.api
com.android.ondevicepersonalization.services
com.android.federatedcompute.services
com.mitv.tvhome.oemtab
com.xm.webcontent
android.autoinstalls.config.soc.amlogic
com.google.android.play.games
com.google.android.apps.mediashell
com.google.android.katniss
com.google.android.apps.tv.dreamx
com.netflix.tokenmanager
com.android.printspooler
com.android.providers.contacts
com.android.health.connect.backuprestore
com.xiaomi.mitv.smartshare
com.xiaomi.wfdsinkhelperservice
com.xiaomi.mi_connect_service
com.mitv.videoplayer
com.xiaomi.mitv.updateservice
com.google.android.apps.tv.launcherx
com.google.android.tungsten.setupwraith
"""

SOURCES = {"#740": UNIT_A, "#749": UNIT_B, "#740 kept by choice": UNIT_A_KEPT}


def packages(text):
    """Package names a list actually disables: the whole line, not the trailing comment."""
    found = set()
    for line in text.splitlines():
        body = line.split("#", 1)[0].strip()
        if PACKAGE.match(body):
            found.add(body)
    return found


def mentioned(text):
    """Package names a list mentions anywhere, including the ones commented out as kept."""
    found = set()
    for line in text.splitlines():
        body = line.lstrip("#").split("#", 1)[0].strip()
        if PACKAGE.match(body):
            found.add(body)
    return found


def missing(merged, sources):
    return {name: sorted(packages(text) - mentioned(merged)) for name, text in sources.items()}


def invented(merged, sources):
    known = set().union(*(packages(text) for text in sources.values()))
    return sorted(mentioned(merged) - known)


class MergedListTest(unittest.TestCase):
    def merged(self):
        return MERGED.read_text(encoding="utf-8")

    def test_the_second_folder_is_gone(self):
        self.assertFalse((ROOT / "examples" / GONE).exists(),
                         f"examples/{GONE} is the duplicate the merge was meant to remove")

    def test_nothing_still_points_at_the_removed_folder(self):
        offenders = []
        for path in ROOT.rglob("*"):
            if not path.is_file() or ".git" in path.parts:
                continue
            # This file names the folder on purpose, in GONE.
            if path == Path(__file__):
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            if GONE in text:
                offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual(offenders, [], "these still reference the removed folder")

    def test_no_package_from_either_report_was_dropped(self):
        dropped = missing(self.merged(), SOURCES)
        self.assertEqual({k: v for k, v in dropped.items() if v}, {},
                         "packages the merge lost")

    def test_the_merged_list_invents_nothing(self):
        self.assertEqual(invented(self.merged(), SOURCES), [])

    def test_both_reports_are_named_and_their_facts_survive(self):
        text = self.merged()
        # One fact per line, each recorded by only one of the two reports, so a merge that dropped
        # either folder's header is visible here and not just in the package list.
        for fact in (
            "#740 and #749",                                # both reports named
            "Fingerprint: Xiaomi/adastra/twilight",         # #740 only
            "cname=",                                       # #740: the Wi-Fi ownership check
            "RecoveryActivity (HOME priority 1",            # #740: why setupwraith goes first
            "live_tv_package empty",                        # #740: no tuner on a stick
            "/system is full",                              # #749 only
            "port 41817",                                   # #749: wireless debugging unsupported
            "com.xiaomi.mitv.updateservice",                # #749: no firmware updates
            "health.connect.backuprestore",                 # #749 only
        ):
            with self.subTest(fact=fact):
                self.assertIn(fact, text)

    def test_the_two_units_disagreements_are_still_readable(self):
        text = self.merged()
        self.assertIn("disagreed", text, "the kept/did-not-keep choices need saying, not a silent pick")
        for choice in ("com.google.android.katniss", "com.google.android.apps.mediashell",
                       "com.google.android.tv.remote.service", "com.google.android.apps.tv.dreamx"):
            with self.subTest(package=choice):
                self.assertNotIn(choice, packages(text),
                                 "a package only one unit was happy to lose must stay a choice")


class CheckerTest(unittest.TestCase):
    """The comparison above only means something if it can fail."""

    def test_a_commented_line_is_kept_out_of_the_package_list(self):
        text = "# My TV\ncom.example.one\n# com.example.kept   # uncomment to disable\ncom.example.two\n"
        self.assertEqual(packages(text), {"com.example.one", "com.example.two"})
        self.assertEqual(mentioned(text), {"com.example.one", "com.example.two", "com.example.kept"})

    def test_prose_is_not_a_package(self):
        text = ("# - Check before disabling: tools/tv.py \"dumpsys wifi\" | grep -o 'cname=[^ ]*'\n"
                "# Xiaomi/adastra/twilight:14/UKG3.250826.001/V816.0.7.0.UZFAATK:user/release-keys\n"
                "# 1.9 GB RAM; /system is full (100%)\n")
        self.assertEqual(mentioned(text), set())

    def test_a_dropped_package_is_reported(self):
        source = {"a": "com.example.one\ncom.example.two\n"}
        self.assertEqual(missing("com.example.one\n", source), {"a": ["com.example.two"]})
        self.assertEqual(missing("com.example.one\ncom.example.two\n", source), {"a": []})

    def test_a_package_kept_as_a_comment_still_counts_as_mentioned(self):
        # The merged file comments out what one unit kept on purpose; that is a mention, not a loss.
        self.assertEqual(missing("com.example.one\n# com.example.two\n", {"a": "com.example.one\ncom.example.two\n"}), {"a": []})

    def test_an_invented_package_is_reported(self):
        self.assertEqual(invented("com.example.one\ncom.example.extra\n", {"a": "com.example.one\n"}),
                         ["com.example.extra"])
        self.assertEqual(invented("com.example.one\n", {"a": "com.example.one\n"}), [])


if __name__ == "__main__":
    unittest.main()
