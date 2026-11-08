"""cyhu: CleanYourHeadUnit from a computer, over ADB.

Read-only commands never change the unit. Commands that change it show what they will do, ask you
to type "yes" (or take --yes), record the before state, and verify the result.
Use them while the vehicle is parked.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

from . import data, catalog, privacy, launcher, diagnostics
from .adb import AdbDevice, DeviceError
from .debloat import inventory, make_plan, explain, apply_plan, select
from .device import identify
from .fake import FakeDevice
from .records import Records
from .restore import restore, instructions

PARKED = "Do this with the vehicle parked. Keep the engine running or the unit on a stable supply."


def out(obj, as_json: bool):
    if as_json:
        print(json.dumps(obj, indent=2, sort_keys=True, default=str))
        return True
    return False


def confirm(args, what: str) -> bool:
    if args.yes:
        return True
    if not sys.stdin.isatty():
        print(f"Not applied: {what} needs --yes when not run interactively.", file=sys.stderr)
        return False
    ans = input(f"{what}\nType yes to go ahead: ").strip().lower()
    return ans == "yes"


def connect(args):
    if args.demo:
        return FakeDevice.load(args.demo)
    return AdbDevice(args.serial)


def table(rows: list[list[str]]):
    if not rows:
        return
    widths = [max(len(str(r[i])) for r in rows) for i in range(len(rows[0]))]
    for r in rows:
        print("  ".join(str(c).ljust(widths[i]) for i, c in enumerate(r)).rstrip())


# --- commands ----------------------------------------------------------------------------------
def cmd_status(args, dev, info, rec):
    s = info.summary()
    if out({"device": s, "mcu": info.mcu_props}, args.json):
        return 0
    print("CleanYourHeadUnit - head unit")
    for k in ("manufacturer", "brand", "model", "android", "build", "chipset", "abi", "firmware_family",
              "screen", "density", "orientation", "ram_total_mb", "ram_available_mb", "storage_free_mb", "home"):
        v = s.get(k)
        print(f"  {k.replace('_', ' '):18} {v if v not in (None, '') else 'not reported'}")
    print(f"  {'mcu':18} {', '.join(f'{k}={v}' for k, v in info.mcu_props.items()) or 'not reported'}")
    print(f"  {'profiles':18} {', '.join(p['id'] + ' (' + p['status'] + ')' for p in info.profiles)}")
    print(f"  {'packages':18} {s['packages']} ({s['disabled']} disabled)")
    if info.android_spoofed:
        print(f"  note: Settings say Android {info.android_claimed}, but the unit runs {info.android} (API {info.sdk}).")
    if info.sdk and info.sdk < 19:
        print("  note: below Android 4.4 is not supported.")
    return 0


def cmd_inventory(args, dev, info, rec):
    rows = []
    for v, enabled in inventory(info):
        if args.category and v.category not in args.category.split(","):
            continue
        rows.append({"package": v.package, "category": v.category, "enabled": enabled,
                     "finding": v.finding or None, "reason": v.reason})
    if out(rows, args.json):
        return 0
    table([["PACKAGE", "CATEGORY", "STATE", "WHY"]] +
          [[r["package"], r["category"], "on" if r["enabled"] else "off", (r["reason"] or "")[:70]] for r in rows])
    counts = {}
    for r in rows:
        counts[r["category"]] = counts.get(r["category"], 0) + 1
    print("\n" + ", ".join(f"{k}: {v}" for k, v in sorted(counts.items())))
    return 0


def _wanted(args, info):
    pk = [p for p in (args.packages or "").split(",") if p]
    if args.category:
        pk += select(info, args.category.split(","))
    return pk


def cmd_debloat(args, dev, info, rec):
    wanted = _wanted(args, info)
    if not wanted:
        print("Name packages with --packages or a category with --category recommended|optional|advanced.")
        return 2
    plan = make_plan(info, wanted, set(args.allow_unknown or []), args.advanced)
    rows = explain(plan, info.sdk)
    if args.json and args.action == "plan":
        out({"plan": rows, "refused": plan.refused, "skipped": plan.skipped}, True)
        return 0
    for r in rows:
        print(f"\n{r['package']}  [{r['category']}]")
        for k in ("purpose", "action", "confidence", "risk", "restore"):
            print(f"  {k:10} {r[k]}")
        if r["finding"]:
            print(f"  {'finding':10} {r['finding']}")
    for name, why in plan.refused:
        print(f"\nrefused  {name}: {why}")
    for name, why in plan.skipped:
        print(f"skipped  {name}: {why}")
    if args.action == "plan" or not plan.items:
        if not plan.items:
            print("\nNothing to change.")
        return 0
    if args.dry_run:
        print("\nDry run. These commands would run:")
        for it in plan.items:
            print(f"  adb shell {it.command}")
        return 0
    print("\n" + PARKED)
    if not confirm(args, f"Disable {len(plan.items)} package(s) listed above?"):
        rec.log("declined", {"packages": [i.verdict.package for i in plan.items]})
        print("Nothing changed.")
        return 1
    results = apply_plan(dev, info, rec, plan, privacy.get_private_dns(dev, info.sdk))
    bad = 0
    for r in results:
        print(f"{'ok    ' if r.ok else 'FAILED'} {r.package}: {r.detail}")
        bad += not r.ok
    print(f"\nRecord: {rec.dir}\nUndo any time: cyhu restore --all")
    return 1 if bad else 0


def cmd_restore(args, dev, info, rec):
    if args.script:
        sys.stdout.write(instructions(rec, info.sdk))
        return 0
    kinds = []
    if args.launcher:
        kinds.append("home")
    if args.dns:
        kinds.append("private_dns")
    if args.package:
        kinds.append("package")
    if not (args.all or kinds):
        print("Say what to restore: --package NAME, --launcher, --dns or --all.")
        return 2
    pending = [c for c in rec.changes() if c["status"] == "applied"]
    if not pending:
        print("Nothing to restore: CleanYourHeadUnit has no applied changes recorded for this unit.")
        return 0
    if not args.dry_run and not confirm(args, "Put back the recorded changes?"):
        print("Nothing changed.")
        return 1
    res = restore(dev, rec, info.sdk, packages=args.package, kinds=None if args.all else kinds,
                  force=args.force, dry_run=args.dry_run)
    for u in res:
        print(f"{u.outcome:14} {u.kind:12} {u.target} {u.detail}")
    return 1 if any(u.outcome == "failed" for u in res) else 0


def cmd_backup(args, dev, info, rec):
    wrote = rec.save_baseline(info, privacy.get_private_dns(dev, info.sdk))
    print(("Saved the baseline to " if wrote else "A baseline already exists (it is never overwritten): ")
          + rec.path("baseline.json"))
    return 0


def cmd_privacy(args, dev, info, rec):
    if args.action == "audit":
        findings = privacy.telemetry_findings(info)
        perms = privacy.permission_audit(dev, info)
        dns = privacy.get_private_dns(dev, info.sdk)
        if out({"findings": findings, "permissions": perms, "private_dns": dns}, args.json):
            return 0
        print("Privacy Shield audit\n")
        if not findings:
            print("  No package from the telemetry catalogue is installed.")
            print("  (The catalogue lists only packages with published evidence; an empty result is not a clean bill.)")
        for f in findings:
            print(f"  [{f['finding']}] {f['package']} - {f['name']} ({'enabled' if f['enabled'] else 'disabled'})")
            print(f"      {f['evidence']}")
            print(f"      category here: {f['category_here']}. Risk: {f['risk']}")
        print("\nPermissions granted (what apps may do, not what they did):")
        for g, names in perms.items():
            print(f"  {g:10} {len(names):3}  {', '.join(names[:8])}{' ...' if len(names) > 8 else ''}")
        print(f"\nPrivate DNS: {privacy.dns_label(dns)}")
        print("Network activity per app is not observable over ADB without root; see PRIVACY.md.")
        return 0
    if args.action == "profile":
        custom = None
        if args.file:
            with open(args.file, encoding="utf-8") as fh:
                custom = json.load(fh)
        plan = privacy.profile_plan(info, args.name or "standard", custom)
        if out(plan, args.json):
            return 0
        print(f"Privacy Shield profile: {plan['profile']} (proposal; nothing applied)")
        for k in ("disable", "review", "firewall"):
            print(f"  {k:12} {', '.join(plan[k]) or '-'}")
        print(f"  {'private dns':12} {plan['private_dns'] or 'unchanged'}")
        for n in plan["notes"]:
            print(f"  note: {n}")
        print("\nApply the disables with: cyhu debloat apply --packages <names> (each is checked again).")
        return 0
    if args.action == "dns":
        if not args.name or args.name == "show":
            dns = privacy.get_private_dns(dev, info.sdk)
            print(json.dumps(dns) if dns is not None else "Private DNS needs Android 9 or newer.")
            print("Providers:")
            for p in data.dns_providers()["providers"]:
                print(f"  {p['id']:16} {p['hostname']:26} {p['blocks']}")
            return 0
        host = None
        if args.name != "off":
            prov = {p["id"]: p["hostname"] for p in data.dns_providers()["providers"]}
            host = prov.get(args.name, args.name)
        print("DNS filtering blocks lookups of listed domains. It doesn't stop apps that use their own DNS or fixed IP addresses.")
        if not confirm(args, f"Set Private DNS to {host or 'automatic'}?"):
            print("Nothing changed.")
            return 1
        ok, msg = privacy.set_private_dns(dev, info, rec, host)
        print(msg)
        return 0 if ok else 1
    return 2


def cmd_launcher(args, dev, info, rec):
    if args.action == "list":
        print(f"Home now: {info.home or ('not set (chooser)' if info.sdk >= 24 else 'not readable below Android 7')}")
        for c in info.home_candidates:
            print(f"  {c}")
        return 0
    if args.action == "recommend":
        for r in launcher.recommend(catalog.items("launcher"), info.orientation, info.sdk):
            print(f"  {'fits ' if r['fits'] else 'check'} {r['name']}  {'; '.join(r['warnings'])}")
        return 0
    if args.action == "set":
        policy = next((p.get("policy", {}).get("launcher_changes") for p in info.profiles if p.get("policy")), None)
        if not confirm(args, f"Make {args.component} the Home app? The current one stays installed and enabled."):
            print("Nothing changed.")
            return 1
        ok, msg = launcher.set_home(dev, info, rec, args.component, policy)
        print(msg)
        return 0 if ok else 1
    return 2


def cmd_catalog(args, dev, info, rec):
    if args.action == "validate":
        if args.submission:
            with open(args.submission, encoding="utf-8") as fh:
                errs = catalog.validate_submission(json.load(fh))
        else:
            errs = catalog.validate()
            for name in sorted(os.listdir(os.path.join(data.ROOT, "themes"))):
                folder = os.path.join(data.ROOT, "themes", name)
                if os.path.isdir(folder):
                    errs += [f"themes/{name}: {e}" for e in catalog.check_theme_folder(folder)]
        for e in errs:
            print(f"error: {e}")
        print("catalog OK" if not errs else f"{len(errs)} problem(s)")
        return 1 if errs else 0
    if args.action == "show":
        it = catalog.item(args.id)
        if not it:
            print(f"no catalog entry {args.id}")
            return 2
        out(it, True)
        return 0
    for it in catalog.items(args.kind):
        st = it.get("automotive_status") or it["compatibility"]["status"]
        here = ""
        if info and it.get("package") and it.get("install") != "builtin":
            here = " installed" if it["package"] in info.packages else ""
        print(f"  {it['kind']:8} {it['id']:18} {it['name']:26} by {catalog.credit(it['developer']):14} [{st}]{here}")
    return 0


def cmd_install(args, dev, info, rec):
    it = catalog.item(args.id)
    if not it:
        print(f"no catalog entry {args.id}")
        return 2
    if it.get("status") == "planned" or it.get("install") != "github_release":
        print(f"{it['name']} has no direct install here ({it.get('automotive_status') or it.get('install')}). "
              f"Official page: {next(iter(it.get('links', {}).values()), '-')}")
        return 2
    if info.sdk < it["android"]["min_sdk"]:
        print(f"{it['name']} needs Android {it['android']['min_version']}; this unit runs {info.android}.")
        return 2
    if info.orientation and info.orientation not in it.get("orientations", []):
        print(f"warning: {it['name']} is made for {'/'.join(it['orientations'])} screens; this one is {info.orientation}.")
    rel = catalog.latest_release(it["source"]["repo"])
    asset = catalog.pick_asset(rel, it["source"]["asset_pattern"])
    if not asset:
        print(f"The latest release of {it['source']['repo']} has no APK matching the catalog. Nothing installed.")
        return 1
    print(f"{it['name']} {rel.get('tag_name')} from https://github.com/{it['source']['repo']} by {catalog.credit(it['developer'])}")
    path = catalog.download(asset["browser_download_url"])
    try:
        problems = catalog.check_apk(it, path, asset.get("digest"), args.allow_unpinned)
        if not asset.get("digest"):
            print("note: the release publishes no checksum; the signing certificate pin was checked instead.")
        if problems:
            for p in problems:
                print(f"refused: {p}")
            rec.log("install-refused", {"id": it["id"], "problems": problems})
            return 1
        before = catalog.installed_version(dev, it["package"])
        print(PARKED)
        if not confirm(args, f"Install {it['name']} {rel.get('tag_name')}" + (f" over {before}" if before else "") + "?"):
            print("Nothing installed.")
            return 1
        res, rc = dev.install(path, replace=before is not None)
        print(res)
        if rc != 0 or "Success" not in res:
            rec.log("install-failed", {"id": it["id"], "output": res[-300:]})
            return 1
        rec.add_change("install", it["package"], before, catalog.installed_version(dev, it["package"]) or rel.get("tag_name"))
        rec.log("install", {"id": it["id"], "version": rel.get("tag_name")})
        return 0
    finally:
        os.unlink(path)


def cmd_diagnostics(args, dev, info, rec):
    rep = diagnostics.report(info, privacy.telemetry_findings(info), privacy.get_private_dns(dev, info.sdk),
                             rec.changes(), catalog.items())
    text = json.dumps(rep, indent=2, sort_keys=True)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(text + "\n")
        print(f"Wrote {args.out}. It is sanitised, but read it before you share it. Nothing was uploaded.")
    else:
        print(text)
    return 0


def cmd_profiles(args, dev, info, rec):
    for p in data.profiles():
        mark = "*" if info and p in info.profiles else " "
        print(f" {mark} {p['id']:16} {p['status']:12} {p['name']}")
    print("\n* matched this unit" if info else "")
    return 0


def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(prog="cyhu", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", help="adb serial or host:port of the unit")
    ap.add_argument("--demo", metavar="FIXTURE", help="use a simulated unit from a JSON fixture (tests/fixtures)")
    ap.add_argument("--firmware", action="append", default=[], help="declare a firmware profile, e.g. sergey-kozlov")
    ap.add_argument("--records", help="where backups and change records live (default ~/.cleanyourheadunit)")
    ap.add_argument("--json", action="store_true", help="machine-readable output where offered")
    ap.add_argument("--yes", action="store_true", help="confirm changes without asking")
    sub = ap.add_subparsers(dest="command", required=True)
    sub.add_parser("status", help="identify the unit")
    p = sub.add_parser("inventory", help="every package with its category")
    p.add_argument("--category")
    d = sub.add_parser("debloat", help="plan or apply reversible package disabling")
    d.add_argument("action", choices=["plan", "apply"])
    d.add_argument("--packages")
    d.add_argument("--category", help="recommended, optional, advanced (comma separated)")
    d.add_argument("--allow-unknown", action="append", metavar="PACKAGE")
    d.add_argument("--advanced", action="store_true")
    d.add_argument("--dry-run", action="store_true")
    r = sub.add_parser("restore", help="undo recorded changes")
    r.add_argument("--package", action="append")
    r.add_argument("--launcher", action="store_true")
    r.add_argument("--dns", action="store_true")
    r.add_argument("--all", action="store_true")
    r.add_argument("--force", action="store_true")
    r.add_argument("--dry-run", action="store_true")
    r.add_argument("--script", action="store_true", help="print plain adb commands that undo everything")
    sub.add_parser("backup", help="save the baseline now")
    pv = sub.add_parser("privacy", help="Privacy Shield")
    pv.add_argument("action", choices=["audit", "profile", "dns"])
    pv.add_argument("name", nargs="?", help="profile name, or DNS provider id / hostname / off / show")
    pv.add_argument("--file", help="custom profile JSON")
    lp = sub.add_parser("launcher", help="Home apps")
    lp.add_argument("action", choices=["list", "set", "recommend"])
    lp.add_argument("component", nargs="?")
    cp = sub.add_parser("catalog", help="Community Hub catalog")
    cp.add_argument("action", choices=["list", "show", "validate"])
    cp.add_argument("id", nargs="?")
    cp.add_argument("--kind", choices=catalog.KINDS)
    cp.add_argument("--submission")
    ip = sub.add_parser("install", help="install a catalog entry from its official release")
    ip.add_argument("id")
    ip.add_argument("--allow-unpinned", action="store_true")
    dp = sub.add_parser("diagnostics", help="sanitised report")
    dp.add_argument("--out")
    sub.add_parser("profiles", help="device profiles")
    return ap


COMMANDS = {"status": cmd_status, "inventory": cmd_inventory, "debloat": cmd_debloat, "restore": cmd_restore,
            "backup": cmd_backup, "privacy": cmd_privacy, "launcher": cmd_launcher, "catalog": cmd_catalog,
            "install": cmd_install, "diagnostics": cmd_diagnostics, "profiles": cmd_profiles}
OFFLINE = {"catalog", "profiles"}


def main(argv=None) -> int:
    ap = build_parser()
    args = ap.parse_args(argv)
    for f in args.firmware:
        if not data.profile(f) or data.profile(f).get("match", {}).get("declared") is not True:
            ap.error(f"--firmware {f}: not a declarable firmware profile")
    dev = info = rec = None
    try:
        if args.command not in OFFLINE or args.demo or args.serial:
            dev = connect(args)
            info = identify(dev, args.firmware)
            if info.sdk and info.sdk < 19 and args.command not in ("status", "diagnostics"):
                print(f"Android {info.android} is below 4.4, which CleanYourHeadUnit doesn't support.", file=sys.stderr)
                return 2
            rec = Records(info.key, args.records)
        return COMMANDS[args.command](args, dev, info, rec)
    except DeviceError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    except ValueError as e:
        print(f"refused: {e}", file=sys.stderr)
        return 2
