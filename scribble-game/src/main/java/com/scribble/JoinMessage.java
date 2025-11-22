package com.scribble;

/**
 * Message for joining a room.
 */
public class JoinMessage extends Message {
    private static final long serialVersionUID = 1L;
    private String playerName;
    private String roomName;

    public JoinMessage(String playerName, String roomName) {
        super("JOIN");
        this.playerName = playerName;
        this.roomName = roomName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRoomName() {
        return roomName;
    }
}