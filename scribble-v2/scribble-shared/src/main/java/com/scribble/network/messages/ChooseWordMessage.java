package com.scribble.network.messages;

import java.io.Serial;

public final class ChooseWordMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String word;

    public ChooseWordMessage(String word) {
        this.word = word;
    }

    public String getWord() {
        return word;
    }
}
