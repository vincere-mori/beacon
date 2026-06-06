@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0.."

set "VERSION=%~1"
if "%VERSION%"=="" set "VERSION=0.5.0"
set "VERSION=%VERSION:v=%"

echo ============================================================
echo  Beacon Windows build  v%VERSION%
echo ============================================================

echo.
echo [1/3] Ensuring sing-box.exe...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-sing-box.ps1"
if errorlevel 1 goto :fail

echo.
echo [2/3] Running tests...
call ".\gradlew.bat" :core:test :desktop:test --console=plain
if errorlevel 1 goto :fail

echo.
echo [3/3] Packaging installer...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package-windows.ps1" -Version %VERSION%
if errorlevel 1 goto :fail

echo.
echo ============================================================
echo  DONE
echo ============================================================
dir /b "build\release\Beacon-Windows-v%VERSION%.exe" 2>nul
echo Installer: build\release\Beacon-Windows-v%VERSION%.exe
echo.
explorer "build\release"
exit /b 0

:fail
echo.
echo ============================================================
echo  BUILD FAILED
echo ============================================================
pause
exit /b 1
