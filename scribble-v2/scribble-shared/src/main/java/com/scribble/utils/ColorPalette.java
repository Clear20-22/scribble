package com.scribble.utils;

import java.util.List;

public final class ColorPalette {
    private static final List<PaletteColor> COLORS = List.of(
            new PaletteColor("Black", "#1F1F1F"),
            new PaletteColor("Red", "#E74C3C"),
            new PaletteColor("Orange", "#F39C12"),
            new PaletteColor("Yellow", "#F1C40F"),
            new PaletteColor("Green", "#2ECC71"),
            new PaletteColor("Blue", "#3498DB"),
            new PaletteColor("Purple", "#9B59B6"),
            new PaletteColor("Brown", "#8E5A3C")
    );

    private ColorPalette() {
        throw new AssertionError("No instances");
    }

    public static List<PaletteColor> getColors() {
        return COLORS;
    }
}
