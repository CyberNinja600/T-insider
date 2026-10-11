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
        
        // 1. Populate standard hardcoded UI theme keys
        populateDefaultColors(bg, text, cursor, selection);

        // 2. Ensure core terminal keys are explicitly set
        colors.put("terminal.background", bg);
        colors.put("terminal.foreground", text);
        colors.put("terminal.cursor", cursor);
        colors.put("terminal.selection", selection);
        
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

    private void populateDefaultColors(Color bg, Color text, Color cursor, Color selection) {
        colors.put("app.window.background", selection);
        colors.put("app.window.background.gradient.end", bg);
        colors.put("app.window.background.gradient.start", selection);
        colors.put("app.window.border", selection);
        colors.put("cards.item.background", bg);
        colors.put("cards.item.badge.background", bg);
        colors.put("cards.item.badge.foreground", text);
        colors.put("cards.item.badge.hover.background", selection);
        colors.put("cards.item.badge.hover.foreground", cursor);
        colors.put("cards.item.badge.selected.background", selection);
        colors.put("cards.item.badge.selected.foreground", cursor);
        colors.put("cards.item.border", selection);
        colors.put("cards.item.hover.background", cursor);
        colors.put("cards.item.hover.border", cursor);
        colors.put("cards.item.selected.background", bg);
        colors.put("cards.item.selected.border", cursor);
        colors.put("cards.item.selected.glow", cursor);
        colors.put("cards.item.subtext", text);
        colors.put("cards.item.title", text);
        colors.put("cards.list.background", selection);
        colors.put("cards.list.border", selection);
        colors.put("connecting.background", bg);
        colors.put("connecting.foreground", text);
        colors.put("connecting.icon", cursor);
        colors.put("controls.button.background", bg);
        colors.put("controls.button.border", bg);
        colors.put("controls.button.foreground", text);
        colors.put("controls.button.hover.background", cursor);
        colors.put("controls.button.hover.border", cursor);
        colors.put("controls.button.hover.foreground", bg);
        colors.put("controls.button.hover.icon", bg);
        colors.put("controls.button.icon", text);
        colors.put("controls.combo.background", bg);
        colors.put("controls.combo.border", selection);
        colors.put("controls.combo.foreground", text);
        colors.put("controls.combo.hover.foreground", bg);
        colors.put("controls.colorpicker.background", bg);
        colors.put("controls.colorpicker.border", bg);
        colors.put("controls.colorpicker.foreground", text);
        colors.put("controls.colorpicker.popup.background", bg);
        colors.put("controls.colorpicker.popup.border", selection);
        colors.put("controls.colorpicker.custom.background", bg);
        colors.put("controls.colorpicker.custom.controls.background", selection);
        colors.put("controls.colorpicker.custom.button.background", bg);
        colors.put("controls.colorpicker.custom.button.border", bg);
        colors.put("controls.colorpicker.custom.button.foreground", text);
        colors.put("controls.colorpicker.custom.button.hover.background", cursor);
        colors.put("controls.colorpicker.custom.button.hover.border", cursor);
        colors.put("controls.colorpicker.custom.button.hover.foreground", bg);
        colors.put("controls.colorpicker.custom.toggle.background", bg);
        colors.put("controls.colorpicker.custom.toggle.border", selection);
        colors.put("controls.colorpicker.custom.toggle.foreground", text);
        colors.put("controls.colorpicker.custom.toggle.selected.background", cursor);
        colors.put("controls.colorpicker.custom.toggle.selected.border", cursor);
        colors.put("controls.colorpicker.custom.toggle.selected.foreground", bg);
        colors.put("controls.home.active.background", bg);
        colors.put("controls.home.active.border", bg);
        colors.put("controls.home.active.foreground", cursor);
        colors.put("controls.home.background", bg);
        colors.put("controls.home.border", bg);
        colors.put("controls.home.foreground", cursor);
        colors.put("controls.home.hover.background", bg);
        colors.put("controls.home.hover.border", bg);
        colors.put("controls.home.hover.foreground", cursor);
        colors.put("controls.input.background", selection);
        colors.put("controls.input.border", bg);
        colors.put("controls.input.foreground", text);
        colors.put("controls.settings.active.background", bg);
        colors.put("controls.settings.active.border", bg);
        colors.put("controls.settings.active.foreground", cursor);
        colors.put("controls.settings.background", bg);
        colors.put("controls.settings.border", bg);
        colors.put("controls.settings.foreground", cursor);
        colors.put("controls.settings.hover.background", bg);
        colors.put("controls.settings.hover.border", bg);
        colors.put("controls.settings.hover.foreground", cursor);
        colors.put("controls.sidebar.toggle.active.background", bg);
        colors.put("controls.sidebar.toggle.active.border", bg);
        colors.put("controls.sidebar.toggle.active.foreground", cursor);
        colors.put("controls.sidebar.toggle.background", bg);
        colors.put("controls.sidebar.toggle.border", bg);
        colors.put("controls.sidebar.toggle.foreground", cursor);
        colors.put("controls.sidebar.toggle.hover.background", bg);
        colors.put("controls.sidebar.toggle.hover.border", bg);
        colors.put("controls.sidebar.toggle.hover.foreground", cursor);
        colors.put("controls.snippet.hover.btn.background", bg);
        colors.put("controls.snippet.hover.btn.border", selection);
        colors.put("controls.snippet.hover.btn.foreground", text);
        colors.put("controls.snippet.hover.btn.hover.background", selection);
        colors.put("controls.snippet.hover.btn.hover.border", cursor);
        colors.put("controls.snippet.hover.btn.hover.foreground", cursor);
        colors.put("controls.zoom.active.background", bg);
        colors.put("controls.zoom.active.border", bg);
        colors.put("controls.zoom.active.foreground", cursor);
        colors.put("controls.zoom.background", bg);
        colors.put("controls.zoom.border", bg);
        colors.put("controls.zoom.foreground", cursor);
        colors.put("controls.zoom.hover.background", bg);
        colors.put("controls.zoom.hover.border", bg);
        colors.put("controls.zoom.hover.foreground", cursor);
        colors.put("dashboard.package.background", selection);
        colors.put("dashboard.package.border", selection);
        colors.put("dialog.background", selection);
        colors.put("dialog.background.gradient.start", selection);
        colors.put("dialog.background.gradient.end", bg);
        colors.put("dialog.border", cursor);
        colors.put("dialog.header.background", bg);
        colors.put("dialog.header.foreground", cursor);
        colors.put("dropdown.item.background", selection);
        colors.put("dropdown.item.foreground", text);
        colors.put("dropdown.item.hover.background", bg);
        colors.put("dropdown.item.hover.foreground", cursor);
        colors.put("dropdown.list.background", bg);
        colors.put("dropdown.list.border", bg);
        colors.put("dropdown.scrollbar.thumb", selection);
        colors.put("dropdown.scrollbar.thumb.hover", cursor);
        colors.put("global.label.foreground", text);
        colors.put("scrollbar.thumb", cursor);
        colors.put("scrollbar.thumb.hover", cursor);
        colors.put("search.bar.active.background", bg);
        colors.put("search.bar.active.border", cursor);
        colors.put("search.bar.background", bg);
        colors.put("search.bar.border", selection);
        colors.put("search.bar.foreground", cursor);
        colors.put("search.bar.hover.background", bg);
        colors.put("sidebar.background", selection);
        colors.put("sidebar.background.gradient.end", bg);
        colors.put("sidebar.background.gradient.start", selection);
        colors.put("sidebar.border", selection);
        colors.put("sidebar.nav.foreground", text);
        colors.put("sidebar.nav.hover.background", cursor);
        colors.put("sidebar.nav.hover.foreground", bg);
        colors.put("sidebar.nav.selected.background", cursor);
        colors.put("sidebar.nav.selected.border", cursor);
        colors.put("sidebar.package.content.background", selection);
        colors.put("sidebar.package.background.active", cursor);
        colors.put("sidebar.package.background.inactive", bg);
        colors.put("snippetLauncher.label", cursor);
        colors.put("tabs.header.background", selection);
        colors.put("tabs.plus.foreground", text);
        colors.put("tabs.plus.hover.background", selection);
        colors.put("tabs.plus.hover.foreground", cursor);
        colors.put("tabs.tab.active.background", bg);
        colors.put("tabs.tab.active.border", cursor);
        colors.put("tabs.tab.active.foreground", cursor);
        colors.put("tabs.tab.hover.background", bg);
        colors.put("tabs.tab.inactive.background", selection);
        colors.put("tabs.tab.inactive.foreground", text);
        colors.put("terminal.pane.background", bg);
        colors.put("terminal.pane.border", cursor);
        colors.put("terminal.pane.empty.background", bg);
        colors.put("terminal.pane.empty.border", cursor);
        colors.put("terminal.pane.empty.button.background", selection);
        colors.put("terminal.pane.empty.button.foreground", text);
        colors.put("terminal.pane.empty.button.hover.background", cursor);
        colors.put("terminal.pane.empty.button.hover.foreground", bg);
        colors.put("terminal.pane.empty.description", text);
        colors.put("terminal.pane.empty.title", cursor);
        colors.put("terminal.pane.header.background", bg);
        colors.put("terminal.pane.header.border", bg);
        colors.put("terminal.pane.header.button.foreground", bg);
        colors.put("terminal.pane.header.button.hover.background", selection);
        colors.put("terminal.pane.header.button.hover.foreground", cursor);
        colors.put("terminal.pane.header.foreground", text);
        colors.put("terminal.splitpane.background", selection);
        colors.put("terminal.splitpane.divider.background", selection);
        colors.put("terminal.splitpane.divider.hover", selection);
        colors.put("titlebar.background", selection);
        colors.put("titlebar.border", selection);
        colors.put("titlebar.button.foreground", text);
        colors.put("titlebar.button.hover.background", selection);
        colors.put("titlebar.button.hover.foreground", cursor);
        colors.put("titlebar.close.hover.background", cursor);
        colors.put("titlebar.close.hover.foreground", text);
        colors.put("toolbar.background", selection);
        colors.put("toolbar.border", selection);
        colors.put("highlights.sidebar.toggle.background", bg);
        colors.put("highlights.sidebar.toggle.hover.background", cursor);
        colors.put("highlights.sidebar.toggle.hover.border", cursor);
        colors.put("highlights.sidebar.toggle.active.background", cursor);
        colors.put("highlights.sidebar.toggle.active.border", cursor);
        colors.put("highlights.sidebar.toggle.foreground", text);
        colors.put("highlights.sidebar.toggle.active.foreground", text);
        colors.put("highlights.sidebar.toggle.eye.inactive", text);
        colors.put("highlights.sidebar.toggle.eye.active", cursor);
        colors.put("highlights.sidebar.package.icon", cursor);
        colors.put("highlights.sidebar.package.icon.hover", cursor);
        colors.put("highlights.sidebar.package.icon.active", cursor);
        colors.put("highlights.sidebar.package.title", text);
        colors.put("highlights.sidebar.package.title.hover", text);
        colors.put("highlights.sidebar.package.title.active", text);
        colors.put("highlights.sidebar.package.eye.active", cursor);
        colors.put("highlights.sidebar.package.eye.inactive", text);
    }
}
