package com.mine.client.network;

/** 客户端运行统计快照（不可变）。 */
public final class ClientStats {

    private final boolean running;
    private final boolean connected;
    private final String host;
    private final int port;
    private final long receivedPackets;
    private final long receivedBytes;
    private final long crcErrors;
    private final long discardedFrames;
    private final long queueDropped;
    private final long sentCommands;
    private final long sendFailures;
    private final long heartbeats;
    private final long reconnectAttempts;
    private final long connectionLosses;
    private final long lastPacketAt;
    private final long connectedAt;
    private final int queueSize;
    private final int queueCapacity;
    private final int queuePeak;
    private final String requestedLocalIp;
    private final String actualLocalIp;

    public ClientStats(boolean running, boolean connected, String host, int port,
                       long receivedPackets, long receivedBytes, long crcErrors,
                       long discardedFrames, long queueDropped, long sentCommands,
                       long sendFailures, long heartbeats, long reconnectAttempts,
                       long connectionLosses, long lastPacketAt, long connectedAt,
                       int queueSize, int queueCapacity, int queuePeak,
                       String requestedLocalIp, String actualLocalIp) {
        this.running = running;
        this.connected = connected;
        this.host = host;
        this.port = port;
        this.receivedPackets = receivedPackets;
        this.receivedBytes = receivedBytes;
        this.crcErrors = crcErrors;
        this.discardedFrames = discardedFrames;
        this.queueDropped = queueDropped;
        this.sentCommands = sentCommands;
        this.sendFailures = sendFailures;
        this.heartbeats = heartbeats;
        this.reconnectAttempts = reconnectAttempts;
        this.connectionLosses = connectionLosses;
        this.lastPacketAt = lastPacketAt;
        this.connectedAt = connectedAt;
        this.queueSize = queueSize;
        this.queueCapacity = queueCapacity;
        this.queuePeak = queuePeak;
        this.requestedLocalIp = requestedLocalIp == null ? "" : requestedLocalIp;
        this.actualLocalIp = actualLocalIp == null ? "" : actualLocalIp;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long getReceivedPackets() {
        return receivedPackets;
    }

    public long getReceivedBytes() {
        return receivedBytes;
    }

    public long getCrcErrors() {
        return crcErrors;
    }

    public long getDiscardedFrames() {
        return discardedFrames;
    }

    public long getQueueDropped() {
        return queueDropped;
    }

    public long getSentCommands() {
        return sentCommands;
    }

    public long getSendFailures() {
        return sendFailures;
    }

    public long getHeartbeats() {
        return heartbeats;
    }

    public long getReconnectAttempts() {
        return reconnectAttempts;
    }

    public long getConnectionLosses() {
        return connectionLosses;
    }

    public long getLastPacketAt() {
        return lastPacketAt;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public int getQueuePeak() {
        return queuePeak;
    }

    public long getLastPacketAgeMs() {
        return lastPacketAt <= 0 ? -1 : System.currentTimeMillis() - lastPacketAt;
    }

    public String getRequestedLocalIp() {
        return requestedLocalIp;
    }

    public String getActualLocalIp() {
        return actualLocalIp;
    }
}
