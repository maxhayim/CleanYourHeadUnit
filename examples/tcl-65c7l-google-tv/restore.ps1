# Put this TCL 65" C7L back the way it was before the cleanup on 2026-10-22: every package in
# debloat.txt enabled again, the Google TV home (launcherx) back as Home, the promo screensaver and
# automatic DNS back. Nothing was disabled before the cleanup (pm list packages -d was empty), so
# enabling the whole list is the exact undo.
#
# Usage: examples\tcl-65c7l-google-tv\restore.ps1 -TvHost 192.168.1.50 [-UninstallLauncher]
#        -UninstallLauncher also removes the new launcher (com.gathin.tvhome) from the TV.
# Needs network ADB on the TV (Developer options > Wireless debugging) and tools\venv.
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$UninstallLauncher
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$tv = Join-Path $repo 'tools\tv.py'

# Packages and Home. setupwraith comes back with the list; the Wi-Fi network now belongs to
# Settings, so re-enabling it does not touch the connection.
& (Join-Path $repo 'tools\restore.ps1') -TvHost $TvHost (Join-Path $PSScriptRoot 'debloat.txt') -Stock com.google.android.apps.tv.launcherx

# Settings changed outside the list. Before the cleanup screensaver_components was unset (the
# Google ambient screensaver) and Private DNS was on its default, automatic.
@(
    'settings delete secure screensaver_components',
    'settings put global private_dns_mode opportunistic',
    'settings delete global private_dns_specifier'
) | & $py -I $tv --batch

if ($UninstallLauncher) {
    & $py -I $tv 'pm uninstall com.gathin.tvhome'
}
& $py -I $tv 'input keyevent HOME'
Write-Host 'Restored. Press Home on the remote: the Google TV home should come up.'
