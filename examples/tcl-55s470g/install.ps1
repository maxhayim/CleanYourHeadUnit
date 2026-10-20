# Build the launcher and set this TCL 55S470G up the way it was done the first time: disable
# setupwraith (it claims Home once launcherx is off), build and install the launcher as Home, then push
# the dock theme in theme\config.json.
#
# Usage: powershell -ExecutionPolicy Bypass -File examples\tcl-55s470g\install.ps1 -TvHost 192.168.1.50
param([string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$repo = (Resolve-Path "$PSScriptRoot\..\..").Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
# The saved Wi-Fi must not belong to setupwraith, or disabling it deletes the network (README Troubleshooting).
$wifi = (& $py -I "$repo\tools\tv.py" 'dumpsys wifi') -join "`n"
if ($wifi -match 'cname=[^ ]*setupwraith') { throw 'the saved Wi-Fi network belongs to setupwraith: forget it and join it again in Settings > Network & Internet, then run this again' }
& $py -I "$repo\tools\tv.py" 'pm disable-user --user 0 com.google.android.tungsten.setupwraith'
# -HomeKeyService: the remote's gear button (keycode 83) opens the quick settings panel, see theme\config.json.
& "$repo\launcher\build-and-install.ps1" -TvHost $TvHost -HomeKeyService
if (-not $?) { throw 'launcher install failed' }
# TCL's boot blocker (TclAppBoot) refuses to start the key service after a restart unless the launcher
# may auto-start; without this the gear and app buttons die at every boot until the service is toggled.
& $py -I "$repo\tools\tv.py" 'appops set com.gathin.tvhome APP_AUTO_START allow; appops get com.gathin.tvhome APP_AUTO_START'
& "$repo\tools\theme.ps1" -TvHost $TvHost "$PSScriptRoot\theme"
