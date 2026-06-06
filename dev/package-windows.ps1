param(
    [string]$Version = "0.5.0",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$Repo = Resolve-Path (Join-Path $PSScriptRoot "..")
$CleanVersion = $Version.TrimStart("v")
if ($CleanVersion -notmatch "^\d+\.\d+\.\d+$") {
    $CleanVersion = "0.5.0"
}

# MSI version: embed a day-stamp in the patch field (month*31+day = 32..403).
# This makes every new build "newer" than the previous one so the installer
# always replaces files on reinstall, even when the semver hasn't changed.
# The user-facing filename keeps the clean semver (e.g. v0.5.0).
$Major, $Minor = $CleanVersion -split '\.' | Select-Object -First 2
$Now = Get-Date
$DayStamp = $Now.Month * 31 + $Now.Day
$MsiVersion = "$Major.$Minor.$DayStamp"

$BuildDir = Join-Path $Repo "build"
$ToolsDir = Join-Path $BuildDir "tools"
$PackageDir = Join-Path $BuildDir "windows-package"
$InputDir = Join-Path $PackageDir "input"
$RuntimeDir = Join-Path $PackageDir "runtime"
$ReleaseDir = Join-Path $BuildDir "release"
$IconPath = Join-Path $Repo "desktop\src\main\resources\icon.ico"

# Минимальный набор JDK-модулей: java.desktop для Swing/AWT/FlatLaf,
# jdk.unsupported для sun.misc.Unsafe (JNA), jdk.crypto.ec для X25519
# (WarpManager), java.naming для TLS handshake (HttpsURLConnection),
# jdk.localedata для локалей. Этого достаточно для текущего функционала.
$JlinkModules = "java.base,java.desktop,java.logging,java.naming,java.management,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.zipfs"

# JVM-флаги под Swing-приложение с малым heap'ом: SerialGC экономит RSS
# по сравнению с G1, ограничения metaspace/code cache держат рабочий
# набор в пределах ~80-120 МБ. DisableExplicitGC чтобы библиотеки не
# вызывали полную сборку через System.gc(). MinHeapFreeRatio/Max - JVM
# возвращает неиспользуемый heap в ОС когда клиент в простое (RSS падает
# при минимизации/idle до ~60 МБ).
$JavaOptions = "-Dfile.encoding=UTF-8 -Xmx128m -Xms16m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:+DisableExplicitGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20"

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
    & (Join-Path $Repo "gradlew.bat") "--project-dir" $Repo ":core:test" ":desktop:test" ":desktop:installDist"
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

# Собираем урезанный runtime через jlink: вместо полного JDK (~200 МБ)
# получаем образ ~50 МБ только с нужными модулями. jpackage потом
# берёт его через --runtime-image вместо генерации своего.
if (Test-Path $RuntimeDir) { Remove-Item -LiteralPath $RuntimeDir -Recurse -Force }
& jlink `
    --strip-debug `
    --strip-native-commands `
    --no-header-files `
    --no-man-pages `
    --compress=2 `
    --add-modules $JlinkModules `
    --output $RuntimeDir
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

& jpackage `
    --type exe `
    --name Beacon `
    --app-version $MsiVersion `
    --vendor Beacon `
    --description "VLESS Reality client" `
    --input $InputDir `
    --runtime-image $RuntimeDir `
    --main-jar desktop.jar `
    --main-class app.beacon.desktop.BeaconDesktopKt `
    --icon $IconPath `
    --dest $TempOutput `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --win-upgrade-uuid "6B9A3E90-9B7E-4D1D-9C32-5E3F3A6E4F51" `
    --java-options $JavaOptions

$Installer = Get-ChildItem $TempOutput -Filter "*.exe" | Select-Object -First 1
if ($null -eq $Installer) {
    throw "installer exe not found"
}

$Target = Join-Path $ReleaseDir "Beacon-Windows-v$CleanVersion.exe"
Copy-Item $Installer.FullName $Target -Force

# Рядом с инсталлером кладём скрипт тихого апгрейда:
# пользователь запускает update.ps1 — никаких кликов, только прогресс-бар.
# Или администратор/CI делает тихую установку через /install /quiet.
$UpdateScript = @"
# Тихий апгрейд Beacon: запускает инсталлер без визарда.
# Двойной клик на installer.exe показывает визард — этот скрипт его пропускает.
param([switch]`$Quiet)
`$exe = Join-Path `$PSScriptRoot "Beacon-Windows-v$CleanVersion.exe"
`$ui  = if (`$Quiet) { '/quiet' } else { '/passive' }
`$proc = Start-Process -FilePath `$exe ``
    -ArgumentList '/install', `$ui, '/norestart' ``
    -Wait -PassThru
exit `$proc.ExitCode
"@
$UpdateScript | Set-Content (Join-Path $ReleaseDir "update.ps1") -Encoding utf8

Write-Output $Target
