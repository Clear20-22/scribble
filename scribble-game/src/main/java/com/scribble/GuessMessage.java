package com.scribble;

/**
 * Message for guessing the word.
 */
public class GuessMessage extends Message {
    private static final long serialVersionUID = 1L;
    private String guess;

    public GuessMessage(String guess) {
        super("GUESS");
        this.guess = guess;
    }

    public String getGuess() {
        return guess;
    }
}