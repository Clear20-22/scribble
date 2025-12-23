package com.scribble.network.messages;

import java.io.Serial;
import java.util.List;

public final class PlayerListMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<PlayerInfo> players;

    public PlayerListMessage(List<PlayerInfo> players) {
        this.players = List.copyOf(players);
    }

    public List<PlayerInfo> getPlayers() {
        return players;
    }
}
