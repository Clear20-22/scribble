package com.scribble.utils;

public final class PaletteColor {
    private final String name;
    private final String hex;

    public PaletteColor(String name, String hex) {
        this.name = name;
        this.hex = hex;
    }

    public String getName() {
        return name;
    }

    public String getHex() {
        return hex;
    }
}
