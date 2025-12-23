package com.scribble.network.messages;

import java.io.Serial;
import java.util.List;

public final class StrokeHistoryMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<DrawMessage> strokes;

    public StrokeHistoryMessage(List<DrawMessage> strokes) {
        this.strokes = List.copyOf(strokes);
    }

    public List<DrawMessage> getStrokes() {
        return strokes;
    }
}
