"""Launchers: list the Home apps, set one as Home, go back to the previous one.

Uses Android's own Home setting (`cmd package set-home-activity`, Android 7+). The factory
launcher is never disabled to force another one: CleanYourHeadUnit changes which one is Home, and
nothing else. On Android 4.4 to 6 there is no shell command for it; the owner presses Home and
picks "Always" instead, and this says so.
"""
from __future__ import annotations

from .adb import Device, check_component
from .device import DeviceInfo, home_info
from .records import Records


def set_home(dev: Device, info: DeviceInfo, rec: Records, component: str, warn_policy: str | None = None) -> tuple[bool, str]:
    check_component(component)
    if info.sdk < 24:
        return False, ("This unit runs Android %s: there's no command to set Home. Press Home on the unit, "
                       "pick the launcher and choose Always." % info.android)
    if component not in info.home_candidates:
        return False, f"{component} is not a Home app on this unit (installed and enabled?)"
    before = info.home
    rec.save_baseline(info, None)
    out, rc = dev.shell(f"cmd package set-home-activity {component}")
    now, _ = home_info(dev, info.sdk)
    if rc != 0 or now != component:
        rec.log("home-failed", {"wanted": component, "output": out[-300:]})
        return False, out.strip() or "the unit did not keep the new Home app"
    rec.add_change("home", "home", before, component)
    rec.log("home", {"before": before, "after": component})
    info.home = component
    msg = f"Home is now {component}" + (f" (was {before})" if before else "")
    if warn_policy == "warn":
        msg += ". Your firmware profile asks for care with launcher changes: check the firmware's own features."
    return True, msg


def undo_home(dev, rec: Records, i, ch, force, dry_run):
    from .restore import Undo
    now, cands = home_info(dev, 99)
    before = ch["before"]
    if now == before:
        if not dry_run:
            rec.mark(i, "restored", "was already back")
        return Undo("home", "home", "already")
    if now != ch["after"] and not force:
        return Undo("home", "home", "changed-since", f"now {now}")
    if not before:
        return Undo("home", "home", "failed", "no Home app was set before; press Home and choose one")
    if before not in cands:
        return Undo("home", "home", "failed", f"{before} is no longer a Home app on the unit")
    if dry_run:
        return Undo("home", "home", "restored", f"would set {before}")
    check_component(before)
    dev.shell(f"cmd package set-home-activity {before}")
    now, _ = home_info(dev, 99)
    if now != before:
        return Undo("home", "home", "failed", f"Home is {now}")
    rec.mark(i, "restored")
    rec.log("restore", {"home": before})
    return Undo("home", "home", "restored", before)


def recommend(catalog_items: list[dict], orientation: str | None, sdk: int) -> list[dict]:
    """Catalog launchers ordered for this screen. Never hides one: a mismatch is a warning."""
    out = []
    for it in catalog_items:
        if it["kind"] != "launcher":
            continue
        warn = []
        if orientation and orientation not in it.get("orientations", []):
            warn.append(f"made for {'/'.join(it.get('orientations', []))} screens; this one is {orientation}")
        if sdk and sdk < it.get("android", {}).get("min_sdk", 0):
            warn.append(f"needs Android {it['android']['min_version']}+")
        out.append({"id": it["id"], "name": it["name"], "fits": not warn, "warnings": warn})
    out.sort(key=lambda r: (not r["fits"], r["name"]))
    return out
