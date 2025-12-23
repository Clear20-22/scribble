package com.scribble.network.messages;

import java.io.Serial;
import java.util.List;

public final class RoomListMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<RoomSummary> rooms;

    public RoomListMessage(List<RoomSummary> rooms) {
        this.rooms = List.copyOf(rooms);
    }

    public List<RoomSummary> getRooms() {
        return rooms;
    }
}
