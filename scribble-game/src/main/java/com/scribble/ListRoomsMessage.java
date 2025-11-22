package com.scribble;

/**
 * Message to request list of available rooms.
 */
public class ListRoomsMessage extends Message {
    private static final long serialVersionUID = 1L;

    public ListRoomsMessage() {
        super("LIST_ROOMS");
    }
}