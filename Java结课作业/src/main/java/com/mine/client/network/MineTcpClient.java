package com.mine.client.network;

import com.mine.client.concurrent.DataQueueManager;
import com.mine.common.IpRange;
import com.mine.common.MinePacket;
import com.mine.common.PacketStreamDecoder;
import com.mine.common.Protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP 长连接客户端。
 *
 * <p>网络线程只负责：连接、读取字节流、拆包、CRC 校验、入队；不做业务、
 * 不碰 Swing。连接断开会自动退避重连，心跳线程负责保活和链路超时检测。</p>
 */
public class MineTcpClient {

    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int HEARTBEAT_INTERVAL_MS = 3000;
    public static final int STALE_TIMEOUT_MS = 12000;
    private static final long MAX_BACKOFF_MS = 5000L;

    private final DataQueueManager dataQueue;
    private final ScheduledExecutorService maintenance;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong receivedPackets = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong crcErrors = new AtomicLong();
    private final AtomicLong discardedFrames = new AtomicLong();
    private final AtomicLong sentCommands = new AtomicLong();
    private final AtomicLong sendFailures = new AtomicLong();
    private final AtomicLong heartbeats = new AtomicLong();
    private final AtomicLong reconnectAttempts = new AtomicLong();
    private final AtomicLong connectionLosses = new AtomicLong();
    private final Object lifecycleLock = new Object();
    private final Object sendLock = new Object();

    private volatile Socket socket;
    private volatile OutputStream output;
    private volatile String host = Protocol.DEFAULT_HOST;
    private volatile int port = Protocol.DEFAULT_PORT;
    private volatile Thread supervisorThread;
    private volatile ClientConnectionListener listener;
    private volatile long lastPacketAt;
    private volatile long connectedAt;
    private volatile long lastHeartbeatAt;
    private volatile String requestedLocalIp = "";
    private volatile String actualLocalIp = "";

