package com.mine.client.tools;

import com.mine.common.IpRange;
import com.mine.common.MinePacket;
import com.mine.common.PacketStreamDecoder;
import com.mine.common.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 工控机网络预检：检查 IP 区间、本机 IP 是否配置、TCP 连接、心跳 ACK。
 *
 * <pre>
 * java -cp out/classes com.mine.client.tools.NetworkPreflight \
 *      --host 172.20.205.10 --port 8888 --local-ip 172.20.205.11
 * </pre>
 */
public final class NetworkPreflight {

    private NetworkPreflight() {
    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = Protocol.DEFAULT_PORT;
        String localIp = "";
        int timeout = 5000;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--host":
                        host = require(args, ++i, "--host");
                        break;
                    case "--port":
                        port = Integer.parseInt(require(args, ++i, "--port"));
                        break;
                    case "--local-ip":
                        localIp = require(args, ++i, "--local-ip");
                        break;
                    case "--timeout":
                        timeout = Integer.parseInt(require(args, ++i, "--timeout"));
                        break;
                    case "--help":
                    case "-h":
                        usage();
                        return;
                    default:
                        fail(2, "未知参数: " + args[i]);
                        return;
                }
            }
        } catch (RuntimeException e) {
            fail(2, "参数错误: " + e.getMessage());
            return;
        }

        String hostError = IpRange.validateHost(host);
        if (hostError != null) {
            fail(2, "网关地址不合法: " + hostError);
            return;
        }
        String localError = IpRange.validateLocalIp(localIp);
        if (localError != null) {
            fail(2, "本机 IP 不合法: " + localError);
            return;
        }
        if (port < 1 || port > 65535) {
            fail(2, "端口必须在 1~65535 之间");
            return;
        }
        if (timeout < 500) {
            timeout = 500;
        }

        System.out.println("==================================================");
        System.out.println(" 工控机网络预检");
        System.out.println(" 允许网关网段 : " + IpRange.rangeText() + "（或 127.0.0.1/localhost）");
        System.out.println(" 目标网关     : " + host + ":" + port);
        System.out.println(" 本机源 IP    : " + (localIp.isEmpty() ? "自动（系统路由）" : localIp));
        System.out.println(" 本机 IPv4    : " + join(localIpv4Addresses()));
        System.out.println("==================================================");

        if (!localIp.isEmpty() && !isAssigned(localIp)) {
            fail(3, "本机 IP " + localIp + " 未配置在任何已启用网卡上。"
                    + "请先在操作系统中设置静态 IP，或留空使用自动源地址。");
            return;
        }

        Socket socket = new Socket();
        try {
            if (!localIp.isEmpty()) {
                socket.bind(new InetSocketAddress(InetAddress.getByName(localIp), 0));
            }
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);

            InetAddress actualAddress = socket.getLocalAddress();
            String actual = actualAddress.getHostAddress();
            if (!actualAddress.isLoopbackAddress() && !IpRange.isInRange(actual)) {
                fail(4, "实际源 IP " + actual + " 不在允许范围。"
                        + "请在【本机IP】填写已配置的工控机地址。");
                return;
            }
            if (!localIp.isEmpty() && !actualAddress.equals(InetAddress.getByName(localIp))) {
                fail(4, "实际源 IP " + actual + " 与指定本机 IP " + localIp + " 不一致。");
                return;
            }
            System.out.println("[OK] TCP 已连接，实际源 IP = " + actual
                    + "，远端 = " + socket.getRemoteSocketAddress());

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            MinePacket heartbeat = Protocol.heartbeat();
            out.write(heartbeat.encode());
            out.flush();

            PacketStreamDecoder decoder = new PacketStreamDecoder();
            List<MinePacket> packets = new ArrayList<MinePacket>();
            byte[] buffer = new byte[4096];
            long deadline = System.currentTimeMillis() + timeout;
            boolean ack = false;
            int frames = 0;
            while (System.currentTimeMillis() < deadline) {
                int read;
                try {
                    read = in.read(buffer);
                } catch (SocketTimeoutException e) {
                    break;
                }
                if (read < 0) {
                    break;
                }
                packets.clear();
                decoder.feed(buffer, 0, read, packets);
                frames += packets.size();
                for (MinePacket packet : packets) {
                    if (packet.isAck() && packet.getDeviceId() == Protocol.DEVICE_BROADCAST
                            && packet.getValue() >= 0.5F) {
                        ack = true;
                    }
                }
                if (ack) {
                    break;
                }
            }
            if (!ack) {
                fail(5, "TCP 已连接，但 " + timeout + " ms 内未收到心跳 ACK；"
                        + "已接收 " + frames + " 帧，CRC错误=" + decoder.getCrcErrors()
                        + "。请检查网关是否运行在 8888 端口。");
                return;
            }
            System.out.println("[OK] 心跳 ACK 成功，已接收 " + frames + " 帧，CRC错误="
                    + decoder.getCrcErrors());
            System.out.println("[PASS] 网络预检通过，可以启动 GUI 客户端。");
        } catch (BindException e) {
            fail(3, "本机 IP 绑定失败: " + e.getMessage()
                    + "。请确认该 IP 已配置在网卡上，且没有被其他程序占用。");
        } catch (IOException e) {
            fail(4, "TCP 连接失败: " + e.getMessage()
                    + "。请检查网关是否启动、IP/端口是否正确、防火墙是否放行。");
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private static void usage() {
        System.out.println("用法: java -cp out/classes com.mine.client.tools.NetworkPreflight [options]");
        System.out.println("  --host <ip>       网关 IP，默认 127.0.0.1");
        System.out.println("  --port <port>     网关端口，默认 8888");
        System.out.println("  --local-ip <ip>   本机工控机 IP；留空自动使用系统源地址");
        System.out.println("  --timeout <ms>    连接/心跳超时，默认 5000");
        System.out.println("  --help            显示帮助");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  ./check-network.sh --host 172.20.205.10 --port 8888 --local-ip 172.20.205.11");
    }

    private static String require(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " 缺少参数值");
        }
        return args[index];
    }

    private static List<String> localIpv4Addresses() {
        List<String> result = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        result.add(address.getHostAddress() + " (" + networkInterface.getName() + ")");
                    }
                }
            }
        } catch (Exception e) {
            result.add("无法枚举: " + e.getMessage());
        }
        Collections.sort(result);
        return result;
    }

    private static boolean isAssigned(String ip) {
        if (IpRange.LOCALHOST.equals(ip)) {
            return true;
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    if (addresses.nextElement().getHostAddress().equals(ip)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static void fail(int code, String message) {
        System.out.println("[FAIL] " + message);
        System.exit(code);
    }
}
