package com.scribble.network.messages;

import java.io.Serial;

public final class CreateRoomMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String roomName;
    private final RoomSettings settings;

    public CreateRoomMessage(String roomName, RoomSettings settings) {
        this.roomName = roomName;
        this.settings = settings;
    }

    public String getRoomName() {
        return roomName;
    }

    public RoomSettings getSettings() {
        return settings;
    }
}
