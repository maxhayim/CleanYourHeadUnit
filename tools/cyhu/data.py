"""The shared JSON in data/: protected rules, telemetry catalogue, device profiles, catalog.

The Android app reads the same files (copied into its assets at build time), and both sides run
the vectors in tests/vectors/ so their answers cannot drift apart.
"""
from __future__ import annotations

import json
import os
from functools import lru_cache

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.environ.get("CYHU_DATA") or os.path.join(ROOT, "data")


def _load(name: str):
    with open(os.path.join(DATA, name), encoding="utf-8") as fh:
        return json.load(fh)


@lru_cache(maxsize=None)
def protected() -> dict:
    return _load("protected.json")


@lru_cache(maxsize=None)
def telemetry() -> dict:
    return _load("telemetry.json")


@lru_cache(maxsize=None)
def dns_providers() -> dict:
    return _load("dns-providers.json")


@lru_cache(maxsize=None)
def catalog() -> dict:
    return _load("catalog.json")


@lru_cache(maxsize=None)
def profiles() -> tuple:
    folder = os.path.join(DATA, "profiles")
    out = []
    for name in sorted(os.listdir(folder)):
        if name.endswith(".json"):
            with open(os.path.join(folder, name), encoding="utf-8") as fh:
                out.append(json.load(fh))
    return tuple(out)


def profile(pid: str) -> dict | None:
    for p in profiles():
        if p["id"] == pid:
            return p
    return None
