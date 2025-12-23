package com.scribble.network.messages;

import java.io.Serial;

public final class PingMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long timestamp;

    public PingMessage(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
