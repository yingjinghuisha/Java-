package com.mine.gateway;

import com.mine.common.MinePacket;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 每个客户端连接的发送线程：从有界阻塞队列取报文，编码后写入 Socket。
 * 发送线程与采集线程解耦，慢客户端不会反压传感器线程。
 */
public class GatewayPacketSender implements Runnable {

    private static final long POLL_TIMEOUT_MS = 500L;

    private final ClientSession session;
    private final BlockingQueue<MinePacket> queue;

    GatewayPacketSender(ClientSession session) {
        this.session = session;
        this.queue = session.getSendQueue();
    }

    @Override
    public void run() {
        try {
            OutputStream out = new BufferedOutputStream(session.getSocket().getOutputStream(), 8192);
            while (session.isOpen() && !Thread.currentThread().isInterrupted()) {
                MinePacket packet = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    out.flush();
                    continue;
                }
                out.write(packet.encode());
                session.recordSent();
                if (queue.isEmpty()) {
                    out.flush();
                }
            }
            out.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (session.isOpen()) {
                session.close("发送失败: " + e.getMessage());
            }
        } catch (RuntimeException e) {
            if (session.isOpen()) {
                session.close("发送线程异常: " + e.getMessage());
            }
        }
    }
}
