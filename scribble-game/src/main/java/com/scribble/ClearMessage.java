package com.scribble;

/**
 * Message for clearing the canvas.
 */
public class ClearMessage extends Message {
    private static final long serialVersionUID = 1L;

    public ClearMessage() {
        super("CLEAR");
    }
}