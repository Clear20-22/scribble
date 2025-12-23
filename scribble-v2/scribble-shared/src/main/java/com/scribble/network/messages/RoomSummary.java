package com.scribble.network.messages;

import java.io.Serial;
import java.io.Serializable;

/**
 * Lightweight summary for lobby room list.
 */
public final class RoomSummary implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final int playerCount;
    private final boolean inGame;
    private final int roundNumber;

    public RoomSummary(String name, int playerCount, boolean inGame, int roundNumber) {
        this.name = name;
        this.playerCount = playerCount;
        this.inGame = inGame;
        this.roundNumber = roundNumber;
    }

    public String getName() {
        return name;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public boolean isInGame() {
        return inGame;
    }

    public int getRoundNumber() {
        return roundNumber;
    }
}
