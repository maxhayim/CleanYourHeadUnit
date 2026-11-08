"""Tests for tools/cyhu against simulated head units (tests/fixtures).

What these prove: the tool sends the right commands, refuses what it must refuse, records before
and after states, verifies results against the unit's own answers, and puts everything back.
What they can't prove: that a real head unit's firmware behaves like the fixture. See
docs/hardware-checklist.md for that.
"""
import io
import json
import os
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stdout

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "tools"))

from cyhu import data, catalog, privacy, launcher, diagnostics, apk  # noqa: E402
from cyhu.adb import check_package, check_component, check_hostname  # noqa: E402
from cyhu.classify import Classifier  # noqa: E402
from cyhu.debloat import make_plan, apply_plan, select, inventory, explain  # noqa: E402
from cyhu.device import identify, parse_getprop, parse_package_list, match_profiles  # noqa: E402
from cyhu.fake import FakeDevice  # noqa: E402
from cyhu.records import Records  # noqa: E402
from cyhu.restore import restore, instructions  # noqa: E402
from cyhu import cli  # noqa: E402

FIX = os.path.join(HERE, "fixtures")
FYT = os.path.join(FIX, "fyt-android10-1280x720.json")
ALPS = os.path.join(FIX, "alps-android8-1024x600.json")
DOFUN = os.path.join(FIX, "dofun-android12-portrait.json")
KITKAT = os.path.join(FIX, "kitkat-android44-800x480.json")


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="cyhu-test-")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def unit(self, path, declared=None):
        dev = FakeDevice.load(path)
        info = identify(dev, declared)
        return dev, info, Records(info.key, self.tmp)


class TestIdentify(Base):
    def test_fyt_identified_by_service_not_name(self):
        dev, info, _ = self.unit(FYT)
        self.assertEqual(info.firmware_family, "fyt")
        self.assertEqual(info.sdk, 29)
        self.assertEqual(info.size, "1280x720")
        self.assertEqual(info.orientation, "landscape")
        self.assertEqual(info.home, "com.android.launcher6/com.android.launcher3.Launcher")
        self.assertIn("com.aiconsole.kittlauncher2/com.aiconsole.kittlauncher2.MainActivity", info.home_candidates)
        self.assertEqual(info.mcu_props, {"persist.sys.fyt.mcu.version": "SIM-MCU-1.0"})

    def test_alps_yt9216_matched_by_build_string(self):
        _, info, _ = self.unit(ALPS)
        self.assertEqual([p["id"] for p in info.profiles], ["alps-yt9216", "generic-android"])

    def test_name_alone_never_matches_a_profile(self):
        # A unit whose marketing name says FYT but has no FYT service is not an FYT unit.
        props = {"ro.product.model": "FYT 7862 Pro"}
        self.assertEqual([p["id"] for p in match_profiles(props, {"com.example.x"})], ["generic-android"])

    def test_portrait_and_kitkat(self):
        _, info, _ = self.unit(DOFUN)
        self.assertEqual(info.orientation, "portrait")
        self.assertEqual(info.firmware_family, "dofun")
        dev, info, _ = self.unit(KITKAT)
        self.assertEqual(info.sdk, 19)
        self.assertIsNone(info.home)              # no cmd package before Android 7
        self.assertIsNone(info.data_total_kb)     # human-readable df on 4.4: not guessed
        self.assertFalse(any(c.startswith("cmd package") for c in dev.calls if "4.4" in c))

    def test_sergey_declared_only(self):
        _, info, _ = self.unit(FYT)
        self.assertNotIn("sergey-kozlov", [p["id"] for p in info.profiles])
        _, info, _ = self.unit(FYT, ["sergey-kozlov"])
        self.assertIn("sergey-kozlov", [p["id"] for p in info.profiles])

    def test_parsers(self):
        self.assertEqual(parse_getprop("[a.b]: [c]\n[x]: []\njunk"), {"a.b": "c", "x": ""})
        self.assertEqual(parse_package_list("package:/system/app/X.apk=com.x\npackage:com.y\n"), ["com.x", "com.y"])

    def test_key_hides_serial(self):
        _, info, _ = self.unit(FYT)
        self.assertNotIn("FAKE", info.key)
        self.assertEqual(len(info.key), 16)


