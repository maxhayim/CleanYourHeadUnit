"""Which debloat category a package is in, and why.

Order, first match wins:
  1. core Android rule              -> protected (nothing overrides it)
  2. found on the unit (Home apps)  -> protected
     a matched profile's protected  -> protected
  3. telemetry catalogue entry      -> its category (evidence-backed, with published sources;
                                       the only thing that can override 4 and 5)
  4. vehicle namespace rule         -> protected
  5. vehicle keyword in the name    -> protected
  6. matched profile package entry  -> its category ('recommended' only on a 'tested' profile);
                                       a profile can never make a vehicle app removable
  7. user-installed (not system)    -> user (the owner's own app: never proposed)
  8. anything else                  -> unknown

Recommended is only ever given by a device profile whose status is 'tested'. A profile that is
not tested has its 'recommended' entries shown as 'optional'.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

from . import data

CATEGORIES = ("recommended", "optional", "advanced", "protected", "unknown", "user")
STRICTNESS = {"recommended": 0, "optional": 1, "advanced": 2, "unknown": 3, "protected": 4, "user": 3}
CONFIDENCE = {"recommended": "high (tested on this device profile)",
              "optional": "medium (known purpose)",
              "advanced": "low (needs your review)",
              "protected": "n/a (never changed)",
              "unknown": "none (purpose not verified)",
              "user": "n/a (your own app)"}


@dataclass
class Verdict:
    package: str
    category: str
    reason: str
    purpose: str = ""
    risk: str = ""
    source: str = ""
    finding: str = ""          # telemetry finding, if any
    evidence: str = ""
    profile: str = ""
    rule: str = ""
    sources: list = field(default_factory=list)

    @property
    def confidence(self) -> str:
        return CONFIDENCE[self.category]

    def as_dict(self) -> dict:
        d = dict(self.__dict__)
        d["confidence"] = self.confidence
        return d


def _keyword_hit(package: str, words: list, prefix_short=()) -> str | None:
    segs = package.lower().split(".")[1:]
    # com.android.* and com.google.android.* are AOSP/Google namespaces, not vendor ones: their
    # names are judged by the core rule and the generic profile instead.
    if package.startswith(("com.android.", "com.google.android.", "android.")):
        return None
    for seg in segs:
        for part in re.split(r"[_\d]+", seg):
            for w in words:
                if part == w or ((len(w) >= 4 or w in prefix_short) and part.startswith(w)):
                    return w
    return None


class Classifier:
    def __init__(self, matched_profiles: list[dict] | None = None, extra_protected: dict | None = None):
        self.profiles = list(matched_profiles or [])
        # Found on the unit itself: the current Home app and every app that can be Home.
        self.extra_protected = dict(extra_protected or {})
        prot = data.protected()
        self.core = [(re.compile(r["pattern"]), r) for r in prot["core"]]
        self.vehicle = [(re.compile(r["pattern"]), r) for r in prot["vehicle"]]
        self.words = prot["keyword"]["words"]
        self.prefix_short = set(prot["keyword"].get("prefix_short", []))
        self.keyword_reason = prot["keyword"]["reason"]
        self.telemetry = {e["package"]: e for e in data.telemetry()["packages"]}
        self.profile_protect = []
        self.profile_pkgs = {}
        for p in self.profiles:
            for pat in p.get("protected", []):
                self.profile_protect.append((re.compile(pat), p))
            for entry in p.get("packages", []):
                cat = entry["category"]
                if cat == "recommended" and p.get("status") != "tested":
                    cat = "optional"
                if p.get("policy", {}).get("recommended") == "none" and cat == "recommended":
                    cat = "optional"
                prev = self.profile_pkgs.get(entry["package"])
                # When two profiles speak about one package, the stricter one wins.
                if prev is None or STRICTNESS[cat] > STRICTNESS[prev[0]]:
                    self.profile_pkgs[entry["package"]] = (cat, entry, p)

    def classify(self, package: str, system: bool = True) -> Verdict:
        for rx, rule in self.core:
            if rx.match(package):
                return Verdict(package, "protected", rule["reason"], source=rule.get("source", ""), rule="core")
        if package in self.extra_protected:
            return Verdict(package, "protected", self.extra_protected[package], rule="device")
        for rx, p in self.profile_protect:
            if rx.match(package):
                why = next((r["reason"] for vx, r in self.vehicle if vx.match(package)), None)
                return Verdict(package, "protected", why or f"Protected by the {p['name']} profile.",
                               profile=p["id"], rule="profile")
        t = self.telemetry.get(package)
        if t:
            cat = t["category"]
            prof = self.profile_pkgs.get(package)
            if prof and STRICTNESS[prof[0]] > STRICTNESS[cat]:
                cat = prof[0]
            return Verdict(package, cat, t["evidence"], purpose=t["purpose"], risk=t["risk"],
                           finding=t["finding"], evidence=t["evidence"], sources=t.get("sources", []),
                           rule="telemetry")
        for rx, rule in self.vehicle:
            if rx.match(package):
                return Verdict(package, "protected", rule["reason"], source=rule.get("source", ""), rule="vehicle")
        hit = _keyword_hit(package, self.words, self.prefix_short)
        if hit:
            return Verdict(package, "protected", f"{self.keyword_reason} (word: {hit})", rule="keyword")
        prof = self.profile_pkgs.get(package)
        if prof:
            cat, entry, p = prof
            return Verdict(package, cat, entry.get("purpose", ""), purpose=entry.get("purpose", ""),
                           risk=entry.get("risk", ""), evidence=entry.get("evidence", ""),
                           profile=p["id"], rule="profile")
        if not system:
            return Verdict(package, "user", "Installed by the owner, not part of the firmware.", rule="user")
        return Verdict(package, "unknown", "Purpose not verified on any device profile.", rule="unknown")
