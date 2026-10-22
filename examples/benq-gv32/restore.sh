#!/usr/bin/env bash
# Put a BenQ GV32 back the way it came: re-enable every package in debloat.txt, give Home back to
# launcherx, switch the launcher's key helper off and remove the theme (dock, hidden tile, button maps).
#
# Usage, from the repo root: TV_HOST=<projector ip> examples/benq-gv32/restore.sh [--uninstall-launcher]
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${TV_HOST:?set TV_HOST to the projector IP address}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
tv() { "$PY" -I tools/tv.py "$@"; }

tools/restore.sh examples/benq-gv32/debloat.txt com.google.android.apps.tv.launcherx
tools/theme.sh --reset || true
# Drop only the launcher's helper from the accessibility list, keeping anything else (TalkBack) that is on.
tv 's=$(settings get secure enabled_accessibility_services | tr ":" "\n" | grep -v "^com.gathin.tvhome/" | paste -sd: -); if [ -z "$s" ]; then settings put secure enabled_accessibility_services ""; settings put secure accessibility_enabled 0; else settings put secure enabled_accessibility_services "$s"; fi'
if [ "${1:-}" = --uninstall-launcher ]; then
  tv 'pm uninstall com.gathin.tvhome'
fi
tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1'
echo "Restored. Press Home on the remote; restart the projector if the old home screen does not come up."
