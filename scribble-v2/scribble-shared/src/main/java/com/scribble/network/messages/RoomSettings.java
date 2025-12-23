package com.scribble.network.messages;

import java.io.Serial;
import java.io.Serializable;

public final class RoomSettings implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int maxPlayers;
    private final int roundTimeSeconds;
    private final int totalRounds;
    private final boolean privateRoom;

    public RoomSettings(int maxPlayers, int roundTimeSeconds, int totalRounds, boolean privateRoom) {
        this.maxPlayers = maxPlayers;
        this.roundTimeSeconds = roundTimeSeconds;
        this.totalRounds = totalRounds;
        this.privateRoom = privateRoom;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getRoundTimeSeconds() {
        return roundTimeSeconds;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public boolean isPrivateRoom() {
        return privateRoom;
    }
}
