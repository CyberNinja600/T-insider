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
        File configFile = new File("./config/theme.config.properties");
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

    public static void populateThemeColors(TerminalTheme theme, Color bg, Color text, Color cursor, Color selection) {
        Map<String, Color> baseColors = new HashMap<>();
        baseColors.put("BackgroundColor", bg);
        baseColors.put("TextColor", text);
        baseColors.put("CursorColor", cursor);
        baseColors.put("SelectionColor", selection);

        for (String propName : config.stringPropertyNames()) {
            String plistKey = config.getProperty(propName);
            if (plistKey != null) {
                plistKey = plistKey.trim();
                Color color = baseColors.get(plistKey);
                if (color != null) {
                    theme.setColor(propName, color);
                }
            }
        }
    }

    private static void applyDefaults() {
        // Establish fallbacks if properties or keys are missing inside the configuration file
        setDefaultIfMissing("app.window.background", "SelectionColor");
        setDefaultIfMissing("app.window.background.gradient.end", "BackgroundColor");
        setDefaultIfMissing("app.window.background.gradient.ratio", "10% 90%");
        setDefaultIfMissing("app.window.background.gradient.start", "SelectionColor");
        setDefaultIfMissing("app.window.background.opacity", "1.0");
        setDefaultIfMissing("app.window.border", "SelectionColor");
        setDefaultIfMissing("app.window.border.opacity", "1.0");
        setDefaultIfMissing("cards.item.background", "BackgroundColor");
        setDefaultIfMissing("cards.item.background.opacity", ".5");
        setDefaultIfMissing("cards.item.badge.background", "BackgroundColor");
        setDefaultIfMissing("cards.item.badge.foreground", "TextColor");
        setDefaultIfMissing("cards.item.badge.hover.background", "SelectionColor");
        setDefaultIfMissing("cards.item.badge.hover.foreground", "CursorColor");
        setDefaultIfMissing("cards.item.badge.hover.opacity", "1");
        setDefaultIfMissing("cards.item.badge.opacity", "0.15");
        setDefaultIfMissing("cards.item.badge.selected.background", "SelectionColor");
        setDefaultIfMissing("cards.item.badge.selected.foreground", "CursorColor");
        setDefaultIfMissing("cards.item.badge.selected.opacity", "1.0");
        setDefaultIfMissing("cards.item.border", "SelectionColor");
        setDefaultIfMissing("cards.item.border.opacity", "1.0");
        setDefaultIfMissing("cards.item.hover.background", "CursorColor");
        setDefaultIfMissing("cards.item.hover.background.opacity", ".15");
        setDefaultIfMissing("cards.item.hover.border", "CursorColor");
        setDefaultIfMissing("cards.item.hover.border.opacity", "1.0");
        setDefaultIfMissing("cards.item.selected.background", "BackgroundColor");
        setDefaultIfMissing("cards.item.selected.background.opacity", "1.0");
        setDefaultIfMissing("cards.item.selected.border", "CursorColor");
        setDefaultIfMissing("cards.item.selected.border.opacity", "1.0");
        setDefaultIfMissing("cards.item.selected.glow", "CursorColor");
        setDefaultIfMissing("cards.item.subtext", "TextColor");
        setDefaultIfMissing("cards.item.title", "TextColor");
        setDefaultIfMissing("cards.list.background", "SelectionColor");
        setDefaultIfMissing("cards.list.border", "SelectionColor");
        setDefaultIfMissing("connecting.background", "BackgroundColor");
        setDefaultIfMissing("connecting.foreground", "TextColor");
        setDefaultIfMissing("connecting.icon", "CursorColor");
        setDefaultIfMissing("controls.button.background", "BackgroundColor");
        setDefaultIfMissing("controls.button.background.opacity", "1.0");
        setDefaultIfMissing("controls.button.border", "BackgroundColor");
        setDefaultIfMissing("controls.button.border.opacity", "1.0");
        setDefaultIfMissing("controls.button.foreground", "TextColor");
        setDefaultIfMissing("controls.button.hover.background", "CursorColor");
        setDefaultIfMissing("controls.button.hover.background.opacity", "1.0");
        setDefaultIfMissing("controls.button.hover.border", "CursorColor");
        setDefaultIfMissing("controls.button.hover.border.opacity", "1.0");
        setDefaultIfMissing("controls.button.hover.foreground", "BackgroundColor");
        setDefaultIfMissing("controls.button.hover.icon", "BackgroundColor");
        setDefaultIfMissing("controls.button.icon", "TextColor");
        setDefaultIfMissing("controls.combo.background", "BackgroundColor");
        setDefaultIfMissing("controls.combo.border", "SelectionColor");
        setDefaultIfMissing("controls.combo.foreground", "TextColor");
        setDefaultIfMissing("controls.colorpicker.background", "BackgroundColor");
        setDefaultIfMissing("controls.colorpicker.border", "BackgroundColor");
        setDefaultIfMissing("controls.colorpicker.foreground", "TextColor");
        setDefaultIfMissing("controls.colorpicker.popup.background", "BackgroundColor");
        setDefaultIfMissing("controls.colorpicker.popup.border", "SelectionColor");
        setDefaultIfMissing("controls.colorpicker.custom.background", "BackgroundColor");
        setDefaultIfMissing("controls.colorpicker.custom.controls.background", "SelectionColor");
        setDefaultIfMissing("controls.colorpicker.custom.button.background", "BackgroundColor");
        setDefaultIfMissing("controls.colorpicker.custom.button.border", "BackgroundColor");
        setDefaultIfMissing("controls.colorpicker.custom.button.foreground", "TextColor");
        setDefaultIfMissing("controls.colorpicker.custom.button.hover.background", "CursorColor");
        setDefaultIfMissing("controls.colorpicker.custom.button.hover.border", "CursorColor");
        setDefaultIfMissing("controls.colorpicker.custom.button.hover.foreground", "BackgroundColor");
        setDefaultIfMissing("controls.colorpicker.custom.toggle.background", "BackgroundColor");
        setDefaultIfMissing("controls.colorpicker.custom.toggle.border", "SelectionColor");
        setDefaultIfMissing("controls.colorpicker.custom.toggle.foreground", "TextColor");
        setDefaultIfMissing("controls.colorpicker.custom.toggle.selected.background", "CursorColor");
        setDefaultIfMissing("controls.colorpicker.custom.toggle.selected.border", "CursorColor");
        setDefaultIfMissing("controls.colorpicker.custom.toggle.selected.foreground", "BackgroundColor");
        setDefaultIfMissing("controls.home.active.background", "BackgroundColor");
        setDefaultIfMissing("controls.home.active.background.opacity", "0.50");
        setDefaultIfMissing("controls.home.active.border", "BackgroundColor");
        setDefaultIfMissing("controls.home.active.border.opacity", "0.50");
        setDefaultIfMissing("controls.home.active.foreground", "CursorColor");
        setDefaultIfMissing("controls.home.background", "BackgroundColor");
        setDefaultIfMissing("controls.home.background.opacity", ".50");
        setDefaultIfMissing("controls.home.border", "BackgroundColor");
        setDefaultIfMissing("controls.home.border.opacity", "0.10");
        setDefaultIfMissing("controls.home.foreground", "CursorColor");
        setDefaultIfMissing("controls.home.hover.background", "BackgroundColor");
        setDefaultIfMissing("controls.home.hover.background.opacity", "1.0");
        setDefaultIfMissing("controls.home.hover.border", "BackgroundColor");
        setDefaultIfMissing("controls.home.hover.border.opacity", "1.0");
        setDefaultIfMissing("controls.home.hover.foreground", "CursorColor");
        setDefaultIfMissing("controls.input.background", "SelectionColor");
        setDefaultIfMissing("controls.input.border", "BackgroundColor");
        setDefaultIfMissing("controls.input.foreground", "TextColor");
        setDefaultIfMissing("controls.settings.active.background", "BackgroundColor");
        setDefaultIfMissing("controls.settings.active.background.opacity", "0.50");
        setDefaultIfMissing("controls.settings.active.border", "BackgroundColor");
        setDefaultIfMissing("controls.settings.active.border.opacity", "0.50");
        setDefaultIfMissing("controls.settings.active.foreground", "CursorColor");
        setDefaultIfMissing("controls.settings.background", "BackgroundColor");
        setDefaultIfMissing("controls.settings.background.opacity", ".50");
        setDefaultIfMissing("controls.settings.border", "BackgroundColor");
        setDefaultIfMissing("controls.settings.border.opacity", "0.10");
        setDefaultIfMissing("controls.settings.foreground", "CursorColor");
        setDefaultIfMissing("controls.settings.hover.background", "BackgroundColor");
        setDefaultIfMissing("controls.settings.hover.background.opacity", "1.0");
        setDefaultIfMissing("controls.settings.hover.border", "BackgroundColor");
        setDefaultIfMissing("controls.settings.hover.border.opacity", "1.0");
        setDefaultIfMissing("controls.settings.hover.foreground", "CursorColor");
        setDefaultIfMissing("controls.sidebar.toggle.active.background", "BackgroundColor");
        setDefaultIfMissing("controls.sidebar.toggle.active.background.opacity", "0.50");
        setDefaultIfMissing("controls.sidebar.toggle.active.border", "BackgroundColor");
        setDefaultIfMissing("controls.sidebar.toggle.active.border.opacity", "0.50");
        setDefaultIfMissing("controls.sidebar.toggle.active.foreground", "CursorColor");
        setDefaultIfMissing("controls.sidebar.toggle.background", "BackgroundColor");
        setDefaultIfMissing("controls.sidebar.toggle.background.opacity", "0.50");
        setDefaultIfMissing("controls.sidebar.toggle.border", "BackgroundColor");
        setDefaultIfMissing("controls.sidebar.toggle.border.opacity", "0.10");
        setDefaultIfMissing("controls.sidebar.toggle.foreground", "CursorColor");
        setDefaultIfMissing("controls.sidebar.toggle.hover.background", "BackgroundColor");
        setDefaultIfMissing("controls.sidebar.toggle.hover.background.opacity", "1");
        setDefaultIfMissing("controls.sidebar.toggle.hover.border", "BackgroundColor");
        setDefaultIfMissing("controls.sidebar.toggle.hover.border.opacity", "1");
        setDefaultIfMissing("controls.sidebar.toggle.hover.foreground", "CursorColor");
        setDefaultIfMissing("controls.snippet.hover.btn.background", "BackgroundColor");
        setDefaultIfMissing("controls.snippet.hover.btn.background.opacity", "1.0");
        setDefaultIfMissing("controls.snippet.hover.btn.border", "SelectionColor");
        setDefaultIfMissing("controls.snippet.hover.btn.border.opacity", "0.30");
        setDefaultIfMissing("controls.snippet.hover.btn.foreground", "TextColor");
        setDefaultIfMissing("controls.snippet.hover.btn.hover.background", "SelectionColor");
        setDefaultIfMissing("controls.snippet.hover.btn.hover.background.opacity", "1.0");
        setDefaultIfMissing("controls.snippet.hover.btn.hover.border", "CursorColor");
        setDefaultIfMissing("controls.snippet.hover.btn.hover.border.opacity", "1.0");
        setDefaultIfMissing("controls.snippet.hover.btn.hover.foreground", "CursorColor");
        setDefaultIfMissing("controls.zoom.active.background", "BackgroundColor");
        setDefaultIfMissing("controls.zoom.active.background.opacity", "0.50");
        setDefaultIfMissing("controls.zoom.active.border", "BackgroundColor");
        setDefaultIfMissing("controls.zoom.active.border.opacity", "0.50");
        setDefaultIfMissing("controls.zoom.active.foreground", "CursorColor");
        setDefaultIfMissing("controls.zoom.background", "BackgroundColor");
        setDefaultIfMissing("controls.zoom.background.opacity", "0.50");
        setDefaultIfMissing("controls.zoom.border", "BackgroundColor");
        setDefaultIfMissing("controls.zoom.border.opacity", "0.50");
        setDefaultIfMissing("controls.zoom.foreground", "CursorColor");
        setDefaultIfMissing("controls.zoom.hover.background", "BackgroundColor");
        setDefaultIfMissing("controls.zoom.hover.background.opacity", "1.0");
        setDefaultIfMissing("controls.zoom.hover.border", "BackgroundColor");
        setDefaultIfMissing("controls.zoom.hover.border.opacity", "1.0");
        setDefaultIfMissing("controls.zoom.hover.foreground", "CursorColor");
        setDefaultIfMissing("dashboard.package.background", "SelectionColor");
        setDefaultIfMissing("dashboard.package.background.opacity", "0.3");
        setDefaultIfMissing("dashboard.package.border", "SelectionColor");
        setDefaultIfMissing("dashboard.package.border.opacity", "1.0");
        setDefaultIfMissing("dialog.background", "SelectionColor");
        setDefaultIfMissing("dialog.background.gradient.end", "BackgroundColor");
        setDefaultIfMissing("dialog.background.gradient.ratio", "100% 0%");
        setDefaultIfMissing("dialog.background.gradient.start", "SelectionColor");
        setDefaultIfMissing("dialog.background.opacity", "1.0");
        setDefaultIfMissing("dialog.border", "CursorColor");
        setDefaultIfMissing("dialog.border.opacity", "1.0");
        setDefaultIfMissing("dialog.header.background", "BackgroundColor");
        setDefaultIfMissing("dialog.header.background.opacity", "0.5");
        setDefaultIfMissing("dialog.header.foreground", "CursorColor");
        setDefaultIfMissing("dropdown.item.background", "SelectionColor");
        setDefaultIfMissing("dropdown.item.foreground", "TextColor");
        setDefaultIfMissing("dropdown.item.hover.background", "BackgroundColor");
        setDefaultIfMissing("dropdown.item.hover.foreground", "CursorColor");
        setDefaultIfMissing("dropdown.list.background", "BackgroundColor");
        setDefaultIfMissing("dropdown.list.border", "SelectionColor");
        setDefaultIfMissing("dropdown.scrollbar.thumb", "SelectionColor");
        setDefaultIfMissing("dropdown.scrollbar.thumb.hover", "CursorColor");
        setDefaultIfMissing("dropdown.scrollbar.thumb.hover.opacity", "0.7");
        setDefaultIfMissing("dropdown.scrollbar.thumb.opacity", "0.4");
        setDefaultIfMissing("global.label.foreground", "TextColor");
        setDefaultIfMissing("scrollbar.thumb", "CursorColor");
        setDefaultIfMissing("scrollbar.thumb.hover", "CursorColor");
        setDefaultIfMissing("scrollbar.thumb.hover.opacity", "0.6");
        setDefaultIfMissing("scrollbar.thumb.opacity", "0.3");
        setDefaultIfMissing("scrollbar.width", "10");
        setDefaultIfMissing("search.bar.active.background", "BackgroundColor");
        setDefaultIfMissing("search.bar.active.border", "CursorColor");
        setDefaultIfMissing("search.bar.background", "BackgroundColor");
        setDefaultIfMissing("search.bar.background.opacity", ".5");
        setDefaultIfMissing("search.bar.border", "SelectionColor");
        setDefaultIfMissing("search.bar.foreground", "CursorColor");
        setDefaultIfMissing("search.bar.hover.background", "BackgroundColor");
        setDefaultIfMissing("sidebar.background", "SelectionColor");
        setDefaultIfMissing("sidebar.background.gradient.end", "BackgroundColor");
        setDefaultIfMissing("sidebar.background.gradient.ratio", "20% 80%");
        setDefaultIfMissing("sidebar.background.gradient.start", "SelectionColor");
        setDefaultIfMissing("sidebar.background.opacity", "1.0");
        setDefaultIfMissing("sidebar.border", "SelectionColor");
        setDefaultIfMissing("sidebar.border.opacity", "1.0");
        setDefaultIfMissing("sidebar.nav.foreground", "TextColor");
        setDefaultIfMissing("sidebar.nav.hover.background", "CursorColor");
        setDefaultIfMissing("sidebar.nav.hover.foreground", "BackgroundColor");
        setDefaultIfMissing("sidebar.nav.selected.background", "CursorColor");
        setDefaultIfMissing("sidebar.nav.selected.background.opacity", ".150");
        setDefaultIfMissing("sidebar.nav.selected.border", "CursorColor");
        setDefaultIfMissing("sidebar.package.content.background", "SelectionColor");
        setDefaultIfMissing("sidebar.package.content.background.opacity", "0.15");
        setDefaultIfMissing("sidebar.package.background.active", "CursorColor");
        setDefaultIfMissing("sidebar.package.background.active.opacity", "0.15");
        setDefaultIfMissing("sidebar.package.background.inactive", "BackgroundColor");
        setDefaultIfMissing("sidebar.package.background.inactive.opacity", "0.30");
        setDefaultIfMissing("snippetLauncher.label", "CursorColor");
        setDefaultIfMissing("tabs.header.background", "SelectionColor");
        setDefaultIfMissing("tabs.plus.foreground", "TextColor");
        setDefaultIfMissing("tabs.plus.hover.background", "SelectionColor");
        setDefaultIfMissing("tabs.plus.hover.foreground", "CursorColor");
        setDefaultIfMissing("tabs.tab.active.background", "BackgroundColor");
        setDefaultIfMissing("tabs.tab.active.border", "CursorColor");
        setDefaultIfMissing("tabs.tab.active.foreground", "CursorColor");
        setDefaultIfMissing("tabs.tab.hover.background", "BackgroundColor");
        setDefaultIfMissing("tabs.tab.inactive.background", "SelectionColor");
        setDefaultIfMissing("tabs.tab.inactive.foreground", "TextColor");
        setDefaultIfMissing("terminal.background", "BackgroundColor");
        setDefaultIfMissing("terminal.cursor", "CursorColor");
        setDefaultIfMissing("terminal.foreground", "TextColor");
        setDefaultIfMissing("terminal.pane.background", "BackgroundColor");
        setDefaultIfMissing("terminal.pane.border", "CursorColor");
        setDefaultIfMissing("terminal.pane.empty.background", "BackgroundColor");
        setDefaultIfMissing("terminal.pane.empty.border", "CursorColor");
        setDefaultIfMissing("terminal.pane.empty.button.background", "SelectionColor");
        setDefaultIfMissing("terminal.pane.empty.button.foreground", "TextColor");
        setDefaultIfMissing("terminal.pane.empty.button.hover.background", "CursorColor");
        setDefaultIfMissing("terminal.pane.empty.button.hover.foreground", "BackgroundColor");
        setDefaultIfMissing("terminal.pane.empty.description", "TextColor");
        setDefaultIfMissing("terminal.pane.empty.title", "CursorColor");
        setDefaultIfMissing("terminal.pane.header.background", "BackgroundColor");
        setDefaultIfMissing("terminal.pane.header.border", "BackgroundColor");
        setDefaultIfMissing("terminal.pane.header.button.foreground", "BackgroundColor");
        setDefaultIfMissing("terminal.pane.header.button.hover.background", "SelectionColor");
        setDefaultIfMissing("terminal.pane.header.button.hover.foreground", "CursorColor");
        setDefaultIfMissing("terminal.pane.header.foreground", "TextColor");
        setDefaultIfMissing("terminal.selection", "SelectionColor");
        setDefaultIfMissing("terminal.splitpane.background", "SelectionColor");
        setDefaultIfMissing("terminal.splitpane.divider.background", "SelectionColor");
        setDefaultIfMissing("terminal.splitpane.divider.hover", "SelectionColor");
        setDefaultIfMissing("titlebar.background", "SelectionColor");
        setDefaultIfMissing("titlebar.background.opacity", "1.0");
        setDefaultIfMissing("titlebar.border", "SelectionColor");
        setDefaultIfMissing("titlebar.border.opacity", "1.0");
        setDefaultIfMissing("titlebar.button.foreground", "TextColor");
        setDefaultIfMissing("titlebar.button.hover.background", "SelectionColor");
        setDefaultIfMissing("titlebar.button.hover.foreground", "CursorColor");
        setDefaultIfMissing("titlebar.close.hover.background", "CursorColor");
        setDefaultIfMissing("titlebar.close.hover.foreground", "TextColor");
        setDefaultIfMissing("toolbar.background", "SelectionColor");
        setDefaultIfMissing("toolbar.background.opacity", "1.0");
        setDefaultIfMissing("toolbar.border", "SelectionColor");
        setDefaultIfMissing("toolbar.border.opacity", "1.0");
        setDefaultIfMissing("highlights.sidebar.toggle.background", "BackgroundColor");
        setDefaultIfMissing("highlights.sidebar.toggle.background.opacity", ".50");
        setDefaultIfMissing("highlights.sidebar.toggle.border.opacity", ".50");
        setDefaultIfMissing("highlights.sidebar.toggle.hover.background", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.toggle.hover.border", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.toggle.hover.background.opacity", "0.150");
        setDefaultIfMissing("highlights.sidebar.toggle.hover.border.opacity", "1.0");
        setDefaultIfMissing("highlights.sidebar.toggle.active.background", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.toggle.active.background.opacity", ".25");
        setDefaultIfMissing("highlights.sidebar.toggle.active.border", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.toggle.active.border.opacity", "0.75");
        setDefaultIfMissing("highlights.sidebar.toggle.foreground", "TextColor");
        setDefaultIfMissing("highlights.sidebar.toggle.active.foreground", "TextColor");
        setDefaultIfMissing("highlights.sidebar.toggle.eye.inactive", "TextColor");
        setDefaultIfMissing("highlights.sidebar.toggle.eye.active", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.package.icon", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.package.icon.hover", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.package.icon.active", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.package.title", "TextColor");
        setDefaultIfMissing("highlights.sidebar.package.title.hover", "TextColor");
        setDefaultIfMissing("highlights.sidebar.package.title.active", "TextColor");
        setDefaultIfMissing("highlights.sidebar.package.eye.active", "CursorColor");
        setDefaultIfMissing("highlights.sidebar.package.eye.inactive", "TextColor");
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

    public static void setProperty(String key, String value) {
        config.setProperty(key, value);
        saveConfig();
    }

    private static void saveConfig() {
        File configFile = new File("./config/theme.config.properties");
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(configFile)) {
                config.store(out, "T-Insider Dynamic UI Color Mapping Configurations");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
