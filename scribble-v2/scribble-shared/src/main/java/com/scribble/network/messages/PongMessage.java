package com.scribble.network.messages;

import java.io.Serial;

public final class PongMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long timestamp;

    public PongMessage(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
