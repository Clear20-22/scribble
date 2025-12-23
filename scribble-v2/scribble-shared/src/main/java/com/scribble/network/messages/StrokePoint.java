package com.scribble.network.messages;

import java.io.Serial;
import java.io.Serializable;

/**
 * A single point in a stroke.
 */
public final class StrokePoint implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final double x;
    private final double y;

    public StrokePoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
