package com.scribble.server.monitor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a single packet/message log entry for network monitoring.
 * Immutable and thread-safe.
 */
public final class PacketLogEntry implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum Direction {
        TX("→", "Sent"),
        RX("←", "Received");

        private final String symbol;
        private final String label;

        Direction(String symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getLabel() {
            return label;
        }
    }

    private final long timestamp;
    private final Direction direction;
    private final String messageType;
    private final int sizeBytes;
    private final String clientId;
    private final String roomId;
    private final String src;
    private final String dst;
    private final Integer recipientCount;

    public PacketLogEntry(long timestamp, Direction direction, String messageType,
            int sizeBytes, String clientId, String roomId) {
        this(timestamp, direction, messageType, sizeBytes, clientId, roomId, null, null, null);
    }

    public PacketLogEntry(long timestamp, Direction direction, String messageType,
            int sizeBytes, String clientId, String roomId,
            String src, String dst, Integer recipientCount) {
        this.timestamp = timestamp;
        this.direction = direction;
        this.messageType = messageType;
        this.sizeBytes = sizeBytes;
        this.clientId = clientId;
        this.roomId = roomId;
        this.src = src;
        this.dst = dst;
        this.recipientCount = recipientCount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getMessageType() {
        return messageType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSrc() {
        return src;
    }

    public String getDst() {
        return dst;
    }

    public Integer getRecipientCount() {
        return recipientCount;
    }

    @Override
    public String toString() {
        if (src != null && dst != null) {
            return String.format("[%tT] %s %s -> %s %s (%d B) room=%s",
                    timestamp, direction.getSymbol(), src, dst, messageType, sizeBytes, roomId);
        }
        return String.format("[%tT] %s %s %s (%d B) client=%s room=%s",
                timestamp, direction.getSymbol(), direction.getLabel(),
                messageType, sizeBytes, clientId, roomId);
    }
}
