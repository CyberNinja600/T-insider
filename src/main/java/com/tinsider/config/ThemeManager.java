package com.tinsider.config;

import com.jediterm.core.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class ThemeManager {

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

        Color bg = parseColorFromPlist(plist, "BackgroundColor", new Color(9, 15, 34));
        Color text = parseColorFromPlist(plist, "TextColor", new Color(226, 232, 240));
        Color cursor = parseColorFromPlist(plist, "CursorColor", new Color(0, 173, 181));
        Color selection = parseColorFromPlist(plist, "SelectionColor", new Color(22, 27, 48));

        TerminalTheme theme = new TerminalTheme(themeName, bg, text, cursor, selection);

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
}