class TestClassifyVectors(unittest.TestCase):
    def test_shared_vectors(self):
        with open(os.path.join(HERE, "vectors", "classify.json")) as fh:
            vec = json.load(fh)["vectors"]
        for x in vec:
            c = Classifier([data.profile(p) for p in x["profiles"]], x["extra_protected"])
            with self.subTest(pkg=x["package"], note=x["note"]):
                self.assertEqual(c.classify(x["package"], x["system"]).category, x["expect"])

    def test_untested_profile_never_recommends(self):
        prof = {"id": "t", "name": "t", "status": "unverified",
                "packages": [{"package": "com.example.promo", "category": "recommended"}]}
        self.assertEqual(Classifier([prof]).classify("com.example.promo").category, "optional")
        prof["status"] = "tested"
        self.assertEqual(Classifier([prof]).classify("com.example.promo").category, "recommended")

    def test_profile_cannot_unprotect_core(self):
        prof = {"id": "t", "name": "t", "status": "tested",
                "packages": [{"package": "com.android.systemui", "category": "recommended"}]}
        self.assertEqual(Classifier([prof]).classify("com.android.systemui").category, "protected")

    def test_profile_cannot_unprotect_doors_or_vehicle(self):
        # Even a "tested" profile that lists them can't make the door display or CAN bus apps removable.
        pkgs = ["com.syu.canbus", "com.microntek.canbus", "com.vendor.doorstatus", "com.vendor.carinfo",
                "com.vendor.canbox", "com.vendor.instrumentcluster"]
        prof = {"id": "t", "name": "t", "status": "tested",
                "packages": [{"package": p, "category": "recommended"} for p in pkgs]}
        for p in pkgs:
            self.assertEqual(Classifier([prof]).classify(p).category, "protected", p)


