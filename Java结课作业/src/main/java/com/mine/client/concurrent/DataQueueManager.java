package com.mine.client.concurrent;

import com.mine.common.MinePacket;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 报文缓冲队列：网络线程（生产者）→ 业务线程池（消费者）。
 *
 * <p>有界队列用于削峰，防止海量报文把内存撑爆。队列满时丢弃最旧的一帧，
 * 保留最新数据，符合监控系统“最新值优先”的语义。</p>
 */
public class DataQueueManager {

    private final BlockingQueue<MinePacket> queue;
    private final int capacity;
    private final AtomicLong offered = new AtomicLong();
    private final AtomicLong taken = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicInteger peakSize = new AtomicInteger();

    public DataQueueManager(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("队列容量必须大于 0");
        }
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<MinePacket>(capacity);
    }

    /**
     * 非阻塞入队；满时淘汰最旧一帧，避免网络线程被阻塞。
     */
    public boolean offer(MinePacket packet) {
        if (packet == null) {
            return false;
        }
        offered.incrementAndGet();
        boolean accepted = queue.offer(packet);
        if (!accepted) {
            // 队列满：丢弃队首旧数据，再放入最新数据
            MinePacket discarded = queue.poll();
            dropped.incrementAndGet();
            if (discarded != null) {
                accepted = queue.offer(packet);
            }
        }
        updatePeak();
        return accepted;
    }

    public MinePacket take() throws InterruptedException {
        MinePacket packet = queue.take();
        taken.incrementAndGet();
        return packet;
    }

    public MinePacket poll(long timeout, TimeUnit unit) throws InterruptedException {
        MinePacket packet = queue.poll(timeout, unit);
        if (packet != null) {
            taken.incrementAndGet();
        }
        return packet;
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public int getCapacity() {
        return capacity;
    }

    public long getOffered() {
        return offered.get();
    }

    public long getTaken() {
        return taken.get();
    }

    public long getDropped() {
        return dropped.get();
    }

    public int getPeakSize() {
        return peakSize.get();
    }

    public void clear() {
        queue.clear();
    }

    public int drainTo(List<MinePacket> target, int maxElements) {
        return queue.drainTo(target, maxElements);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    private void updatePeak() {
        int size = queue.size();
        int peak;
        while (size > (peak = peakSize.get()) && !peakSize.compareAndSet(peak, size)) {
            // CAS 重试
        }
    }
}
