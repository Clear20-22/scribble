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
    private final GamePhase phase;
    private final int roundNumber;
    private final RoomSettings settings;

    public RoomSummary(String name, int playerCount, GamePhase phase, int roundNumber, RoomSettings settings) {
        this.name = name;
        this.playerCount = playerCount;
        this.phase = phase;
        this.roundNumber = roundNumber;
        this.settings = settings;
    }

    public String getName() {
        return name;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public RoomSettings getSettings() {
        return settings;
    }
}
