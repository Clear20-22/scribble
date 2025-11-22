package com.scribble;

/**
 * Message for game state updates.
 */
public class GameStateMessage extends Message {
    private static final long serialVersionUID = 1L;
    private String currentDrawer;
    private String word;
    private int timeLeft;
    private java.util.Map<String, Integer> scores;

    public GameStateMessage(String currentDrawer, String word, int timeLeft, java.util.Map<String, Integer> scores) {
        super("GAME_STATE");
        this.currentDrawer = currentDrawer;
        this.word = word;
        this.timeLeft = timeLeft;
        this.scores = scores;
    }

    public String getCurrentDrawer() {
        return currentDrawer;
    }

    public String getWord() {
        return word;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public java.util.Map<String, Integer> getScores() {
        return scores;
    }
}