package com.editor.model;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class StyledChar {
    private char character;
    private String fontName;
    private double fontSize;
    private Color color;

    public StyledChar(char character, Font font, Color color) {
        this.character = character;
        this.fontName = font.getName();
        this.fontSize = font.getSize();
        this.color = color;
    }

    // Getters y Setters
    public char getCharacter() { return character; }
    public String getFontName() { return fontName; }
    public double getFontSize() { return fontSize; }
    public Color getColor() { return color; }
}
