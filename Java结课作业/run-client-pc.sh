#!/usr/bin/env bash
set -euo pipefail
if [ $# -lt 1 ]; then
  echo "用法: ./run-client-pc.sh <本机工控机IP> [网关IP] [端口]"
  echo "示例: ./run-client-pc.sh 172.20.205.11 172.20.205.10 8888"
  exit 1
fi
ROOT="$(cd "$(dirname "$0")" && pwd)"
LOCAL_IP="$1"
GATEWAY_IP="${2:-127.0.0.1}"
PORT="${3:-8888}"
exec "$ROOT/run-client.sh" --host "$GATEWAY_IP" --port "$PORT" --local-ip "$LOCAL_IP" --auto-connect
