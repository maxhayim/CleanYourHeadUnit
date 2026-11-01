"""The Community Hub catalog: validation, listing, and installs from official sources.

The catalog is data, never code. Validation refuses any entry that carries a command, a script or a
shell fragment, any link that is not https, and any GitHub release source whose owner is not the
listed developer: a download comes from the developer's own repository or not at all.
"""
from __future__ import annotations

import json
import os
import re
import tempfile
import urllib.request

from . import data, apk
from .adb import PACKAGE_RE, Device

ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]{1,40}$")
GH_USER_RE = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
REPO_RE = re.compile(r"^([A-Za-z0-9-]{1,39})/([A-Za-z0-9._-]{1,100})$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
COLOR_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")
KINDS = ("launcher", "module", "theme")
INSTALLS = ("github_release", "store_listing", "none", "builtin")
STATUSES = ("verified", "community", "untested", "planned")
FORBIDDEN_KEYS = {"command", "commands", "script", "scripts", "shell", "exec", "run", "postinstall",
                  "preinstall", "install_command", "adb", "cmd"}
SHELLISH = re.compile(r"(\$\(|`|\badb\s+(shell|install|push)\b|\bpm\s+(install|uninstall|disable)|\bsu\s+-c\b|\bchmod\b|\bcurl\b.*\|\s*sh)", re.I)
THEME_KEYS = {"schema", "id", "name", "author", "license", "target", "dark", "colors", "layout", "wallpaper", "description"}
THEME_COLORS = {"background", "surface", "text", "text_dim", "accent", "warn", "ok"}
THEME_LAYOUT = {"dock": ("bottom", "left"), "icon_size": ("small", "medium", "large")}
THEME_FILES = re.compile(r"^[a-z0-9][a-z0-9._-]*\.(json|png|jpg|jpeg|webp|md|txt)$")
MAX_WALLPAPER = 2 * 1024 * 1024


def _walk_strings(obj, path=""):
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield from _walk_strings(v, f"{path}.{k}" if path else k)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from _walk_strings(v, f"{path}[{i}]")
    elif isinstance(obj, str):
        yield path, obj


def _walk_keys(obj, path=""):
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield f"{path}.{k}" if path else k, k
            yield from _walk_keys(v, f"{path}.{k}" if path else k)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from _walk_keys(v, f"{path}[{i}]")


def check_item(it: dict, devs: dict, launcher_ids: set, root: str) -> list[str]:
    e = []
    iid = it.get("id", "?")
    say = lambda m: e.append(f"{iid}: {m}")
    if not ID_RE.match(str(it.get("id", ""))):
        say("id must be lower-case letters, digits and hyphens")
    if it.get("kind") not in KINDS:
        say(f"kind must be one of {KINDS}")
    name = it.get("name", "")
    if not (1 <= len(name) <= 40):
        say("name must be 1 to 40 characters")
    for p, k in _walk_keys(it):
        if k.lower() in FORBIDDEN_KEYS:
            say(f"'{p}' is not allowed: the catalog never carries commands or scripts")
    for p, s in _walk_strings(it):
        if SHELLISH.search(s):
            say(f"'{p}' looks like a shell command; describe instead of instructing")
        if p.startswith("links.") or p.endswith("url"):
            if not s.startswith("https://") or "@" in s.split("/")[2]:
                say(f"'{p}' must be an https link without credentials")
    dev = devs.get(it.get("developer"))
    if not dev:
        say("developer must be one of the catalog's developers")
    if not it.get("description"):
        say("description is required")
    if not it.get("license"):
        say("license is required (an SPDX id, or a plain statement such as 'Proprietary, free to use')")
    comp = it.get("compatibility", {})
    if comp.get("status") not in STATUSES:
        say(f"compatibility.status must be one of {STATUSES}")
    if comp.get("status") == "verified" and not comp.get("verified"):
        say("'verified' needs at least one entry in compatibility.verified naming the tested unit")
    inst = it.get("install")
    if inst not in INSTALLS:
        say(f"install must be one of {INSTALLS}")
    if it.get("kind") in ("launcher", "module") and inst != "builtin":
        if not PACKAGE_RE.match(it.get("package", "")):
            say("package must be an Android package name")
        a = it.get("android", {})
        if not isinstance(a.get("min_sdk"), int) or not 14 <= a["min_sdk"] <= 40:
            say("android.min_sdk must be an API level (14 to 40)")
        if not set(it.get("orientations", [])) or not set(it.get("orientations", [])) <= {"landscape", "portrait"}:
            say("orientations must list landscape and/or portrait")
    if inst == "github_release":
        src = it.get("source", {})
        m = REPO_RE.match(src.get("repo", ""))
        if src.get("type") != "github_release" or not m:
            say("a github_release install needs source.type github_release and source.repo owner/name")
        elif dev and (dev.get("github") or "").lower() != m.group(1).lower():
            say("source.repo must belong to the listed developer: downloads come from the official repository only")
        try:
            re.compile(src.get("asset_pattern", ""))
            if not src.get("asset_pattern"):
                raise re.error("empty")
        except re.error:
            say("source.asset_pattern must be a regular expression for the APK file name")
        if not HEX64.match(it.get("signing_cert_sha256", "")):
            say("signing_cert_sha256 (the APK signer's certificate SHA-256) is required for direct installs")
    if it.get("status") == "planned" and inst not in ("none",):
        say("a planned entry can't offer an install")
    if it.get("featured") and dev and not dev.get("github") and not dev.get("name"):
        say("a featured entry needs a named developer")
    if it.get("kind") == "theme":
        t = it.get("target", {})
        if t.get("launcher") not in launcher_ids:
            say("target.launcher must be a launcher id in the catalog")
        if it.get("path"):
            e.extend(f"{iid}: {m}" for m in check_theme_folder(os.path.join(root, it["path"])))
    return e


def check_theme_folder(folder: str) -> list[str]:
    e = []
    tj = os.path.join(folder, "theme.json")
    try:
        with open(tj, encoding="utf-8") as fh:
            t = json.load(fh)
    except (OSError, ValueError) as ex:
        return [f"theme.json unreadable: {ex}"]
    e += check_theme(t)
    for f in os.listdir(folder):
        if f.startswith("."):
            continue
        if not THEME_FILES.match(f):
            e.append(f"{f}: only json, png, jpg, webp, md and txt files belong in a theme (no code)")
    wp = t.get("wallpaper")
    if wp:
        p = os.path.join(folder, wp)
        if not os.path.isfile(p):
            e.append(f"wallpaper {wp} is missing")
        elif os.path.getsize(p) > MAX_WALLPAPER:
            e.append("wallpaper is over 2 MB")
    return e


def check_theme(t: dict) -> list[str]:
    e = []
    extra = set(t) - THEME_KEYS
    if extra:
        e.append(f"unknown keys {sorted(extra)} (themes are data only)")
    if t.get("schema") != 1:
        e.append("schema must be 1")
    if not ID_RE.match(str(t.get("id", ""))):
        e.append("id must be lower-case letters, digits and hyphens")
    if not t.get("license"):
        e.append("license is required")
    tgt = t.get("target", {})
    if not tgt.get("launcher") or not tgt.get("min_version"):
        e.append("target.launcher and target.min_version are required: a theme names the launcher it is for")
    cols = t.get("colors", {})
    if set(cols) != THEME_COLORS:
        e.append(f"colors must have exactly {sorted(THEME_COLORS)}")
    for k, v in cols.items():
        if not COLOR_RE.match(str(v)):
            e.append(f"colors.{k} must be #RRGGBB")
    lay = t.get("layout", {})
    for k, allowed in THEME_LAYOUT.items():
        if k in lay and lay[k] not in allowed:
            e.append(f"layout.{k} must be one of {allowed}")
    if "clock" in lay and not isinstance(lay["clock"], bool):
        e.append("layout.clock must be true or false")
    for k in ("columns_landscape", "columns_portrait"):
        if k in lay and not (isinstance(lay[k], int) and 2 <= lay[k] <= 10):
            e.append(f"layout.{k} must be 2 to 10")
    for p, s in _walk_strings(t):
        if SHELLISH.search(s):
            e.append(f"'{p}' looks like a shell command")
    return e


def validate(cat: dict | None = None, root: str | None = None) -> list[str]:
    cat = cat or data.catalog()
    root = root or data.ROOT
    errors = []
    devs = {}
    for d in cat.get("developers", []):
        if not ID_RE.match(d.get("id", "")):
            errors.append(f"developer {d.get('id')}: bad id")
        gh = d.get("github")
        if gh is not None and not GH_USER_RE.match(gh):
            errors.append(f"developer {d.get('id')}: github must be a GitHub username or null")
        if gh is None and not d.get("name"):
            errors.append(f"developer {d.get('id')}: needs a github username or a name")
        site = d.get("website")
        if site is not None and not re.match(r"^https://[^\s/@]+(/\S*)?$", str(site)):
            errors.append(f"developer {d.get('id')}: website must be an https link without credentials")
        devs[d.get("id")] = d
    ids = [it.get("id") for it in cat.get("items", [])]
    for dup in {i for i in ids if ids.count(i) > 1}:
        errors.append(f"{dup}: duplicate id")
    launchers = {it["id"] for it in cat.get("items", []) if it.get("kind") == "launcher"}
    for it in cat.get("items", []):
        errors += check_item(it, devs, launchers, root)
    return errors


def validate_submission(sub: dict, root: str | None = None) -> list[str]:
    """A pull request's new entry: the catalog rules, plus screenshots and a known developer."""
    cat = json.loads(json.dumps(data.catalog()))
    for d in sub.get("developers", []):
        if d.get("id") not in {x["id"] for x in cat["developers"]}:
            cat["developers"].append(d)
    item = sub.get("item", sub)
    cat["items"] = [x for x in cat["items"] if x["id"] != item.get("id")] + [item]
    errs = [m for m in validate(cat, root) if m.startswith(f"{item.get('id')}:") or m.startswith("developer")]
    if not item.get("screenshots"):
        errs.append(f"{item.get('id')}: at least one screenshot is required")
    return errs


def items(kind: str | None = None) -> list[dict]:
    return [it for it in data.catalog()["items"] if kind is None or it["kind"] == kind]


def item(iid: str) -> dict | None:
    for it in data.catalog()["items"]:
        if it["id"] == iid:
            return it
    return None


def developer(did: str) -> dict | None:
    for d in data.catalog()["developers"]:
        if d["id"] == did:
            return d
    return None


def credit(did: str) -> str:
    d = developer(did) or {}
    return f"@{d['github']}" if d.get("github") else (d.get("name") or did)


# --- installs ----------------------------------------------------------------------------------
def latest_release(repo: str, opener=urllib.request.urlopen) -> dict:
    req = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases/latest",
                                 headers={"Accept": "application/vnd.github+json",
                                          "User-Agent": "CleanYourHeadUnit"})
    with opener(req, timeout=30) as r:
        return json.load(r)