class TestDebloat(Base):
    def test_plan_refuses_protected_unknown_user_advanced(self):
        _, info, _ = self.unit(FYT)
        plan = make_plan(info, ["com.syu.ms", "com.example.vendor.promo", "com.spotify.music",
                                "com.adups.fota", "com.android.email", "com.android.launcher6", "not a pkg;rm"])
        refused = dict(plan.refused)
        self.assertIn("com.syu.ms", refused)
        self.assertIn("com.android.launcher6", refused)
        self.assertTrue(refused["com.example.vendor.promo"].startswith("Unknown"))
        self.assertIn("com.spotify.music", refused)
        self.assertTrue(refused["com.adups.fota"].startswith("Advanced"))
        self.assertEqual(refused["not a pkg;rm"], "not a package name")
        self.assertEqual([i.verdict.package for i in plan.items], ["com.android.email"])

    def test_allow_unknown_and_advanced_explicitly(self):
        _, info, _ = self.unit(FYT)
        plan = make_plan(info, ["com.example.vendor.promo", "com.adups.fota"],
                         allow_unknown={"com.example.vendor.promo"}, allow_advanced=True)
        self.assertEqual(sorted(i.verdict.package for i in plan.items), ["com.adups.fota", "com.example.vendor.promo"])

    def test_select_never_includes_unsafe(self):
        _, info, _ = self.unit(FYT)
        for cat in (["optional"], ["optional", "advanced", "unknown", "protected", "user"]):
            sel = select(info, cat)
            verdicts = {v.package: v.category for v, _ in inventory(info)}
            self.assertTrue(all(verdicts[p] in ("optional", "advanced") for p in sel))

    def test_explain_has_every_field(self):
        _, info, _ = self.unit(FYT)
        rows = explain(make_plan(info, ["com.android.egg"]), info.sdk)
        for k in ("package", "purpose", "action", "confidence", "risk", "restore", "command"):
            self.assertTrue(rows[0][k], k)
        self.assertEqual(rows[0]["command"], "pm disable-user --user 0 com.android.egg")

    def test_apply_records_verifies_and_restores(self):
        dev, info, rec = self.unit(FYT)
        plan = make_plan(info, ["com.android.email", "com.android.egg"])
        res = apply_plan(dev, info, rec, plan, None)
        self.assertTrue(all(r.ok for r in res))
        self.assertFalse(dev.packages["com.android.email"]["enabled"])
        self.assertIsNotNone(rec.baseline())
        self.assertTrue(rec.baseline()["packages"]["com.android.email"]["enabled"])
        self.assertEqual(len(rec.changes()), 2)
        # Every command that changed state was pm disable-user, nothing else.
        changing = [c for c in dev.calls if c.startswith("pm ") and "list" not in c]
        self.assertTrue(all(c.startswith("pm disable-user --user 0 ") for c in changing))
        out = restore(dev, rec, info.sdk, packages=["com.android.egg"])
        self.assertEqual([(u.target, u.outcome) for u in out], [("com.android.egg", "restored")])
        self.assertTrue(dev.packages["com.android.egg"]["enabled"])
        self.assertFalse(dev.packages["com.android.email"]["enabled"])
        out = restore(dev, rec, info.sdk)
        self.assertEqual([u.outcome for u in out], ["restored"])
        self.assertEqual(restore(dev, rec, info.sdk), [])        # idempotent: nothing left

    def test_restore_leaves_owner_changes_alone(self):
        dev, info, rec = self.unit(FYT)
        apply_plan(dev, info, rec, make_plan(info, ["com.android.email"]), None)
        # The owner re-enabled it and the unit is fine: restore says so and marks it done.
        dev.packages["com.android.email"]["enabled"] = True
        self.assertEqual(restore(dev, rec, info.sdk)[0].outcome, "already")

    def test_failed_disable_is_reported_not_recorded(self):
        dev, info, rec = self.unit(FYT)
        plan = make_plan(info, ["com.android.stk"], allow_advanced=True)
        res = apply_plan(dev, info, rec, plan, None)
        self.assertFalse(res[0].ok)
        self.assertEqual(rec.changes(), [])
        self.assertEqual(rec.read_log()[-1]["action"], "disable-failed")

    def test_baseline_never_overwritten(self):
        dev, info, rec = self.unit(FYT)
        apply_plan(dev, info, rec, make_plan(info, ["com.android.email"]), None)
        first = rec.baseline()["time"]
        _, info2, _ = self.unit(FYT)
        self.assertFalse(rec.save_baseline(info2, None))
        self.assertEqual(rec.baseline()["time"], first)

    def test_restore_script_is_plain_adb(self):
        dev, info, rec = self.unit(FYT)
        apply_plan(dev, info, rec, make_plan(info, ["com.android.email"]), None)
        s = instructions(rec, info.sdk)
        self.assertIn("adb shell pm enable --user 0 com.android.email", s)

    def test_kitkat_uses_user_flag(self):
        dev, info, rec = self.unit(KITKAT)
        res = apply_plan(dev, info, rec, make_plan(info, ["com.android.browser"]), None)
        self.assertTrue(res[0].ok)
        self.assertIn("pm disable-user --user 0 com.android.browser", dev.calls)


