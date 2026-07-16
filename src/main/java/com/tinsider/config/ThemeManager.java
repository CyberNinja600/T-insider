package com.tinsider.config;

import com.jediterm.core.Color;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class ThemeManager {
    private static final Properties config = new Properties();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        File configFile = new File("./themes/theme.config.properties");
        try {
            if (configFile.exists()) {
                try (FileInputStream in = new FileInputStream(configFile)) {
                    config.load(in);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        applyDefaults();
    }

    public static void reloadConfig() {
        config.clear();
        loadConfig();
    }

    private static void applyDefaults() {
        // Establish fallbacks if properties or keys are missing inside the configuration file
        setDefaultIfMissing("app.window.background", "BackgroundColor");
        setDefaultIfMissing("app.window.border", "SelectionColor");
        setDefaultIfMissing("titlebar.background", "BackgroundColor");
        setDefaultIfMissing("titlebar.border", "SelectionColor");
        setDefaultIfMissing("toolbar.background", "BackgroundColor");
        setDefaultIfMissing("toolbar.border", "SelectionColor");
        setDefaultIfMissing("sidebar.background", "BackgroundColor");
        setDefaultIfMissing("sidebar.border", "SelectionColor");
        setDefaultIfMissing("sidebar.nav.foreground", "TextColor");
        setDefaultIfMissing("sidebar.nav.hover.background", "SelectionColor");
        setDefaultIfMissing("sidebar.nav.hover.foreground", "CursorColor");
        setDefaultIfMissing("sidebar.nav.selected.background", "SelectionColor");
        setDefaultIfMissing("sidebar.nav.selected.border", "CursorColor");
        setDefaultIfMissing("global.label.foreground", "TextColor");
        setDefaultIfMissing("snippetLauncher.label", "CursorColor");

        setDefaultIfMissing("terminal.background", "BackgroundColor");
        setDefaultIfMissing("terminal.foreground", "TextColor");
        setDefaultIfMissing("terminal.cursor", "CursorColor");
        setDefaultIfMissing("terminal.selection", "SelectionColor");

        setDefaultIfMissing("tabs.header.background", "BackgroundColor");
        setDefaultIfMissing("tabs.tab.inactive.background", "BackgroundColor");
        setDefaultIfMissing("tabs.tab.inactive.foreground", "TextColor");
        setDefaultIfMissing("tabs.tab.active.background", "BackgroundColor");
        setDefaultIfMissing("tabs.tab.active.foreground", "CursorColor");
        setDefaultIfMissing("tabs.tab.active.border", "CursorColor");
        setDefaultIfMissing("tabs.tab.hover.background", "SelectionColor");
        setDefaultIfMissing("tabs.plus.foreground", "TextColor");
        setDefaultIfMissing("tabs.plus.hover.background", "SelectionColor");
        setDefaultIfMissing("tabs.plus.hover.foreground", "CursorColor");

        setDefaultIfMissing("cards.list.background", "BackgroundColor");
        setDefaultIfMissing("cards.list.border", "SelectionColor");
        setDefaultIfMissing("cards.item.background", "SelectionColor");
        setDefaultIfMissing("cards.item.border", "BackgroundColor");
        setDefaultIfMissing("cards.item.title", "TextColor");
        setDefaultIfMissing("cards.item.subtext", "TextColor");
        setDefaultIfMissing("cards.item.badge.background", "CursorColor");
        setDefaultIfMissing("cards.item.badge.foreground", "TextColor");
        setDefaultIfMissing("cards.item.badge.hover.background", "SelectionColor");
        setDefaultIfMissing("cards.item.badge.hover.foreground", "CursorColor");
        setDefaultIfMissing("cards.item.badge.selected.background", "BackgroundColor");
        setDefaultIfMissing("cards.item.badge.selected.foreground", "CursorColor");
        setDefaultIfMissing("cards.item.hover.background", "SelectionColor");
        setDefaultIfMissing("cards.item.hover.border", "SelectionColor");
        setDefaultIfMissing("cards.item.selected.background", "SelectionColor");
        setDefaultIfMissing("cards.item.selected.border", "CursorColor");
        setDefaultIfMissing("cards.item.selected.glow", "CursorColor");

        setDefaultIfMissing("controls.button.background", "SelectionColor");
        setDefaultIfMissing("controls.button.border", "BackgroundColor");
        setDefaultIfMissing("controls.button.foreground", "TextColor");
        setDefaultIfMissing("controls.button.hover.background", "SelectionColor");
        setDefaultIfMissing("controls.combo.background", "SelectionColor");
        setDefaultIfMissing("controls.combo.border", "BackgroundColor");
        setDefaultIfMissing("controls.combo.foreground", "TextColor");
        setDefaultIfMissing("controls.input.background", "SelectionColor");
        setDefaultIfMissing("controls.input.border", "BackgroundColor");
        setDefaultIfMissing("controls.input.foreground", "TextColor");

        setDefaultIfMissing("search.bar.background", "SelectionColor");
        setDefaultIfMissing("search.bar.border", "BackgroundColor");
        setDefaultIfMissing("search.bar.foreground", "TextColor");
        setDefaultIfMissing("search.bar.hover.background", "SelectionColor");
        setDefaultIfMissing("search.bar.active.border", "CursorColor");
        setDefaultIfMissing("search.bar.active.background", "SelectionColor");
        setDefaultIfMissing("search.bar.background.opacity", "1.0");

        setDefaultIfMissing("dropdown.list.background", "BackgroundColor");
        setDefaultIfMissing("dropdown.list.border", "SelectionColor");
        setDefaultIfMissing("dropdown.item.background", "BackgroundColor");
        setDefaultIfMissing("dropdown.item.foreground", "TextColor");
        setDefaultIfMissing("dropdown.item.hover.background", "SelectionColor");
        setDefaultIfMissing("dropdown.item.hover.foreground", "CursorColor");

        setDefaultIfMissing("titlebar.button.foreground", "TextColor");
        setDefaultIfMissing("titlebar.button.hover.background", "SelectionColor");
        setDefaultIfMissing("titlebar.button.hover.foreground", "CursorColor");
        setDefaultIfMissing("titlebar.close.hover.background", "CursorColor");
        setDefaultIfMissing("titlebar.close.hover.foreground", "TextColor");
    }

    private static void setDefaultIfMissing(String key, String plistValue) {
        if (!config.containsKey(key)) {
            config.setProperty(key, plistValue);
        }
    }

    private static Color parseColorFromPlist(Map<String, String> plist, String key, Color fallback) {
        String base64 = plist.get(key);
        if (base64 == null) return fallback;
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            String ascii = new String(decoded, StandardCharsets.US_ASCII);
            Pattern pattern = Pattern.compile("([01]\\.[0-9]+|1|0)\\s+([01]\\.[0-9]+|1|0)\\s+([01]\\.[0-9]+|1|0)");
            Matcher matcher = pattern.matcher(ascii);
            if (matcher.find()) {
                float r = Float.parseFloat(matcher.group(1));
                float g = Float.parseFloat(matcher.group(2));
                float b = Float.parseFloat(matcher.group(3));
                return new Color(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    public static TerminalTheme loadThemeFromFile(File file) {
        Map<String, String> plist = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            doc.getDocumentElement().normalize();
            
            NodeList keyList = doc.getElementsByTagName("key");
            NodeList dataList = doc.getElementsByTagName("data");
            
            for (int i = 0; i < keyList.getLength(); i++) {
                String key = keyList.item(i).getTextContent();
                if (i < dataList.getLength()) {
                    String data = dataList.item(i).getTextContent();
                    plist.put(key, data.trim());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String themeName = file.getName().replace(".terminal", "");
        if (themeName.length() > 0) {
            themeName = themeName.substring(0, 1).toUpperCase() + themeName.substring(1);
        }

        TerminalTheme theme = new TerminalTheme(themeName);

        // Core fallback mappings for plist key defaults
        Map<String, Color> fallbacks = new HashMap<>();
        fallbacks.put("BackgroundColor", new Color(9, 15, 34));
        fallbacks.put("TextColor", new Color(226, 232, 240));
        fallbacks.put("CursorColor", new Color(0, 173, 181));
        fallbacks.put("SelectionColor", new Color(22, 27, 48));

        // Dynamically resolve each decoupled property color from XML plist
        for (String propName : config.stringPropertyNames()) {
            String plistKey = config.getProperty(propName).trim();
            Color fb = fallbacks.getOrDefault(plistKey, new Color(128, 128, 128));
            theme.setColor(propName, parseColorFromPlist(plist, plistKey, fb));
        }

        String[] ansiKeys = {
            "ANSIBlackColor", "ANSIRedColor", "ANSIGreenColor", "ANSIYellowColor",
            "ANSIBlueColor", "ANSIMagentaColor", "ANSICyanColor", "ANSIWhiteColor",
            "ANSIBrightBlackColor", "ANSIBrightRedColor", "ANSIBrightGreenColor", "ANSIBrightYellowColor",
            "ANSIBrightBlueColor", "ANSIBrightMagentaColor", "ANSIBrightCyanColor", "ANSIBrightWhiteColor"
        };

        for (int i = 0; i < 16; i++) {
            Color fallback = new Color(128, 128, 128);
            theme.setAnsiColor(i, parseColorFromPlist(plist, ansiKeys[i], fallback));
        }

        return theme;
    }

    public static String getProperty(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }
}
