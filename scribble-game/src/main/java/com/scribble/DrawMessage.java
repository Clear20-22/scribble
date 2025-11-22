package com.scribble;

/**
 * Message for drawing strokes.
 */
public class DrawMessage extends Message {
    private static final long serialVersionUID = 1L;
    private double startX, startY, endX, endY;
    private String color;
    private double brushSize;

    public DrawMessage(double startX, double startY, double endX, double endY, String color, double brushSize) {
        super("DRAW");
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.color = color;
        this.brushSize = brushSize;
    }

    public double getStartX() {
        return startX;
    }

    public double getStartY() {
        return startY;
    }

    public double getEndX() {
        return endX;
    }

    public double getEndY() {
        return endY;
    }

    public String getColor() {
        return color;
    }

    public double getBrushSize() {
        return brushSize;
    }
}