package com.scribble.network.messages;

import java.io.Serial;

public final class JoinResultMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String reason;
    private final String roomName;
    private final String playerName;
    private final boolean spectator;

    public JoinResultMessage(boolean success, String reason, String roomName, String playerName, boolean spectator) {
        this.success = success;
        this.reason = reason;
        this.roomName = roomName;
        this.playerName = playerName;
        this.spectator = spectator;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isSpectator() {
        return spectator;
    }
}
