"""Talking to the head unit over ADB.

Every command that reaches the unit goes through a Device. AdbDevice runs the Google `adb`
binary; FakeDevice (fake.py) answers the same commands from a JSON fixture, for tests and --demo.

Names that end up inside a shell command on the unit are checked against PACKAGE_RE /
COMPONENT_RE first, so nothing that could carry shell syntax is ever sent (the same rule as
CleanYourTV's tools/disable.sh, #165 there).
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess

PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
COMPONENT_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$")
HOSTNAME_RE = re.compile(r"^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$")
RC_MARK = "__cyhu_rc="


class DeviceError(RuntimeError):
    pass


def check_package(name: str) -> str:
    if not PACKAGE_RE.match(name or ""):
        raise ValueError(f"not a package name: {name!r}")
    return name


def check_component(name: str) -> str:
    if not COMPONENT_RE.match(name or ""):
        raise ValueError(f"not a component name: {name!r}")
    return name


def check_hostname(name: str) -> str:
    if not HOSTNAME_RE.match(name or ""):
        raise ValueError(f"not a hostname: {name!r}")
    return name


class Device:
    """What every transport offers. shell() returns (output, exit status)."""

    def shell(self, command: str) -> tuple[str, int]:
        raise NotImplementedError

    def install(self, apk_path: str, replace: bool) -> tuple[str, int]:
        raise NotImplementedError

    def out(self, command: str) -> str:
        return self.shell(command)[0]


def find_adb() -> str | None:
    env = os.environ.get("CYHU_ADB")
    if env:
        return env
    found = shutil.which("adb")
    if found:
        return found
    for home in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                 "/opt/homebrew/share/android-commandlinetools"):
        if home:
            cand = os.path.join(home, "platform-tools", "adb")
            if os.path.exists(cand):
                return cand
    return None


class AdbDevice(Device):
    def __init__(self, serial: str | None = None, adb: str | None = None, timeout: int = 60):
        self.adb = adb or find_adb()
        if not self.adb:
            raise DeviceError("adb not found: install Android platform-tools, or set CYHU_ADB to the adb binary")
        self.serial = serial or os.environ.get("ANDROID_SERIAL") or os.environ.get("CYHU_SERIAL")
        self.timeout = timeout

    def _base(self) -> list[str]:
        return [self.adb] + (["-s", self.serial] if self.serial else [])

    def shell(self, command: str) -> tuple[str, int]:
        # Old adbd (Android 4.4 to 6) does not pass the exit status back, so print it ourselves.
        try:
            p = subprocess.run(self._base() + ["shell", f"{command}; echo {RC_MARK}$?"],
                               capture_output=True, text=True, timeout=self.timeout)
        except subprocess.TimeoutExpired as e:
            raise DeviceError(f"the unit did not answer within {self.timeout} s: {command}") from e
        if p.returncode != 0 and RC_MARK not in p.stdout:
            raise DeviceError((p.stderr or p.stdout).strip() or "adb failed")
        text = p.stdout.replace("\r\n", "\n")
        rc = 0
        idx = text.rfind(RC_MARK)
        if idx >= 0:
            tail = text[idx + len(RC_MARK):].strip()
            rc = int(tail) if tail.isdigit() else 1
            text = text[:idx]
        return text.rstrip("\n"), rc

    def install(self, apk_path: str, replace: bool) -> tuple[str, int]:
        args = self._base() + ["install"] + (["-r"] if replace else []) + [apk_path]
        p = subprocess.run(args, capture_output=True, text=True, timeout=600)
        return (p.stdout + p.stderr).strip(), p.returncode
