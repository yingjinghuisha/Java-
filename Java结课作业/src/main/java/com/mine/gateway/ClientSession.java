package com.mine.gateway;

import com.mine.common.MinePacket;
import com.mine.common.PacketStreamDecoder;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 一个上位机客户端对应的会话：独立接收线程 + 独立发送线程 + 有界发送队列。
 */
public class ClientSession {

    private static final int SEND_QUEUE_CAPACITY = 2048;

    private final GatewayServer server;
    private final Socket socket;
    private final int sessionId;
    private final String remoteAddress;
    private final long connectedAt = System.currentTimeMillis();
    private final BlockingQueue<MinePacket> sendQueue =
            new LinkedBlockingQueue<MinePacket>(SEND_QUEUE_CAPACITY);
    private final AtomicBoolean open = new AtomicBoolean(true);

    private final AtomicLong receivedPackets = new AtomicLong();
    private final AtomicLong sentPackets = new AtomicLong();
    private final AtomicLong droppedNetwork = new AtomicLong();
    private final AtomicLong droppedQueue = new AtomicLong();
    private final AtomicLong crcErrors = new AtomicLong();
    private final PacketStreamDecoder decoder = new PacketStreamDecoder();

    private Thread readerThread;
    private Thread senderThread;

    ClientSession(GatewayServer server, Socket socket, int sessionId) {
        this.server = server;
        this.socket = socket;
        this.sessionId = sessionId;
        this.remoteAddress = socket.getRemoteSocketAddress() == null
                ? "unknown" : socket.getRemoteSocketAddress().toString();
    }

    public void start() {
        readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "gateway-reader-" + sessionId);
        readerThread.setDaemon(true);

        senderThread = new Thread(new GatewayPacketSender(this), "gateway-sender-" + sessionId);
        senderThread.setDaemon(true);

        readerThread.start();
        senderThread.start();
    }

    private void readLoop() {
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            InputStream input = new BufferedInputStream(socket.getInputStream(), 8192);
            byte[] buffer = new byte[8192];
            List<MinePacket> packets = new ArrayList<MinePacket>(16);
            while (isOpen()) {
                int read = input.read(buffer);
                if (read < 0) {
                    close("客户端主动断开");
                    return;
                }
                if (read == 0) {
                    continue;
                }
                packets.clear();
                decoder.feed(buffer, 0, read, packets);
                long crc = decoder.getCrcErrors();
                if (crc > crcErrors.get()) {
                    long delta = crc - crcErrors.get();
                    crcErrors.addAndGet(delta);
                    if (crcErrors.get() <= 3 || crcErrors.get() % 50 == 0) {
                        GatewayLog.warn("SESSION", "客户端#" + sessionId + " CRC 校验失败累计 "
                                + crcErrors.get() + " 帧，已丢弃");
                    }
                }
                for (MinePacket packet : packets) {
                    receivedPackets.incrementAndGet();
                    server.handlePacket(this, packet);
                }
            }
        } catch (SocketException e) {
            if (isOpen()) {
                close("连接异常: " + e.getMessage());
            }
        } catch (IOException e) {
            if (isOpen()) {
                close("接收异常: " + e.getMessage());
            }
        } catch (RuntimeException e) {
            if (isOpen()) {
                close("会话处理异常: " + e.getMessage());
            }
        }
    }

    /**
     * 广播数据：按网关丢包率随机丢弃，再进入有界队列。
     * @return true 表示成功入队
     */
    boolean enqueue(MinePacket packet, int dropPercent) {
        if (!open.get()) {
            return false;
        }
        if (dropPercent > 0 && ThreadLocalRandom.current().nextInt(100) < dropPercent) {
            droppedNetwork.incrementAndGet();
            return false;
        }
        return enqueueInternal(packet);
    }

    /** 应答报文不参与随机丢包，尽量保证控制可靠性。 */
    void enqueueDirect(MinePacket packet) {
        if (open.get()) {
            enqueueInternal(packet);
        }
    }

    private boolean enqueueInternal(MinePacket packet) {
        if (sendQueue.offer(packet)) {
            return true;
        }
        droppedQueue.incrementAndGet();
        return false;
    }

    public void close(String reason) {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // 关闭失败无需处理
        }
        if (senderThread != null) {
            senderThread.interrupt();
        }
        server.removeSession(this);
        GatewayLog.info("SESSION", "客户端#" + sessionId + " 已断开（" + reason + "），累计发送 "
                + sentPackets.get() + " 包");
    }

    void recordSent() {
        sentPackets.incrementAndGet();
    }

    public boolean isOpen() {
        return open.get();
    }

    public Socket getSocket() {
        return socket;
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public BlockingQueue<MinePacket> getSendQueue() {
        return sendQueue;
    }

    public long getReceivedPackets() {
        return receivedPackets.get();
    }

    public long getSentPackets() {
        return sentPackets.get();
    }

    public long getDroppedNetwork() {
        return droppedNetwork.get();
    }

    public long getDroppedQueue() {
        return droppedQueue.get();
    }

    public long getCrcErrors() {
        return crcErrors.get();
    }

    public int getSendQueueSize() {
        return sendQueue.size();
    }
}
