"""Undo what CleanYourHeadUnit changed: one package, the launcher, Private DNS, or everything.

Idempotent: running it twice changes nothing the second time. A value the owner changed after
CleanYourHeadUnit did is left alone and reported, unless --force.
"""
from __future__ import annotations

from dataclasses import dataclass

from .adb import Device, check_package
from .debloat import enable_command, disable_command
from .device import parse_package_list
from .records import Records
from . import privacy, launcher


@dataclass
class Undo:
    kind: str
    target: str
    outcome: str      # restored | already | changed-since | failed
    detail: str = ""


def _package_state(dev: Device, name: str) -> str | None:
    if name not in set(parse_package_list(dev.out("pm list packages"))):
        return None
    return "disabled" if name in set(parse_package_list(dev.out("pm list packages -d"))) else "enabled"


def restore(dev: Device, rec: Records, sdk: int, packages: list[str] | None = None,
            kinds: list[str] | None = None, force: bool = False, dry_run: bool = False) -> list[Undo]:
    out = []
    changes = rec.changes()
    for i in range(len(changes) - 1, -1, -1):
        ch = changes[i]
        if ch["status"] != "applied":
            continue
        if kinds and ch["kind"] not in kinds:
            continue
        if packages is not None and not (ch["kind"] == "package" and ch["target"] in packages):
            continue
        u = _undo_one(dev, rec, sdk, i, ch, force, dry_run)
        out.append(u)
    return out


def _undo_one(dev, rec, sdk, i, ch, force, dry_run) -> Undo:
    kind, target = ch["kind"], ch["target"]
    if kind == "package":
        now = _package_state(dev, target)
        if now is None:
            if not dry_run:
                rec.mark(i, "gone", "no longer installed")
            return Undo(kind, target, "failed", "no longer installed")
        if now == ch["before"]:
            if not dry_run:
                rec.mark(i, "restored", "was already back")
            return Undo(kind, target, "already")
        if now != ch["after"] and not force:
            return Undo(kind, target, "changed-since", f"now {now}")
        cmd = enable_command(target, sdk) if ch["before"] == "enabled" else disable_command(target, sdk)
        if dry_run:
            return Undo(kind, target, "restored", f"would run: {cmd}")
        res, rc = dev.shell(cmd)
        if rc != 0 or _package_state(dev, target) != ch["before"]:
            rec.log("restore-failed", {"package": target, "output": res[-300:]})
            return Undo(kind, target, "failed", res.strip())
        rec.mark(i, "restored")
        rec.log("restore", {"package": target})
        return Undo(kind, target, "restored", cmd)
    if kind == "private_dns":
        return privacy.undo_private_dns(dev, rec, i, ch, force, dry_run)
    if kind == "home":
        return launcher.undo_home(dev, rec, i, ch, force, dry_run)
    if kind == "install":
        return _undo_install(dev, rec, i, ch, dry_run)
    return Undo(kind, target, "failed", "unknown change kind")


def _undo_install(dev, rec, i, ch, dry_run) -> Undo:
    target = ch["target"]
    if ch["before"] is not None:
        # It was installed before; an update can't be rolled back without the old APK.
        return Undo("install", target, "changed-since", f"was {ch['before']} before; reinstall that version by hand")
    if _package_state(dev, target) is None:
        if not dry_run:
            rec.mark(i, "restored", "was already uninstalled")
        return Undo("install", target, "already")
    cmd = f"pm uninstall {check_package(target)}"
    if dry_run:
        return Undo("install", target, "restored", f"would run: {cmd}")
    res, rc = dev.shell(cmd)
    if rc != 0 or _package_state(dev, target) is not None:
        return Undo("install", target, "failed", res.strip())
    rec.mark(i, "restored")
    rec.log("restore", {"uninstalled": target})
    return Undo("install", target, "restored", cmd)


def instructions(rec: Records, sdk: int) -> str:
    """Plain adb commands that undo every applied change, for when this tool can't run."""
    lines = ["# CleanYourHeadUnit: undo every change by hand. Run from a computer with adb.",
             "# Newest change first. Each line is safe to run again."]
    for ch in reversed(rec.changes()):
        if ch["status"] != "applied":
            continue
        if ch["kind"] == "package" and ch["before"] == "enabled":
            lines.append(f"adb shell {enable_command(ch['target'], sdk)}")
        elif ch["kind"] == "home" and ch["before"]:
            lines.append(f"adb shell cmd package set-home-activity {ch['before']}")
        elif ch["kind"] == "install" and ch["before"] is None:
            lines.append(f"adb shell pm uninstall {ch['target']}")
        elif ch["kind"] == "private_dns":
            b = ch["before"] or {}
            mode = b.get("mode") or "opportunistic"
            lines.append(f"adb shell settings put global private_dns_mode {mode}")
            if b.get("specifier"):
                lines.append(f"adb shell settings put global private_dns_specifier {b['specifier']}")
            else:
                lines.append("adb shell settings delete global private_dns_specifier")
    return "\n".join(lines) + "\n"
