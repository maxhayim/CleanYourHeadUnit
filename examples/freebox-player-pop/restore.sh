#!/usr/bin/env bash
# Undo everything done to a Freebox Player POP: re-enable the packages debloat.txt disabled (except ones
# that were already disabled before), give Home back to OQEE, and put Private DNS back to automatic.
# The launcher stays installed but loses Home; remove it with: tools/venv/bin/python -I tools/tv.py 'pm uninstall com.gathin.tvhome'
#
# Usage: TV_HOST=<player ip> examples/freebox-player-pop/restore.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the Player IP address, e.g. TV_HOST=192.168.1.50 $0}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
"$ROOT/tools/restore.sh" "$HERE/debloat.txt" net.oqee.launcher
PY="$ROOT/tools/venv/bin/python"
printf '%s\n' "settings put global private_dns_mode opportunistic" "settings delete global private_dns_specifier" \
  | "$PY" -I "$ROOT/tools/tv.py" --batch