class TestPrivacy(Base):
    def test_findings_and_ordering(self):
        _, info, _ = self.unit(DOFUN)
        f = privacy.telemetry_findings(info)
        self.assertEqual([x["package"] for x in f], ["com.tw.jar1", "com.tw.core"])
        _, info, _ = self.unit(KITKAT)
        self.assertEqual(privacy.telemetry_findings(info), [])

    def test_permission_audit_new_and_old(self):
        dev, info, _ = self.unit(FYT)
        perms = privacy.permission_audit(dev, info)
        self.assertIn("com.syu.ms", perms["location"])
        self.assertIn("com.adups.fota", perms["contacts"])        # READ_SMS
        self.assertIn("com.maxhayim.retrodeck", perms["microphone"])
        self.assertNotIn("com.spotify.music", perms["location"])

    def test_old_grant_format(self):
        txt = "    grantedPermissions:\n      android.permission.ACCESS_FINE_LOCATION\n      android.permission.INTERNET\n"
        self.assertEqual(privacy.parse_grants(txt, 19), {"android.permission.ACCESS_FINE_LOCATION", "android.permission.INTERNET"})

    def test_private_dns_set_and_restore(self):
        dev, info, rec = self.unit(FYT)
        self.assertEqual(privacy.get_private_dns(dev, info.sdk), {"mode": "opportunistic", "specifier": None})
        ok, _ = privacy.set_private_dns(dev, info, rec, "dns.adguard-dns.com")
        self.assertTrue(ok)
        self.assertEqual(dev.settings["global"]["private_dns_mode"], "hostname")
        out = restore(dev, rec, info.sdk, kinds=["private_dns"])
        self.assertEqual(out[0].outcome, "restored")
        self.assertEqual(privacy.get_private_dns(dev, info.sdk), {"mode": "opportunistic", "specifier": None})

    def test_private_dns_refused_below_android9_and_bad_host(self):
        dev, info, rec = self.unit(ALPS)
        ok, msg = privacy.set_private_dns(dev, info, rec, "dns.quad9.net")
        self.assertFalse(ok)
        self.assertIn("Android 9", msg)
        dev, info, rec = self.unit(FYT)
        with self.assertRaises(ValueError):
            privacy.set_private_dns(dev, info, rec, "x; reboot")

    def test_profiles(self):
        _, info, _ = self.unit(DOFUN)
        std = privacy.profile_plan(info, "standard")
        self.assertEqual(std["disable"], [])                    # nothing tested: nothing recommended
        self.assertEqual(sorted(std["review"]), ["com.tw.core", "com.tw.jar1"])
        self.assertIsNone(std["private_dns"])
        strict = privacy.profile_plan(info, "strict")
        self.assertIn("com.tw.jar1", strict["disable"])
        self.assertIn("com.tw.jar1", strict["firewall"])
        self.assertTrue(strict["private_dns"])
        custom = privacy.profile_plan(info, "custom", {"disable": ["com.android.systemui", "com.tw.core"]})
        self.assertEqual(custom["disable"], ["com.tw.core"])
        self.assertTrue(any("protected" in n for n in custom["notes"]))
        with self.assertRaises(ValueError):
            privacy.profile_plan(info, "paranoid")


class TestLauncher(Base):
    def test_set_and_restore_home(self):
        dev, info, rec = self.unit(FYT)
        kitt = "com.aiconsole.kittlauncher2/com.aiconsole.kittlauncher2.MainActivity"
        ok, _ = launcher.set_home(dev, info, rec, kitt)
        self.assertTrue(ok)
        self.assertEqual(dev.home, kitt)
        self.assertTrue(dev.packages["com.android.launcher6"]["enabled"])   # factory launcher untouched
        out = restore(dev, rec, info.sdk, kinds=["home"])
        self.assertEqual(out[0].outcome, "restored")
        self.assertEqual(dev.home, "com.android.launcher6/com.android.launcher3.Launcher")

    def test_home_refusals(self):
        dev, info, rec = self.unit(KITKAT)
        ok, msg = launcher.set_home(dev, info, rec, "com.android.launcher3/com.android.launcher3.Launcher")
        self.assertFalse(ok)
        self.assertIn("Always", msg)
        dev, info, rec = self.unit(FYT)
        ok, _ = launcher.set_home(dev, info, rec, "com.spotify.music/.Main")
        self.assertFalse(ok)
        with self.assertRaises(ValueError):
            launcher.set_home(dev, info, rec, "x/y;reboot")

    def test_recommend_by_orientation(self):
        rec = launcher.recommend(catalog.items("launcher"), "portrait", 31)
        self.assertEqual(rec[0]["fits"], True)
        kitt2 = next(r for r in rec if r["id"] == "kitt-launcher-2")
        self.assertFalse(kitt2["fits"])
        old = launcher.recommend(catalog.items("launcher"), "landscape", 19)
        self.assertFalse(next(r for r in old if r["id"] == "kitt-launcher-2")["fits"])


class TestAndroidVersion(unittest.TestCase):
    def test_real_version_beats_settings(self):
        # A real unit (2014 Camry, sp7731e): Settings say 13, the API level is 27, which is 8.1.
        from cyhu.device import DeviceInfo
        info = DeviceInfo(props={"ro.build.version.release": "13", "ro.build.version.sdk": "27"}, sdk=27, packages={})
        self.assertEqual(info.android, "8.1")
        self.assertEqual(info.android_claimed, "13")
        self.assertTrue(info.android_spoofed)
        self.assertEqual(info.summary()["android_claimed"], "13")
        honest = DeviceInfo(props={"ro.build.version.release": "8.1.0"}, sdk=27, packages={})
        self.assertFalse(honest.android_spoofed)
        self.assertFalse(DeviceInfo(props={"ro.build.version.release": "12"}, sdk=32, packages={}).android_spoofed)


