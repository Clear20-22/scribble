package com.scribble.network.messages;

import java.io.Serial;

public final class GuessHintMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String hint;

    public GuessHintMessage(String hint) {
        this.hint = hint;
    }

    public String getHint() {
        return hint;
    }
}
