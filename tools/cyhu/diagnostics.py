"""A diagnostic report the owner can read and choose to share. Never uploaded by this tool.

Sanitised: serial numbers, IMEI/IMSI/ICCID, MAC and Bluetooth addresses, Wi-Fi names, phone
numbers, account names, e-mail addresses, tokens and keys are removed before anything is written.
"""
from __future__ import annotations

import re

SENSITIVE_KEY = re.compile(r"(serial|imei|meid|imsi|iccid|msisdn|mac|bt\.?addr|bluetooth\.addr|wifi|wlan\.ssid|ssid|"
                           r"phone\.number|line1|account|email|token|password|passwd|secret|key|"
                           r"\.sn$|\.sn\.|uuid|android_id|gsm\.operator\.(alpha|numeric)|gsm\.sim\.operator)", re.I)
MAC = re.compile(r"\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b")
EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
LONG_DIGITS = re.compile(r"\b\d{12,}\b")
IPV4_PRIVATE = re.compile(r"\b(10|192\.168|172\.(1[6-9]|2\d|3[01]))(\.\d{1,3}){2,3}\b")
SAFE_KEYS = {"ro.build.fingerprint", "ro.build.description", "ro.build.display.id"}


def scrub_value(v: str) -> str:
    v = MAC.sub("[mac]", v)
    v = EMAIL.sub("[email]", v)
    v = LONG_DIGITS.sub("[number]", v)
    v = IPV4_PRIVATE.sub("[lan-ip]", v)
    return v


def sanitize_props(props: dict) -> dict:
    out = {}
    for k, v in sorted(props.items()):
        if k not in SAFE_KEYS and SENSITIVE_KEY.search(k):
            continue
        out[k] = scrub_value(v)
    return out


def report(info, findings: list, private_dns: dict | None, changes: list, catalog_items: list) -> dict:
    installed = {}
    for it in catalog_items:
        if it.get("package") and it.get("install") != "builtin" and it["package"] in info.packages:
            installed[it["id"]] = {"package": it["package"], "enabled": info.packages[it["package"]].enabled}
    return {
        "report": "CleanYourHeadUnit diagnostics (sanitised; review before sharing)",
        "summary": {k: v for k, v in info.summary().items() if k != "mcu"},
        "mcu_properties": {k: scrub_value(v) for k, v in info.mcu_props.items()} or "not reported",
        "props": sanitize_props(info.props),
        "disabled_packages": sorted(n for n, p in info.packages.items() if not p.enabled),
        "privacy": {"findings": [{"package": f["package"], "finding": f["finding"], "enabled": f["enabled"]}
                                 for f in findings],
                    "private_dns": private_dns if private_dns is not None else "not available below Android 9"},
        "catalog_installed": installed,
        "changes_by_cleanyourheadunit": [{k: c[k] for k in ("time", "kind", "target", "before", "after", "status")}
                                         for c in changes],
    }
