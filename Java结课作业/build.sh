#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

JAVAC_VERSION="$(javac -version 2>&1 | awk '{print $2}')"
echo "==> 使用 JDK $JAVAC_VERSION 构建（目标字节码 Java 8）"
echo "==> 清理输出目录"
rm -rf out/classes out/test-classes dist
mkdir -p out/classes out/test-classes dist

JAVAC_FLAGS=(-encoding UTF-8 -Xlint:-serial -d out/classes)
case "$JAVAC_VERSION" in
  1.8*)
    JAVAC_FLAGS+=(-source 1.8 -target 1.8)
    ;;
  *)
    JAVAC_FLAGS+=(--release 8)
    ;;
esac

echo "==> 编译主程序"
find src/main/java -name '*.java' > out/main-sources.txt
javac "${JAVAC_FLAGS[@]}" @out/main-sources.txt

echo "==> 编译自检程序"
javac -encoding UTF-8 -Xlint:-serial -cp out/classes -d out/test-classes \
  $(find src/test/java -name '*.java')

echo "==> 生成可执行 JAR"
cat > out/MANIFEST-client.txt <<'MANIFEST'
Manifest-Version: 1.0
Main-Class: com.mine.client.MainClient

MANIFEST
cat > out/MANIFEST-gateway.txt <<'MANIFEST'
Manifest-Version: 1.0
Main-Class: com.mine.gateway.GatewayMain

MANIFEST
jar cfm dist/mine-client.jar out/MANIFEST-client.txt -C out/classes .
jar cfm dist/mine-gateway.jar out/MANIFEST-gateway.txt -C out/classes .

echo ""
echo "构建完成："
echo "  dist/mine-client.jar    GUI 客户端"
echo "  dist/mine-gateway.jar   仿真网关"
echo "  out/classes             直接以 classpath 运行"
echo ""
echo "启动网关： ./run-gateway.sh"
echo "启动客户端：./run-client.sh 或 ./run-client-pc.sh <本机IP> <网关IP>"
echo "网络预检： ./check-network.sh --host <网关IP> --local-ip <本机IP>"
echo "运行自检： ./run-tests.sh"
