# Build the launcher, install it on this TCL 65" C7L as Home, and push this folder's config.json
# (dock order, columns, Live TV app). Run it again after editing config.json or pulling a new
# launcher version; it is safe to repeat.
#
# Usage: examples\tcl-65c7l-google-tv\install.ps1 -TvHost 192.168.1.50
# Needs JDK 17 (JAVA_HOME, or a jdk-17* folder in %USERPROFILE%\.jdks) and Android SDK 34
# (%LOCALAPPDATA%\Android\Sdk), network ADB on the TV and tools\venv.
#
# On a TV that was reset or restored, apply the list first (it holds back setupwraith and
# launcherx until it is safe), then this script, then the list once more:
#   tools\disable.ps1 -TvHost <ip> examples\tcl-65c7l-google-tv\debloat.txt
param([string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}

# No -InputKeyService / -HomeKeyService: every remote button still worked with launcherx disabled
# (checked 2026-10-22), and leaving the key helpers off keeps volume hold-to-repeat untouched.
& (Join-Path $repo 'launcher\build-and-install.ps1') -TvHost $TvHost
& (Join-Path $repo 'tools\theme.ps1') -TvHost $TvHost $PSScriptRoot

# The launcher's drifting clock instead of Google's promo screensaver (dreamx is in the list).
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
& $py -I (Join-Path $repo 'tools\tv.py') 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
