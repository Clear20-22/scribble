package com.scribble.network.messages;

import java.io.Serial;

public final class PlayAgainRequestMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean playAgain;

    public PlayAgainRequestMessage(boolean playAgain) {
        this.playAgain = playAgain;
    }

    public boolean isPlayAgain() {
        return playAgain;
    }
}
