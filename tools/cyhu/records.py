"""Backups and the change record, kept on the computer, per head unit.

    ~/.cleanyourheadunit/<unit key>/
        baseline.json   the unit as first seen: every package's state, Home, Private DNS.
                        Written once and never overwritten.
        changes.json    every change CleanYourHeadUnit made: what it was before and after.
        log.jsonl       one line per action, including refusals and failures.

Restore works from changes.json, newest first, and only puts a value back if it is still the
value CleanYourHeadUnit set. If the owner changed it since, it is left alone and reported.
"""
from __future__ import annotations

import json
import os
import time

HOME = os.environ.get("CYHU_HOME") or os.path.join(os.path.expanduser("~"), ".cleanyourheadunit")


def _now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S%z")


class Records:
    def __init__(self, unit_key: str, root: str | None = None):
        self.dir = os.path.join(root or HOME, unit_key)
        os.makedirs(self.dir, exist_ok=True)
        try:
            os.chmod(self.dir, 0o700)
        except OSError:
            pass

    def path(self, name: str) -> str:
        return os.path.join(self.dir, name)

    def _read(self, name: str, default):
        try:
            with open(self.path(name), encoding="utf-8") as fh:
                return json.load(fh)
        except FileNotFoundError:
            return default

    def _write(self, name: str, value) -> None:
        tmp = self.path(name + ".tmp")
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(value, fh, indent=2, sort_keys=True)
            fh.write("\n")
        os.replace(tmp, self.path(name))

    # --- baseline --------------------------------------------------------------------------
    def baseline(self) -> dict | None:
        return self._read("baseline.json", None)

    def save_baseline(self, info, private_dns: dict | None) -> bool:
        """Write the baseline unless one exists. Returns True when it was written now."""
        if self.baseline() is not None:
            return False
        self._write("baseline.json", {
            "time": _now(),
            "summary": info.summary(),
            "packages": {n: {"system": p.system, "enabled": p.enabled} for n, p in sorted(info.packages.items())},
            "home": info.home,
            "private_dns": private_dns,
        })
        self.log("baseline", {"packages": len(info.packages)})
        return True

    # --- changes ---------------------------------------------------------------------------
    def changes(self) -> list:
        return self._read("changes.json", [])

    def add_change(self, kind: str, target: str, before, after, note: str = "") -> None:
        ch = self.changes()
        ch.append({"time": _now(), "kind": kind, "target": target, "before": before, "after": after,
                   "status": "applied", "note": note})
        self._write("changes.json", ch)

    def mark(self, index: int, status: str, note: str = "") -> None:
        ch = self.changes()
        ch[index]["status"] = status
        ch[index]["status_time"] = _now()
        if note:
            ch[index]["note"] = note
        self._write("changes.json", ch)

    def log(self, action: str, detail: dict) -> None:
        with open(self.path("log.jsonl"), "a", encoding="utf-8") as fh:
            fh.write(json.dumps({"time": _now(), "action": action, **detail}, sort_keys=True) + "\n")

    def read_log(self) -> list:
        try:
            with open(self.path("log.jsonl"), encoding="utf-8") as fh:
                return [json.loads(line) for line in fh if line.strip()]
        except FileNotFoundError:
            return []
