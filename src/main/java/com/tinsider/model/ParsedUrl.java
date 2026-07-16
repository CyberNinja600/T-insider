package com.tinsider.model;

public class ParsedUrl {
    private String username;
    private String host;
    private int port = 22;

    public ParsedUrl(String username, String host, int port) {
        this.username = username;
        this.host = host;
        this.port = port;
    }

    public String getUsername() { return username; }
    public String getHost() { return host; }
    public int getPort() { return port; }

    public static ParsedUrl parseSshUrl(String url, String defaultUser) {
        String username = defaultUser;
        String host = "";
        int port = 22;

        String temp = url.trim();
        if (temp.startsWith("ssh://")) {
            temp = temp.substring(6);
        } else if (temp.startsWith("ssh:")) {
            temp = temp.substring(4);
        }

        int atIndex = temp.indexOf('@');
        if (atIndex != -1) {
            String userPart = temp.substring(0, atIndex);
            temp = temp.substring(atIndex + 1);
            int colonInUser = userPart.indexOf(':');
            if (colonInUser != -1) {
                username = userPart.substring(0, colonInUser);
            } else {
                username = userPart;
            }
        }

        int colonInHost = temp.indexOf(':');
        if (colonInHost != -1) {
            host = temp.substring(0, colonInHost);
            try {
                port = Integer.parseInt(temp.substring(colonInHost + 1));
            } catch (NumberFormatException ignored) {}
        } else {
            host = temp;
        }

        return new ParsedUrl(username, host, port);
    }
}
