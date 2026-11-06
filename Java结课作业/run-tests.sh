#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

./build.sh

CP="out/classes:out/test-classes"
echo ""
echo "==> 1/3 协议、CRC、拆包、并发容器、线程池"
java -cp "$CP" com.mine.ProtocolSelfTest
echo ""
echo "==> 2/3 业务层：告警边沿 / 恢复 / ACK / 离线"
java -Djava.awt.headless=true -cp "$CP" com.mine.ServiceSelfTest
echo ""
echo "==> 3/3 端到端：TCP / 指令 ACK / 脏报文 / 断线重连"
java -Djava.awt.headless=true -cp "$CP" com.mine.GatewayClientIntegrationTest
echo ""
echo "全部自检通过。"
