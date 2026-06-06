#!/usr/bin/env bash
# Builds Beacon for Linux using jpackage (app-image) and produces a tarball.
# Usage: package-linux.sh [version]
# Requires: JDK 17+, jpackage (bundled with JDK), curl, tar
set -euo pipefail

VERSION="${1:-0.5.0}"
VERSION="${VERSION#v}"

REPO="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$REPO/build"
PACKAGE_DIR="$BUILD_DIR/linux-package"
INPUT_DIR="$PACKAGE_DIR/input"
OUTPUT_DIR="$PACKAGE_DIR/output"
RUNTIME_DIR="$PACKAGE_DIR/runtime"
RELEASE_DIR="$BUILD_DIR/release"
LIB_DIR="$REPO/desktop/build/install/desktop/lib"

# Минимальный набор JDK-модулей: java.desktop для Swing/AWT/FlatLaf,
# jdk.unsupported для sun.misc.Unsafe (JNA), jdk.crypto.ec для X25519
# (WarpManager), java.naming для TLS, jdk.localedata для локалей.
JLINK_MODULES="java.base,java.desktop,java.logging,java.naming,java.management,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.zipfs"

# SerialGC + ограничения metaspace/code cache удерживают рабочий набор
# в ~80-120 МБ. DisableExplicitGC блокирует System.gc() из библиотек.
# MinHeapFreeRatio/Max - JVM возвращает свободный heap в ОС при простое.
JAVA_OPTIONS="-Dfile.encoding=UTF-8 -Xmx128m -Xms16m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:+DisableExplicitGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20"

rm -rf "$INPUT_DIR" "$OUTPUT_DIR" "$RUNTIME_DIR"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR" "$RELEASE_DIR"

if [ ! -f "$LIB_DIR/desktop.jar" ]; then
  echo "Running installDist first..."
  "$REPO/gradlew" --project-dir "$REPO" :core:test :desktop:test :desktop:installDist
fi

cp -r "$LIB_DIR"/. "$INPUT_DIR/"

"$REPO/dev/ensure-sing-box.sh" "$INPUT_DIR/sing-box"

ICON="$REPO/desktop/src/main/resources/icon.png"

# Урезанный runtime через jlink (~50 МБ вместо ~200 МБ полного JDK).
jlink \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=2 \
  --add-modules "$JLINK_MODULES" \
  --output "$RUNTIME_DIR"

jpackage \
  --type app-image \
  --name Beacon \
  --app-version "$VERSION" \
  --vendor Beacon \
  --description "VLESS Reality client" \
  --input "$INPUT_DIR" \
  --runtime-image "$RUNTIME_DIR" \
  --main-jar desktop.jar \
  --main-class app.beacon.desktop.BeaconDesktopKt \
  --icon "$ICON" \
  --dest "$OUTPUT_DIR" \
  --java-options "$JAVA_OPTIONS"

TARBALL="$RELEASE_DIR/Beacon-Linux-v$VERSION.tar.gz"
tar -czf "$TARBALL" -C "$OUTPUT_DIR" Beacon

echo "$TARBALL"
