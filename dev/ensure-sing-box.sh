#!/usr/bin/env bash
# Downloads the latest sing-box binary for the current OS and arch.
# Usage: ensure-sing-box.sh <destination>
set -euo pipefail

DEST="${1:-sing-box}"

OS="$(uname -s)"
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64)  ARCH_TAG="amd64" ;;
  aarch64|arm64) ARCH_TAG="arm64" ;;
  *) echo "Unsupported arch: $ARCH" >&2; exit 1 ;;
esac

case "$OS" in
  Linux) OS_TAG="linux" ;;
  Darwin) OS_TAG="darwin" ;;
  *) echo "Unsupported OS: $OS" >&2; exit 1 ;;
esac

AUTH_HEADER=()
if [ -n "${GITHUB_TOKEN:-}" ]; then
  AUTH_HEADER=(-H "Authorization: Bearer $GITHUB_TOKEN")
fi

VERSION="$(curl -fsSL "${AUTH_HEADER[@]}" https://api.github.com/repos/SagerNet/sing-box/releases/latest \
  | grep '"tag_name"' | sed 's/.*"v\([^"]*\)".*/\1/')"

URL="https://github.com/SagerNet/sing-box/releases/download/v${VERSION}/sing-box-${VERSION}-${OS_TAG}-${ARCH_TAG}.tar.gz"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading sing-box v${VERSION} (${OS_TAG}-${ARCH_TAG})..."
curl -fsSL "$URL" -o "$TMP/sing-box.tar.gz"
tar -xzf "$TMP/sing-box.tar.gz" -C "$TMP"
cp "$TMP/sing-box-${VERSION}-${OS_TAG}-${ARCH_TAG}/sing-box" "$DEST"
chmod +x "$DEST"
echo "sing-box installed: $DEST"
