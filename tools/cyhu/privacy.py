"""Privacy Shield, computer side: telemetry findings, permission audit, Private DNS, profiles.

What this can and can't see over ADB without root:
  - which catalogued telemetry, advertising and malware-indicator packages are installed (exact
    package names with published evidence; never a guess from the name);
  - which apps hold location, microphone, contacts and storage permissions (granted runtime
    permissions on Android 6+, install-time permissions before that);
  - the Private DNS setting (Android 9+), and changing it with the owner's consent.
It can't see which app sent what to where: that needs a capture this tool doesn't do. The local
firewall in the app counts what it blocks itself, and nothing more.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

from . import data
from .adb import Device, check_hostname
from .debloat import classifier_for, select
from .device import DeviceInfo
from .records import Records

WATCHED = {
    "location": ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                 "android.permission.ACCESS_BACKGROUND_LOCATION"),
    "microphone": ("android.permission.RECORD_AUDIO",),
    "contacts": ("android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG",
                 "android.permission.READ_SMS"),
    "storage": ("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO"),
    "network": ("android.permission.INTERNET",),
    "background": ("android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.SYSTEM_ALERT_WINDOW",
                   "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"),
}
GRANT_RE = re.compile(r"^\s*(android\.permission\.[A-Z_]+): granted=(true|false)", re.M)
OLD_GRANT_RE = re.compile(r"^\s{6}(android\.permission\.[A-Z_]+)\s*$", re.M)

PROFILES = ("standard", "strict", "custom")


def parse_grants(dumpsys: str, sdk: int) -> set:
    if sdk >= 23:
        return {m.group(1) for m in GRANT_RE.finditer(dumpsys) if m.group(2) == "true"}
    sect = dumpsys.split("grantedPermissions:", 1)
    return {m.group(1) for m in OLD_GRANT_RE.finditer(sect[1])} if len(sect) == 2 else set()


@dataclass
class Audit:
    findings: list = field(default_factory=list)       # telemetry catalogue hits
    permissions: dict = field(default_factory=dict)    # group -> [package]
    private_dns: dict | None = None
    notes: list = field(default_factory=list)


def telemetry_findings(info: DeviceInfo) -> list[dict]:
    c = classifier_for(info)
    out = []
    for entry in data.telemetry()["packages"]:
        name = entry["package"]
        if name in info.packages:
            v = c.classify(name, info.packages[name].system)
            out.append({**entry, "installed": True, "enabled": info.packages[name].enabled,
                        "category_here": v.category})
    order = {"malware_indicator": 0, "confirmed_telemetry": 1, "advertising": 2, "suspected_tracking": 3,
             "connected_service": 4, "unknown_traffic": 5}
    out.sort(key=lambda e: order.get(e["finding"], 9))
    return out


def permission_audit(dev: Device, info: DeviceInfo, groups=("location", "microphone", "contacts", "storage"),
                     packages: list[str] | None = None) -> dict:
    res = {g: [] for g in groups}
    names = packages if packages is not None else sorted(n for n, p in info.packages.items() if p.enabled)
    for name in names:
        grants = parse_grants(dev.out(f"dumpsys package {name}"), info.sdk)
        for g in groups:
            if any(p in grants for p in WATCHED[g]):
                res[g].append(name)
    return res


def get_private_dns(dev: Device, sdk: int) -> dict | None:
    if sdk < 28:
        return None
    mode = dev.out("settings get global private_dns_mode").strip()
    spec = dev.out("settings get global private_dns_specifier").strip()
    return {"mode": None if mode in ("", "null") else mode, "specifier": None if spec in ("", "null") else spec}


def set_private_dns(dev: Device, info: DeviceInfo, rec: Records, hostname: str | None) -> tuple[bool, str]:
    """hostname None turns Private DNS back to Android's default (automatic)."""
    if info.sdk < 28:
        return False, f"Private DNS needs Android 9; this unit runs Android {info.android}."
    before = get_private_dns(dev, info.sdk)
    rec.save_baseline(info, before)
    if hostname:
        check_hostname(hostname)
        dev.shell(f"settings put global private_dns_specifier {hostname}")
        dev.shell("settings put global private_dns_mode hostname")
    else:
        dev.shell("settings put global private_dns_mode opportunistic")
    after = get_private_dns(dev, info.sdk)
    want_mode = "hostname" if hostname else "opportunistic"
    if not after or after["mode"] != want_mode or (hostname and after["specifier"] != hostname):
        rec.log("private-dns-failed", {"wanted": hostname, "now": after})
        return False, f"the unit did not keep the setting (now {after})"
    rec.add_change("private_dns", "private_dns", before, after)
    rec.log("private-dns", {"before": before, "after": after})
    return True, f"Private DNS is now {after['specifier'] or 'automatic'}"


