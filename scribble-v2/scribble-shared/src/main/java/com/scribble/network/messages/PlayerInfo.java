package com.scribble.network.messages;

import java.io.Serial;
import java.io.Serializable;

/**
 * Player info for scoreboards.
 */
public final class PlayerInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final int score;
    private final boolean drawer;
    private final boolean guessed;

    public PlayerInfo(String name, int score, boolean drawer, boolean guessed) {
        this.name = name;
        this.score = score;
        this.drawer = drawer;
        this.guessed = guessed;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public boolean isDrawer() {
        return drawer;
    }

    public boolean hasGuessed() {
        return guessed;
    }
}
