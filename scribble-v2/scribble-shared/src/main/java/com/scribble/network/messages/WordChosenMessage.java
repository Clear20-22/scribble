package com.scribble.network.messages;

import java.io.Serial;

public final class WordChosenMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String word;
    private final boolean drawer;

    public WordChosenMessage(String word, boolean drawer) {
        this.word = word;
        this.drawer = drawer;
    }

    public String getWord() {
        return word;
    }

    public boolean isDrawer() {
        return drawer;
    }
}
