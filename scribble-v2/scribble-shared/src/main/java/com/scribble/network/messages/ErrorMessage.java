package com.scribble.network.messages;

import java.io.Serial;

public final class ErrorMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String message;

    public ErrorMessage(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
