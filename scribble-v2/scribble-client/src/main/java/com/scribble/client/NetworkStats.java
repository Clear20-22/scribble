package com.scribble.client;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks network statistics for visualization and debugging.
 * Useful for demonstrating networking concepts in academic/project settings.
 */
public class NetworkStats {
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);
    private final AtomicLong connectionStartTime = new AtomicLong(0);
    private final AtomicLong reconnectCount = new AtomicLong(0);

    // Latency history for graphing (last 60 samples)
    private final LinkedList<Long> latencyHistory = new LinkedList<>();
    private static final int MAX_LATENCY_HISTORY = 60;

    // Message log for packet inspection (last 100 messages)
    private final LinkedList<MessageLogEntry> messageLog = new LinkedList<>();
    private static final int MAX_MESSAGE_LOG = 100;

    public void reset() {
        messagesSent.set(0);
        messagesReceived.set(0);
        bytesSent.set(0);
        bytesReceived.set(0);
        connectionStartTime.set(System.currentTimeMillis());
        reconnectCount.set(0);
        synchronized (latencyHistory) {
            latencyHistory.clear();
        }
        synchronized (messageLog) {
            messageLog.clear();
        }
    }

    public void onConnectionStarted() {
        connectionStartTime.set(System.currentTimeMillis());
    }

    public void onMessageSent(String messageType, int estimatedBytes) {
        messagesSent.incrementAndGet();
        bytesSent.addAndGet(estimatedBytes);
        logMessage(MessageDirection.SENT, messageType, estimatedBytes);
    }

    public void onMessageReceived(String messageType, int estimatedBytes) {
        messagesReceived.incrementAndGet();
        bytesReceived.addAndGet(estimatedBytes);
        logMessage(MessageDirection.RECEIVED, messageType, estimatedBytes);
    }

    public void onLatencyMeasured(long latencyMs) {
        synchronized (latencyHistory) {
            latencyHistory.addLast(latencyMs);
            while (latencyHistory.size() > MAX_LATENCY_HISTORY) {
                latencyHistory.removeFirst();
            }
        }
    }

    public void onReconnect() {
        reconnectCount.incrementAndGet();
    }

    private void logMessage(MessageDirection direction, String messageType, int bytes) {
        synchronized (messageLog) {
            messageLog.addFirst(new MessageLogEntry(
                    System.currentTimeMillis(),
                    direction,
                    messageType,
                    bytes));
            while (messageLog.size() > MAX_MESSAGE_LOG) {
                messageLog.removeLast();
            }
        }
    }

    // Getters
    public long getMessagesSent() {
        return messagesSent.get();
    }

    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    public long getBytesSent() {
        return bytesSent.get();
    }

    public long getBytesReceived() {
        return bytesReceived.get();
    }

    public long getConnectionDurationMs() {
        long start = connectionStartTime.get();
        return start > 0 ? System.currentTimeMillis() - start : 0;
    }

    public long getReconnectCount() {
        return reconnectCount.get();
    }

    public List<Long> getLatencyHistory() {
        synchronized (latencyHistory) {
            return new ArrayList<>(latencyHistory);
        }
    }

    public List<MessageLogEntry> getMessageLog() {
        synchronized (messageLog) {
            return new ArrayList<>(messageLog);
        }
    }

    public double getAverageLatency() {
        synchronized (latencyHistory) {
            if (latencyHistory.isEmpty()) {
                return 0;
            }
            return latencyHistory.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }

    public long getMinLatency() {
        synchronized (latencyHistory) {
            return latencyHistory.stream().mapToLong(Long::longValue).min().orElse(0);
        }
    }

    public long getMaxLatency() {
        synchronized (latencyHistory) {
            return latencyHistory.stream().mapToLong(Long::longValue).max().orElse(0);
        }
    }

    public double getMessagesPerSecond() {
        long duration = getConnectionDurationMs();
        if (duration <= 0)
            return 0;
        return (messagesSent.get() + messagesReceived.get()) * 1000.0 / duration;
    }

    public String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        }
        return String.format("%d:%02d", minutes, seconds % 60);
    }

    // Inner classes
    public enum MessageDirection {
        SENT("→"), RECEIVED("←");

        private final String symbol;

        MessageDirection(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public static class MessageLogEntry {
        private final long timestamp;
        private final MessageDirection direction;
        private final String messageType;
        private final int bytes;

        public MessageLogEntry(long timestamp, MessageDirection direction, String messageType, int bytes) {
            this.timestamp = timestamp;
            this.direction = direction;
            this.messageType = messageType;
            this.bytes = bytes;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public MessageDirection getDirection() {
            return direction;
        }

        public String getMessageType() {
            return messageType;
        }

        public int getBytes() {
            return bytes;
        }

        @Override
        public String toString() {
            return String.format("%s %s (%d bytes)", direction.getSymbol(), messageType, bytes);
        }
    }
}
