package com.scribble;

import java.io.Serializable;

/**
 * Abstract base class for all messages sent between client and server.
 */
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private String type;

    public Message(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}