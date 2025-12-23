package com.scribble.network.messages;

import java.io.Serial;
import java.util.List;

public final class DrawMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String strokeId;
    private final List<StrokePoint> points;
    private final String color;
    private final double brushSize;
    private final boolean eraser;

    public DrawMessage(String strokeId, List<StrokePoint> points, String color, double brushSize, boolean eraser) {
        this.strokeId = strokeId;
        this.points = List.copyOf(points);
        this.color = color;
        this.brushSize = brushSize;
        this.eraser = eraser;
    }

    public String getStrokeId() {
        return strokeId;
    }

    public List<StrokePoint> getPoints() {
        return points;
    }

    public String getColor() {
        return color;
    }

    public double getBrushSize() {
        return brushSize;
    }

    public boolean isEraser() {
        return eraser;
    }
}