def undo_private_dns(dev, rec, i, ch, force, dry_run):
    from .restore import Undo
    now = get_private_dns(dev, 99)
    before, after = ch["before"] or {}, ch["after"] or {}
    if now == before or (now or {}).get("mode") == before.get("mode") and (now or {}).get("specifier") == before.get("specifier"):
        if not dry_run:
            rec.mark(i, "restored", "was already back")
        return Undo("private_dns", "private_dns", "already")
    if now != after and not force:
        return Undo("private_dns", "private_dns", "changed-since", f"now {now}")
    if dry_run:
        return Undo("private_dns", "private_dns", "restored", f"would set {before}")
    if before.get("specifier"):
        check_hostname(before["specifier"])
        dev.shell(f"settings put global private_dns_specifier {before['specifier']}")
    else:
        dev.shell("settings delete global private_dns_specifier")
    mode = before.get("mode")
    if mode in ("off", "opportunistic", "hostname"):
        dev.shell(f"settings put global private_dns_mode {mode}")
    else:
        dev.shell("settings delete global private_dns_mode")
    rec.mark(i, "restored")
    rec.log("restore", {"private_dns": before})
    return Undo("private_dns", "private_dns", "restored", str(before))


def profile_plan(info: DeviceInfo, profile: str, custom: dict | None = None) -> dict:
    """What a Privacy Shield profile proposes. Proposals only: nothing is applied from here."""
    if profile not in PROFILES:
        raise ValueError(f"unknown privacy profile: {profile}")
    findings = telemetry_findings(info)
    out = {"profile": profile, "disable": [], "review": [], "private_dns": None, "firewall": [],
           "notes": []}
    if profile in ("standard", "strict"):
        out["disable"] = select(info, ["recommended"])
        for f in findings:
            if f["enabled"] and f["category_here"] not in ("protected",):
                out["review"].append(f["package"])
        out["notes"].append("Standard keeps navigation, connected apps and every protected package. It "
                            "doesn't filter the network.")
    if profile == "strict":
        out["notes"] = ["Strict adds the Advanced telemetry entries (each still needs your confirmation), "
                        "suggests a filtering Private DNS and lists apps to block in the local firewall."]
        out["disable"] += [f["package"] for f in findings if f["enabled"] and f["category_here"] == "advanced"]
        out["private_dns"] = "choose one: " + ", ".join(p["hostname"] for p in data.dns_providers()["providers"])
        out["firewall"] = [f["package"] for f in findings if f["finding"] in ("malware_indicator", "confirmed_telemetry")]
    if profile == "custom":
        custom = custom or {}
        c = classifier_for(info)
        for name in custom.get("disable", []):
            if name in info.packages and c.classify(name, info.packages[name].system).category == "protected":
                out["notes"].append(f"{name} is protected and was dropped from the custom profile.")
            else:
                out["disable"].append(name)
        if custom.get("private_dns"):
            check_hostname(custom["private_dns"])
            out["private_dns"] = custom["private_dns"]
        out["firewall"] = list(custom.get("firewall", []))
    seen = set()
    out["disable"] = [x for x in out["disable"] if not (x in seen or seen.add(x))]
    return out