def pick_asset(release: dict, pattern: str) -> dict | None:
    rx = re.compile(pattern)
    for a in release.get("assets", []):
        if rx.match(a.get("name", "")):
            return a
    return None


def download(url: str, opener=urllib.request.urlopen) -> str:
    if not url.startswith("https://github.com/") and not url.startswith("https://objects.githubusercontent.com/"):
        raise ValueError("downloads come from GitHub release assets only")
    fd, path = tempfile.mkstemp(suffix=".apk", prefix="cyhu-")
    with os.fdopen(fd, "wb") as out, opener(urllib.request.Request(url, headers={"User-Agent": "CleanYourHeadUnit"}), timeout=300) as r:
        while True:
            chunk = r.read(1 << 16)
            if not chunk:
                break
            out.write(chunk)
    return path


def check_apk(it: dict, path: str, published_digest: str | None, allow_unpinned: bool = False) -> list[str]:
    """Problems that stop an install. Empty means the APK is the one the catalog describes."""
    problems = []
    info = apk.inspect(path)
    if published_digest:
        want = published_digest.split(":", 1)[-1].lower()
        if info["sha256"] != want:
            problems.append(f"download checksum {info['sha256']} does not match the release's {want}")
    if info["package"] != it["package"]:
        problems.append(f"package is {info['package']}, the catalog lists {it['package']}")
    if info["signer_sha256"] is None:
        if not allow_unpinned:
            problems.append("no v2/v3 signature block: the signing certificate can't be checked")
    elif info["signer_sha256"] != it.get("signing_cert_sha256"):
        problems.append("signed by a different certificate than the catalog's pin: refusing "
                        f"({info['signer_sha256'][:16]}... vs {it.get('signing_cert_sha256', '')[:16]}...)")
    return problems


def installed_version(dev: Device, package: str) -> str | None:
    out = dev.out(f"dumpsys package {package}")
    m = re.search(r"versionName=(\S+)", out)
    return m.group(1) if m and "Unable to find package" not in out else None
