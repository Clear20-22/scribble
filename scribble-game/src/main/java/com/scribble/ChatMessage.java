package com.scribble;

/**
 * Message for chat.
 */
public class ChatMessage extends Message {
    private static final long serialVersionUID = 1L;
    private String sender;
    private String text;

    public ChatMessage(String sender, String text) {
        super("CHAT");
        this.sender = sender;
        this.text = text;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }
}