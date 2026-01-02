package com.scribble.server.monitor;

import java.io.Serial;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-client network statistics tracker.
 * Thread-safe using atomic operations and lock-free data structures.
 */
public final class ClientNetworkStats implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // Client identification
    private final String clientId;
    private final String remoteAddress;
    private final int remotePort;
    private final long connectionStartTime;

    // Current state
    private volatile String playerName;
    private volatile String currentRoomId;
    private volatile boolean connected;

    // Message counters (lock-free)
    private final LongAdder messagesSent = new LongAdder();
    private final LongAdder messagesReceived = new LongAdder();
    private final LongAdder bytesSent = new LongAdder();
    private final LongAdder bytesReceived = new LongAdder();

    // Per-type message counters
    private final java.util.concurrent.ConcurrentHashMap<String, LongAdder> messageTypeCountsTx = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, LongAdder> messageTypeCountsRx = new java.util.concurrent.ConcurrentHashMap<>();

    // Latency tracking
    private static final int DEFAULT_RING_SIZE = getIntProperty("scribble.monitor.ringSize", 60);
    private final TimeSeriesRingBuffer latencyHistory = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);
    private volatile long lastPingTime;
    private volatile long lastPongTime;
    private volatile long lastRtt;

    // Packet log (ring buffer using ConcurrentLinkedDeque + size cap)
    private final ConcurrentLinkedDeque<PacketLogEntry> packetLog = new ConcurrentLinkedDeque<>();
    private static final int MAX_PACKET_LOG = getIntProperty("scribble.monitor.logSize", 50);

    // Throughput tracking
    private volatile long lastThroughputCalcTime;
    private volatile long lastBytesSentSnapshot;
    private volatile long lastBytesReceivedSnapshot;
    private volatile long lastMsgsSentSnapshot;
    private volatile long lastMsgsReceivedSnapshot;
    private volatile double currentTxBytesPerSec;
    private volatile double currentRxBytesPerSec;
    private volatile double currentMsgsPerSec;

    // Error tracking
    private final LongAdder parseErrors = new LongAdder();
    private final LongAdder droppedMessages = new LongAdder();

    public ClientNetworkStats(String clientId, InetSocketAddress remoteAddress) {
        this.clientId = clientId;
        this.remoteAddress = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
        this.remotePort = remoteAddress != null ? remoteAddress.getPort() : 0;
        this.connectionStartTime = System.currentTimeMillis();
        this.connected = true;
        this.lastThroughputCalcTime = connectionStartTime;
    }

    private static int getIntProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // --- Recording methods ---

    public void recordMessageSent(String messageType, int bytes) {
        messagesSent.increment();
        bytesSent.add(bytes);
        messageTypeCountsTx.computeIfAbsent(messageType, k -> new LongAdder()).increment();
        logPacket(PacketLogEntry.Direction.TX, messageType, bytes);
    }

    public void recordMessageReceived(String messageType, int bytes) {
        messagesReceived.increment();
        bytesReceived.add(bytes);
        messageTypeCountsRx.computeIfAbsent(messageType, k -> new LongAdder()).increment();
        logPacket(PacketLogEntry.Direction.RX, messageType, bytes);
    }

    public void recordPingSent() {
        lastPingTime = System.currentTimeMillis();
    }

    public void recordPongReceived() {
        lastPongTime = System.currentTimeMillis();
        if (lastPingTime > 0) {
            lastRtt = lastPongTime - lastPingTime;
            latencyHistory.add(lastRtt);
        }
    }

    public void recordParseError() {
        parseErrors.increment();
    }

    public void recordDroppedMessage() {
        droppedMessages.increment();
    }

    /**
     * Convenience method to record a transmitted message with room context.
     */
    public void recordTx(String messageType, int sizeBytes, String roomName) {
        recordMessageSent(messageType, sizeBytes);
        if (roomName != null) {
            this.currentRoomId = roomName;
        }
    }

    /**
     * Convenience method to record a received message with room context.
     */
    public void recordRx(String messageType, int sizeBytes, String roomName) {
        recordMessageReceived(messageType, sizeBytes);
        if (roomName != null) {
            this.currentRoomId = roomName;
        }
    }

    /**
     * Records a new latency measurement (RTT in milliseconds).
     */
    public void recordLatency(long rttMs) {
        this.lastRtt = rttMs;
        latencyHistory.add(rttMs);
    }

    private void logPacket(PacketLogEntry.Direction direction, String messageType, int bytes) {
        PacketLogEntry entry = new PacketLogEntry(
                System.currentTimeMillis(),
                direction,
                messageType,
                bytes,
                clientId,
                currentRoomId);
        packetLog.addFirst(entry);
        // Trim to max size
        while (packetLog.size() > MAX_PACKET_LOG) {
            packetLog.pollLast();
        }
    }

    /**
     * Calculate rolling throughput. Call this periodically (e.g., every second).
     */
    public void calculateThroughput() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastThroughputCalcTime;
        if (elapsed <= 0)
            return;

        long currentBytesSent = bytesSent.sum();
        long currentBytesReceived = bytesReceived.sum();
        long currentMsgsSent = messagesSent.sum();
        long currentMsgsReceived = messagesReceived.sum();

        double seconds = elapsed / 1000.0;
        currentTxBytesPerSec = (currentBytesSent - lastBytesSentSnapshot) / seconds;
        currentRxBytesPerSec = (currentBytesReceived - lastBytesReceivedSnapshot) / seconds;
        currentMsgsPerSec = ((currentMsgsSent - lastMsgsSentSnapshot) +
                (currentMsgsReceived - lastMsgsReceivedSnapshot)) / seconds;

        lastThroughputCalcTime = now;
        lastBytesSentSnapshot = currentBytesSent;
        lastBytesReceivedSnapshot = currentBytesReceived;
        lastMsgsSentSnapshot = currentMsgsSent;
        lastMsgsReceivedSnapshot = currentMsgsReceived;
    }

    // --- Setters ---

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public void setCurrentRoomId(String roomId) {
        this.currentRoomId = roomId;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    // --- Getters ---

    public String getClientId() {
        return clientId;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public long getConnectionStartTime() {
        return connectionStartTime;
    }

    public long getSessionDurationMs() {
        return System.currentTimeMillis() - connectionStartTime;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getCurrentRoomId() {
        return currentRoomId;
    }

    public boolean isConnected() {
        return connected;
    }

    public long getMessagesSent() {
        return messagesSent.sum();
    }

    public long getMessagesReceived() {
        return messagesReceived.sum();
    }

    public long getBytesSent() {
        return bytesSent.sum();
    }

    public long getBytesReceived() {
        return bytesReceived.sum();
    }

    public long getLastRtt() {
        return lastRtt;
    }

    public double getAverageRtt() {
        return latencyHistory.getAverage();
    }

    public double getMinRtt() {
        return latencyHistory.getMin();
    }

    public double getMaxRtt() {
        return latencyHistory.getMax();
    }

    public double[] getLatencyHistory() {
        return latencyHistory.getValues();
    }

    public List<PacketLogEntry> getPacketLog() {
        return new ArrayList<>(packetLog);
    }

    public double getCurrentTxBytesPerSec() {
        return currentTxBytesPerSec;
    }

    public double getCurrentRxBytesPerSec() {
        return currentRxBytesPerSec;
    }

    public double getCurrentMsgsPerSec() {
        return currentMsgsPerSec;
    }

    public long getParseErrors() {
        return parseErrors.sum();
    }

    public long getDroppedMessages() {
        return droppedMessages.sum();
    }

    public java.util.Map<String, Long> getMessageTypeCountsTx() {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        messageTypeCountsTx.forEach((k, v) -> result.put(k, v.sum()));
        return result;
    }

    public java.util.Map<String, Long> getMessageTypeCountsRx() {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        messageTypeCountsRx.forEach((k, v) -> result.put(k, v.sum()));
        return result;
    }

    /**
     * Create a snapshot for serialization/transmission.
     */
    public ClientStatsSnapshot toSnapshot() {
        return new ClientStatsSnapshot(
                clientId,
                remoteAddress,
                remotePort,
                playerName,
                currentRoomId,
                connected,
                connectionStartTime,
                getSessionDurationMs(),
                getMessagesSent(),
                getMessagesReceived(),
                getBytesSent(),
                getBytesReceived(),
                lastRtt,
                getAverageRtt(),
                getMinRtt(),
                getMaxRtt(),
                getLatencyHistory(),
                currentTxBytesPerSec,
                currentRxBytesPerSec,
                currentMsgsPerSec,
                getParseErrors(),
                getDroppedMessages());
    }

    /**
     * Immutable snapshot of client stats for transmission.
     */
    public static class ClientStatsSnapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final String clientId;
        public final String remoteAddress;
        public final int remotePort;
        public final String playerName;
        public final String currentRoomId;
        public final boolean connected;
        public final long connectionStartTime;
        public final long sessionDurationMs;
        public final long messagesSent;
        public final long messagesReceived;
        public final long bytesSent;
        public final long bytesReceived;
        public final long lastRtt;
        public final double avgRtt;
        public final double minRtt;
        public final double maxRtt;
        public final double[] latencyHistory;
        public final double txBytesPerSec;
        public final double rxBytesPerSec;
        public final double msgsPerSec;
        public final long parseErrors;
        public final long droppedMessages;

        public ClientStatsSnapshot(String clientId, String remoteAddress, int remotePort,
                String playerName, String currentRoomId, boolean connected,
                long connectionStartTime, long sessionDurationMs,
                long messagesSent, long messagesReceived,
                long bytesSent, long bytesReceived,
                long lastRtt, double avgRtt, double minRtt, double maxRtt,
                double[] latencyHistory,
                double txBytesPerSec, double rxBytesPerSec, double msgsPerSec,
                long parseErrors, long droppedMessages) {
            this.clientId = clientId;
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;
            this.playerName = playerName;
            this.currentRoomId = currentRoomId;
            this.connected = connected;
            this.connectionStartTime = connectionStartTime;
            this.sessionDurationMs = sessionDurationMs;
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.bytesSent = bytesSent;
            this.bytesReceived = bytesReceived;
            this.lastRtt = lastRtt;
            this.avgRtt = avgRtt;
            this.minRtt = minRtt;
            this.maxRtt = maxRtt;
            this.latencyHistory = latencyHistory;
            this.txBytesPerSec = txBytesPerSec;
            this.rxBytesPerSec = rxBytesPerSec;
            this.msgsPerSec = msgsPerSec;
            this.parseErrors = parseErrors;
            this.droppedMessages = droppedMessages;
        }
    }
}
