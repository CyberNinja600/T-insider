package com.tinsider.config;

import com.jediterm.core.Color;
import java.util.HashMap;
import java.util.Map;

public class TerminalTheme {
    private final String name;
    private final Map<String, Color> colors = new HashMap<>();
    private final Color[] ansiColors = new Color[16];

    public TerminalTheme(String name) {
        this.name = name;
        for (int i = 0; i < 16; i++) {
            ansiColors[i] = new Color(128, 128, 128); // Fallbacks
        }
    }

    public TerminalTheme(String name, Color bg, Color text, Color cursor, Color selection) {
        this(name);
        
        // Populate standard default keys directly to maintain backward compatibility and default theme setup!
        colors.put("app.window.background", bg);
        colors.put("app.window.border", selection);
        colors.put("titlebar.background", bg);
        colors.put("titlebar.border", selection);
        colors.put("toolbar.background", bg);
        colors.put("toolbar.border", selection);
        colors.put("sidebar.background", bg);
        colors.put("sidebar.border", selection);
        colors.put("global.label.foreground", text);
        colors.put("snippetLauncher.label", cursor);

        colors.put("terminal.background", bg);
        colors.put("terminal.foreground", text);
        colors.put("terminal.cursor", cursor);
        colors.put("terminal.selection", selection);

        colors.put("tabs.header.background", bg);
        colors.put("tabs.tab.inactive.background", bg);
        colors.put("tabs.tab.inactive.foreground", text);
        colors.put("tabs.tab.active.background", bg);
        colors.put("tabs.tab.active.foreground", cursor);
        colors.put("tabs.tab.active.border", cursor);
        colors.put("tabs.tab.hover.background", selection);
        colors.put("tabs.plus.foreground", text);
        colors.put("tabs.plus.hover.background", selection);
        colors.put("tabs.plus.hover.foreground", cursor);

        colors.put("cards.list.background", bg);
        colors.put("cards.list.border", selection);
        colors.put("cards.item.background", selection);
        colors.put("cards.item.border", bg);
        colors.put("cards.item.title", text);
        colors.put("cards.item.subtext", text);
        colors.put("cards.item.badge.background", cursor);
        colors.put("cards.item.badge.foreground", text);
        colors.put("cards.item.badge.hover.background", selection);
        colors.put("cards.item.badge.hover.foreground", cursor);
        colors.put("cards.item.badge.selected.background", bg);
        colors.put("cards.item.badge.selected.foreground", cursor);
        colors.put("cards.item.hover.background", selection);
        colors.put("cards.item.hover.border", selection);
        colors.put("cards.item.selected.background", selection);
        colors.put("cards.item.selected.border", cursor);
        colors.put("cards.item.selected.glow", cursor);

        colors.put("controls.button.background", selection);
        colors.put("controls.button.border", bg);
        colors.put("controls.button.foreground", text);
        colors.put("controls.button.hover.background", selection);
        colors.put("controls.combo.background", selection);
        colors.put("controls.combo.border", bg);
        colors.put("controls.combo.foreground", text);
        colors.put("controls.input.background", selection);
        colors.put("controls.input.border", bg);
        colors.put("controls.input.foreground", text);

        colors.put("dropdown.list.background", bg);
        colors.put("dropdown.list.border", selection);
        colors.put("dropdown.item.background", bg);
        colors.put("dropdown.item.foreground", text);
        colors.put("dropdown.item.hover.background", selection);
        colors.put("dropdown.item.hover.foreground", cursor);

        colors.put("titlebar.button.foreground", text);
        colors.put("titlebar.button.hover.background", selection);
        colors.put("titlebar.button.hover.foreground", cursor);
        colors.put("titlebar.close.hover.background", cursor);
        colors.put("titlebar.close.hover.foreground", text);

        colors.put("terminal.pane.background", bg);
        colors.put("terminal.pane.border", selection);
        colors.put("terminal.pane.header.background", selection);
        colors.put("terminal.pane.header.border", selection);
        colors.put("terminal.pane.header.foreground", text);
        colors.put("terminal.pane.header.button.foreground", text);
        colors.put("terminal.pane.header.button.hover.background", selection);
        colors.put("terminal.pane.header.button.hover.foreground", cursor);

        colors.put("terminal.pane.empty.background", selection);
        colors.put("terminal.pane.empty.border", cursor);
        colors.put("terminal.pane.empty.title", cursor);
        colors.put("terminal.pane.empty.description", text);
        colors.put("terminal.pane.empty.button.background", selection);
        colors.put("terminal.pane.empty.button.foreground", text);
        colors.put("terminal.pane.empty.button.hover.background", cursor);
        colors.put("terminal.pane.empty.button.hover.foreground", bg);
        
        // Set standard ANSI-16 color palette initialization (modern vibrant Terminal theme colors)
        ansiColors[0] = new Color(22, 27, 48);       // 0: Black (matching dark themes)
        ansiColors[1] = new Color(230, 108, 117);    // 1: Red (Errors, critical logs)
        ansiColors[2] = new Color(152, 195, 121);    // 2: Green (Success, Info, logs)
        ansiColors[3] = new Color(229, 192, 123);    // 3: Yellow (Warnings)
        ansiColors[4] = new Color(97, 175, 239);     // 4: Blue (Debug, secondary highlights)
        ansiColors[5] = new Color(198, 120, 221);    // 5: Magenta
        ansiColors[6] = new Color(86, 182, 194);     // 6: Cyan (Trace, metadata)
        ansiColors[7] = new Color(171, 178, 191);    // 7: White
        
        // Bright ANSI-16 variants
        ansiColors[8] = new Color(92, 99, 112);      // 8: Bright Black (Gray comments, headers)
        ansiColors[9] = new Color(230, 108, 117);    // 9: Bright Red
        ansiColors[10] = new Color(152, 195, 121);   // 10: Bright Green
        ansiColors[11] = new Color(229, 192, 123);   // 11: Bright Yellow
        ansiColors[12] = new Color(97, 175, 239);    // 12: Bright Blue
        ansiColors[13] = new Color(198, 120, 221);   // 13: Bright Magenta
        ansiColors[14] = new Color(86, 182, 194);    // 14: Bright Cyan
        ansiColors[15] = new Color(255, 255, 255);   // 15: Bright White
    }

    public String getName() { return name; }

    public Color getColor(String propertyKey, Color fallback) {
        return colors.getOrDefault(propertyKey, fallback);
    }

    public void setColor(String propertyKey, Color color) {
        colors.put(propertyKey, color);
    }

    public Color getBackgroundColor() { return getColor("terminal.background", new Color(9, 15, 34)); }
    public Color getTextColor() { return getColor("terminal.foreground", new Color(226, 232, 240)); }
    public Color getCursorColor() { return getColor("terminal.cursor", new Color(0, 173, 181)); }
    public Color getSelectionColor() { return getColor("terminal.selection", new Color(22, 27, 48)); }

    public Color getAnsiColor(int index) {
        if (index >= 0 && index < 16) {
            return ansiColors[index];
        }
        return new Color(128, 128, 128);
    }

    public void setAnsiColor(int index, Color color) {
        if (index >= 0 && index < 16) {
            ansiColors[index] = color;
        }
    }
}
