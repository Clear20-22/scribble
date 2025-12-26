package com.scribble.utils;

public final class Constants {
    private Constants() {
        throw new AssertionError("No instances");
    }

    public static final int DEFAULT_PORT = 5555;
    public static final String DEFAULT_HOST = "localhost";

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 12;
    public static final int ROUND_DURATION_SECONDS = 60;
    public static final int INTERMISSION_SECONDS = 3;
    public static final int CHOOSE_WORD_SECONDS = 10;
    public static final int DEFAULT_TOTAL_ROUNDS = 5;
    public static final int MIN_ROUND_SECONDS = 30;
    public static final int MAX_ROUND_SECONDS = 180;
    public static final int MIN_WORD_CHOICE_SECONDS = 5;
    public static final int MAX_WORD_CHOICE_SECONDS = 20;

    public static final int GUESS_BASE_POINTS = 100;
    public static final int GUESS_TIME_MULTIPLIER = 5;
    public static final int DRAWER_BONUS_PER_GUESS = 50;
    public static final int FIRST_GUESS_BONUS = 30;

    public static final int CHAT_MAX_LENGTH = 200;
    public static final int NAME_MIN_LENGTH = 3;
    public static final int NAME_MAX_LENGTH = 16;
    public static final String NAME_PATTERN = "^[a-zA-Z0-9_]+$";

    public static final int ROOM_NAME_MIN_LENGTH = 2;
    public static final int ROOM_NAME_MAX_LENGTH = 20;
    public static final String ROOM_NAME_PATTERN = "^[a-zA-Z0-9_-]+$";

    public static final int DRAW_RATE_LIMIT_MS = 5;
    public static final int DRAW_BATCH_INTERVAL_MS = 30;
    public static final int DRAW_BATCH_SIZE = 6;
    public static final int DRAW_MAX_POINTS = 32;
    public static final int CHAT_RATE_LIMIT_MS = 400;
    public static final int GUESS_RATE_LIMIT_MS = 300;
    public static final int MUTE_SECONDS = 15;
    public static final int WARN_VIOLATIONS = 2;
    public static final int MUTE_VIOLATIONS = 4;
    public static final int KICK_VIOLATIONS = 8;

    public static final int PING_INTERVAL_SECONDS = 5;
    public static final int PING_TIMEOUT_SECONDS = 25;

    public static final String WORDS_RESOURCE = "/words.txt";

    public static final int REVEAL_START_SECONDS = 10;
    public static final int REVEAL_EVERY_SECONDS = 2;

    public static final int HINT_FIRST_REVEAL_PERCENT = 50;
    public static final int HINT_SECOND_REVEAL_PERCENT = 25;

    public static final int RECONNECT_INITIAL_DELAY_MS = 1000;
    public static final int RECONNECT_MAX_DELAY_MS = 8000;
}
