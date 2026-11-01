"""A head unit made of JSON, for tests and --demo.

It answers the shell commands CleanYourHeadUnit sends, with the output format of a real unit of
the fixture's Android version, and keeps state: a package disabled here stays disabled, a home
activity set here is what resolve-activity answers next. It is not an emulator; only the commands
the tool uses are understood, and anything else exits 127 like an unknown command.
"""
from __future__ import annotations

import copy
import json
import re
import shlex

from .adb import Device


class FakeDevice(Device):
    def __init__(self, fixture: dict):
        f = copy.deepcopy(fixture)
        self.props: dict = f["props"]
        self.sdk = int(self.props.get("ro.build.version.sdk", "0"))
        # packages: name -> {system, enabled, version, home, perms{name: granted}}
        self.packages: dict = f["packages"]
        self.home: str | None = f.get("home")
        self.settings: dict = f.get("settings", {"global": {}, "secure": {}, "system": {}})
        self.size = f.get("wm_size", "1024x600")
        self.density = f.get("wm_density", "160")
        self.meminfo = f.get("meminfo", "MemTotal:        2009444 kB\nMemAvailable:     812345 kB\n")
        self.df = f.get("df", "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-2 25450460 6123456 19327004 25% /data\n")
        self.calls: list[str] = []
        self.installed_apks: list[str] = []
        self.fail_disable: set = set(f.get("fail_disable", []))

    @classmethod
    def load(cls, path: str) -> "FakeDevice":
        with open(path, encoding="utf-8") as fh:
            return cls(json.load(fh))

    # --- helpers ---------------------------------------------------------------------------
    def _user_flag_ok(self, args: list[str]) -> list[str]:
        if "--user" in args:
            i = args.index("--user")
            del args[i:i + 2]
        return args

    def _list(self, flags: list[str]) -> str:
        names = []
        for name, p in sorted(self.packages.items()):
            if "-s" in flags and not p.get("system"):
                continue
            if "-3" in flags and p.get("system"):
                continue
            if "-d" in flags and p.get("enabled", True):
                continue
            if "-e" in flags and not p.get("enabled", True):
                continue
            names.append(f"package:{name}")
        return "\n".join(names)

    def _home_candidates(self) -> list[str]:
        return [p["home"] for n, p in sorted(self.packages.items())
                if p.get("home") and p.get("enabled", True)]

    def _dumpsys_package(self, name: str) -> str:
        p = self.packages.get(name)
        if not p:
            return f"Unable to find package: {name}"
        lines = [f"Packages:", f"  Package [{name}] (fake):",
                 f"    versionCode={p.get('code', 1)} minSdk=19 targetSdk=33",
                 f"    versionName={p.get('version', '1.0')}",
                 f"    pkgFlags=[ {'SYSTEM ' if p.get('system') else ''}HAS_CODE ]"]
        perms = p.get("perms", {})
        if self.sdk >= 23:
            lines.append("    install permissions:")
            for perm, granted in perms.items():
                if perm in ("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                            "android.permission.RECEIVE_BOOT_COMPLETED"):
                    lines.append(f"      {perm}: granted={str(granted).lower()}")
            lines.append("    User 0: ceDataInode=1 installed=true hidden=false suspended=false "
                         f"enabled={0 if p.get('enabled', True) else 3}")
            lines.append("      runtime permissions:")
            for perm, granted in perms.items():
                if perm not in ("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                                "android.permission.RECEIVE_BOOT_COMPLETED"):
                    lines.append(f"        {perm}: granted={str(granted).lower()}")
        else:
            lines.append("    grantedPermissions:")
            for perm, granted in perms.items():
                if granted:
                    lines.append(f"      {perm}")
        return "\n".join(lines)

    # --- the shell -------------------------------------------------------------------------
    def shell(self, command: str) -> tuple[str, int]:
        self.calls.append(command)
        try:
            args = shlex.split(command)
        except ValueError:
            return "syntax error", 2
        if not args:
            return "", 0
        cmd = args[0]
        if cmd == "getprop":
            if len(args) == 2:
                return self.props.get(args[1], ""), 0
            return "\n".join(f"[{k}]: [{v}]" for k, v in sorted(self.props.items())), 0
        if cmd == "pm" and len(args) >= 3 and args[1:3] == ["list", "packages"]:
            return self._list(args[3:]), 0
        if cmd == "pm" and len(args) >= 2 and args[1] in ("disable-user", "enable"):
            rest = self._user_flag_ok(args[2:])
            if args[1] == "disable-user" and self.sdk < 14:
                return "Error: unknown command 'disable-user'", 1
            if not rest or rest[0] not in self.packages:
                return f"Error: Unknown package: {rest[0] if rest else ''}", 1
            name = rest[0]
            if args[1] == "disable-user":
                if name in self.fail_disable:
                    return f"Error: java.lang.SecurityException: Cannot disable a protected package: {name}", 1
                self.packages[name]["enabled"] = False
                return f"Package {name} new state: disabled-user", 0
            self.packages[name]["enabled"] = True
            return f"Package {name} new state: enabled", 0
        if cmd == "cmd" and len(args) >= 2 and args[1] == "package":
            if self.sdk < 24:
                return "cmd: Can't find service: package", 1
            sub = args[2] if len(args) > 2 else ""
            if sub == "resolve-activity":
                if self.home and self.home.split("/")[0] in self.packages and \
                        self.packages[self.home.split("/")[0]].get("enabled", True):
                    return f"priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n{self.home}", 0
                return "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\nandroid/com.android.internal.app.ResolverActivity", 0
            if sub == "query-activities":
                cands = self._home_candidates()
                return f"{len(cands)} activities found:\n" + "\n".join(f"  Activity #{i}:\n    {c}" for i, c in enumerate(cands)), 0
            if sub == "set-home-activity":
                comp = self._user_flag_ok(args[3:])[0]
                if comp not in self._home_candidates():
                    return f"Error: component {comp} is not a home activity", 1
                self.home = comp
                return "Success", 0
            return f"Unknown command: {sub}", 1
        if cmd == "settings" and len(args) >= 4:
            op, ns, key = args[1], args[2], args[3]
            if op == "get":
                return str(self.settings.get(ns, {}).get(key, "null")), 0
            if op == "put" and len(args) >= 5:
                self.settings.setdefault(ns, {})[key] = args[4]
                return "", 0
            if op == "delete":
                self.settings.get(ns, {}).pop(key, None)
                return "Deleted 1 rows", 0
        if cmd == "dumpsys" and len(args) >= 3 and args[1] == "package":
            return self._dumpsys_package(args[2]), 0
        if cmd == "wm" and len(args) == 2 and args[1] in ("size", "density"):
            if self.sdk < 18:
                return "", 127
            return (f"Physical size: {self.size}" if args[1] == "size" else f"Physical density: {self.density}"), 0
        if cmd == "cat" and args[1:] == ["/proc/meminfo"]:
            return self.meminfo, 0
        if cmd == "df" and args[1:] == ["/data"]:
            return self.df, 0
        if cmd == "monkey" or cmd == "am":
            return "Events injected: 1", 0
        return f"/system/bin/sh: {cmd}: not found", 127

    def install(self, apk_path: str, replace: bool) -> tuple[str, int]:
        self.installed_apks.append(apk_path)
        return "Success", 0


def first_line_component(text: str) -> str | None:
    for line in reversed(text.strip().splitlines()):
        line = line.strip()
        if re.match(r"^[\w.]+/[\w.$]+$", line):
            return line
    return None
