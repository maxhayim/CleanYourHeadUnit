# Windows version of restore.sh: undo everything done to this TCL 55S470G. Re-enables every package in
# debloat.txt (setupwraith included), gives Home back to the stock Google TV launcher, puts DNS back to
# automatic and the ambient-mode screensaver back. The custom launcher stays installed.
#
# Usage: powershell -ExecutionPolicy Bypass -File examples\tcl-55s470g\restore.ps1 -TvHost 192.168.1.50
param([string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$repo = (Resolve-Path "$PSScriptRoot\..\..").Path
& "$repo\tools\restore.ps1" -TvHost $TvHost "$PSScriptRoot\debloat.txt" -Stock com.google.android.apps.tv.launcherx
@(
    'settings put global private_dns_mode opportunistic',
    'settings delete global private_dns_specifier',
    'settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop',
    'settings put secure screensaver_default_component com.google.android.apps.tv.dreamx/.service.Backdrop'
) | & (Join-Path $repo 'tools\venv\Scripts\python.exe') -I "$repo\tools\tv.py" --batch
