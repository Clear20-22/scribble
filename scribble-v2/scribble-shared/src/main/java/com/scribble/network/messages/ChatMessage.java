package com.scribble.network.messages;

import java.io.Serial;

public final class ChatMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String sender;
    private final String text;
    private final ChatType type;

    public ChatMessage(String text) {
        this(null, text, ChatType.CHAT);
    }

    public ChatMessage(String sender, String text, ChatType type) {
        this.sender = sender;
        this.text = text;
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public ChatType getType() {
        return type;
    }
}
