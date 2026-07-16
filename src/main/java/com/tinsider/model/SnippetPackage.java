package com.tinsider.model;

import java.util.UUID;

public class SnippetPackage {
    private String id;
    private String name;

    public SnippetPackage() {
        this.id = UUID.randomUUID().toString();
    }

    public SnippetPackage(String name) {
        this();
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String toString() {
        return name;
    }
}
