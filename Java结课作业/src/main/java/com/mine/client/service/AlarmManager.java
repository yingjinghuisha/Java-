package com.mine.client.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 告警中心：业务线程只负责入队，独立分发线程消费并回调 GUI，
 * 实现业务处理与界面更新解耦。
 */
public class AlarmManager {

    private static final int QUEUE_CAPACITY = 2048;

    private final BlockingQueue<AlarmEvent> queue = new LinkedBlockingQueue<AlarmEvent>(QUEUE_CAPACITY);
    private final CopyOnWriteArrayList<AlarmEvent> history = new CopyOnWriteArrayList<AlarmEvent>();
    private final AtomicInteger alarmCount = new AtomicInteger();
    private final AtomicInteger criticalCount = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile AlarmListener listener;
    private volatile Thread dispatcherThread;

    public synchronized void start(AlarmListener alarmListener) {
        this.listener = alarmListener;
        if (!running.compareAndSet(false, true)) {
            return;
        }
        dispatcherThread = new Thread(new Runnable() {
            @Override
            public void run() {
                dispatchLoop();
            }
        }, "alarm-dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    private void dispatchLoop() {
        while (running.get()) {
            try {
                AlarmEvent event = queue.poll(300L, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }
                AlarmListener current = listener;
                if (current != null) {
                    try {
                        current.onAlarm(event);
                    } catch (Throwable t) {
                        System.err.println("[告警中心] GUI 回调异常: " + t);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** 业务线程调用，非阻塞入队。队列满时淘汰最旧告警。 */
    public void publish(AlarmEvent event) {
        if (event == null) {
            return;
        }
        history.add(event);
        if (event.getLevel().isCounted()) {
            alarmCount.incrementAndGet();
        }
        if (event.getLevel() == AlarmLevel.CRITICAL) {
            criticalCount.incrementAndGet();
        }
        if (!queue.offer(event)) {
            queue.poll();
            if (!queue.offer(event)) {
                dropped.incrementAndGet();
            }
        }
    }

    public List<AlarmEvent> historySnapshot() {
        return new ArrayList<AlarmEvent>(history);
    }

    public void clear() {
        history.clear();
        queue.clear();
        alarmCount.set(0);
        criticalCount.set(0);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Thread thread = dispatcherThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public int getAlarmCount() {
        return alarmCount.get();
    }

    public int getCriticalCount() {
        return criticalCount.get();
    }

    public int getHistorySize() {
        return history.size();
    }

    public int getPendingCount() {
        return queue.size();
    }

    public long getDroppedCount() {
        return dropped.get();
    }

    public boolean isRunning() {
        return running.get();
    }
}
