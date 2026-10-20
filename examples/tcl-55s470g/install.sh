#!/usr/bin/env bash
# Build the launcher and set this TCL 55S470G up the way it was done the first time: disable
# setupwraith (it claims Home once launcherx is off), build and install the launcher as Home, then push
# the dock theme in theme/config.json.
#
# Usage: TV_HOST=192.168.1.50 examples/tcl-55s470g/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV's IP}"
cd "$(dirname "$0")/../.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
# The saved Wi-Fi must not belong to setupwraith, or disabling it deletes the network (README Troubleshooting).
if "$PY" -I tools/tv.py "dumpsys wifi" | grep -o 'cname=[^ ]*' | grep setupwraith >/dev/null; then
  echo "the saved Wi-Fi network belongs to setupwraith: forget it and join it again in Settings, then run this again" >&2
  exit 1
fi
"$PY" -I tools/tv.py "pm disable-user --user 0 com.google.android.tungsten.setupwraith"
# HOME_KEY_SERVICE=1: the remote's gear button (keycode 83) opens the quick settings panel, see theme/config.json.
HOME_KEY_SERVICE=1 launcher/build-and-install.sh
# TCL's boot blocker (TclAppBoot) refuses to start the key service after a restart unless the launcher
# may auto-start; without this the gear and app buttons die at every boot until the service is toggled.
"$PY" -I tools/tv.py "appops set com.gathin.tvhome APP_AUTO_START allow; appops get com.gathin.tvhome APP_AUTO_START"
tools/theme.sh examples/tcl-55s470g/theme
