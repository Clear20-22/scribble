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
    private final String roomCode;
    private final boolean allowSpectators;
    private final int wordChoiceSeconds;
    private final HintMode hintMode;
    private final WordDifficulty wordDifficulty;

    public RoomSettings(int maxPlayers, int roundTimeSeconds, int totalRounds, boolean privateRoom,
                        String roomCode, boolean allowSpectators, int wordChoiceSeconds,
                        HintMode hintMode, WordDifficulty wordDifficulty) {
        this.maxPlayers = maxPlayers;
        this.roundTimeSeconds = roundTimeSeconds;
        this.totalRounds = totalRounds;
        this.privateRoom = privateRoom;
        this.roomCode = roomCode;
        this.allowSpectators = allowSpectators;
        this.wordChoiceSeconds = wordChoiceSeconds;
        this.hintMode = hintMode;
        this.wordDifficulty = wordDifficulty;
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

    public String getRoomCode() {
        return roomCode;
    }

    public boolean isAllowSpectators() {
        return allowSpectators;
    }

    public int getWordChoiceSeconds() {
        return wordChoiceSeconds;
    }

    public HintMode getHintMode() {
        return hintMode;
    }

    public WordDifficulty getWordDifficulty() {
        return wordDifficulty;
    }
}
