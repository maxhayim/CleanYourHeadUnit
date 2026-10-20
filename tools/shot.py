#!/usr/bin/env python3
"""Take a screenshot on the TV and save it locally as PNG (via base64 over the shell, no file pull).

Usage: shot.py <local.png> [keyevent ...]   keyevents (e.g. DPAD_DOWN HOME) are sent first, 0.6 s apart.
"""
import base64
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tv  # noqa: E402

out, keys = sys.argv[1], sys.argv[2:]
dev = tv.connect()
for k in keys:
    if k.startswith("sleep"):
        time.sleep(float(k[5:] or 1))
        continue
    dev.shell(f"input keyevent {k if k.isdigit() else 'KEYCODE_' + k}")
    time.sleep(0.6)
if keys:
    time.sleep(0.8)
b64 = dev.shell("screencap -p /data/local/tmp/s.png && base64 /data/local/tmp/s.png && rm /data/local/tmp/s.png",
                read_timeout_s=120.0, decode=False)
data = base64.b64decode(b"".join(b64.split()))
open(out, "wb").write(data)
print(f"{out}: {len(data)} bytes")
dev.close()
