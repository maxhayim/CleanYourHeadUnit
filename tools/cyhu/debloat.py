"""The safe debloater: inventory, plan, apply, verify.

Only `pm disable-user` is ever used: reversible, no root, no uninstall. Before anything changes,
the baseline is saved and each change is written to the record with its before and after state.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .adb import Device, check_package
from .classify import Classifier, Verdict
from .device import DeviceInfo, parse_package_list
from .records import Records


def classifier_for(info: DeviceInfo) -> Classifier:
    extra = {}
    for comp in info.home_candidates:
        extra[comp.split("/")[0]] = "Can be the Home app on this unit. Home apps are never disabled."
    if info.home:
        extra[info.home.split("/")[0]] = "The current Home app. It is never disabled."
    return Classifier(info.profiles, extra)


def inventory(info: DeviceInfo) -> list[tuple[Verdict, bool]]:
    c = classifier_for(info)
    return [(c.classify(n, p.system), p.enabled) for n, p in sorted(info.packages.items())]


@dataclass
class PlanItem:
    verdict: Verdict
    command: str


@dataclass
class Plan:
    items: list = field(default_factory=list)
    refused: list = field(default_factory=list)     # (package, why)
    skipped: list = field(default_factory=list)     # (package, why)


def disable_command(package: str, sdk: int) -> str:
    check_package(package)
    return f"pm disable-user --user 0 {package}" if sdk >= 17 else f"pm disable-user {package}"


def enable_command(package: str, sdk: int) -> str:
    check_package(package)
    return f"pm enable --user 0 {package}" if sdk >= 17 else f"pm enable {package}"


def select(info: DeviceInfo, categories: list[str]) -> list[str]:
    """Packages in the given categories that are installed and enabled. Never protected/unknown/user."""
    allowed = [c for c in categories if c in ("recommended", "optional", "advanced")]
    return [v.package for v, enabled in inventory(info) if enabled and v.category in allowed]


def make_plan(info: DeviceInfo, packages: list[str], allow_unknown: set | None = None,
              allow_advanced: bool = False) -> Plan:
    allow_unknown = set(allow_unknown or ())
    c = classifier_for(info)
    plan = Plan()
    seen = set()
    for name in packages:
        if name in seen:
            continue
        seen.add(name)
        try:
            check_package(name)
        except ValueError:
            plan.refused.append((name, "not a package name"))
            continue
        pkg = info.packages.get(name)
        if not pkg:
            plan.skipped.append((name, "not installed on this unit"))
            continue
        v = c.classify(name, pkg.system)
        if v.category == "protected":
            plan.refused.append((name, f"Protected: {v.reason}"))
            continue
        if v.category == "user":
            plan.refused.append((name, "Your own app: uninstall it in Android Settings if you don't want it."))
            continue
        if v.category == "unknown" and name not in allow_unknown:
            plan.refused.append((name, "Unknown: its purpose is not verified. Name it with --allow-unknown "
                                       "after you've checked what it does."))
            continue
        if v.category == "advanced" and not allow_advanced:
            plan.refused.append((name, "Advanced: needs --advanced after you've read the risk."))
            continue
        if not pkg.enabled:
            plan.skipped.append((name, "already disabled"))
            continue
        plan.items.append(PlanItem(v, disable_command(name, info.sdk)))
    return plan


def explain(plan: Plan, sdk: int) -> list[dict]:
    rows = []
    for it in plan.items:
        v = it.verdict
        rows.append({
            "package": v.package,
            "category": v.category,
            "purpose": v.purpose or "not documented",
            "action": "disable for user 0 (reversible; nothing is uninstalled)",
            "command": it.command,
            "confidence": v.confidence,
            "risk": v.risk or "not documented: test the unit's functions afterwards",
            "finding": v.finding or None,
            "restore": f"cyhu restore --package {v.package}   (or: adb shell {enable_command(v.package, sdk)})",
        })
    return rows


@dataclass
class Result:
    package: str
    ok: bool
    detail: str


def apply_plan(dev: Device, info: DeviceInfo, rec: Records, plan: Plan, private_dns: dict | None) -> list[Result]:
    rec.save_baseline(info, private_dns)
    results = []
    for it in plan.items:
        name = it.verdict.package
        out, rc = dev.shell(it.command)
        if rc != 0 or "disabled" not in out:
            rec.log("disable-failed", {"package": name, "output": out[-300:]})
            results.append(Result(name, False, out.strip() or f"exit {rc}"))
            continue
        rec.add_change("package", name, "enabled", "disabled", it.verdict.category)
        rec.log("disable", {"package": name, "category": it.verdict.category})
        results.append(Result(name, True, out.strip()))
    # Verify against the unit's own list, not the command's answer.
    disabled_now = set(parse_package_list(dev.out("pm list packages -d")))
    for r in results:
        if r.ok and r.package not in disabled_now:
            r.ok = False
            r.detail = "the unit still lists it as enabled"
            rec.log("verify-failed", {"package": r.package})
        if r.ok:
            info.packages[r.package].enabled = False
    return results
