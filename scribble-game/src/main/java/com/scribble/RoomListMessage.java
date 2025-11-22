package com.scribble;

/**
 * Message containing list of available rooms.
 */
public class RoomListMessage extends Message {
    private static final long serialVersionUID = 1L;
    private java.util.List<String> rooms;

    public RoomListMessage(java.util.List<String> rooms) {
        super("ROOM_LIST");
        this.rooms = rooms;
    }

    public java.util.List<String> getRooms() {
        return rooms;
    }
}