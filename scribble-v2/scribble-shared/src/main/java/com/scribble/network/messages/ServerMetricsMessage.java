package com.scribble.network.messages;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Server metrics update message sent to subscribed admin clients.
 * Contains comprehensive server, client, and room statistics.
 * 
 * This message is backwards-compatible: older clients that don't
 * recognize it will simply ignore it (standard Java serialization behavior).
 */
public final class ServerMetricsMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    // Server info
    private final long serverStartTime;
    private final long serverUptime;
    private final int serverPort;

    // Connection stats
    private final int currentConnectedClients;
    private final long totalConnectionsAccepted;
    private final long totalDisconnects;
    private final long totalReconnectAttempts;

    // Message stats
    private final long totalMessagesSent;
    private final long totalMessagesReceived;
    private final long totalBytesSent;
    private final long totalBytesReceived;

    // Throughput
    private final double bytesPerSecIn;
    private final double bytesPerSecOut;
    private final double msgsPerSec;

    // Message size stats
    private final double avgMessageSize;
    private final int minMessageSize;
    private final int maxMessageSize;

    // Error stats
    private final long parseErrors;
    private final long invalidPayloads;
    private final long droppedMessages;

    // Room stats
    private final int roomCount;

    // Top message types
    private final List<MessageTypeInfo> topMessageTypes;

    // Time series data for graphs
    private final double[] bytesPerSecInHistory;
    private final double[] bytesPerSecOutHistory;
    private final double[] msgsPerSecHistory;
    private final double[] connectedClientsHistory;

    // JVM metrics
    private final double[] cpuLoadHistory;
    private final double[] heapUsedMbHistory;
    private final double[] threadCountHistory;
    private final double heapMaxMb;
    private final long gcCollections;
    private final long gcTimeMs;

    // Per-client stats (optional, can be null for bandwidth savings)
    private final Map<String, ClientInfo> clientStats;

    // Per-room stats (optional)
    private final Map<String, RoomInfo> roomStats;

    // Recent packet log (optional)
    private final List<PacketInfo> recentPackets;

    public ServerMetricsMessage(
            long serverStartTime, long serverUptime, int serverPort,
            int currentConnectedClients, long totalConnectionsAccepted,
            long totalDisconnects, long totalReconnectAttempts,
            long totalMessagesSent, long totalMessagesReceived,
            long totalBytesSent, long totalBytesReceived,
            double bytesPerSecIn, double bytesPerSecOut, double msgsPerSec,
            double avgMessageSize, int minMessageSize, int maxMessageSize,
            long parseErrors, long invalidPayloads, long droppedMessages,
            int roomCount, List<MessageTypeInfo> topMessageTypes,
            double[] bytesPerSecInHistory, double[] bytesPerSecOutHistory,
            double[] msgsPerSecHistory, double[] connectedClientsHistory,
            double[] cpuLoadHistory, double[] heapUsedMbHistory,
            double[] threadCountHistory, double heapMaxMb,
            long gcCollections, long gcTimeMs,
            Map<String, ClientInfo> clientStats,
            Map<String, RoomInfo> roomStats,
            List<PacketInfo> recentPackets) {
        this.serverStartTime = serverStartTime;
        this.serverUptime = serverUptime;
        this.serverPort = serverPort;
        this.currentConnectedClients = currentConnectedClients;
        this.totalConnectionsAccepted = totalConnectionsAccepted;
        this.totalDisconnects = totalDisconnects;
        this.totalReconnectAttempts = totalReconnectAttempts;
        this.totalMessagesSent = totalMessagesSent;
        this.totalMessagesReceived = totalMessagesReceived;
        this.totalBytesSent = totalBytesSent;
        this.totalBytesReceived = totalBytesReceived;
        this.bytesPerSecIn = bytesPerSecIn;
        this.bytesPerSecOut = bytesPerSecOut;
        this.msgsPerSec = msgsPerSec;
        this.avgMessageSize = avgMessageSize;
        this.minMessageSize = minMessageSize;
        this.maxMessageSize = maxMessageSize;
        this.parseErrors = parseErrors;
        this.invalidPayloads = invalidPayloads;
        this.droppedMessages = droppedMessages;
        this.roomCount = roomCount;
        this.topMessageTypes = topMessageTypes;
        this.bytesPerSecInHistory = bytesPerSecInHistory;
        this.bytesPerSecOutHistory = bytesPerSecOutHistory;
        this.msgsPerSecHistory = msgsPerSecHistory;
        this.connectedClientsHistory = connectedClientsHistory;
        this.cpuLoadHistory = cpuLoadHistory;
        this.heapUsedMbHistory = heapUsedMbHistory;
        this.threadCountHistory = threadCountHistory;
        this.heapMaxMb = heapMaxMb;
        this.gcCollections = gcCollections;
        this.gcTimeMs = gcTimeMs;
        this.clientStats = clientStats;
        this.roomStats = roomStats;
        this.recentPackets = recentPackets;
    }

    // Getters
    public long getServerStartTime() {
        return serverStartTime;
    }

    public long getServerUptime() {
        return serverUptime;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getCurrentConnectedClients() {
        return currentConnectedClients;
    }

    public long getTotalConnectionsAccepted() {
        return totalConnectionsAccepted;
    }

    public long getTotalDisconnects() {
        return totalDisconnects;
    }

    public long getTotalReconnectAttempts() {
        return totalReconnectAttempts;
    }

    public long getTotalMessagesSent() {
        return totalMessagesSent;
    }

    public long getTotalMessagesReceived() {
        return totalMessagesReceived;
    }

    public long getTotalBytesSent() {
        return totalBytesSent;
    }

    public long getTotalBytesReceived() {
        return totalBytesReceived;
    }

    public double getBytesPerSecIn() {
        return bytesPerSecIn;
    }

    public double getBytesPerSecOut() {
        return bytesPerSecOut;
    }

    public double getMsgsPerSec() {
        return msgsPerSec;
    }

    public double getAvgMessageSize() {
        return avgMessageSize;
    }

    public int getMinMessageSize() {
        return minMessageSize;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public long getParseErrors() {
        return parseErrors;
    }

    public long getInvalidPayloads() {
        return invalidPayloads;
    }

    public long getDroppedMessages() {
        return droppedMessages;
    }

    public int getRoomCount() {
        return roomCount;
    }

    public List<MessageTypeInfo> getTopMessageTypes() {
        return topMessageTypes;
    }

    public double[] getBytesPerSecInHistory() {
        return bytesPerSecInHistory;
    }

    public double[] getBytesPerSecOutHistory() {
        return bytesPerSecOutHistory;
    }

    public double[] getMsgsPerSecHistory() {
        return msgsPerSecHistory;
    }

    public double[] getConnectedClientsHistory() {
        return connectedClientsHistory;
    }

    public double[] getCpuLoadHistory() {
        return cpuLoadHistory;
    }

    public double[] getHeapUsedMbHistory() {
        return heapUsedMbHistory;
    }

    public double[] getThreadCountHistory() {
        return threadCountHistory;
    }

    public double getHeapMaxMb() {
        return heapMaxMb;
    }

    public long getGcCollections() {
        return gcCollections;
    }

    public long getGcTimeMs() {
        return gcTimeMs;
    }

    public Map<String, ClientInfo> getClientStats() {
        return clientStats;
    }

    public Map<String, RoomInfo> getRoomStats() {
        return roomStats;
    }

    public List<PacketInfo> getRecentPackets() {
        return recentPackets;
    }

    // Nested DTOs for client stats
    public static class ClientInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final String clientId;
        public final String remoteAddress;
        public final int remotePort;
        public final String playerName;
        public final String currentRoomId;
        public final boolean connected;
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

        public ClientInfo(String clientId, String remoteAddress, int remotePort,
                String playerName, String currentRoomId, boolean connected,
                long sessionDurationMs, long messagesSent, long messagesReceived,
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

    // Nested DTO for room stats
    public static class RoomInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final String roomId;
        public final long uptime;
        public final long messagesBroadcast;
        public final long messagesReceived;
        public final long bytesBroadcast;
        public final long bytesReceived;
        public final int currentPlayerCount;
        public final int currentSpectatorCount;
        public final long roundsPlayed;
        public final long drawMessages;
        public final long chatMessages;
        public final long guessAttempts;
        public final long correctGuesses;
        public final double[] trafficHistory;

        public RoomInfo(String roomId, long uptime,
                long messagesBroadcast, long messagesReceived,
                long bytesBroadcast, long bytesReceived,
                int currentPlayerCount, int currentSpectatorCount,
                long roundsPlayed, long drawMessages, long chatMessages,
                long guessAttempts, long correctGuesses,
                double[] trafficHistory) {
            this.roomId = roomId;
            this.uptime = uptime;
            this.messagesBroadcast = messagesBroadcast;
            this.messagesReceived = messagesReceived;
            this.bytesBroadcast = bytesBroadcast;
            this.bytesReceived = bytesReceived;
            this.currentPlayerCount = currentPlayerCount;
            this.currentSpectatorCount = currentSpectatorCount;
            this.roundsPlayed = roundsPlayed;
            this.drawMessages = drawMessages;
            this.chatMessages = chatMessages;
            this.guessAttempts = guessAttempts;
            this.correctGuesses = correctGuesses;
            this.trafficHistory = trafficHistory;
        }
    }

    // Nested DTO for message type stats
    public static class MessageTypeInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final String messageType;
        public final long countRx;
        public final long countTx;
        public final long totalBytes;

        public MessageTypeInfo(String messageType, long countRx, long countTx, long totalBytes) {
            this.messageType = messageType;
            this.countRx = countRx;
            this.countTx = countTx;
            this.totalBytes = totalBytes;
        }
    }

    // Nested DTO for packet log entry
    public static class PacketInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final long timestamp;
        public final String direction; // "TX" or "RX"
        public final String messageType;
        public final int sizeBytes;
        public final String clientId;
        public final String roomId;

        public PacketInfo(long timestamp, String direction, String messageType,
                int sizeBytes, String clientId, String roomId) {
            this.timestamp = timestamp;
            this.direction = direction;
            this.messageType = messageType;
            this.sizeBytes = sizeBytes;
            this.clientId = clientId;
            this.roomId = roomId;
        }
    }
}
