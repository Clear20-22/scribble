package com.scribble.network.messages;

import java.io.Serial;
import java.util.List;

public final class WordChoicesMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> choices;
    private final int secondsToChoose;

    public WordChoicesMessage(List<String> choices, int secondsToChoose) {
        this.choices = List.copyOf(choices);
        this.secondsToChoose = secondsToChoose;
    }

    public List<String> getChoices() {
        return choices;
    }

    public int getSecondsToChoose() {
        return secondsToChoose;
    }
}
