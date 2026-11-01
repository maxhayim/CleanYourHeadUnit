"""Who the head unit is: build properties, packages, display, memory, firmware family.

Nothing here changes the unit. A value the unit doesn't report is None, and is shown as
"not reported", never guessed.
"""
from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field

from . import data
from .adb import Device
from .fake import first_line_component

ANDROID_NAMES = {19: "4.4", 20: "4.4W", 21: "5.0", 22: "5.1", 23: "6.0", 24: "7.0", 25: "7.1",
                 26: "8.0", 27: "8.1", 28: "9", 29: "10", 30: "11", 31: "12", 32: "12L",
                 33: "13", 34: "14", 35: "15", 36: "16"}

PROP_LINE = re.compile(r"^\[([^\]]+)\]: \[(.*)\]$")


def parse_getprop(text: str) -> dict:
    props = {}
    for line in text.splitlines():
        m = PROP_LINE.match(line.strip())
        if m:
            props[m.group(1)] = m.group(2)
    return props


def parse_package_list(text: str) -> list[str]:
    out = []
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            name = line[len("package:"):]
            # `pm list packages -f` puts the path first: package:/system/app/X.apk=com.x
            if "=" in name:
                name = name.rsplit("=", 1)[1]
            out.append(name)
    return out


@dataclass
class Package:
    name: str
    system: bool
    enabled: bool


@dataclass
class DeviceInfo:
    props: dict
    sdk: int
    packages: dict                      # name -> Package
    home: str | None = None
    home_candidates: list = field(default_factory=list)
    size: str | None = None
    density: str | None = None
    mem_total_kb: int | None = None
    mem_available_kb: int | None = None
    data_total_kb: int | None = None
    data_free_kb: int | None = None
    profiles: list = field(default_factory=list)
    declared: list = field(default_factory=list)

    @property
    def android(self) -> str:
        return self.props.get("ro.build.version.release") or ANDROID_NAMES.get(self.sdk, "?")

    @property
    def key(self) -> str:
        """A stable local name for this unit's records. Hashed, so the serial is never written out."""
        raw = "|".join(self.props.get(k, "") for k in
                       ("ro.serialno", "ro.boot.serialno", "ro.product.model", "ro.product.device"))
        return hashlib.sha256(raw.encode()).hexdigest()[:16]

    @property
    def orientation(self) -> str | None:
        if not self.size:
            return None
        w, h = (int(x) for x in self.size.split("x"))
        return "portrait" if h > w else "landscape"

    @property
    def mcu_props(self) -> dict:
        return {k: v for k, v in self.props.items() if "mcu" in k.lower() and v}

    @property
    def chipset(self) -> str | None:
        for k in ("ro.board.platform", "ro.hardware", "ro.boot.hardware", "ro.product.board"):
            if self.props.get(k):
                return self.props[k]
        return None

    @property
    def firmware_family(self) -> str:
        for p in self.profiles:
            if p["id"] != "generic-android":
                return p["firmware_family"]
        return "unknown"

    def summary(self) -> dict:
        p = self.props
        return {
            "manufacturer": p.get("ro.product.manufacturer") or None,
            "brand": p.get("ro.product.brand") or None,
            "model": p.get("ro.product.model") or None,
            "device": p.get("ro.product.device") or None,
            "android": self.android,
            "sdk": self.sdk,
            "build": p.get("ro.build.display.id") or None,
            "chipset": self.chipset,
            "abi": p.get("ro.product.cpu.abi") or None,
            "mcu": self.mcu_props or None,
            "firmware_family": self.firmware_family,
            "profiles": [x["id"] for x in self.profiles],
            "screen": self.size,
            "density": self.density,
            "orientation": self.orientation,
            "ram_total_mb": self.mem_total_kb // 1024 if self.mem_total_kb else None,
            "ram_available_mb": self.mem_available_kb // 1024 if self.mem_available_kb else None,
            "storage_total_mb": self.data_total_kb // 1024 if self.data_total_kb else None,
            "storage_free_mb": self.data_free_kb // 1024 if self.data_free_kb else None,
            "home": self.home,
            "packages": len(self.packages),
            "disabled": sum(1 for x in self.packages.values() if not x.enabled),
        }


def match_profile(profile: dict, props: dict, packages: set, declared: list[str]) -> bool:
    m = profile.get("match", {})
    if m.get("always"):
        return True
    if m.get("declared"):
        return profile["id"] in declared
    ok = False
    if "packages_all" in m:
        if not all(p in packages for p in m["packages_all"]):
            return False
        ok = True
    if "packages_regex_any" in m:
        rxs = [re.compile(r) for r in m["packages_regex_any"]]
        if not any(rx.match(p) for rx in rxs for p in packages):
            return False
        ok = True
    if "props_any" in m:
        if not any(re.search(rx, props.get(k, "")) for k, rx in m["props_any"].items()):
            return False
        ok = True
    # A profile needs at least one hardware or firmware signal; a name alone never matches.
    return ok


def match_profiles(props: dict, packages: set, declared: list[str] | None = None) -> list[dict]:
    declared = declared or []
    found = [p for p in data.profiles() if match_profile(p, props, packages, declared)]
    # Specific profiles first, the generic rules last.
    found.sort(key=lambda p: p["id"] == "generic-android")
    return found


def home_info(dev: Device, sdk: int) -> tuple[str | None, list[str]]:
    if sdk < 24:
        return None, []
    out, rc = dev.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN "
                        "-c android.intent.category.HOME")
    home = first_line_component(out) if rc == 0 else None
    if home and home.startswith("android/"):
        home = None              # the chooser: no default Home set
    out, rc = dev.shell("cmd package query-activities --brief -a android.intent.action.MAIN "
                        "-c android.intent.category.HOME")
    cands = []
    if rc == 0:
        for line in out.splitlines():
            line = line.strip()
            if re.match(r"^[\w.]+/[\w.$]+$", line):
                cands.append(line)
    return home, cands


def identify(dev: Device, declared: list[str] | None = None) -> DeviceInfo:
    props = parse_getprop(dev.out("getprop"))
    try:
        sdk = int(props.get("ro.build.version.sdk", "0"))
    except ValueError:
        sdk = 0
    all_pkgs = parse_package_list(dev.out("pm list packages"))
    system = set(parse_package_list(dev.out("pm list packages -s")))
    disabled = set(parse_package_list(dev.out("pm list packages -d")))
    packages = {n: Package(n, n in system, n not in disabled) for n in all_pkgs}
    info = DeviceInfo(props=props, sdk=sdk, packages=packages, declared=list(declared or []))
    info.home, info.home_candidates = home_info(dev, sdk)
    out, rc = dev.shell("wm size")
    m = re.findall(r"(\d+x\d+)", out) if rc == 0 else []
    info.size = m[-1] if m else None          # an override ("Override size") is listed last
    out, rc = dev.shell("wm density")
    m = re.findall(r"density: (\d+)", out) if rc == 0 else []
    info.density = m[-1] if m else None
    mem = dev.out("cat /proc/meminfo")
    for key, attr in (("MemTotal", "mem_total_kb"), ("MemAvailable", "mem_available_kb")):
        mm = re.search(rf"^{key}:\s+(\d+) kB", mem, re.M)
        if mm:
            setattr(info, attr, int(mm.group(1)))
    df = dev.out("df /data").splitlines()
    if len(df) >= 2:
        cols = df[-1].split()
        nums = [c for c in cols if c.isdigit()]
        if len(nums) >= 3:
            info.data_total_kb, info.data_free_kb = int(nums[0]), int(nums[2])
    info.profiles = match_profiles(props, set(packages), info.declared)
    return info
