package com.tinsider.model;

import java.util.UUID;

public class HostProfile {
    private String id;
    private String name;
    private String ip;
    private int port;
    private String username;
    private String password;
    private String url;

    public HostProfile() {
        this.id = UUID.randomUUID().toString();
    }

    public HostProfile(String name, String ip, int port, String username, String password) {
        this();
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    @Override
    public String toString() {
        if (url != null && !url.isEmpty()) {
            return name + " (" + url + ")";
        }
        return name + " (" + username + "@" + ip + ":" + port + ")";
    }
}
