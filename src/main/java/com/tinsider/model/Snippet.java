package com.tinsider.model;

import java.util.UUID;

public class Snippet {
    private String id;
    private String title;
    private String code;
    private String packageId;

    public Snippet() {
        this.id = UUID.randomUUID().toString();
    }

    public Snippet(String title, String code) {
        this();
        this.title = title;
        this.code = code;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }

    @Override
    public String toString() {
        return title;
    }
}