class TestCatalog(unittest.TestCase):
    def test_shipped_catalog_is_valid(self):
        self.assertEqual(catalog.validate(), [])
        for name in os.listdir(os.path.join(data.ROOT, "themes")):
            folder = os.path.join(data.ROOT, "themes", name)
            if os.path.isdir(folder):
                self.assertEqual(catalog.check_theme_folder(folder), [], name)

    def test_recommended_apps(self):
        rec = [r["id"] for r in data.catalog()["recommended"]]
        self.assertEqual(rec, ["retrodeck", "webmote", "localsend"])
        ls = catalog.item("localsend")
        self.assertEqual(ls["install"], "none")          # one APK per processor: links only
        self.assertEqual(ls["package"], "org.localsend.localsend_app")
        bad = json.loads(json.dumps(data.catalog()))
        bad["recommended"].append({"id": "nope", "why": ""})
        errs = catalog.validate(bad)
        self.assertTrue(any("nope: not an item" in e for e in errs))
        self.assertTrue(any("needs a 'why'" in e for e in errs))

    def test_attribution_github_first(self):
        self.assertEqual(catalog.credit("tdater"), "@tdater")
        self.assertEqual(catalog.credit("maxhayim"), "@maxhayim")
        self.assertEqual(catalog.credit("sergey-kozlov"), "Sergey Kozlov")

    def test_webmote_is_planned_not_installable(self):
        w = catalog.item("webmote")
        self.assertEqual(w["automotive_status"], "Automotive support planned")
        self.assertEqual(w["install"], "none")

    def _sub(self, **kw):
        it = json.loads(json.dumps(catalog.item("retrodeck")))
        it["id"] = "new-thing"
        it.update(kw)
        return it

    def test_submission_rules(self):
        ok = self._sub()
        self.assertEqual(catalog.validate_submission(ok), [])
        bad = [
            self._sub(postinstall="adb shell rm -rf /sdcard"),
            self._sub(description="Run adb shell pm uninstall com.x first"),
            self._sub(links={"repository": "http://example.com"}),
            self._sub(source={"type": "github_release", "repo": "someone-else/RetroDeck", "asset_pattern": ".*"}),
            self._sub(signing_cert_sha256="nope"),
            self._sub(package="not a package"),
            self._sub(screenshots=[]),
            self._sub(compatibility={"status": "verified", "verified": []}),
            self._sub(status="planned"),
            self._sub(developer="nobody"),
        ]
        for b in bad:
            with self.subTest(b=str(b)[:80]):
                self.assertNotEqual(catalog.validate_submission(b), [])

    def test_theme_rules(self):
        with open(os.path.join(data.ROOT, "themes", "midnight", "theme.json")) as fh:
            good = json.load(fh)
        self.assertEqual(catalog.check_theme(good), [])
        for change in ({"script": "x"}, {"colors": {"background": "red"}}, {"target": {}},
                       {"layout": {"dock": "top"}}, {"name": "$(reboot)"}):
            t = dict(good)
            t.update(change)
            self.assertNotEqual(catalog.check_theme(t), [], change)

    def test_theme_folder_refuses_code(self):
        d = tempfile.mkdtemp()
        try:
            shutil.copy(os.path.join(data.ROOT, "themes", "midnight", "theme.json"), d)
            with open(os.path.join(d, "apply.sh"), "w") as fh:
                fh.write("echo hi")
            self.assertTrue(any("apply.sh" in e for e in catalog.check_theme_folder(d)))
        finally:
            shutil.rmtree(d)

    def test_pick_asset_and_download_origin(self):
        rel = {"assets": [{"name": "RetroDeck-0.15.0.apk"}, {"name": "notes.txt"}]}
        self.assertEqual(catalog.pick_asset(rel, catalog.item("retrodeck")["source"]["asset_pattern"])["name"],
                         "RetroDeck-0.15.0.apk")
        with self.assertRaises(ValueError):
            catalog.download("https://example.com/evil.apk")


