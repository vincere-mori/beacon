param(
    [string]$Version = "0.5.0",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$Repo = Resolve-Path (Join-Path $PSScriptRoot "..")

$InstallerPath = & (Join-Path $PSScriptRoot "package-windows.ps1") -Version $Version -SkipBuild:$SkipBuild

if (!(Test-Path $InstallerPath)) {
    throw "installer not found: $InstallerPath"
}

Write-Host "Installing $InstallerPath ..." -ForegroundColor Cyan

# /install — install or upgrade (не repair/remove)
# /passive  — только прогресс-бар, без кликов
# /norestart — не перезагружать
$proc = Start-Process -FilePath $InstallerPath `
    -ArgumentList "/install", "/passive", "/norestart" `
    -Wait -PassThru

if ($proc.ExitCode -ne 0) {
    throw "installer exited with code $($proc.ExitCode)"
}

Write-Host "Done." -ForegroundColor Green
