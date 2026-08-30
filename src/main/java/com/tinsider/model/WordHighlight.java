package com.tinsider.model;

import java.util.UUID;

public class WordHighlight {
    private String id;
    private String word;
    private String colorHex;
    private boolean caseInsensitive;

    public WordHighlight() {
        this.id = UUID.randomUUID().toString();
    }

    public WordHighlight(String word, String colorHex) {
        this();
        this.word = word;
        this.colorHex = colorHex;
        this.caseInsensitive = false;
    }

    public WordHighlight(String word, String colorHex, boolean caseInsensitive) {
        this();
        this.word = word;
        this.colorHex = colorHex;
        this.caseInsensitive = caseInsensitive;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    public void setCaseInsensitive(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    @Override
    public String toString() {
        return word + " (" + colorHex + ")";
    }
}
