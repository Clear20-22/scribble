package com.scribble;

/**
 * Message for updating player list.
 */
public class PlayerListMessage extends Message {
    private static final long serialVersionUID = 1L;
    private java.util.List<String> players;

    public PlayerListMessage(java.util.List<String> players) {
        super("PLAYER_LIST");
        this.players = players;
    }

    public java.util.List<String> getPlayers() {
        return players;
    }
}