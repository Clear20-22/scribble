package com.scribble.game;

import com.scribble.utils.Constants;

public final class Scoring {
    private Scoring() {
        throw new AssertionError("No instances");
    }

    public static int guessPoints(int timeLeft, int totalTime, int guessOrder) {
        int points = Constants.GUESS_BASE_POINTS + timeLeft * Constants.GUESS_TIME_MULTIPLIER;
        if (guessOrder == 0) {
            points += Constants.FIRST_GUESS_BONUS;
        }
        return Math.min(points, Constants.GUESS_BASE_POINTS + totalTime * Constants.GUESS_TIME_MULTIPLIER);
    }
}
