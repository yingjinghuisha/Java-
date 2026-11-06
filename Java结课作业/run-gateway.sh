#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
if [ ! -f dist/mine-gateway.jar ] || [ -n "$(find src/main/java -newer dist/mine-gateway.jar -print -quit)" ]; then
  ./build.sh
fi
exec java -jar dist/mine-gateway.jar "$@"