class TestApk(unittest.TestCase):
    """Runs against a real APK when one is available (CYHU_TEST_APK), else the app built in this repo."""

    def _apk(self):
        for cand in (os.environ.get("CYHU_TEST_APK"),
                     os.path.join(data.ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")):
            if cand and os.path.exists(cand):
                return cand
        self.skipTest("no APK to inspect (set CYHU_TEST_APK)")

    def test_inspect(self):
        info = apk.inspect(self._apk())
        self.assertRegex(info["package"], r"^[a-z][\w.]+$")
        self.assertRegex(info["signer_sha256"] or "", r"^[0-9a-f]{64}$")

    def test_check_apk_refuses_wrong_pin(self):
        path = self._apk()
        info = apk.inspect(path)
        it = {"package": info["package"], "signing_cert_sha256": "0" * 64}
        self.assertTrue(any("different certificate" in p for p in catalog.check_apk(it, path, None)))
        it["signing_cert_sha256"] = info["signer_sha256"]
        self.assertEqual(catalog.check_apk(it, path, "sha256:" + info["sha256"]), [])
        self.assertTrue(catalog.check_apk(it, path, "sha256:" + "1" * 64))


class TestDiagnostics(Base):
    def test_sanitised(self):
        dev, info, rec = self.unit(FYT)
        rep = diagnostics.report(info, privacy.telemetry_findings(info), None, rec.changes(), catalog.items())
        text = json.dumps(rep)
        self.assertNotIn("FAKE1234567890", text)
        self.assertNotIn("02:00:00:aa:bb:cc", text)
        self.assertIn("ro.build.fingerprint", text)
        self.assertIn("retrodeck", rep["catalog_installed"])
        self.assertEqual(diagnostics.scrub_value("x a@b.com 192.168.1.20 123456789012345"),
                         "x [email] [lan-ip] [number]")


class TestValidators(unittest.TestCase):
    def test_names(self):
        for bad in ("", "a", "com.x;reboot", "com.x y", "$(id)", "com.x`id`"):
            with self.assertRaises(ValueError):
                check_package(bad)
        check_package("com.syu.ms")
        check_component("com.a.b/.Main")
        with self.assertRaises(ValueError):
            check_component("com.a.b/.Main;id")
        check_hostname("dns.quad9.net")
        for bad in ("dns", "a b.com", "x.com;id", "-x.com"):
            with self.assertRaises(ValueError):
                check_hostname(bad)


class TestCli(Base):
    def run_cli(self, *args):
        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = cli.main(["--records", self.tmp] + list(args))
        return rc, buf.getvalue()

    def test_read_only_commands(self):
        for args in (["status"], ["inventory"], ["privacy", "audit"], ["launcher", "list"],
                     ["launcher", "recommend"], ["profiles"], ["privacy", "dns", "show"]):
            rc, text = self.run_cli("--demo", FYT, *args)
            self.assertEqual(rc, 0, args)
        rc, text = self.run_cli("catalog", "list")
        self.assertIn("@tdater", text)
        self.assertIn("Automotive support planned", text)
        self.assertEqual(self.run_cli("catalog", "validate")[0], 0)

    def test_apply_needs_yes_and_dry_run_changes_nothing(self):
        rc, text = self.run_cli("--demo", FYT, "debloat", "apply", "--packages", "com.android.email", "--dry-run")
        self.assertEqual(rc, 0)
        self.assertIn("adb shell pm disable-user --user 0 com.android.email", text)
        rc, text = self.run_cli("--demo", FYT, "debloat", "apply", "--packages", "com.android.email")
        self.assertEqual(rc, 1)          # stdin is not a tty: refused without --yes
        rc, text = self.run_cli("--demo", FYT, "--yes", "debloat", "apply", "--packages", "com.android.email")
        self.assertEqual(rc, 0, text)
        self.assertIn("ok", text)

    def test_install_refuses_planned_and_old_android(self):
        rc, text = self.run_cli("--demo", FYT, "install", "webmote")
        self.assertEqual(rc, 2)
        self.assertIn("Automotive support planned", text)
        rc, text = self.run_cli("--demo", KITKAT, "install", "kitt-launcher-2")
        self.assertEqual(rc, 2)
        self.assertIn("needs Android 7.0", text)

    def test_firmware_flag_only_takes_declarable_profiles(self):
        with self.assertRaises(SystemExit):
            with redirect_stdout(io.StringIO()):
                cli.main(["--demo", FYT, "--firmware", "fyt", "status"])


if __name__ == "__main__":
    unittest.main()
