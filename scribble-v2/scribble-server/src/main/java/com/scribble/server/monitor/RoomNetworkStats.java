package com.scribble.server.monitor;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-room network statistics tracker.
 * Thread-safe using atomic operations.
 */
public final class RoomNetworkStats implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String roomId;
    private final long createdTime;

    // Message counters
    private final LongAdder messagesBroadcast = new LongAdder();
    private final LongAdder messagesReceived = new LongAdder();
    private final LongAdder bytesBroadcast = new LongAdder();
    private final LongAdder bytesReceived = new LongAdder();

    // Player tracking
    private volatile int currentPlayerCount;
    private volatile int currentSpectatorCount;
    private final LongAdder totalPlayersJoined = new LongAdder();
    private final LongAdder totalPlayersLeft = new LongAdder();

    // Game stats
    private final LongAdder roundsPlayed = new LongAdder();
    private final LongAdder drawMessages = new LongAdder();
    private final LongAdder chatMessages = new LongAdder();
    private final LongAdder guessAttempts = new LongAdder();
    private final LongAdder correctGuesses = new LongAdder();

    // Time series for graphs
    private static final int DEFAULT_RING_SIZE = getIntProperty("scribble.monitor.ringSize", 60);
    private final TimeSeriesRingBuffer trafficHistory = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);
    private volatile long lastTrafficCalcTime;
    private volatile long lastBytesSnapshot;

    public RoomNetworkStats(String roomId) {
        this.roomId = roomId;
        this.createdTime = System.currentTimeMillis();
        this.lastTrafficCalcTime = createdTime;
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

    public void recordBroadcast(int messageCount, int totalBytes) {
        messagesBroadcast.add(messageCount);
        bytesBroadcast.add(totalBytes);
    }

    public void recordMessageReceived(String messageType, int bytes) {
        messagesReceived.increment();
        bytesReceived.add(bytes);

        // Track specific message types
        switch (messageType) {
            case "DrawMessage" -> drawMessages.increment();
            case "ChatMessage" -> chatMessages.increment();
            case "GuessMessage" -> guessAttempts.increment();
        }
    }

    public void recordCorrectGuess() {
        correctGuesses.increment();
    }

    public void recordRoundCompleted() {
        roundsPlayed.increment();
    }

    public void recordPlayerJoined() {
        totalPlayersJoined.increment();
    }

    public void recordPlayerLeft() {
        totalPlayersLeft.increment();
    }

    /**
     * Convenience method: Record a player joining by their clientId.
     */
    public void recordPlayerJoin(String clientId) {
        recordPlayerJoined();
    }

    /**
     * Convenience method: Record a player leaving by their clientId.
     */
    public void recordPlayerLeave(String clientId) {
        recordPlayerLeft();
    }

    /**
     * Convenience method: Record a draw event.
     */
    public void recordDrawEvent() {
        drawMessages.increment();
    }

    /**
     * Convenience method: Record a guess attempt.
     * 
     * @param correct Whether the guess was correct
     */
    public void recordGuessAttempt(boolean correct) {
        guessAttempts.increment();
        if (correct) {
            correctGuesses.increment();
        }
    }

    /**
     * Convenience method: Record round starting.
     */
    public void recordRoundStart() {
        roundsPlayed.increment();
    }

    /**
     * Convenience method: Record a broadcast with just recipient count.
     */
    public void recordBroadcast(int recipientCount) {
        messagesBroadcast.add(recipientCount);
    }

    public void updatePlayerCounts(int players, int spectators) {
        this.currentPlayerCount = players;
        this.currentSpectatorCount = spectators;
    }

    /**
     * Calculate and record traffic sample. Call periodically (e.g., every second).
     */
    public void recordTrafficSample() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTrafficCalcTime;
        if (elapsed <= 0)
            return;

        long currentBytes = bytesBroadcast.sum() + bytesReceived.sum();
        double bytesPerSec = (currentBytes - lastBytesSnapshot) * 1000.0 / elapsed;
        trafficHistory.add(bytesPerSec);

        lastTrafficCalcTime = now;
        lastBytesSnapshot = currentBytes;
    }

    // --- Getters ---

    public String getRoomId() {
        return roomId;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public long getUptime() {
        return System.currentTimeMillis() - createdTime;
    }

    public long getMessagesBroadcast() {
        return messagesBroadcast.sum();
    }

    public long getMessagesReceived() {
        return messagesReceived.sum();
    }

    public long getBytesBroadcast() {
        return bytesBroadcast.sum();
    }

    public long getBytesReceived() {
        return bytesReceived.sum();
    }

    public int getCurrentPlayerCount() {
        return currentPlayerCount;
    }

    public int getCurrentSpectatorCount() {
        return currentSpectatorCount;
    }

    public long getTotalPlayersJoined() {
        return totalPlayersJoined.sum();
    }

    public long getTotalPlayersLeft() {
        return totalPlayersLeft.sum();
    }

    public long getRoundsPlayed() {
        return roundsPlayed.sum();
    }

    public long getDrawMessages() {
        return drawMessages.sum();
    }

    public long getChatMessages() {
        return chatMessages.sum();
    }

    public long getGuessAttempts() {
        return guessAttempts.sum();
    }

    public long getCorrectGuesses() {
        return correctGuesses.sum();
    }

    public double[] getTrafficHistory() {
        return trafficHistory.getValues();
    }

    /**
     * Create a snapshot for serialization.
     */
    public RoomStatsSnapshot toSnapshot() {
        return new RoomStatsSnapshot(
                roomId,
                createdTime,
                getUptime(),
                getMessagesBroadcast(),
                getMessagesReceived(),
                getBytesBroadcast(),
                getBytesReceived(),
                currentPlayerCount,
                currentSpectatorCount,
                getTotalPlayersJoined(),
                getTotalPlayersLeft(),
                getRoundsPlayed(),
                getDrawMessages(),
                getChatMessages(),
                getGuessAttempts(),
                getCorrectGuesses(),
                getTrafficHistory());
    }

    /**
     * Immutable snapshot of room stats.
     */
    public static class RoomStatsSnapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final String roomId;
        public final long createdTime;
        public final long uptime;
        public final long messagesBroadcast;
        public final long messagesReceived;
        public final long bytesBroadcast;
        public final long bytesReceived;
        public final int currentPlayerCount;
        public final int currentSpectatorCount;
        public final long totalPlayersJoined;
        public final long totalPlayersLeft;
        public final long roundsPlayed;
        public final long drawMessages;
        public final long chatMessages;
        public final long guessAttempts;
        public final long correctGuesses;
        public final double[] trafficHistory;

        public RoomStatsSnapshot(String roomId, long createdTime, long uptime,
                long messagesBroadcast, long messagesReceived,
                long bytesBroadcast, long bytesReceived,
                int currentPlayerCount, int currentSpectatorCount,
                long totalPlayersJoined, long totalPlayersLeft,
                long roundsPlayed, long drawMessages, long chatMessages,
                long guessAttempts, long correctGuesses,
                double[] trafficHistory) {
            this.roomId = roomId;
            this.createdTime = createdTime;
            this.uptime = uptime;
            this.messagesBroadcast = messagesBroadcast;
            this.messagesReceived = messagesReceived;
            this.bytesBroadcast = bytesBroadcast;
            this.bytesReceived = bytesReceived;
            this.currentPlayerCount = currentPlayerCount;
            this.currentSpectatorCount = currentSpectatorCount;
            this.totalPlayersJoined = totalPlayersJoined;
            this.totalPlayersLeft = totalPlayersLeft;
            this.roundsPlayed = roundsPlayed;
            this.drawMessages = drawMessages;
            this.chatMessages = chatMessages;
            this.guessAttempts = guessAttempts;
            this.correctGuesses = correctGuesses;
            this.trafficHistory = trafficHistory;
        }
    }
}
