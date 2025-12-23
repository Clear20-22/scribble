package com.scribble.network.messages;

import java.io.Serial;

public final class GameStateMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String currentDrawer;
    private final String word;
    private final int timeLeft;
    private final int roundNumber;
    private final GamePhase phase;
    private final boolean drawer;

    public GameStateMessage(String currentDrawer, String word, int timeLeft, int roundNumber, GamePhase phase, boolean drawer) {
        this.currentDrawer = currentDrawer;
        this.word = word;
        this.timeLeft = timeLeft;
        this.roundNumber = roundNumber;
        this.phase = phase;
        this.drawer = drawer;
    }

    public String getCurrentDrawer() {
        return currentDrawer;
    }

    public String getWord() {
        return word;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public boolean isDrawer() {
        return drawer;
    }
}
