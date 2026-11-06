package com.mine.common;

import java.util.List;

/**
 * TCP 字节流拆包器（解决粘包/半包）。
 *
 * <p>TCP 是字节流，不保留消息边界。本类维护一个可增长的接收缓冲区：
 * 先查找帧头 0xAA55，凑满 19 字节后做 CRC 校验；校验失败则从下一字节
 * 重新寻找帧头（resync），不会因为一段脏数据导致后续正常帧全部丢失。</p>
 *
 * <p>本类非线程安全；每个 Socket 连接应持有独立实例，仅由该连接的
 * 接收线程调用。</p>
 */
public final class PacketStreamDecoder {

    private byte[] buffer = new byte[1024];
    private int start;
    private int end;

    private long validPackets;
    private long crcErrors;
    private long invalidFrames;
    private long garbageBytes;
    private long resyncs;

    /**
     * 喂入一段字节流，解析出的完整报文追加到 out。
     *
     * @return 本次新增的合法报文数量
     */
    public int feed(byte[] data, int offset, int length, List<MinePacket> out) {
        if (data == null || length <= 0) {
            return 0;
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException("feed 参数越界");
        }
        ensureCapacity(end + length);
        System.arraycopy(data, offset, buffer, end, length);
        end += length;

        int before = out.size();
        while (true) {
            int headerPos = findHeader(start, end);
            if (headerPos < 0) {
                // 没有帧头：保留末尾可能是半个帧头的 1 个字节，其余全部丢弃
                int keep = (end > start && (buffer[end - 1] & 0xFF) == Protocol.HEADER_0) ? 1 : 0;
                int discard = end - start - keep;
                if (discard > 0) {
                    garbageBytes += discard;
                    start += discard;
                }
                break;
            }
            if (headerPos > start) {
                garbageBytes += headerPos - start;
                start = headerPos;
            }
            if (end - start < Protocol.PACKET_LENGTH) {
                break; // 半包，等待下一次 feed
            }
            try {
                MinePacket packet = MinePacket.decode(buffer, start, Protocol.PACKET_LENGTH);
                out.add(packet);
                validPackets++;
                start += Protocol.PACKET_LENGTH;
            } catch (CrcMismatchException e) {
                crcErrors++;
                resyncs++;
                start++; // CRC 错误：丢弃当前字节，继续向后寻找帧头
            } catch (PacketFormatException e) {
                invalidFrames++;
                resyncs++;
                start++;
            }
        }
        compact();
        return out.size() - before;
    }

    /** 在没有更多输入时调用，返回因半包而滞留的字节数。 */
    public int bufferedBytes() {
        return end - start;
    }

    public long getValidPackets() {
        return validPackets;
    }

    public long getCrcErrors() {
        return crcErrors;
    }

    public long getInvalidFrames() {
        return invalidFrames;
    }

    public long getGarbageBytes() {
        return garbageBytes;
    }

    public long getResyncs() {
        return resyncs;
    }

    public void resetStats() {
        validPackets = 0;
        crcErrors = 0;
        invalidFrames = 0;
        garbageBytes = 0;
        resyncs = 0;
    }

    /** 连接重建时清空半包缓存。 */
    public void reset() {
        start = 0;
        end = 0;
    }

    private int findHeader(int from, int to) {
        for (int i = from; i + 1 < to; i++) {
            if ((buffer[i] & 0xFF) == Protocol.HEADER_0
                    && (buffer[i + 1] & 0xFF) == Protocol.HEADER_1) {
                return i;
            }
        }
        return -1;
    }

    private void compact() {
        if (start == 0) {
            return;
        }
        int remain = end - start;
        if (remain > 0) {
            System.arraycopy(buffer, start, buffer, 0, remain);
        }
        start = 0;
        end = remain;
    }

    private void ensureCapacity(int needed) {
        if (needed <= buffer.length) {
            return;
        }
        int newLength = buffer.length;
        while (newLength < needed) {
            newLength <<= 1;
        }
        byte[] enlarged = new byte[newLength];
        System.arraycopy(buffer, 0, enlarged, 0, end);
        buffer = enlarged;
    }
}
