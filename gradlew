#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$APP_HOME"

GRADLE_VERSION="8.9"
DIST_DIR="$APP_HOME/.gradle-dist/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/bin/gradle"
ZIP_FILE="$APP_HOME/.gradle-dist/gradle-$GRADLE_VERSION-bin.zip"

if [ -x "$GRADLE_BIN" ]; then
  exec "$GRADLE_BIN" "$@"
fi

mkdir -p "$APP_HOME/.gradle-dist"

if [ ! -f "$ZIP_FILE" ]; then
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  echo "Downloading Gradle $GRADLE_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --connect-timeout 20 -o "$ZIP_FILE" "$URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_FILE" "$URL"
  else
    echo "ERROR: curl or wget is required to download Gradle $GRADLE_VERSION." >&2
    exit 2
  fi
fi

if [ ! -d "$DIST_DIR" ]; then
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP_FILE" -d "$APP_HOME/.gradle-dist"
  else
    echo "ERROR: unzip is required to extract Gradle." >&2
    exit 2
  fi
fi

exec "$GRADLE_BIN" "$@"
