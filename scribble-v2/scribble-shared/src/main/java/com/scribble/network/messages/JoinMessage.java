package com.scribble.network.messages;

import java.io.Serial;

public final class JoinMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String roomName;
    private final String playerName;

    public JoinMessage(String roomName, String playerName) {
        this.roomName = roomName;
        this.playerName = playerName;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getPlayerName() {
        return playerName;
    }
}