    public MineTcpClient(DataQueueManager dataQueue) {
        if (dataQueue == null) {
            throw new IllegalArgumentException("dataQueue 不能为空");
        }
        this.dataQueue = dataQueue;
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "mine-net-maintenance");
                thread.setDaemon(true);
                return thread;
            }
        };
        this.maintenance = Executors.newSingleThreadScheduledExecutor(factory);
        this.maintenance.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                maintenanceTick();
            }
        }, 1L, 1L, TimeUnit.SECONDS);
    }

    public boolean start(String newHost, int newPort) {
        return start(newHost, newPort, "");
    }

    /**
     * 启动连接监督线程（不会阻塞调用线程）。重复调用返回 false，防止多 Socket。
     *
     * @param localIp 本机工控机源 IP；空白表示跟随系统路由，非空时绑定该源地址
     */
    public boolean start(String newHost, int newPort, String localIp) {
        synchronized (lifecycleLock) {
            if (running.get() || shutdown.get()) {
                return false;
            }
            if (newHost == null || newHost.trim().isEmpty() || newPort < 1 || newPort > 65535) {
                return false;
            }
            String normalizedLocal = localIp == null ? "" : localIp.trim();
            if (IpRange.validateLocalIp(normalizedLocal) != null) {
                return false;
            }
            this.host = newHost.trim();
            this.port = newPort;
            this.requestedLocalIp = normalizedLocal;
            this.actualLocalIp = "";
            resetCounters();
            final long gen = generation.incrementAndGet();
            running.set(true);
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    supervise(gen);
                }
            }, "mine-net-supervisor");
            thread.setDaemon(true);
            supervisorThread = thread;
            thread.start();
            return true;
        }
    }

    /** 用户主动断开：关闭 Socket、终止重连，但不关闭心跳调度器（允许再次连接）。 */
    public void stop() {
        boolean wasRunning;
        synchronized (lifecycleLock) {
            wasRunning = running.getAndSet(false);
            generation.incrementAndGet();
            connected.set(false);
            Socket socketToClose = socket;
            socket = null;
            output = null;
            closeQuietly(socketToClose);
            Thread thread = supervisorThread;
            if (thread != null) {
                thread.interrupt();
            }
        }
        if (wasRunning) {
            notifyListener(ConnectionState.DISCONNECTED, "已断开网关连接");
        }
    }

    /** 程序退出时调用，释放定时线程。 */
    public void shutdown() {
        shutdown.set(true);
        stop();
        maintenance.shutdownNow();
    }

    private void supervise(long gen) {
        int failures = 0;
        while (isActive(gen)) {
            Socket current = new Socket();
            boolean established = false;
            boolean fatalConfiguration = false;
            String failureReason = null;
            try {
                if (isActive(gen)) {
                    notifyState(gen, failures == 0 ? ConnectionState.CONNECTING
                                    : ConnectionState.RECONNECTING,
                            failures == 0
                                    ? "正在连接 " + host + ":" + port + " …"
                                    : "正在进行第 " + failures + " 次重连 …");
                }
                if (!requestedLocalIp.isEmpty()) {
                    InetAddress localAddress = InetAddress.getByName(requestedLocalIp);
                    current.bind(new InetSocketAddress(localAddress, 0));
                }
                current.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                current.setTcpNoDelay(true);
                current.setKeepAlive(true);
                if (!isActive(gen)) {
                    break;
                }
                InetAddress boundAddress = current.getLocalAddress();
                String actual = boundAddress.getHostAddress();
                if (!boundAddress.isLoopbackAddress() && !IpRange.isInRange(actual)) {
                    notifyState(gen, ConnectionState.DISCONNECTED,
                            "本机源地址 " + actual + " 不在允许范围 " + IpRange.rangeText()
                                    + "，请在【本机IP】填写该工控机的网段地址");
                    running.set(false);
                    fatalConfiguration = true;
                    break;
                }
                actualLocalIp = boundAddress.isLoopbackAddress()
                        ? IpRange.LOCALHOST : actual;
                InputStream input = new BufferedInputStream(current.getInputStream(), 8192);
                OutputStream out = new BufferedOutputStream(current.getOutputStream(), 8192);
                synchronized (sendLock) {
                    if (!isActive(gen)) {
                        break;
                    }
                    socket = current;
                    output = out;
                    connected.set(true);
                    connectedAt = System.currentTimeMillis();
                    lastPacketAt = connectedAt;
                    lastHeartbeatAt = 0L;
                }
                established = true;
                failures = 0;
                notifyState(gen, ConnectionState.CONNECTED,
                        "网关连接成功 " + host + ":" + port + " · 本机 " + actualLocalIp
                                + "，正在接收传感数据…");
                readLoop(gen, current, input);
            } catch (BindException e) {
                if (isActive(gen)) {
                    notifyState(gen, ConnectionState.DISCONNECTED,
                            "本机工控机 IP " + requestedLocalIp + " 绑定失败：" + e.getMessage()
                                    + "。请确认网卡已配置该 IP，或清空【本机IP】使用自动地址");
                    running.set(false);
                }
                fatalConfiguration = true;
            } catch (IOException e) {
                failureReason = e.getMessage();
            } catch (RuntimeException e) {
                failureReason = e.toString();
            } finally {
                closeQuietly(current);
                synchronized (sendLock) {
                    if (socket == current) {
                        socket = null;
                        output = null;
                        connected.set(false);
                    }
                }
            }
            if (fatalConfiguration || !isActive(gen)) {
                break;
            }
            if (established) {
                connectionLosses.incrementAndGet();
            }
            failures++;
            long backoff = Math.min(MAX_BACKOFF_MS, 1000L * Math.min(5, failures));
            long attempts = reconnectAttempts.incrementAndGet();
            String reason = failureReason == null ? "连接中断" : failureReason;
            notifyState(gen, ConnectionState.RECONNECTING,
                    (established ? "连接已断开（" + reason + "），" : "连接失败（" + reason + "），")
                            + "第 " + attempts + " 次重连将在 " + (backoff / 1000L) + " 秒后发起…");
            if (!sleepInterruptibly(backoff, gen)) {
                break;
            }
        }
    }

    private void readLoop(long gen, Socket current, InputStream input) throws IOException {
        PacketStreamDecoder decoder = new PacketStreamDecoder();
        List<MinePacket> packets = new ArrayList<MinePacket>(16);
        byte[] buffer = new byte[8192];
        long crcBaseline = 0L;
        long invalidBaseline = 0L;
        while (isActive(gen) && connected.get() && !current.isClosed()) {
            int read;
            try {
                read = input.read(buffer);
            } catch (SocketTimeoutException e) {
                continue;
            }
            if (read < 0) {
                throw new EOFException("网关关闭了连接");
            }
            if (read == 0) {
                continue;
            }
            receivedBytes.addAndGet(read);
            packets.clear();
            decoder.feed(buffer, 0, read, packets);

            long crc = decoder.getCrcErrors();
            if (crc > crcBaseline) {
                crcErrors.addAndGet(crc - crcBaseline);
                crcBaseline = crc;
            }
            long invalid = decoder.getInvalidFrames();
            if (invalid > invalidBaseline) {
                discardedFrames.addAndGet(invalid - invalidBaseline);
                invalidBaseline = invalid;
            }
            for (MinePacket packet : packets) {
                lastPacketAt = System.currentTimeMillis();
                receivedPackets.incrementAndGet();
                dataQueue.offer(packet);
            }
        }
        if (isActive(gen)) {
            throw new EOFException("连接已关闭");
        }
    }

    /**
     * 下发控制指令（线程安全，可在后台线程调用）。
     */
    public boolean sendCommand(int targetDevice, int commandType, float value) {
        if (!Protocol.isCommand(commandType)) {
            throw new IllegalArgumentException("不是控制指令: " + commandType);
        }
        return send(Protocol.command(targetDevice, commandType, value));
    }

    /** 发送任意报文，成功返回 true。写入操作串行化，避免多线程交叉字节。 */
    public boolean send(MinePacket packet) {
        if (packet == null || !connected.get() || shutdown.get()) {
            return false;
        }
        synchronized (sendLock) {
            Socket current = socket;
            OutputStream out = output;
            if (current == null || out == null || current.isClosed()) {
                return false;
            }
            try {
                out.write(packet.encode());
                out.flush();
                if (packet.isCommand() && packet.getDataType() != Protocol.CMD_HEARTBEAT) {
                    sentCommands.incrementAndGet();
                }
                return true;
            } catch (IOException e) {
                sendFailures.incrementAndGet();
                closeQuietly(current);
                if (socket == current) {
                    socket = null;
                    output = null;
                    connected.set(false);
                }
                return false;
            }
        }
    }

    private void maintenanceTick() {
        if (!connected.get() || shutdown.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastPacketAt > 0 && now - lastPacketAt > STALE_TIMEOUT_MS) {
            Socket current = socket;
            if (current != null) {
                notifyListener(ConnectionState.RECONNECTING,
                        "超过 " + (STALE_TIMEOUT_MS / 1000L) + " 秒未收到有效数据，判定链路超时，主动断开重连");
                closeQuietly(current);
            }
            return;
        }
        if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
            lastHeartbeatAt = now;
            if (send(Protocol.heartbeat())) {
                heartbeats.incrementAndGet();
            }
        }
    }

    private boolean isActive(long gen) {
        return running.get() && generation.get() == gen && !shutdown.get();
    }

    private boolean sleepInterruptibly(long millis, long gen) {
        long deadline = System.currentTimeMillis() + millis;
        while (isActive(gen)) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return true;
            }
            try {
                Thread.sleep(Math.min(remaining, 200L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void notifyState(long gen, ConnectionState state, String message) {
        if (generation.get() != gen) {
            return;
        }
        notifyListener(state, message);
    }

    private void notifyListener(ConnectionState state, String message) {
        ClientConnectionListener current = listener;
        if (current != null) {
            try {
                current.onStateChanged(state, message);
            } catch (Throwable t) {
                System.err.println("[网络层] 连接状态回调异常: " + t);
            }
        }
    }

    private void resetCounters() {
        receivedPackets.set(0);
        receivedBytes.set(0);
        crcErrors.set(0);
        discardedFrames.set(0);
        sentCommands.set(0);
        sendFailures.set(0);
        heartbeats.set(0);
        reconnectAttempts.set(0);
        connectionLosses.set(0);
        lastPacketAt = 0L;
        connectedAt = 0L;
        lastHeartbeatAt = 0L;
        actualLocalIp = "";
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    public void setListener(ClientConnectionListener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long getLastPacketAt() {
        return lastPacketAt;
    }

    public String getRequestedLocalIp() {
        return requestedLocalIp;
    }

    public String getActualLocalIp() {
        return actualLocalIp;
    }

    public ClientStats snapshot() {
        return new ClientStats(running.get(), connected.get(), host, port,
                receivedPackets.get(), receivedBytes.get(), crcErrors.get(),
                discardedFrames.get(), dataQueue.getDropped(), sentCommands.get(),
                sendFailures.get(), heartbeats.get(), reconnectAttempts.get(),
                connectionLosses.get(), lastPacketAt, connectedAt,
                dataQueue.size(), dataQueue.getCapacity(), dataQueue.getPeakSize(),
                requestedLocalIp, actualLocalIp);
    }
}
