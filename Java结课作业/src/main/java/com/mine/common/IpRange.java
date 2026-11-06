package com.mine.common;

/**
 * 客户端允许连接的网关地址范围。
 *
 * <p>课程实验网段：{@code 172.20.205.0 ~ 172.20.224.255}（包含两端），
 * 本机联调额外允许 {@code 127.0.0.1} 与 {@code localhost}。
 * 该限制只作用于客户端“连接网关”的输入；网关仍可绑定 127.0.0.1、
 * 0.0.0.0 或本机实际配置的任意地址。</p>
 */
public final class IpRange {

    public static final String START = "172.20.205.0";
    public static final String END = "172.20.224.255";
    public static final String LOCALHOST = "127.0.0.1";

    private static final long START_VALUE = parseOrThrow(START);
    private static final long END_VALUE = parseOrThrow(END);
    private static final long LOOPBACK_VALUE = parseOrThrow(LOCALHOST);

    private IpRange() {
    }

    /**
     * 是否为允许的连接目标：本机回环、localhost，或给定网段内的 IPv4。
     */
    public static boolean isAllowedHost(String host) {
        return validateHost(host) == null;
    }

    /**
     * 校验连接目标，合法返回 null，否则返回可直接展示给用户的错误信息。
     */
    public static String validateHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return "请输入网关 IP 或主机名";
        }
        String value = host.trim();
        if ("localhost".equalsIgnoreCase(value)) {
            return null;
        }
        long address = parseIpv4OrNegative(value);
        if (address < 0) {
            return "请输入 IPv4 地址（或 localhost）；仅允许连接 "
                    + rangeText() + "，本机调试可用 " + LOCALHOST;
        }
        if (address == LOOPBACK_VALUE) {
            return null;
        }
        if (address < START_VALUE || address > END_VALUE) {
            return "该 IP 不在允许范围内：仅允许 " + rangeText()
                    + "，本机调试可用 " + LOCALHOST + "/localhost";
        }
        return null;
    }

    /**
     * 校验可选的“本机工控机 IP”：空白表示自动使用系统源地址，允许；
     * 非空时必须是网段内 IPv4，或本机回环 127.0.0.1。
     */
    public static String validateLocalIp(String localIp) {
        if (localIp == null || localIp.trim().isEmpty()) {
            return null;
        }
        String value = localIp.trim();
        if ("localhost".equalsIgnoreCase(value)) {
            return "本机 IP 请填写 IPv4 地址；localhost 仅用于网关地址";
        }
        long address = parseIpv4OrNegative(value);
        if (address < 0) {
            return "本机 IP 格式错误，请填写 IPv4 地址，或留空自动使用系统源地址";
        }
        if (address == LOOPBACK_VALUE) {
            return null;
        }
        if (!inRange(address)) {
            return "本机 IP 不在允许范围：应为 " + rangeText()
                    + "，或 127.0.0.1；留空则自动使用系统源地址";
        }
        return null;
    }

    /** 判断字符串是否位于允许的实验网段（不包含回环例外）。 */
    public static boolean isInRange(String ip) {
        long address = parseIpv4OrNegative(ip);
        return address >= 0 && inRange(address);
    }

    private static boolean inRange(long address) {
        return address >= START_VALUE && address <= END_VALUE;
    }

    /** 判断字符串是否为合法的点分十进制 IPv4（0~255，四段）。 */
    public static boolean isIpv4(String text) {
        return parseIpv4OrNegative(text) >= 0;
    }

    /** 将 IPv4 转为无符号 32 位 long；非法返回 -1。 */
    public static long parseIpv4OrNegative(String text) {
        if (text == null) {
            return -1L;
        }
        String value = text.trim();
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return -1L;
        }
        long result = 0L;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return -1L;
            }
            for (int i = 0; i < part.length(); i++) {
                char ch = part.charAt(i);
                if (ch < '0' || ch > '9') {
                    return -1L;
                }
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return -1L;
            }
            if (octet < 0 || octet > 255) {
                return -1L;
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    private static long parseOrThrow(String text) {
        long value = parseIpv4OrNegative(text);
        if (value < 0) {
            throw new IllegalStateException("非法 IP 常量: " + text);
        }
        return value;
    }

    public static String rangeText() {
        return START + " ~ " + END;
    }

    /** 控制条上的紧凑提示。 */
    public static String shortRangeText() {
        return "允许 172.20.205–172.20.224";
    }
}
