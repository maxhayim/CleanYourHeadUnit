#!/usr/bin/env bash
# Undo everything done to this TCL 55S470G: re-enable every package in debloat.txt (setupwraith
# included), give Home back to the stock Google TV launcher, and put DNS back to automatic.
# The custom launcher stays installed; uninstall it with: tools/tv.py pm uninstall com.gathin.tvhome
#
# Usage: TV_HOST=<tv ip> examples/tcl-55s470g/restore.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
tools/restore.sh examples/tcl-55s470g/debloat.txt com.google.android.apps.tv.launcherx
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
{
  echo "settings put global private_dns_mode opportunistic"
  echo "settings delete global private_dns_specifier"
  echo "settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop"
  echo "settings put secure screensaver_default_component com.google.android.apps.tv.dreamx/.service.Backdrop"
} | "$PY" -I tools/tv.py --batch
