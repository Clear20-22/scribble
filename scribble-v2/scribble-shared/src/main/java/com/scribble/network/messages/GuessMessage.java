package com.scribble.network.messages;

import java.io.Serial;

public final class GuessMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String text;

    public GuessMessage(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
