#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle wrapper has not been generated yet. Run: gradle wrapper --gradle-version 9.6.0 --distribution-type bin" >&2
exit 1
