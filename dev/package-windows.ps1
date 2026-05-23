param(
    [string]$Version = "0.2.0",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$Repo = Resolve-Path (Join-Path $PSScriptRoot "..")
$CleanVersion = $Version.TrimStart("v")
if ($CleanVersion -notmatch "^\d+\.\d+\.\d+$") {
    $CleanVersion = "0.2.0"
}

# MSI version: embed a day-stamp in the patch field (month*31+day = 32..403).
# This makes every new build "newer" than the previous one so the installer
# always replaces files on reinstall, even when the semver hasn't changed.
# The user-facing filename keeps the clean semver (e.g. v0.2.0).
$Major, $Minor = $CleanVersion -split '\.' | Select-Object -First 2
$Now = Get-Date
$DayStamp = $Now.Month * 31 + $Now.Day
$MsiVersion = "$Major.$Minor.$DayStamp"

$BuildDir = Join-Path $Repo "build"
$ToolsDir = Join-Path $BuildDir "tools"
$PackageDir = Join-Path $BuildDir "windows-package"
$InputDir = Join-Path $PackageDir "input"
$ReleaseDir = Join-Path $BuildDir "release"
$IconPath = Join-Path $Repo "desktop\src\main\resources\icon.ico"

# SHA-256 of wix311-binaries.zip (wixtoolset/wix3 release wix3112rtm).
$WixSha256 = "2c1888d5d1dba377fc7fa14444cf556963747ff9a0a289a3599cf09da03b9e2e"

function Reset-Directory($Path) {
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

# jpackage delegates EXE/MSI creation to WiX 3 (candle + light).
# Ensure the tools are on PATH, downloading and verifying them if needed.
function Ensure-Wix {
    if ((Get-Command candle.exe -ErrorAction SilentlyContinue) -and
        (Get-Command light.exe -ErrorAction SilentlyContinue)) { return }

    $WixZip = Join-Path $ToolsDir "wix311-binaries.zip"
    $WixDir = Join-Path $ToolsDir "wix311"
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null

    if (!(Test-Path $WixZip)) {
        Invoke-WebRequest -Uri "https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip" -OutFile $WixZip
    }
    $Hash = (Get-FileHash -Algorithm SHA256 $WixZip).Hash.ToLowerInvariant()
    if ($Hash -ne $WixSha256) {
        Remove-Item -LiteralPath $WixZip -Force
        throw "WiX checksum mismatch: expected $WixSha256, got $Hash"
    }

    if (!(Test-Path (Join-Path $WixDir "candle.exe"))) {
        Reset-Directory $WixDir
        Expand-Archive -Path $WixZip -DestinationPath $WixDir -Force
    }
    $env:PATH = "$WixDir;$env:PATH"
}

if (!$SkipBuild) {
    & (Join-Path $Repo "gradlew.bat") ":core:test" ":desktop:test" ":desktop:installDist"
}

Ensure-Wix
Reset-Directory $InputDir
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null

$LibDir = Join-Path $Repo "desktop\build\install\desktop\lib"
if (!(Test-Path (Join-Path $LibDir "desktop.jar"))) {
    throw "desktop installDist not found"
}

Copy-Item (Join-Path $LibDir "*") $InputDir -Recurse -Force

& (Join-Path $PSScriptRoot "ensure-sing-box.ps1") -Destination (Join-Path $InputDir "sing-box.exe") | Out-Null

$TempOutput = Join-Path $PackageDir "output"
Reset-Directory $TempOutput

& jpackage `
    --type exe `
    --name Beacon `
    --app-version $MsiVersion `
    --vendor Beacon `
    --description "VLESS Reality client" `
    --input $InputDir `
    --main-jar desktop.jar `
    --main-class app.beacon.desktop.BeaconDesktopKt `
    --icon $IconPath `
    --dest $TempOutput `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --win-upgrade-uuid "6B9A3E90-9B7E-4D1D-9C32-5E3F3A6E4F51" `
    --java-options "-Dfile.encoding=UTF-8"

$Installer = Get-ChildItem $TempOutput -Filter "*.exe" | Select-Object -First 1
if ($null -eq $Installer) {
    throw "installer exe not found"
}

$Target = Join-Path $ReleaseDir "Beacon-Windows-v$CleanVersion.exe"
Copy-Item $Installer.FullName $Target -Force
Write-Output $Target
