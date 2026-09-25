#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="8.9"

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

DIST_DIR="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
DIST_ZIP="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
DIST_HOME="$DIST_DIR/gradle-${GRADLE_VERSION}"

if [ ! -x "$DIST_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$DIST_ZIP" ]; then
    URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    echo "Gradle not installed; downloading ${URL}" >&2
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$DIST_ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$DIST_ZIP" "$URL"
    else
      echo "Neither curl nor wget is available." >&2
      exit 1
    fi
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$DIST_ZIP" -d "$DIST_DIR"
  else
    echo "unzip is required to install Gradle." >&2
    exit 1
  fi
fi

exec "$DIST_HOME/bin/gradle" "$@"
