#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
if [ ! -f out/classes/com/mine/client/tools/NetworkPreflight.class ] || [ -n "$(find src/main/java -newer out/classes/com/mine/client/tools/NetworkPreflight.class -print -quit 2>/dev/null)" ]; then
  ./build.sh
fi
exec java -cp out/classes com.mine.client.tools.NetworkPreflight "$@"
