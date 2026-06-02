#!/usr/bin/env bash
# Builds Beacon for macOS using jpackage and produces a DMG.
# Usage: package-macos.sh [version] [arch-label]
# Requires: JDK 17+, jpackage, sips, iconutil, curl
set -euo pipefail

VERSION="${1:-0.2.0}"
VERSION="${VERSION#v}"
ARCH_LABEL="${2:-$(uname -m)}"

case "$ARCH_LABEL" in
  x86_64|amd64|x64) ARCH_LABEL="x64" ;;
  arm64|aarch64) ARCH_LABEL="arm64" ;;
esac

IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"
if [ "${MAJOR:-0}" -le 0 ]; then
  PACKAGE_VERSION="1.${MINOR:-0}.${PATCH:-0}"
else
  PACKAGE_VERSION="$VERSION"
fi

REPO="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$REPO/build"
PACKAGE_DIR="$BUILD_DIR/macos-package-$ARCH_LABEL"
INPUT_DIR="$PACKAGE_DIR/input"
OUTPUT_DIR="$PACKAGE_DIR/output"
RUNTIME_DIR="$PACKAGE_DIR/runtime"
ICONSET_DIR="$PACKAGE_DIR/icon.iconset"
ICON_ICNS="$PACKAGE_DIR/icon.icns"
RELEASE_DIR="$BUILD_DIR/release"
LIB_DIR="$REPO/desktop/build/install/desktop/lib"

JLINK_MODULES="java.base,java.desktop,java.logging,java.naming,java.management,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.zipfs"
JAVA_OPTIONS="-Dfile.encoding=UTF-8 -Xmx128m -Xms16m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:+DisableExplicitGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20"

rm -rf "$INPUT_DIR" "$OUTPUT_DIR" "$RUNTIME_DIR" "$ICONSET_DIR" "$ICON_ICNS"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR" "$RELEASE_DIR" "$ICONSET_DIR"

if [ ! -f "$LIB_DIR/desktop.jar" ]; then
  "$REPO/gradlew" --project-dir "$REPO" :core:test :desktop:test :desktop:installDist
fi

cp -r "$LIB_DIR"/. "$INPUT_DIR/"
"$REPO/dev/ensure-sing-box.sh" "$INPUT_DIR/sing-box"

ICON_PNG="$REPO/desktop/src/main/resources/icon.png"
sips -z 16 16 "$ICON_PNG" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null
sips -z 32 32 "$ICON_PNG" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null
sips -z 32 32 "$ICON_PNG" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null
sips -z 64 64 "$ICON_PNG" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null
sips -z 128 128 "$ICON_PNG" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null
sips -z 256 256 "$ICON_PNG" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null
sips -z 256 256 "$ICON_PNG" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null
sips -z 512 512 "$ICON_PNG" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null
sips -z 512 512 "$ICON_PNG" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null
sips -z 1024 1024 "$ICON_PNG" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null
iconutil -c icns "$ICONSET_DIR" -o "$ICON_ICNS"

jlink \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=2 \
  --add-modules "$JLINK_MODULES" \
  --output "$RUNTIME_DIR"

jpackage \
  --type dmg \
  --name Beacon \
  --app-version "$PACKAGE_VERSION" \
  --vendor Beacon \
  --description "VLESS Reality client" \
  --input "$INPUT_DIR" \
  --runtime-image "$RUNTIME_DIR" \
  --main-jar desktop.jar \
  --main-class app.beacon.desktop.BeaconDesktopKt \
  --icon "$ICON_ICNS" \
  --dest "$OUTPUT_DIR" \
  --mac-package-name Beacon \
  --mac-package-identifier app.beacon.desktop \
  --java-options "$JAVA_OPTIONS"

DMG="$(find "$OUTPUT_DIR" -maxdepth 1 -name '*.dmg' -print -quit)"
if [ -z "$DMG" ]; then
  echo "DMG not found" >&2
  exit 1
fi

TARGET="$RELEASE_DIR/Beacon-macOS-$ARCH_LABEL-v$VERSION.dmg"
cp "$DMG" "$TARGET"
echo "$TARGET"
