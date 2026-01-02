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

    public PacketLogEntry(long timestamp, Direction direction, String messageType,
            int sizeBytes, String clientId, String roomId) {
        this.timestamp = timestamp;
        this.direction = direction;
        this.messageType = messageType;
        this.sizeBytes = sizeBytes;
        this.clientId = clientId;
        this.roomId = roomId;
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

    @Override
    public String toString() {
        return String.format("[%tT] %s %s %s (%d B) client=%s room=%s",
                timestamp, direction.getSymbol(), direction.getLabel(),
                messageType, sizeBytes, clientId, roomId);
    }
}
