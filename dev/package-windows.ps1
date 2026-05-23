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

$BuildDir = Join-Path $Repo "build"
$PackageDir = Join-Path $BuildDir "windows-package"
$InputDir = Join-Path $PackageDir "input"
$ReleaseDir = Join-Path $BuildDir "release"
$IconPath = Join-Path $Repo "desktop\src\main\resources\icon.ico"

function Reset-Directory($Path) {
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

if (!$SkipBuild) {
    & (Join-Path $Repo "gradlew.bat") ":core:test" ":desktop:test" ":desktop:installDist"
}

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
    --app-version $CleanVersion `
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
    --win-per-user-install `
    --win-upgrade-uuid "6B9A3E90-9B7E-4D1D-9C32-5E3F3A6E4F51" `
    --java-options "-Dfile.encoding=UTF-8"

$Installer = Get-ChildItem $TempOutput -Filter "*.exe" | Select-Object -First 1
if ($null -eq $Installer) {
    throw "installer exe not found"
}

$Target = Join-Path $ReleaseDir "Beacon-Windows-v$CleanVersion.exe"
Copy-Item $Installer.FullName $Target -Force
Write-Output $Target
