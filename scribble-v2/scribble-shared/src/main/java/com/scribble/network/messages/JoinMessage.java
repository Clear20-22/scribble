package com.scribble.network.messages;

import java.io.Serial;

public final class JoinMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String roomName;
    private final String playerName;
    private final boolean spectator;

    public JoinMessage(String roomName, String playerName) {
        this(roomName, playerName, false);
    }

    public JoinMessage(String roomName, String playerName, boolean spectator) {
        this.roomName = roomName;
        this.playerName = playerName;
        this.spectator = spectator;
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
