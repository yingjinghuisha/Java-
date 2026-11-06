package com.mine.client.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义业务线程池。
 *
 * <p>core=3，max=5，队列 ArrayBlockingQueue(256)，拒绝策略为 CallerRunsPolicy
 * （在调用者线程执行，形成天然反压），并统计调用者执行次数，便于观察削峰效果。</p>
 */
public class BusinessThreadPool {

    private static final int CORE_SIZE = 3;
    private static final int MAX_SIZE = 5;
    private static final int QUEUE_CAPACITY = 256;
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private final AtomicLong callerRuns = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicInteger threadSeq = new AtomicInteger(1);
    private final ThreadPoolExecutor executor;

    public BusinessThreadPool() {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                Thread thread = new Thread(runnable, "biz-worker-" + threadSeq.getAndIncrement());
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        System.err.println("[业务线程池] " + t.getName() + " 未捕获异常: " + e);
                    }
                });
                return thread;
            }
        };

        ThreadPoolExecutor.CallerRunsPolicy callerRunsPolicy =
                new ThreadPoolExecutor.CallerRunsPolicy() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                        if (e.isShutdown()) {
                            rejected.incrementAndGet();
                            return;
                        }
                        callerRuns.incrementAndGet();
                        super.rejectedExecution(r, e);
                    }
                };

        this.executor = new MetricsExecutor(CORE_SIZE, MAX_SIZE, KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY),
                threadFactory, callerRunsPolicy);
        this.executor.prestartAllCoreThreads();
    }

    /** 提交任务，任何 Throwable 都不会导致工作线程退出。 */
    public void execute(final Runnable task) {
        if (task == null) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    System.err.println("[业务线程池] 任务执行异常: " + t);
                }
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getPoolSize() {
        return executor.getPoolSize();
    }

    public int getLargestPoolSize() {
        return executor.getLargestPoolSize();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public int getQueueRemainingCapacity() {
        return executor.getQueue().remainingCapacity();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public long getCallerRuns() {
        return callerRuns.get();
    }

    public long getRejected() {
        return rejected.get();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    private final class MetricsExecutor extends ThreadPoolExecutor {
        MetricsExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                        TimeUnit unit, ArrayBlockingQueue<Runnable> workQueue,
                        ThreadFactory threadFactory,
                        RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                    threadFactory, handler);
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t != null) {
                System.err.println("[业务线程池] worker 异常: " + t);
            }
        }
    }
}
