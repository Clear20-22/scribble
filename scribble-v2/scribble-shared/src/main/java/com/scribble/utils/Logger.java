package com.scribble.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Logger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String name;

    private Logger(String name) {
        this.name = name;
    }

    public static Logger getLogger(Class<?> type) {
        return new Logger(type.getSimpleName());
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void warn(String message) {
        log("WARN", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }

    public void error(String message, Throwable t) {
        log("ERROR", message + " - " + t.getMessage());
    }

    private void log(String level, String message) {
        String ts = LocalDateTime.now().format(FORMATTER);
        System.out.println(ts + " [" + level + "] " + name + " - " + message);
    }
}
