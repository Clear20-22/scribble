package com.scribble.network.messages;

import java.io.Serial;

public final class UndoMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String strokeId;

    public UndoMessage(String strokeId) {
        this.strokeId = strokeId;
    }

    public String getStrokeId() {
        return strokeId;
    }
}
