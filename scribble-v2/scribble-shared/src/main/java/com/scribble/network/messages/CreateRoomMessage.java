package com.scribble.network.messages;

import java.io.Serial;

public final class CreateRoomMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String roomName;

    public CreateRoomMessage(String roomName) {
        this.roomName = roomName;
    }

    public String getRoomName() {
        return roomName;
    }
}
