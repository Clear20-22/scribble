package com.scribble.game;

import com.scribble.utils.Constants;

public final class Scoring {
    private Scoring() {
        throw new AssertionError("No instances");
    }

    public static int guessPoints(int timeLeft) {
        int points = Constants.GUESS_BASE_POINTS + timeLeft * Constants.GUESS_TIME_MULTIPLIER;
        return Math.min(points, Constants.GUESS_BASE_POINTS + Constants.ROUND_DURATION_SECONDS * Constants.GUESS_TIME_MULTIPLIER);
    }
}
