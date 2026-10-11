package com.tinsider.ui.theme;

import com.jediterm.terminal.ui.JediTermWidget;
import com.tinsider.config.TerminalTheme;
import com.tinsider.storage.StorageManager;
import com.tinsider.ui.components.terminal.split.TerminalSplitPane;
import com.tinsider.ui.dialogs.ModalOverlayManager;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.swing.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Locale;

/**
 * Handles dynamic JavaFX CSS generation and theme application for T-Insider.
 * Translates TerminalTheme properties and opacities into JavaFX CSS stylesheets,
 * updates scenes and open dialogs, and triggers Swing terminal repaint.
 */
public final class ThemeApplicator {

    private ThemeApplicator() {
        // Utility class
    }

    public static String toHex(com.jediterm.core.Color color) {
        if (color == null) return "#000000";
        return String.format(Locale.US, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String toRgba(com.jediterm.core.Color color, double opacity) {
        if (color == null) return "rgba(0, 0, 0, 1.0)";
        return String.format(Locale.US, "rgba(%d, %d, %d, %.2f)", color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }

    public static String getGradientStyle(com.jediterm.core.Color startColor, double startOpacity,
                                          com.jediterm.core.Color endColor, double endOpacity,
                                          String direction, String ratio) {
        String[] stops = ratio.trim().split("\\s+");
        String stop1 = stops.length > 0 ? stops[0] : "0%";
        String stop2 = stops.length > 1 ? stops[1] : "100%";

        return String.format(Locale.US, "linear-gradient(%s, %s %s, %s %s)",
            direction,
            toRgba(startColor, startOpacity), stop1,
            toRgba(endColor, endOpacity), stop2
        );
    }

    /**
     * Applies the given theme to the primary scene and open dialogs, and updates terminal widgets.
     */
    public static void applyTheme(TerminalTheme theme, Scene scene, Collection<TerminalSplitPane> splitPanes) {
        if (theme == null) return;

        boolean gradientsEnabled = Boolean.parseBoolean(StorageManager.loadSettings().getOrDefault("gradientEnabled", "false"));

        double appWinBgOpacity = 1.0;
        double appWinBorderOpacity = 1.0;
        double titleBgOpacity = 1.0;
        double titleBorderOpacity = 1.0;
        double toolbarBgOpacity = 1.0;
        double toolbarBorderOpacity = 1.0;
        double sidebarBgOpacity = 1.0;
        double sidebarBorderOpacity = 1.0;

        // Resolve each granular color from the theme map with standard robust fallbacks
        com.jediterm.core.Color winBg = theme.getColor("app.window.background", theme.getBackgroundColor());
        com.jediterm.core.Color winBorder = theme.getColor("app.window.border", theme.getSelectionColor());
        com.jediterm.core.Color titleBg = theme.getColor("titlebar.background", theme.getBackgroundColor());
        com.jediterm.core.Color titleBorder = theme.getColor("titlebar.border", theme.getSelectionColor());
        com.jediterm.core.Color toolBg = theme.getColor("toolbar.background", theme.getBackgroundColor());
        com.jediterm.core.Color toolBorder = theme.getColor("toolbar.border", theme.getSelectionColor());
        com.jediterm.core.Color sideBg = theme.getColor("sidebar.background", theme.getBackgroundColor());
        com.jediterm.core.Color sideBorder = theme.getColor("sidebar.border", theme.getSelectionColor());
        com.jediterm.core.Color labelFg = theme.getColor("global.label.foreground", theme.getTextColor());

        com.jediterm.core.Color termBg = theme.getColor("terminal.background", theme.getBackgroundColor());
        com.jediterm.core.Color termFg = theme.getColor("terminal.foreground", theme.getTextColor());
        com.jediterm.core.Color termCursor = theme.getColor("terminal.cursor", theme.getCursorColor());
        com.jediterm.core.Color termSelection = theme.getColor("terminal.selection", theme.getSelectionColor());

        com.jediterm.core.Color headerBg = theme.getColor("tabs.header.background", theme.getBackgroundColor());
        com.jediterm.core.Color tabInactiveBg = theme.getColor("tabs.tab.inactive.background", theme.getBackgroundColor());
        com.jediterm.core.Color tabInactiveFg = theme.getColor("tabs.tab.inactive.foreground", theme.getTextColor());
        com.jediterm.core.Color tabActiveBg = theme.getColor("tabs.tab.active.background", theme.getBackgroundColor());
        com.jediterm.core.Color tabActiveFg = theme.getColor("tabs.tab.active.foreground", theme.getCursorColor());
        com.jediterm.core.Color tabActiveBorder = theme.getColor("tabs.tab.active.border", theme.getCursorColor());
        com.jediterm.core.Color tabHoverBg = theme.getColor("tabs.tab.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color plusFg = theme.getColor("tabs.plus.foreground", theme.getTextColor());
        com.jediterm.core.Color plusHoverBg = theme.getColor("tabs.plus.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color plusHoverFg = theme.getColor("tabs.plus.hover.foreground", theme.getCursorColor());

        com.jediterm.core.Color cardsListBg = theme.getColor("cards.list.background", theme.getBackgroundColor());
        com.jediterm.core.Color cardsListBorder = theme.getColor("cards.list.border", theme.getSelectionColor());
        com.jediterm.core.Color cardsItemBg = theme.getColor("cards.item.background", theme.getSelectionColor());
        com.jediterm.core.Color cardsItemBorder = theme.getColor("cards.item.border", theme.getBackgroundColor());
        com.jediterm.core.Color cardsItemTitle = theme.getColor("cards.item.title", theme.getTextColor());
        com.jediterm.core.Color cardsItemSub = theme.getColor("cards.item.subtext", theme.getTextColor());
        com.jediterm.core.Color cardsBadgeBg = theme.getColor("cards.item.badge.background", theme.getCursorColor());
        com.jediterm.core.Color cardsBadgeFg = theme.getColor("cards.item.badge.foreground", theme.getTextColor());
        com.jediterm.core.Color cardsBadgeHoverBg = theme.getColor("cards.item.badge.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color cardsBadgeHoverFg = theme.getColor("cards.item.badge.hover.foreground", theme.getCursorColor());
        com.jediterm.core.Color cardsBadgeSelectedBg = theme.getColor("cards.item.badge.selected.background", theme.getBackgroundColor());
        com.jediterm.core.Color cardsBadgeSelectedFg = theme.getColor("cards.item.badge.selected.foreground", theme.getCursorColor());
        com.jediterm.core.Color cardsHoverBg = theme.getColor("cards.item.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color cardsHoverBorder = theme.getColor("cards.item.hover.border", theme.getSelectionColor());
        com.jediterm.core.Color cardsSelectedBg = theme.getColor("cards.item.selected.background", theme.getSelectionColor());
        com.jediterm.core.Color cardsSelectedBorder = theme.getColor("cards.item.selected.border", theme.getCursorColor());
        com.jediterm.core.Color cardsSelectedGlow = theme.getColor("cards.item.selected.glow", theme.getCursorColor());

        com.jediterm.core.Color btnBg = theme.getColor("controls.button.background", theme.getSelectionColor());
        com.jediterm.core.Color btnBorder = theme.getColor("controls.button.border", theme.getBackgroundColor());
        com.jediterm.core.Color btnFg = theme.getColor("controls.button.foreground", theme.getTextColor());
        com.jediterm.core.Color btnHoverBg = theme.getColor("controls.button.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color btnHoverBorder = theme.getColor("controls.button.hover.border", theme.getCursorColor());
        com.jediterm.core.Color btnHoverFg = theme.getColor("controls.button.hover.foreground", theme.getBackgroundColor());
        com.jediterm.core.Color btnIcon = theme.getColor("controls.button.icon", theme.getTextColor());
        com.jediterm.core.Color btnHoverIcon = theme.getColor("controls.button.hover.icon", theme.getBackgroundColor());
        com.jediterm.core.Color comboBg = theme.getColor("controls.combo.background", theme.getSelectionColor());
        com.jediterm.core.Color comboBorder = theme.getColor("controls.combo.border", theme.getBackgroundColor());
        com.jediterm.core.Color comboFg = theme.getColor("controls.combo.foreground", theme.getTextColor());
        com.jediterm.core.Color comboHoverFg = theme.getColor("controls.combo.hover.foreground", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerBg = theme.getColor("controls.colorpicker.background", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerBorder = theme.getColor("controls.colorpicker.border", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerFg = theme.getColor("controls.colorpicker.foreground", theme.getTextColor());
        com.jediterm.core.Color colorPickerPopupBg = theme.getColor("controls.colorpicker.popup.background", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerPopupBorder = theme.getColor("controls.colorpicker.popup.border", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomBg = theme.getColor("controls.colorpicker.custom.background", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerCustomControlsBg = theme.getColor("controls.colorpicker.custom.controls.background", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomBtnBg = theme.getColor("controls.colorpicker.custom.button.background", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomBtnBorder = theme.getColor("controls.colorpicker.custom.button.border", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerCustomBtnFg = theme.getColor("controls.colorpicker.custom.button.foreground", theme.getTextColor());
        com.jediterm.core.Color colorPickerCustomBtnHoverBg = theme.getColor("controls.colorpicker.custom.button.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomBtnHoverBorder = theme.getColor("controls.colorpicker.custom.button.hover.border", theme.getCursorColor());
        com.jediterm.core.Color colorPickerCustomBtnHoverFg = theme.getColor("controls.colorpicker.custom.button.hover.foreground", theme.getCursorColor());
        com.jediterm.core.Color colorPickerCustomToggleBg = theme.getColor("controls.colorpicker.custom.toggle.background", theme.getBackgroundColor());
        com.jediterm.core.Color colorPickerCustomToggleBorder = theme.getColor("controls.colorpicker.custom.toggle.border", theme.getSelectionColor());
        com.jediterm.core.Color colorPickerCustomToggleFg = theme.getColor("controls.colorpicker.custom.toggle.foreground", theme.getTextColor());
        com.jediterm.core.Color colorPickerCustomToggleSelectedBg = theme.getColor("controls.colorpicker.custom.toggle.selected.background", theme.getCursorColor());
        com.jediterm.core.Color colorPickerCustomToggleSelectedBorder = theme.getColor("controls.colorpicker.custom.toggle.selected.border", theme.getCursorColor());
        com.jediterm.core.Color colorPickerCustomToggleSelectedFg = theme.getColor("controls.colorpicker.custom.toggle.selected.foreground", theme.getBackgroundColor());
        com.jediterm.core.Color inputBg = theme.getColor("controls.input.background", theme.getSelectionColor());
        com.jediterm.core.Color inputBorder = theme.getColor("controls.input.border", theme.getBackgroundColor());
        com.jediterm.core.Color inputFg = theme.getColor("controls.input.foreground", theme.getTextColor());

        com.jediterm.core.Color zoomBg = theme.getColor("controls.zoom.background", theme.getBackgroundColor());
        com.jediterm.core.Color zoomBorder = theme.getColor("controls.zoom.border", theme.getSelectionColor());
        com.jediterm.core.Color zoomFg = theme.getColor("controls.zoom.foreground", theme.getTextColor());
        com.jediterm.core.Color zoomHoverBg = theme.getColor("controls.zoom.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color zoomHoverBorder = theme.getColor("controls.zoom.hover.border", theme.getCursorColor());
        com.jediterm.core.Color zoomHoverFg = theme.getColor("controls.zoom.hover.foreground", theme.getTextColor());
        com.jediterm.core.Color zoomActiveBg = theme.getColor("controls.zoom.active.background", theme.getCursorColor());
        com.jediterm.core.Color zoomActiveBorder = theme.getColor("controls.zoom.active.border", theme.getCursorColor());
        com.jediterm.core.Color zoomActiveFg = theme.getColor("controls.zoom.active.foreground", theme.getBackgroundColor());

        com.jediterm.core.Color toggleBg = theme.getColor("controls.sidebar.toggle.background", theme.getBackgroundColor());
        com.jediterm.core.Color toggleBorder = theme.getColor("controls.sidebar.toggle.border", theme.getSelectionColor());
        com.jediterm.core.Color toggleFg = theme.getColor("controls.sidebar.toggle.foreground", theme.getTextColor());
        com.jediterm.core.Color toggleHoverBg = theme.getColor("controls.sidebar.toggle.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color toggleHoverBorder = theme.getColor("controls.sidebar.toggle.hover.border", theme.getCursorColor());
        com.jediterm.core.Color toggleHoverFg = theme.getColor("controls.sidebar.toggle.hover.foreground", theme.getTextColor());
        com.jediterm.core.Color toggleActiveBg = theme.getColor("controls.sidebar.toggle.active.background", theme.getCursorColor());
        com.jediterm.core.Color toggleActiveBorder = theme.getColor("controls.sidebar.toggle.active.border", theme.getCursorColor());
        com.jediterm.core.Color toggleActiveFg = theme.getColor("controls.sidebar.toggle.active.foreground", theme.getBackgroundColor());

        com.jediterm.core.Color homeBg = theme.getColor("controls.home.background", theme.getBackgroundColor());
        com.jediterm.core.Color homeBorder = theme.getColor("controls.home.border", theme.getSelectionColor());
        com.jediterm.core.Color homeFg = theme.getColor("controls.home.foreground", theme.getTextColor());
        com.jediterm.core.Color homeHoverBg = theme.getColor("controls.home.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color homeHoverBorder = theme.getColor("controls.home.hover.border", theme.getCursorColor());
        com.jediterm.core.Color homeHoverFg = theme.getColor("controls.home.hover.foreground", theme.getTextColor());
        com.jediterm.core.Color homeActiveBg = theme.getColor("controls.home.active.background", theme.getCursorColor());
        com.jediterm.core.Color homeActiveBorder = theme.getColor("controls.home.active.border", theme.getCursorColor());
        com.jediterm.core.Color homeActiveFg = theme.getColor("controls.home.active.foreground", theme.getBackgroundColor());

        com.jediterm.core.Color settingsBg = theme.getColor("controls.settings.background", theme.getBackgroundColor());
        com.jediterm.core.Color settingsBorder = theme.getColor("controls.settings.border", theme.getSelectionColor());
        com.jediterm.core.Color settingsFg = theme.getColor("controls.settings.foreground", theme.getTextColor());
        com.jediterm.core.Color settingsHoverBg = theme.getColor("controls.settings.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color settingsHoverBorder = theme.getColor("controls.settings.hover.border", theme.getCursorColor());
        com.jediterm.core.Color settingsHoverFg = theme.getColor("controls.settings.hover.foreground", theme.getTextColor());
        com.jediterm.core.Color settingsActiveBg = theme.getColor("controls.settings.active.background", theme.getCursorColor());
        com.jediterm.core.Color settingsActiveBorder = theme.getColor("controls.settings.active.border", theme.getCursorColor());
        com.jediterm.core.Color settingsActiveFg = theme.getColor("controls.settings.active.foreground", theme.getBackgroundColor());

        com.jediterm.core.Color snippetHoverBtnBg = theme.getColor("controls.snippet.hover.btn.background", theme.getBackgroundColor());
        com.jediterm.core.Color snippetHoverBtnBorder = theme.getColor("controls.snippet.hover.btn.border", theme.getSelectionColor());
        com.jediterm.core.Color snippetHoverBtnFg = theme.getColor("controls.snippet.hover.btn.foreground", theme.getTextColor());
        com.jediterm.core.Color snippetHoverBtnHoverBg = theme.getColor("controls.snippet.hover.btn.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color snippetHoverBtnHoverBorder = theme.getColor("controls.snippet.hover.btn.hover.border", theme.getCursorColor());
        com.jediterm.core.Color snippetHoverBtnHoverFg = theme.getColor("controls.snippet.hover.btn.hover.foreground", theme.getCursorColor());

        com.jediterm.core.Color connBg = theme.getColor("connecting.background", theme.getBackgroundColor());
        com.jediterm.core.Color connIcon = theme.getColor("connecting.icon", theme.getCursorColor());
        com.jediterm.core.Color connFg = theme.getColor("connecting.foreground", theme.getTextColor());
        com.jediterm.core.Color splitPaneBg = theme.getColor("terminal.splitpane.background", theme.getBackgroundColor());
        com.jediterm.core.Color splitDividerBg = theme.getColor("terminal.splitpane.divider.background", theme.getSelectionColor());
        com.jediterm.core.Color splitDividerHover = theme.getColor("terminal.splitpane.divider.hover", theme.getCursorColor());

        com.jediterm.core.Color searchBg = theme.getColor("search.bar.background", theme.getSelectionColor());
        com.jediterm.core.Color searchBorder = theme.getColor("search.bar.border", theme.getBackgroundColor());
        com.jediterm.core.Color searchFg = theme.getColor("search.bar.foreground", theme.getTextColor());
        com.jediterm.core.Color searchHoverBg = theme.getColor("search.bar.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color searchActiveBorder = theme.getColor("search.bar.active.border", theme.getCursorColor());
        com.jediterm.core.Color searchActiveBg = theme.getColor("search.bar.active.background", theme.getSelectionColor());

        com.jediterm.core.Color dropListBg = theme.getColor("dropdown.list.background", theme.getBackgroundColor());
        com.jediterm.core.Color dropListBorder = theme.getColor("dropdown.list.border", theme.getSelectionColor());
        com.jediterm.core.Color dropItemBg = theme.getColor("dropdown.item.background", theme.getBackgroundColor());
        com.jediterm.core.Color dropItemFg = theme.getColor("dropdown.item.foreground", theme.getTextColor());
        com.jediterm.core.Color dropHoverBg = theme.getColor("dropdown.item.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color dropHoverFg = theme.getColor("dropdown.item.hover.foreground", theme.getCursorColor());

        com.jediterm.core.Color titleBtnFg = theme.getColor("titlebar.button.foreground", theme.getBackgroundColor());
        com.jediterm.core.Color titleBtnHoverBg = theme.getColor("titlebar.button.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color titleBtnHoverFg = theme.getColor("titlebar.button.hover.foreground", theme.getCursorColor());
        com.jediterm.core.Color titleCloseHoverBg = theme.getColor("titlebar.close.hover.background", new com.jediterm.core.Color(255, 85, 85));
        com.jediterm.core.Color titleCloseHoverFg = theme.getColor("titlebar.close.hover.foreground", new com.jediterm.core.Color(255, 255, 255));

        com.jediterm.core.Color sideNavFg = theme.getColor("sidebar.nav.foreground", theme.getTextColor());
        com.jediterm.core.Color sideNavHoverBg = theme.getColor("sidebar.nav.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color sideNavHoverFg = theme.getColor("sidebar.nav.hover.foreground", theme.getCursorColor());
        com.jediterm.core.Color sideNavSelectedBg = theme.getColor("sidebar.nav.selected.background", theme.getSelectionColor());
        com.jediterm.core.Color sideNavSelectedBorder = theme.getColor("sidebar.nav.selected.border", theme.getCursorColor());

        com.jediterm.core.Color snippetLauncherLabelColor = theme.getColor("snippetLauncher.label", theme.getCursorColor());

        com.jediterm.core.Color pkgBg = theme.getColor("dashboard.package.background", theme.getSelectionColor());
        com.jediterm.core.Color pkgBorder = theme.getColor("dashboard.package.border", theme.getSelectionColor());
        com.jediterm.core.Color sidebarPkgContentBg = theme.getColor("sidebar.package.content.background", theme.getBackgroundColor());
        com.jediterm.core.Color scrollThumb = theme.getColor("scrollbar.thumb", theme.getCursorColor());
        com.jediterm.core.Color scrollThumbHover = theme.getColor("scrollbar.thumb.hover", theme.getCursorColor());
        com.jediterm.core.Color dropScrollThumb = theme.getColor("dropdown.scrollbar.thumb", theme.getSelectionColor());
        com.jediterm.core.Color dropScrollThumbHover = theme.getColor("dropdown.scrollbar.thumb.hover", theme.getCursorColor());
        com.jediterm.core.Color dialogBg = theme.getColor("dialog.background", theme.getSelectionColor());
        com.jediterm.core.Color dialogBorder = theme.getColor("dialog.border", theme.getSelectionColor());
        com.jediterm.core.Color dialogHeaderBg = theme.getColor("dialog.header.background", theme.getBackgroundColor());
        com.jediterm.core.Color dialogHeaderFg = theme.getColor("dialog.header.foreground", theme.getCursorColor());

        // Highlights Theming Keys
        com.jediterm.core.Color hlSideBg = theme.getColor("highlights.sidebar.toggle.background", theme.getBackgroundColor());
        com.jediterm.core.Color hlSideActiveBg = theme.getColor("highlights.sidebar.toggle.active.background", theme.getSelectionColor());
        com.jediterm.core.Color hlSideActiveBorder = theme.getColor("highlights.sidebar.toggle.active.border", theme.getSelectionColor());
        com.jediterm.core.Color hlSideFg = theme.getColor("highlights.sidebar.toggle.foreground", theme.getTextColor());
        com.jediterm.core.Color hlSideActiveFg = theme.getColor("highlights.sidebar.toggle.active.foreground", theme.getCursorColor());
        com.jediterm.core.Color hlSideEyeInactive = theme.getColor("highlights.sidebar.toggle.eye.inactive", theme.getTextColor());
        com.jediterm.core.Color hlSideEyeActive = theme.getColor("highlights.sidebar.toggle.eye.active", theme.getCursorColor());
        com.jediterm.core.Color hlSideHoverBg = theme.getColor("highlights.sidebar.toggle.hover.background", theme.getSelectionColor());
        com.jediterm.core.Color hlSideHoverBorder = theme.getColor("highlights.sidebar.toggle.hover.border", theme.getCursorColor());

        // Highlight Package Sidebar configurations
        com.jediterm.core.Color pkgBgActive = theme.getColor("sidebar.package.background.active", theme.getSelectionColor());
        com.jediterm.core.Color pkgBgInactive = theme.getColor("sidebar.package.background.inactive", theme.getBackgroundColor());
        com.jediterm.core.Color pkgIconColor = theme.getColor("highlights.sidebar.package.icon", theme.getCursorColor());
        com.jediterm.core.Color pkgIconHoverColor = theme.getColor("highlights.sidebar.package.icon.hover", theme.getCursorColor());
        com.jediterm.core.Color pkgIconActiveColor = theme.getColor("highlights.sidebar.package.icon.active", theme.getCursorColor());
        com.jediterm.core.Color pkgTitleColor = theme.getColor("highlights.sidebar.package.title", theme.getTextColor());
        com.jediterm.core.Color pkgTitleHoverColor = theme.getColor("highlights.sidebar.package.title.hover", theme.getTextColor());
        com.jediterm.core.Color pkgTitleActiveColor = theme.getColor("highlights.sidebar.package.title.active", theme.getTextColor());
        com.jediterm.core.Color pkgEyeActiveColor = theme.getColor("highlights.sidebar.package.eye.active", theme.getCursorColor());
        com.jediterm.core.Color pkgEyeInactiveColor = theme.getColor("highlights.sidebar.package.eye.inactive", theme.getSelectionColor());

        String hexPkgIcon = toHex(pkgIconColor);
        String hexPkgIconHover = toHex(pkgIconHoverColor);
        String hexPkgIconActive = toHex(pkgIconActiveColor);
        String hexPkgTitle = toHex(pkgTitleColor);
        String hexPkgTitleHover = toHex(pkgTitleHoverColor);
        String hexPkgTitleActive = toHex(pkgTitleActiveColor);
        String hexPkgEyeActive = toHex(pkgEyeActiveColor);
        String hexPkgEyeInactive = toHex(pkgEyeInactiveColor);


        String hexHlSideFg = toHex(hlSideFg);
        String hexHlSideActiveFg = toHex(hlSideActiveFg);
        String hexHlSideEyeInactive = toHex(hlSideEyeInactive);
        String hexHlSideEyeActive = toHex(hlSideEyeActive);

        // Format to hex/rgba/gradient strings for CSS
        String hexWinBg;
        if (gradientsEnabled) {
            com.jediterm.core.Color winBgStart = theme.getColor("app.window.background.gradient.start", winBg);
            com.jediterm.core.Color winBgEnd = theme.getColor("app.window.background.gradient.end", theme.getBackgroundColor());
            String winBgRatio = "10% 90%";
            hexWinBg = getGradientStyle(winBgStart, appWinBgOpacity, winBgEnd, appWinBgOpacity, "to bottom", winBgRatio);
        } else {
            hexWinBg = toRgba(winBg, appWinBgOpacity);
        }

        String hexWinBorder = toRgba(winBorder, appWinBorderOpacity);
        String hexTitleBg = toRgba(titleBg, titleBgOpacity);
        String hexTitleBorder = toRgba(titleBorder, titleBorderOpacity);
        String hexToolBg = toRgba(toolBg, toolbarBgOpacity);
        String hexToolBorder = toRgba(toolBorder, toolbarBorderOpacity);

        String hexSideBg;
        if (gradientsEnabled) {
            com.jediterm.core.Color sideBgStart = theme.getColor("sidebar.background.gradient.start", sideBg);
            com.jediterm.core.Color sideBgEnd = theme.getColor("sidebar.background.gradient.end", theme.getBackgroundColor());
            String sideBgRatio = "20% 80%";
            hexSideBg = getGradientStyle(sideBgStart, sidebarBgOpacity, sideBgEnd, sidebarBgOpacity, "to bottom", sideBgRatio);
        } else {
            hexSideBg = toRgba(sideBg, sidebarBgOpacity);
        }

        String hexSideBorder = toRgba(sideBorder, sidebarBorderOpacity);
        String hexLabelFg = toHex(labelFg);

        String hexTermBg = toHex(termBg);
        String hexTermFg = toHex(termFg);
        String hexTermCursor = toHex(termCursor);
        String hexTermSelection = toHex(termSelection);

        String hexHeaderBg = toHex(headerBg);
        String hexTabInactiveBg = toHex(tabInactiveBg);
        String hexTabInactiveFg = toHex(tabInactiveFg);
        String hexTabActiveBg = toHex(tabActiveBg);
        String hexTabActiveFg = toHex(tabActiveFg);
        String hexTabActiveBorder = toHex(tabActiveBorder);
        String hexTabHoverBg = toHex(tabHoverBg);
        String hexPlusFg = toHex(plusFg);
        String hexPlusHoverBg = toHex(plusHoverBg);
        String hexPlusHoverFg = toHex(plusHoverFg);

        String hexCardsListBg = toHex(cardsListBg);
        String hexCardsListBorder = toHex(cardsListBorder);
        String hexCardsItemTitle = toHex(cardsItemTitle);
        String hexCardsItemSub = toHex(cardsItemSub);
        String hexSnippetLauncherLabelColor = toHex(snippetLauncherLabelColor);

        // Standard UI Opacities
        double globalButtonHoverOpacity = 0.15;
        double itemBgOpacity = 0.5;
        double itemHoverBgOpacity = 0.15;
        double itemSelectedBgOpacity = 1.0;
        double itemBorderOpacity = 1.0;
        double itemHoverBorderOpacity = 1.0;
        double itemSelectedBorderOpacity = 1.0;
        double cardsBadgeOpacity = 0.15;
        double cardsBadgeHoverOpacity = 1.0;
        double cardsBadgeSelectedOpacity = 1.0;
        double sideNavSelectedBgOpacity = 0.15;
        double pkgBgOpacity = 0.3;
        double pkgBorderOpacity = 1.0;
        double sidebarPkgContentBgOpacity = 0.15;
        double pkgBgActiveOpacity = 0.15;
        double pkgBgInactiveOpacity = 0.30;
        double scrollThumbOpacity = 0.3;
        double scrollThumbHoverOpacity = 0.6;
        double dropScrollThumbOpacity = 0.4;
        double dropScrollThumbHoverOpacity = 0.7;
        double dialogBgOpacity = 1.0;
        double dialogBorderOpacity = 1.0;
        double dialogHeaderBgOpacity = 0.5;
        double searchBarBgOpacity = 0.5;
        double zoomBgOpacity = 0.50;
        double zoomBorderOpacity = 0.50;
        double zoomHoverBgOpacity = 1.0;
        double zoomHoverBorderOpacity = 1.0;
        double zoomActiveBgOpacity = 0.50;
        double zoomActiveBorderOpacity = 0.50;

        double toggleBgOpacity = 0.50;
        double toggleBorderOpacity = 0.10;
        double toggleHoverBgOpacity = 1.0;
        double toggleHoverBorderOpacity = 1.0;
        double toggleActiveBgOpacity = 0.50;
        double toggleActiveBorderOpacity = 0.50;

        double homeBgOpacity = 0.50;
        double homeBorderOpacity = 0.10;
        double homeHoverBgOpacity = 1.0;
        double homeHoverBorderOpacity = 1.0;
        double homeActiveBgOpacity = 0.50;
        double homeActiveBorderOpacity = 0.50;

        double settingsBgOpacity = 0.50;
        double settingsBorderOpacity = 0.10;
        double settingsHoverBgOpacity = 1.0;
        double settingsHoverBorderOpacity = 1.0;
        double settingsActiveBgOpacity = 0.50;
        double settingsActiveBorderOpacity = 0.50;

        double snippetHoverBtnBgOpacity = 1.0;
        double snippetHoverBtnBorderOpacity = 0.30;
        double snippetHoverBtnHoverBgOpacity = 1.0;
        double snippetHoverBtnHoverBorderOpacity = 1.0;

        double btnBgOpacity = 1.0;
        double btnBorderOpacity = 1.0;
        double btnHoverBgOpacity = .15;
        double btnHoverBorderOpacity = 1.0;
        double comboHoverBgOpacity = 0.15;
        double dropItemHoverBgOpacity = 0.50;

        double hlSideBgOpacity = 0.50;
        double hlSideBorderOpacity = 0.50;
        double hlSideHoverBgOpacity = 0.15;
        double hlSideHoverBorderOpacity = 1.0;
        double hlSideActiveBgOpacity = 0.25;
        double hlSideActiveBorderOpacity = 0.75;

        String rgbaZoomBg = toRgba(zoomBg, zoomBgOpacity);
        String rgbaZoomBorder = toRgba(zoomBorder, zoomBorderOpacity);
        String hexZoomFg = toHex(zoomFg);
        String rgbaZoomHoverBg = toRgba(zoomHoverBg, zoomHoverBgOpacity);
        String rgbaZoomHoverBorder = toRgba(zoomHoverBorder, zoomHoverBorderOpacity);
        String hexZoomHoverFg = toHex(zoomHoverFg);
        String rgbaZoomActiveBg = toRgba(zoomActiveBg, zoomActiveBgOpacity);
        String rgbaZoomActiveBorder = toRgba(zoomActiveBorder, zoomActiveBorderOpacity);
        String hexZoomActiveFg = toHex(zoomActiveFg);

        String rgbaToggleBg = toRgba(toggleBg, toggleBgOpacity);
        String rgbaToggleBorder = toRgba(toggleBorder, toggleBorderOpacity);
        String hexToggleFg = toHex(toggleFg);
        String rgbaToggleHoverBg = toRgba(toggleHoverBg, toggleHoverBgOpacity);
        String rgbaToggleHoverBorder = toRgba(toggleHoverBorder, toggleHoverBorderOpacity);
        String hexToggleHoverFg = toHex(toggleHoverFg);
        String rgbaToggleActiveBg = toRgba(toggleActiveBg, toggleActiveBgOpacity);
        String rgbaToggleActiveBorder = toRgba(toggleActiveBorder, toggleActiveBorderOpacity);
        String hexToggleActiveFg = toHex(toggleActiveFg);

        String rgbaHomeBg = toRgba(homeBg, homeBgOpacity);
        String rgbaHomeBorder = toRgba(homeBorder, homeBorderOpacity);
        String hexHomeFg = toHex(homeFg);
        String rgbaHomeHoverBg = toRgba(homeHoverBg, homeHoverBgOpacity);
        String rgbaHomeHoverBorder = toRgba(homeHoverBorder, homeHoverBorderOpacity);
        String hexHomeHoverFg = toHex(homeHoverFg);
        String rgbaHomeActiveBg = toRgba(homeActiveBg, homeActiveBgOpacity);
        String rgbaHomeActiveBorder = toRgba(homeActiveBorder, homeActiveBorderOpacity);
        String hexHomeActiveFg = toHex(homeActiveFg);

        String rgbaSettingsBg = toRgba(settingsBg, settingsBgOpacity);
        String rgbaSettingsBorder = toRgba(settingsBorder, settingsBorderOpacity);
        String hexSettingsFg = toHex(settingsFg);
        String rgbaSettingsHoverBg = toRgba(settingsHoverBg, settingsHoverBgOpacity);
        String rgbaSettingsHoverBorder = toRgba(settingsHoverBorder, settingsHoverBorderOpacity);
        String hexSettingsHoverFg = toHex(settingsHoverFg);
        String rgbaSettingsActiveBg = toRgba(settingsActiveBg, settingsActiveBgOpacity);
        String rgbaSettingsActiveBorder = toRgba(settingsActiveBorder, settingsActiveBorderOpacity);
        String hexSettingsActiveFg = toHex(settingsActiveFg);

        String rgbaSnippetHoverBtnBg = toRgba(snippetHoverBtnBg, snippetHoverBtnBgOpacity);
        String rgbaSnippetHoverBtnBorder = toRgba(snippetHoverBtnBorder, snippetHoverBtnBorderOpacity);
        String hexSnippetHoverBtnFg = toHex(snippetHoverBtnFg);
        String rgbaSnippetHoverBtnHoverBg = toRgba(snippetHoverBtnHoverBg, snippetHoverBtnHoverBgOpacity);
        String rgbaSnippetHoverBtnHoverBorder = toRgba(snippetHoverBtnHoverBorder, snippetHoverBtnHoverBorderOpacity);
        String hexSnippetHoverBtnHoverFg = toHex(snippetHoverBtnHoverFg);

        String rgbaCardsItemBg = toRgba(cardsItemBg, itemBgOpacity);
        String rgbaCardsItemBorder = toRgba(cardsItemBorder, itemBorderOpacity);
        String rgbaCardsHoverBg = toRgba(cardsHoverBg, itemHoverBgOpacity);
        String rgbaCardsHoverBorder = toRgba(cardsHoverBorder, itemHoverBorderOpacity);
        String rgbaCardsSelectedBg = toRgba(cardsSelectedBg, itemSelectedBgOpacity);
        String rgbaCardsSelectedBorder = toRgba(cardsSelectedBorder, itemSelectedBorderOpacity);
        String rgbaCardsBadgeBg = toRgba(cardsBadgeBg, cardsBadgeOpacity);
        String rgbaCardsBadgeHoverBg = toRgba(cardsBadgeHoverBg, cardsBadgeHoverOpacity);
        String rgbaCardsBadgeSelectedBg = toRgba(cardsBadgeSelectedBg, cardsBadgeSelectedOpacity);
        String rgbaSideNavSelectedBg = toRgba(sideNavSelectedBg, sideNavSelectedBgOpacity);
        String rgbaPkgBg = toRgba(pkgBg, pkgBgOpacity);
        String rgbaPkgBorder = toRgba(pkgBorder, pkgBorderOpacity);
        String rgbaSidebarPkgContentBg = toRgba(sidebarPkgContentBg, sidebarPkgContentBgOpacity);
        String rgbaPkgBgActive = toRgba(pkgBgActive, pkgBgActiveOpacity);
        String rgbaPkgBgInactive = toRgba(pkgBgInactive, pkgBgInactiveOpacity);
        String rgbaScrollThumb = toRgba(scrollThumb, scrollThumbOpacity);
        String rgbaScrollThumbHover = toRgba(scrollThumbHover, scrollThumbHoverOpacity);
        String rgbaDropScrollThumb = toRgba(dropScrollThumb, dropScrollThumbOpacity);
        String rgbaDropScrollThumbHover = toRgba(dropScrollThumbHover, dropScrollThumbHoverOpacity);

        String rgbaHlSideBg = toRgba(hlSideBg, hlSideBgOpacity);
        String rgbaHlSideHoverBg = toRgba(hlSideHoverBg, hlSideHoverBgOpacity);
        String rgbaHlSideHoverBorder = toRgba(hlSideHoverBorder, hlSideHoverBorderOpacity);
        String rgbaHlSideActiveBg = toRgba(theme.getColor("highlights.sidebar.toggle.active.background", theme.getSelectionColor()), hlSideActiveBgOpacity);
        String rgbaHlSideActiveBorder = toRgba(theme.getColor("highlights.sidebar.toggle.active.border", theme.getSelectionColor()), hlSideActiveBorderOpacity);

        String rgbaDialogBg;
        if (gradientsEnabled) {
            com.jediterm.core.Color dialogBgStart = theme.getColor("dialog.background.gradient.start", dialogBg);
            com.jediterm.core.Color dialogBgEnd = theme.getColor("dialog.background.gradient.end", theme.getBackgroundColor());
            String dialogBgRatio = "100% 0%";
            rgbaDialogBg = getGradientStyle(dialogBgStart, dialogBgOpacity, dialogBgEnd, dialogBgOpacity, "to bottom", dialogBgRatio);
        } else {
            rgbaDialogBg = toRgba(dialogBg, dialogBgOpacity);
        }

        String rgbaDialogBorder = toRgba(dialogBorder, dialogBorderOpacity);
        String rgbaDialogHeaderBg = toRgba(dialogHeaderBg, dialogHeaderBgOpacity);
        String hexDialogHeaderFg = toHex(dialogHeaderFg);

        String hexCardsBadgeFg = toHex(cardsBadgeFg);
        String hexCardsBadgeHoverFg = toHex(cardsBadgeHoverFg);
        String hexCardsBadgeSelectedFg = toHex(cardsBadgeSelectedFg);
        String rgbaCardsSelectedGlow = toRgba(cardsSelectedGlow, 0.4);

        String rgbaBtnBg = toRgba(btnBg, btnBgOpacity);
        String rgbaBtnBorder = toRgba(btnBorder, btnBorderOpacity);
        String hexBtnFg = toHex(btnFg);
        String rgbaBtnHoverBg = toRgba(btnHoverBg, btnHoverBgOpacity);
        String rgbaBtnHoverBorder = toRgba(btnHoverBorder, btnHoverBorderOpacity);
        String hexBtnHoverFg = toHex(btnHoverFg);
        String hexBtnHoverBg = toHex(btnHoverBg);
        String hexBtnIcon = toHex(btnIcon);
        String hexComboBg = toHex(comboBg);
        String hexComboBorder = toHex(comboBorder);
        String hexComboFg = toHex(comboFg);
        String rgbaComboHoverBg = toRgba(btnHoverBg, comboHoverBgOpacity);
        String hexColorPickerBg = toHex(colorPickerBg);
        String hexColorPickerBorder = toHex(colorPickerBorder);
        String hexColorPickerFg = toHex(colorPickerFg);
        String hexColorPickerPopupBg = toHex(colorPickerPopupBg);
        String hexColorPickerPopupBorder = toHex(colorPickerPopupBorder);
        String hexColorPickerCustomBg = toHex(colorPickerCustomBg);
        String hexColorPickerCustomControlsBg = toHex(colorPickerCustomControlsBg);
        String hexColorPickerCustomBtnBg = toHex(colorPickerCustomBtnBg);
        String hexColorPickerCustomBtnBorder = toHex(colorPickerCustomBtnBorder);
        String hexColorPickerCustomBtnFg = toHex(colorPickerCustomBtnFg);
        String hexColorPickerCustomBtnHoverBg = toHex(colorPickerCustomBtnHoverBg);
        String hexColorPickerCustomBtnHoverBorder = toHex(colorPickerCustomBtnHoverBorder);
        String hexColorPickerCustomBtnHoverFg = toHex(colorPickerCustomBtnHoverFg);
        String hexColorPickerCustomToggleBg = toHex(colorPickerCustomToggleBg);
        String hexColorPickerCustomToggleBorder = toHex(colorPickerCustomToggleBorder);
        String hexColorPickerCustomToggleFg = toHex(colorPickerCustomToggleFg);
        String hexColorPickerCustomToggleSelectedBg = toHex(colorPickerCustomToggleSelectedBg);
        String hexColorPickerCustomToggleSelectedBorder = toHex(colorPickerCustomToggleSelectedBorder);
        String hexColorPickerCustomToggleSelectedFg = toHex(colorPickerCustomToggleSelectedFg);
        String hexInputBg = toHex(inputBg);
        String hexInputBorder = toHex(inputBorder);
        String hexInputFg = toHex(inputFg);

        String hexConnBg = toHex(connBg);
        String hexConnIcon = toHex(connIcon);
        String hexConnFg = toHex(connFg);
        String hexSplitPaneBg = toHex(splitPaneBg);
        String hexSplitDividerBg = toHex(splitDividerBg);
        String hexSplitDividerHover = toHex(splitDividerHover);

        String rgbaSearchBg = toRgba(searchBg, searchBarBgOpacity);
        String hexSearchBorder = toHex(searchBorder);
        String hexSearchFg = toHex(searchFg);
        String hexSearchHoverBg = toHex(searchHoverBg);
        String hexSearchActiveBorder = toHex(searchActiveBorder);
        String hexSearchActiveBg = toHex(searchActiveBg);

        String hexDropListBg = toHex(dropListBg);
        String hexDropListBorder = toHex(dropListBorder);
        String hexDropItemFg = toHex(dropItemFg);
        String hexDropHoverBg = toHex(dropHoverBg);
        String hexDropHoverFg = toHex(dropHoverFg);
        String rgbaDropItemHoverBg = toRgba(dropItemBg, dropItemHoverBgOpacity);

        String hexTitleBtnFg = toHex(titleBtnFg);
        String hexTitleBtnHoverBg = toHex(titleBtnHoverBg);
        String hexTitleBtnHoverFg = toHex(titleBtnHoverFg);
        String hexTitleCloseHoverBg = toHex(titleCloseHoverBg);
        String hexTitleCloseHoverFg = toHex(titleCloseHoverFg);

        String hexSideNavFg = toHex(sideNavFg);
        String hexSideNavSelectedBorder = toHex(sideNavSelectedBorder);
        String rgbaHexSideNavHoverBg = toRgba(sideNavHoverBg,globalButtonHoverOpacity);

        String dynamicCss = ".root { -fx-background-color: " + hexWinBg + "; -fx-border-color: " + hexWinBorder + "; -fx-border-width: 1px; -fx-background-radius: 0px; -fx-border-radius: 0px; }\n" +
            ".custom-title-bar { -fx-background-color: " + hexTitleBg + "; -fx-border-color: " + hexTitleBorder + "; -fx-border-width: 0 0 1 0; -fx-background-radius: 0px; }\n" +
            ".button.title-btn { -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0px; -fx-pref-width: 46px; -fx-pref-height: 36px; -fx-min-width: 46px; -fx-min-height: 36px; -fx-max-width: 46px; -fx-max-height: 36px; -fx-padding: 0; -fx-alignment: center; -fx-text-fill: " + hexTitleBtnFg + "; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; }\n" +
            ".button.title-btn:hover { -fx-background-color: " + hexTitleBtnHoverBg + "; -fx-text-fill: " + hexTitleBtnHoverFg + "; }\n" +
            ".button.title-btn-close { -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0px; -fx-pref-width: 46px; -fx-pref-height: 36px; -fx-min-width: 46px; -fx-min-height: 36px; -fx-max-width: 46px; -fx-max-height: 36px; -fx-padding: 0; -fx-alignment: center; -fx-text-fill: " + hexTitleBtnFg + "; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; }\n" +
            ".button.title-btn-close:hover { -fx-background-color: " + hexTitleCloseHoverBg + "; -fx-text-fill: " + hexTitleCloseHoverFg + "; }\n" +
            ".sidebar { -fx-background-color: " + hexSideBg + "; -fx-border-color: " + hexSideBorder + "; }\n" +
            ".top-bar { -fx-background-color: " + hexToolBg + "; -fx-border-color: " + hexToolBorder + "; -fx-pref-height: 46px; -fx-min-height: 46px; -fx-max-height: 46px; }\n" +
            ".button { -fx-background-color: " + rgbaBtnBg + "; -fx-border-color: " + rgbaBtnBorder + "; -fx-text-fill: " + hexBtnFg + "; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
            ".button:hover { -fx-background-color: " + rgbaBtnHoverBg + "; -fx-border-color: " + rgbaBtnHoverBorder + "; -fx-text-fill: " + hexBtnFg + "; }\n" +
            ".button .button-icon { -fx-text-fill: " + hexBtnIcon + "; }\n" +
            ".button:hover .button-icon { -fx-text-fill: " + hexBtnIcon + "; }\n" +
            ".button:disabled { -fx-opacity: 0.4; -fx-cursor: default; }\n" +
            ".button.zoom-btn { -fx-background-color: " + rgbaZoomBg + "; -fx-border-color: " + rgbaZoomBorder + "; -fx-text-fill: " + hexZoomFg + "; -fx-padding: 3px 8px; -fx-font-size: 11px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
            ".button.zoom-btn:hover { -fx-background-color: " + rgbaZoomHoverBg + "; -fx-border-color: " + rgbaZoomHoverBorder + "; -fx-text-fill: " + hexZoomHoverFg + "; }\n" +
            ".button.zoom-btn:pressed { -fx-background-color: " + rgbaZoomActiveBg + "; -fx-border-color: " + rgbaZoomActiveBorder + "; -fx-text-fill: " + hexZoomActiveFg + "; }\n" +
            ".button.zoom-reset-btn { -fx-min-width: 56px; -fx-pref-width: 56px; -fx-max-width: 56px; }\n" +
            ".button.zoom-icon-btn { -fx-font-family: \"codicon\"; -fx-font-size: 11px; }\n" +
            ".button.sidebar-toggle-btn { -fx-background-color: " + rgbaToggleBg + "; -fx-border-color: " + rgbaToggleBorder + "; -fx-text-fill: " + hexToggleFg + "; -fx-padding: 3px 8px; -fx-font-family: \"codicon\"; -fx-font-size: 14px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
            ".button.sidebar-toggle-btn:hover { -fx-background-color: " + rgbaToggleHoverBg + "; -fx-border-color: " + rgbaToggleHoverBorder + "; -fx-text-fill: " + hexToggleHoverFg + "; }\n" +
            ".button.sidebar-toggle-btn:pressed { -fx-background-color: " + rgbaToggleActiveBg + "; -fx-border-color: " + rgbaToggleActiveBorder + "; -fx-text-fill: " + hexToggleActiveFg + "; }\n" +
            ".button.home-btn { -fx-background-color: " + rgbaHomeBg + "; -fx-border-color: " + rgbaHomeBorder + "; -fx-text-fill: " + hexHomeFg + "; -fx-padding: 3px 8px; -fx-font-family: \"codicon\"; -fx-font-size: 14px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
            ".button.home-btn:hover { -fx-background-color: " + rgbaHomeHoverBg + "; -fx-border-color: " + rgbaHomeHoverBorder + "; -fx-text-fill: " + hexHomeHoverFg + "; }\n" +
            ".button.home-btn:pressed { -fx-background-color: " + rgbaHomeActiveBg + "; -fx-border-color: " + rgbaHomeActiveBorder + "; -fx-text-fill: " + hexHomeActiveFg + "; }\n" +
            ".button.settings-btn { -fx-background-color: " + rgbaSettingsBg + "; -fx-border-color: " + rgbaSettingsBorder + "; -fx-text-fill: " + hexSettingsFg + "; -fx-padding: 3px 8px; -fx-font-family: \"codicon\"; -fx-font-size: 14px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
            ".button.settings-btn:hover { -fx-background-color: " + rgbaSettingsHoverBg + "; -fx-border-color: " + rgbaSettingsHoverBorder + "; -fx-text-fill: " + hexSettingsHoverFg + "; }\n" +
            ".button.settings-btn:pressed { -fx-background-color: " + rgbaSettingsActiveBg + "; -fx-border-color: " + rgbaSettingsActiveBorder + "; -fx-text-fill: " + hexSettingsActiveFg + "; }\n" +
            ".button.snippet-hover-btn { -fx-background-color: " + rgbaSnippetHoverBtnBg + "; -fx-border-color: " + rgbaSnippetHoverBtnBorder + "; -fx-text-fill: " + hexSnippetHoverBtnFg + "; -fx-padding: 0; -fx-font-family: \"codicon\"; -fx-font-size: 11px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 3px; -fx-border-radius: 3px; }\n" +
            ".button.snippet-hover-btn:hover { -fx-background-color: " + rgbaSnippetHoverBtnHoverBg + "; -fx-border-color: " + rgbaSnippetHoverBtnHoverBorder + "; -fx-text-fill: " + hexSnippetHoverBtnHoverFg + "; }\n" +
            ".button.terminal-btn { -fx-background-color: " + rgbaHomeBg + "; -fx-border-color: " + rgbaHomeBorder + "; -fx-text-fill: " + hexHomeFg + "; -fx-padding: 3px 8px; -fx-font-family: \"codicon\"; -fx-font-size: 14px; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
            ".button.terminal-btn:hover { -fx-background-color: " + rgbaHomeHoverBg + "; -fx-border-color: " + rgbaHomeHoverBorder + "; -fx-text-fill: " + hexHomeHoverFg + "; }\n" +
            ".button.terminal-btn:pressed { -fx-background-color: " + rgbaHomeActiveBg + "; -fx-border-color: " + rgbaHomeActiveBorder + "; -fx-text-fill: " + hexHomeActiveFg + "; }\n" +
            ".combo-box, .dialog-pane .combo-box { -fx-background-color: " + hexComboBg + "; -fx-text-fill: " + hexComboFg + "; -fx-border-color: " + hexComboBorder + "; -fx-background-insets: 0; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-border-width: 0px; }\n" +
            ".combo-box .arrow-button, .dialog-pane .combo-box .arrow-button { -fx-background-color: transparent; -fx-background-insets: 0; -fx-background-radius: 0; }\n" +
            ".combo-box .arrow-button .arrow, .dialog-pane .combo-box .arrow-button .arrow { -fx-background-color: " + hexComboFg + "; -fx-background-insets: 0; }\n" +
            ".combo-box.shell-dropdown { -fx-pref-width: 140px; -fx-max-width: 140px; }\n" +
            ".combo-box.theme-dropdown { -fx-border-color: transparent; -fx-border-width: 0px; }\n" +
            ".combo-box > .list-cell, .dialog-pane .combo-box > .list-cell { -fx-text-fill: " + hexComboFg + "; -fx-background-radius: 3px; -fx-border-radius: 3px;}\n" +
            ".combo-box:hover, .dialog-pane .combo-box:hover { -fx-background-color: " + rgbaComboHoverBg + "; }\n" +
            ".combo-box.theme-dropdown:hover { -fx-background-color: " + rgbaComboHoverBg + "; }\n" +
            ".combo-box:hover > .list-cell, .dialog-pane .combo-box:hover > .list-cell { -fx-text-fill: " + dropItemFg + "; }\n" +
            ".combo-box:hover .arrow-button .arrow, .dialog-pane .combo-box:hover .arrow-button .arrow { -fx-background-color: " + dropItemFg + "; }\n" +
            ".combo-box:focused, .dialog-pane .combo-box:focused { -fx-border-color: " + hexComboBorder + "; }\n" +
            ".combo-box:disabled, .dialog-pane .combo-box:disabled { -fx-opacity: 0.5; }\n" +
            ".text-field { -fx-background-color: " + rgbaSearchBg + "; -fx-text-fill: " + hexSearchFg + "; -fx-border-color: " + hexSearchBorder + "; -fx-prompt-text-fill: "+ hexColorPickerFg +";}\n" +
            ".text-field:hover { -fx-background-color: " + hexSearchHoverBg + "; }\n" +
            ".text-field:focused { -fx-background-color: " + hexSearchActiveBg + "; -fx-border-color: " + hexSearchActiveBorder + "; }\n" +
            ".scroll-pane { -fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; -fx-border-color: transparent; }\n" +
            ".scroll-pane .viewport { -fx-background-color: transparent; }\n" +
            ".list-view { -fx-background-color: " + hexCardsListBg + "; -fx-control-inner-background: " + hexCardsListBg + "; -fx-border-color: " + hexCardsListBorder + "; }\n" +
            ".list-cell { -fx-background-color: transparent; -fx-padding: 4px 0px; }\n" +
            ".list-cell:filled:hover .host-card, .list-cell:filled:hover .snippet-card, .host-card:hover, .snippet-card:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
            ".list-cell:filled:selected .host-card, .list-cell:filled:selected .snippet-card, .host-card.selected, .snippet-card.selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + rgbaCardsSelectedBorder + "; -fx-effect: dropshadow(three-pass-box, " + rgbaCardsSelectedGlow + ", 8, 0, 0, 0); }\n" +
            ".session-cell { -fx-text-fill: " + hexCardsItemTitle + "; -fx-padding: 8px; -fx-font-size: 13px; -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-background-insets: 0; -fx-border-width: 1px; }\n" +
            ".session-cell:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; -fx-border-radius: 6px; -fx-background-radius: 6px; }\n" +
            ".session-cell:selected, .session-cell.selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + rgbaCardsSelectedBorder + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-effect: dropshadow(three-pass-box, " + rgbaCardsSelectedGlow + ", 8, 0, 0, 0); }\n" +
            ".combo-box-popup .list-view { -fx-background-color: " + hexDropListBg + "; -fx-control-inner-background: " + hexDropListBg + "; -fx-border-color: " + hexDropListBorder + "; -fx-background-radius: 3px; -fx-border-radius: 3px;}\n" +
                ".combo-box-popup .list-view .list-cell, .dialog-pane .combo-box-popup .list-view .list-cell { -fx-text-fill: " + hexDropItemFg + "; -fx-background-color: " + hexDropHoverBg + "; -fx-padding: 6px 12px; -fx-background-insets: 1px 0px 1px 0px; }\n" +
                ".combo-box-popup .list-view .list-cell:hover, .dialog-pane .combo-box-popup .list-view .list-cell:hover { -fx-background-color: " + rgbaDropItemHoverBg + "; -fx-text-fill: " + hexDropHoverFg + "; -fx-background-insets: 1px 0px 1px 0px; }\n" +
                ".combo-box-popup .list-view .list-cell:selected, .dialog-pane .combo-box-popup .list-view .list-cell:selected { -fx-background-color: " + rgbaDropItemHoverBg + "; -fx-text-fill: " + hexDropHoverFg + "; -fx-background-insets: 1px 0px 1px 0px; }\n" +
            ".combo-box-popup .list-view .scroll-bar { -fx-background-color: transparent; }\n" +
            ".combo-box-popup .list-view .scroll-bar:horizontal { -fx-pref-height: 8px; -fx-max-height: 8px; }\n" +
            ".combo-box-popup .list-view .scroll-bar:vertical { -fx-pref-width: 13px; -fx-max-width: 13px; -fx-padding: 0 0 0 5px; }\n" +
            ".combo-box-popup .list-view .scroll-bar .track { -fx-background-color: transparent; -fx-border-color: transparent; -fx-background-insets: 0; }\n" +
            ".combo-box-popup .list-view .scroll-bar .thumb { -fx-background-color: " + rgbaDropScrollThumb + "; -fx-background-radius: 3px; -fx-border-radius: 3px; -fx-background-insets: 1px; }\n" +
            ".combo-box-popup .list-view .scroll-bar .thumb:hover { -fx-background-color: " + rgbaDropScrollThumbHover + "; }\n" +
            ".combo-box-popup .list-view .scroll-bar .increment-button, .combo-box-popup .list-view .scroll-bar .decrement-button { -fx-background-color: transparent; -fx-background-radius: 0; -fx-border-color: transparent; -fx-padding: 0px; }\n" +
            ".combo-box-popup .list-view .scroll-bar .increment-arrow, .combo-box-popup .list-view .scroll-bar .decrement-arrow { -fx-shape: \"\"; -fx-padding: 0px; -fx-max-width: 0px; -fx-max-height: 0px; }\n" +
            ".host-card { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; }\n" +
            ".host-badge { -fx-background-color: " + rgbaCardsBadgeBg + "; -fx-text-fill: " + hexCardsBadgeFg + "; }\n" +
            ".host-card:hover .host-badge { -fx-background-color: " + rgbaCardsBadgeHoverBg + "; -fx-text-fill: " + hexCardsBadgeHoverFg + "; }\n" +
            ".host-name { -fx-text-fill: " + hexCardsItemTitle + "; }\n" +
            ".host-username { -fx-text-fill: " + hexCardsItemSub + "; }\n" +
            ".connection-card { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-border-width: 1px; }\n" +
            ".connection-badge { -fx-background-color: " + rgbaCardsBadgeBg + "; -fx-text-fill: " + hexCardsBadgeFg + "; -fx-background-radius: 16px; }\n" +
            ".connection-card:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
            ".connection-card:hover .connection-badge { -fx-background-color: " + rgbaCardsBadgeHoverBg + "; -fx-text-fill: " + hexCardsBadgeHoverFg + "; }\n" +
            ".connection-card.selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + rgbaCardsSelectedBorder + "; -fx-effect: dropshadow(three-pass-box, " + rgbaCardsSelectedGlow + ", 8, 0, 0, 0); }\n" +
            ".connection-card.selected .connection-badge { -fx-background-color: " + rgbaCardsBadgeSelectedBg + "; -fx-text-fill: " + hexCardsBadgeSelectedFg + "; }\n" +
            ".connection-name { -fx-text-fill: " + hexCardsItemTitle + "; -fx-font-weight: bold; }\n" +
            ".connection-username { -fx-text-fill: " + hexCardsItemSub + "; }\n" +
            ".snippet-card { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; }\n" +
            ".snippet-badge { -fx-background-color: " + rgbaCardsBadgeBg + "; -fx-text-fill: " + hexCardsBadgeFg + "; }\n" +
            ".snippet-card:hover .snippet-badge { -fx-background-color: " + rgbaCardsBadgeHoverBg + "; -fx-text-fill: " + hexCardsBadgeHoverFg + "; }\n" +
            ".list-cell:filled:hover .host-badge, .list-cell:filled:hover .snippet-badge { -fx-background-color: " + rgbaCardsBadgeHoverBg + "; -fx-text-fill: " + hexCardsBadgeHoverFg + "; }\n" +
            ".list-cell:filled:selected .host-badge, .list-cell:filled:selected .snippet-badge, .host-card.selected .host-badge, .snippet-card.selected .snippet-badge { -fx-background-color: " + rgbaCardsBadgeSelectedBg + "; -fx-text-fill: " + hexCardsBadgeSelectedFg + "; }\n" +
            ".snippet-title { -fx-text-fill: " + hexCardsItemTitle + "; }\n" +
            ".snippet-preview { -fx-text-fill: " + hexCardsItemSub + "; }\n" +
            ".tab-pane .tab-header-area .tab-header-background { -fx-background-color: " + hexHeaderBg + "; }\n" +
            ".tab-pane { -fx-tab-min-width: 20px; -fx-tab-max-width: 180px; }\n" +
            ".tab-pane .tab-content-area { -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0px; -fx-padding: 0px; }\n" +
            ".tab-pane .tab-header-area { -fx-padding: 4px 20px 0px 0px; }\n" +
            ".tab-pane .headers-region { -fx-padding: 0px; }\n" +
            ".tab { -fx-background-color: " + hexTabInactiveBg + "; -fx-background-insets: 0 1 0 0; -fx-padding: 3px 12px; -fx-background-radius: 4px 4px 0 0; -fx-border-color: transparent; -fx-border-radius: 4px 4px 0 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; }\n" +
            ".tab .tab-label { -fx-text-fill: " + hexTabInactiveFg + "; }\n" +
            ".tab:focused { -fx-focus-color: transparent; -fx-faint-focus-color: transparent; }\n" +
            ".tab:selected { -fx-background-color: " + hexTabActiveBg + "; -fx-background-insets: 0 1 -1 0; -fx-border-color: " + hexTabActiveBorder + " transparent transparent transparent; -fx-border-width: 2px 0 0 0; }\n" +
            ".tab:selected .tab-label { -fx-text-fill: " + hexTabActiveFg + "; }\n" +
            ".tab:selected .tab-close-button { -fx-background-color: #cbd5e1; }\n" +
            ".tab .tab-close-button:hover { -fx-background-color: #ff5555; }\n" +
            ".tab:hover { -fx-background-color: " + hexTabHoverBg + "; }\n" +
            ".tab-pane .tab.add-tab { -fx-background-color: transparent; -fx-border-color: transparent; -fx-cursor: hand;     -fx-padding:4px 10px; }\n" +
            ".tab-pane .tab.add-tab:hover { -fx-background-color: " + hexPlusHoverBg + "; }\n" +
            ".tab-pane .tab.add-tab .tab-label { -fx-text-fill: " + hexPlusFg + "; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 0px; -fx-alignment: center; }\n" +
            ".tab-pane .tab.add-tab:hover .tab-label { -fx-text-fill: " + hexPlusHoverFg + "; }\n" +
            ".tab-pane > .tab-header-area > .control-buttons-tab { -fx-padding: 0; -fx-max-width: 0; -fx-min-width: 0; -fx-pref-width: 0; -fx-opacity: 0; visibility: hidden; }\n" +
            ".tab-pane > .tab-header-area > .control-buttons-tab > .tab-down-button { -fx-padding: 0; visibility: hidden; }\n" +
            ".slim-nav-rail { -fx-background-color: " + hexSideBg + "; -fx-border-color: " + hexSideBorder + "; -fx-border-width: 0 1 0 0; }\n" +
            ".nav-button { -fx-background-color: transparent; -fx-text-fill: " + hexSideNavFg + "; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 10px 5px; -fx-cursor: hand; -fx-background-radius: 4px; }\n" +
            ".nav-button:hover { -fx-text-fill: " + hexSideNavFg + "; -fx-background-color: " + rgbaHexSideNavHoverBg + "; }\n" +
            ".nav-button:selected { -fx-text-fill: " + hexSideNavSelectedBorder + "; -fx-background-color: " + rgbaSideNavSelectedBg + "; -fx-border-color: transparent transparent transparent " + hexSideNavSelectedBorder + "; -fx-border-width: 0 0 0 3px; }\n" +
            ".dashboard-view { -fx-background-color: " + hexWinBg + "; }\n" +
            ".back-button { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + hexWinBg + "; -fx-text-fill: " + hexTermCursor + "; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8px; -fx-cursor: hand; -fx-background-radius: 4px; }\n" +
            ".back-button:hover { -fx-background-color: " + hexBtnHoverBg + "; -fx-border-color: " + hexTermCursor + "; }\n" +
            ".context-menu { -fx-background-color: " + hexWinBg + "; -fx-border-color: " + hexWinBorder + "; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-border-width: 1px; -fx-padding: 4px 0px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 8, 0, 0, 4); }\n" +
            ".menu-item:hover { -fx-background-color: " + hexTabHoverBg + "; }\n" +
            ".menu-item .label { -fx-text-fill: " + hexPlusFg + "; }\n" +
            ".menu-item:hover .label { -fx-text-fill: " + hexPlusHoverFg + "; }\n" +
            ".label.snippets-launcher-header { -fx-text-fill: " + hexSnippetLauncherLabelColor + "; }\n" + 
            ".app-title { -fx-font-family: \"Impact\", \"Consolas\", monospace; -fx-font-size: 20px; -fx-text-fill: #ffffff; -fx-effect: dropshadow(three-pass-box, " + hexPlusHoverFg + ", 0, 0, 2, 2); }\n" +
            ".label { -fx-text-fill: " + hexLabelFg + "; }\n" +
            ".dashboard-package-section { -fx-background-color: " + rgbaPkgBg + "; -fx-border-color: " + rgbaPkgBorder + "; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-padding: 15px; }\n" +
            ".package-titled-pane { -fx-text-fill: " + hexCardsItemTitle + "; -fx-font-weight: bold; -fx-font-size: 12px; }\n" +
            ".package-titled-pane > .title { -fx-background-color: " + rgbaPkgBgInactive + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 8px 12px; }\n" +
            ".package-titled-pane:expanded > .title { -fx-background-color: " + rgbaPkgBgActive + "; }\n" +
            ".package-titled-pane > .title:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
            ".package-folder-icon { -fx-text-fill: " + hexPkgIcon + "; }\n" +
            ".package-titled-pane > .title:hover .package-folder-icon { -fx-text-fill: " + hexPkgIconHover + "; }\n" +
            ".package-titled-pane:expanded > .title .package-folder-icon { -fx-text-fill: " + hexPkgIconActive + "; }\n" +
            ".package-title-label { -fx-text-fill: " + hexPkgTitle + "; }\n" +
            ".package-titled-pane > .title:hover .package-title-label { -fx-text-fill: " + hexPkgTitleHover + "; }\n" +
            ".package-titled-pane:expanded > .title .package-title-label { -fx-text-fill: " + hexPkgTitleActive + "; }\n" +
            ".package-eye-icon { -fx-font-family: 'codicon'; -fx-font-size: 14px; -fx-cursor: hand; }\n" +
            ".package-eye-icon.active { -fx-text-fill: " + hexPkgEyeActive + "; }\n" +
            ".package-eye-icon.inactive { -fx-text-fill: " + hexPkgEyeInactive + "; }\n" +
            ".package-titled-pane > .title > .arrow-button .arrow { -fx-background-color: " + hexTermCursor + "; }\n" +
            ".package-titled-pane > .content { -fx-background-color: " + rgbaSidebarPkgContentBg + "; -fx-border-color: " + hexSideBorder + "; -fx-border-width: 0 1px 1px 1px; -fx-border-radius: 0 0 4px 4px; -fx-background-radius: 0 0 4px 4px; -fx-padding: 8px; }\n" +
            ".dashboard-package-title { -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + hexCardsItemTitle + "; }\n" +
            ".dashboard-standalone-title { -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + hexCardsItemTitle + "; }\n" +
            ".scroll-bar { -fx-background-color: transparent; }\n" +
            ".scroll-bar:horizontal { -fx-pref-height: 8px; -fx-max-height: 8px; }\n" +
            ".scroll-bar:vertical { -fx-pref-width: 13px; -fx-max-width: 13px; -fx-padding: 0 0 0 5px; }\n" +
            ".scroll-bar .track { -fx-background-color: transparent; -fx-border-color: transparent; -fx-background-insets: 0; }\n" +
            ".scroll-bar .thumb { -fx-background-color: " + rgbaScrollThumb + "; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-background-insets: 1px; }\n" +
            ".scroll-bar .thumb:hover { -fx-background-color: " + rgbaScrollThumbHover + "; }\n" +
            ".scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-background-color: transparent; -fx-background-radius: 0; -fx-border-color: transparent; -fx-padding: 0px; }\n" +
            ".scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-shape: \"\"; -fx-padding: 0px; -fx-max-width: 0px; -fx-max-height: 0px; }\n" +
            ".dialog-pane { -fx-background-color: " + rgbaDialogBg + "; -fx-border-color: " + rgbaDialogBorder + "; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 20, 0, 0, 5); -fx-background-insets: 12; -fx-border-insets: 12; -fx-padding: 12px; }\n" +
            ".dialog-pane > .header-panel { -fx-background-color: " + rgbaDialogHeaderBg + "; -fx-padding: 0; -fx-background-radius: 8px 8px 0 0; }\n" +
            ".dialog-pane .dialog-header-title { -fx-text-fill: " + hexDialogHeaderFg + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10px 0 10px 0; }\n" +
            ".dialog-pane .label { -fx-text-fill: " + hexLabelFg + "; }\n" +
            ".dialog-pane .button-bar { -fx-padding: 10px 15px; }\n" +
            ".dialog-pane .text-field, .dialog-pane .text-area, .dialog-pane .password-field { -fx-background-color: " + hexInputBorder + "; -fx-text-fill: " + hexInputFg + "; -fx-border-color: " + hexInputBorder + "; }\n" +
            ".dialog-pane .text-area .content, .dialog-pane .text-area:focused .content { -fx-background-color: transparent; -fx-text-fill: " + hexInputFg + "; }\n" +
            ".dialog-pane .text-field:disabled, .dialog-pane .text-area:disabled, .dialog-pane .password-field:disabled { -fx-opacity: 0.5; }\n" +
            ".dialog-pane .radio-button { -fx-text-fill: " + hexLabelFg + "; }\n" +
            ".dialog-pane .radio-button .radio { -fx-mark-color: " + hexTermCursor + "; -fx-border-color: transparent; }\n" +
            ".dialog-pane .check-box { -fx-text-fill: " + hexLabelFg + "; }\n" +
            ".dialog-pane .check-box .box { -fx-background-color: " + hexInputBorder + "; -fx-border-color: " + hexInputBorder + "; -fx-background-radius: 3px; -fx-border-radius: 3px; }\n" +
            ".dialog-pane .check-box:focused .box { -fx-border-color: " + hexTermCursor + "; }\n" +
            ".dialog-pane .check-box:selected .mark { -fx-background-color: " + hexTermCursor + "; }\n" +
            ".connecting-pane { -fx-background-color: " + hexConnBg + "; }\n" +
            ".connecting-label { -fx-text-fill: " + hexConnFg + "; -fx-font-size: 13px; }\n" +
            ".connecting-spinner { -fx-progress-color: " + hexConnIcon + "; }\n" +
            ".split-pane { -fx-background-color: " + hexSplitPaneBg + "; }\n" +
            ".terminal-split-pane { -fx-background-color: " + hexSplitPaneBg + "; }\n" +
            ".split-pane *.split-pane-divider { -fx-background-color: " + hexSplitDividerBg + "; }\n" +
            ".split-pane *.split-pane-divider:hover { -fx-background-color: " + hexSplitDividerHover + "; }\n" +
            ".highlight-card { -fx-background-color: " + rgbaCardsItemBg + "; -fx-border-color: " + rgbaCardsItemBorder + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; }\n" +
            ".highlight-card:hover { -fx-background-color: " + rgbaCardsHoverBg + "; -fx-border-color: " + rgbaCardsHoverBorder + "; }\n" +
            ".highlight-card.selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + rgbaCardsSelectedBorder + "; -fx-effect: dropshadow(three-pass-box, " + rgbaCardsSelectedGlow + ", 8, 0, 0, 0); }\n" +
            ".highlight-badge { -fx-background-color: " + rgbaCardsBadgeBg + "; -fx-background-radius: 16px; }\n" +
            ".highlight-sidebar-card { -fx-background-color: " + rgbaHlSideBg + "; -fx-border-color: " + hexSideBorder + "; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 10px; -fx-cursor: hand; }\n" +
            ".highlight-sidebar-card:hover { -fx-background-color: " + rgbaHlSideHoverBg + "; -fx-border-color: " + rgbaHlSideHoverBorder + "; }\n" +
            ".highlight-sidebar-card.active { -fx-background-color: " + rgbaHlSideActiveBg + "; -fx-border-color: " + rgbaHlSideActiveBorder + "; }\n" +
            ".highlight-sidebar-card .label { -fx-text-fill: " + hexHlSideFg + "; }\n" +
            ".highlight-sidebar-card.active .label { -fx-text-fill: " + hexHlSideActiveFg + "; }\n" +
            ".highlight-sidebar-card .toggle-icon { -fx-font-family: 'codicon'; -fx-font-size: 14px; -fx-text-fill: " + hexHlSideEyeInactive + "; }\n" +
            ".highlight-sidebar-card.active .toggle-icon { -fx-text-fill: " + hexHlSideEyeActive + "; }\n" +
            ".color-picker { -fx-background-color: " + hexColorPickerBg + "; -fx-text-fill: " + hexColorPickerFg + "; -fx-border-color: " + hexColorPickerBorder + "; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-border-width: 1px; -fx-background-insets: 0; }\n" +
            ".color-picker:hover { -fx-background-color: " + hexBtnHoverBg + "; }\n" +
            ".color-picker:focused { -fx-border-color: " + hexColorPickerBorder + "; }\n" +
            ".color-picker .color-picker-label { -fx-text-fill: " + hexColorPickerFg + "; }\n" +
            ".color-picker .color-picker-label .text { -fx-fill: " + hexColorPickerFg + "; }\n" +
            ".color-picker .arrow-button { -fx-background-color: transparent; -fx-background-insets: 0; -fx-background-radius: 0; }\n" +
            ".color-picker .arrow-button .arrow { -fx-background-color: " + hexColorPickerFg + "; -fx-background-insets: 0; }\n" +
            ".color-picker-popup { -fx-background-color: " + hexColorPickerPopupBg + "; -fx-border-color: " + hexColorPickerPopupBorder + "; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 8, 0, 0, 4); -fx-padding: 10px; }\n" +
            ".color-picker-popup .color-palette-region { -fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0; -fx-background-insets: 0; -fx-padding: 0; }\n" +
            ".color-picker-popup .label { -fx-text-fill: " + hexColorPickerFg + "; }\n" +
            ".color-picker-popup .custom-color-link { -fx-text-fill: " + hexColorPickerFg + "; }\n" +
            ".color-picker-popup .color-square { -fx-border-color: " + hexColorPickerPopupBorder + "; }\n" +
            ".custom-color-dialog { -fx-background-color: " + hexColorPickerPopupBg + "; }\n" +
            ".custom-color-dialog .label { -fx-text-fill: " + hexColorPickerFg + "; }\n" +
            ".custom-color-dialog .button { -fx-background-color: " + hexColorPickerCustomBtnBg + "; -fx-border-color: " + hexColorPickerCustomBtnBorder + "; -fx-text-fill: " + hexColorPickerCustomBtnFg + "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; }\n" +
            ".custom-color-dialog .button:hover { -fx-background-color: " + hexColorPickerCustomBtnHoverBg + "; -fx-border-color: " + hexColorPickerCustomBtnHoverBorder + "; -fx-text-fill: " + hexColorPickerCustomBtnHoverFg + "; }\n" +
            ".custom-color-dialog .toggle-button { -fx-background-color: " + hexColorPickerCustomToggleBg + "; -fx-border-color: " + hexColorPickerCustomToggleBorder + "; -fx-text-fill: " + hexColorPickerCustomToggleFg + "; -fx-border-width: 1px; -fx-background-insets: 0; }\n" +
            ".custom-color-dialog .toggle-button:selected { -fx-background-color: " + hexColorPickerCustomToggleSelectedBg + "; -fx-border-color: " + hexColorPickerCustomToggleSelectedBorder + "; -fx-text-fill: " + hexColorPickerCustomToggleSelectedFg + "; }\n" +
            ".custom-color-dialog .controls-pane { -fx-background-color: " + hexColorPickerCustomControlsBg + "; -fx-padding: 10px; -fx-border-color: " + hexColorPickerCustomToggleBorder + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; }\n" +
            ".custom-color-dialog .customcolor-controls-background { -fx-background-color: transparent; }\n" +
            ".sidebar-tab-button { -fx-background-color: " + rgbaBtnBg + "; -fx-border-color: " + rgbaBtnBorder + "; -fx-text-fill: " + hexBtnFg + "; -fx-background-insets: 0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-padding: 6px 12px; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: 11px; }\n" +
            ".sidebar-tab-button:hover { -fx-background-color: " + rgbaBtnHoverBg + "; -fx-border-color: " + rgbaBtnHoverBorder + "; -fx-text-fill: " + hexBtnHoverFg + "; }\n" +
            ".sidebar-tab-button:selected { -fx-background-color: " + rgbaCardsSelectedBg + "; -fx-border-color: " + hexSideNavSelectedBorder + "; -fx-text-fill: " + hexSideNavSelectedBorder + "; }\n" +
            ".global-text {-fx-text-fill: "+ hexColorPickerFg +";}";

        try {
            File cssFile = File.createTempFile("theme", ".css");
            cssFile.deleteOnExit();
            Files.write(cssFile.toPath(), dynamicCss.getBytes(StandardCharsets.UTF_8));

            if (scene != null) {
                String styleCssUri = ThemeApplicator.class.getResource("/style.css") != null 
                    ? ThemeApplicator.class.getResource("/style.css").toExternalForm() 
                    : "";
                String dynamicCssUri = cssFile.toURI().toString();

                scene.getStylesheets().clear();
                if (!styleCssUri.isEmpty()) {
                    scene.getStylesheets().add(styleCssUri);
                }
                scene.getStylesheets().add(dynamicCssUri);

                // Update all active embedded in-scene modals so that they get themed instantly without closing
                ModalOverlayManager.applyThemeToActiveModals(styleCssUri, dynamicCssUri);

                // Update all active dialogs/stages so that they get themed instantly without closing
                for (Window window : Window.getWindows()) {
                    if (window != scene.getWindow() && window.getScene() != null) {
                        Parent sceneRoot = window.getScene().getRoot();
                        if (sceneRoot instanceof DialogPane dp) {
                            dp.getStylesheets().clear();
                            if (!styleCssUri.isEmpty()) dp.getStylesheets().add(styleCssUri);
                            dp.getStylesheets().add(dynamicCssUri);
                        } else {
                            window.getScene().getStylesheets().clear();
                            if (!styleCssUri.isEmpty()) window.getScene().getStylesheets().add(styleCssUri);
                            window.getScene().getStylesheets().add(dynamicCssUri);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Apply native title bar decoration
        if (scene != null && scene.getWindow() instanceof Stage stage) {
            DwmUtil.applyNativeDwm(stage, theme);
        }

        // Repaint all active terminal panels using the theme colors
        if (splitPanes != null) {
            repaintAllTerminals(splitPanes, theme);
        }
    }

    /**
     * Repaints all active terminal instances across all split panes.
     */
    public static void repaintAllTerminals(Collection<TerminalSplitPane> splitPanes, TerminalTheme currentTheme) {
        if (currentTheme == null || splitPanes == null) return;

        com.jediterm.core.Color termBg = currentTheme.getBackgroundColor();
        com.jediterm.core.Color termFg = currentTheme.getTextColor();

        for (TerminalSplitPane splitPane : splitPanes) {
            Platform.runLater(() -> splitPane.updateActiveTheme(currentTheme));
            for (JediTermWidget widget : splitPane.getAllWidgets()) {
                SwingUtilities.invokeLater(() -> {
                    // Add 1ms delay to fix scrollbar race condition when adding new tabs
                    Timer timer = new Timer(1, e -> {
                        try {
                            java.awt.Color awtBg = new java.awt.Color(termBg.getRed(), termBg.getGreen(), termBg.getBlue());
                            java.awt.Color awtFg = new java.awt.Color(termFg.getRed(), termFg.getGreen(), termFg.getBlue());

                            widget.setBackground(awtBg);
                            widget.setForeground(awtFg);
                            if (widget.getTerminalPanel() != null) {
                                widget.getTerminalPanel().setBackground(awtBg);
                                widget.getTerminalPanel().setForeground(awtFg);
                            }

                            if (widget.getTerminal() != null && widget.getTerminal().getStyleState() != null) {
                                widget.getTerminal().getStyleState().setDefaultStyle(com.jediterm.terminal.TextStyle.EMPTY);
                                widget.getTerminal().getStyleState().reset();
                            }

                            if (widget.getTerminalPanel() != null) {
                                widget.getTerminalPanel().repaint();
                            }
                            widget.invalidate();
                            widget.repaint();
                            widget.revalidate();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                    timer.setRepeats(false);
                    timer.start();
                });
            }
        }
    }
}
