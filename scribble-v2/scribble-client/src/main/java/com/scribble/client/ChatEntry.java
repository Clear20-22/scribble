package com.scribble.client;

import com.scribble.network.messages.ChatType;

public final class ChatEntry {
    private final String text;
    private final ChatType type;

    public ChatEntry(String text, ChatType type) {
        this.text = text;
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public ChatType getType() {
        return type;
    }
}
