package com.scribble.utils;

import java.util.regex.Pattern;

public final class ValidationUtils {
    private static final Pattern NAME_REGEX = Pattern.compile(Constants.NAME_PATTERN);
    private static final Pattern ROOM_REGEX = Pattern.compile(Constants.ROOM_NAME_PATTERN);

    private ValidationUtils() {
        throw new AssertionError("No instances");
    }

    public static ValidationResult validatePlayerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Name cannot be empty");
        }
        String trimmed = name.trim();
        if (trimmed.length() < Constants.NAME_MIN_LENGTH || trimmed.length() > Constants.NAME_MAX_LENGTH) {
            return ValidationResult.error("Name must be " + Constants.NAME_MIN_LENGTH + "-" + Constants.NAME_MAX_LENGTH + " characters");
        }
        if (!NAME_REGEX.matcher(trimmed).matches()) {
            return ValidationResult.error("Name can only contain letters, numbers, and underscore");
        }
        return ValidationResult.success();
    }

    public static ValidationResult validateRoomName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Room name cannot be empty");
        }
        String trimmed = name.trim();
        if (trimmed.length() < Constants.ROOM_NAME_MIN_LENGTH || trimmed.length() > Constants.ROOM_NAME_MAX_LENGTH) {
            return ValidationResult.error("Room name must be " + Constants.ROOM_NAME_MIN_LENGTH + "-" + Constants.ROOM_NAME_MAX_LENGTH + " characters");
        }
        if (!ROOM_REGEX.matcher(trimmed).matches()) {
            return ValidationResult.error("Room name can only contain letters, numbers, hyphen, and underscore");
        }
        return ValidationResult.success();
    }

    public static ValidationResult validateChatMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return ValidationResult.error("Message cannot be empty");
        }
        if (text.length() > Constants.CHAT_MAX_LENGTH) {
            return ValidationResult.error("Message too long");
        }
        return ValidationResult.success();
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
