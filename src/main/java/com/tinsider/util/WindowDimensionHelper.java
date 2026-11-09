package com.tinsider.util;

import javafx.geometry.Dimension2D;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

/**
 * Utility for automatically calculating responsive initial window and scene dimensions
 * based on monitor resolution and usable visual bounds (excluding OS taskbars/docks).
 *
 * Maintains a strict 16:9 aspect ratio and selects standard crisp resolution tiers:
 * - >= 1080p display: 1280 x 720 (Standard 720p)
 * - ~900p display: 1152 x 648 (648p)
 * - ~768p / 720p or high-DPI scaled laptops: 1024 x 576 (576p)
 * - Compact / small displays: 960 x 540 (540p) or 854 x 480 (480p)
 */
public final class WindowDimensionHelper {

    public static final double DEFAULT_WIDTH = 1280.0;
    public static final double DEFAULT_HEIGHT = 720.0;

    public static final double MIN_WINDOW_WIDTH = 854.0;
    public static final double MIN_WINDOW_HEIGHT = 480.0;

    public static final double ASPECT_RATIO_WIDTH = 16.0;
    public static final double ASPECT_RATIO_HEIGHT = 9.0;
    public static final double ASPECT_RATIO = ASPECT_RATIO_WIDTH / ASPECT_RATIO_HEIGHT;

    private WindowDimensionHelper() {
        // Utility class
    }

    /**
     * Determines initial scene dimensions using the primary screen.
     * Falls back to 1280x720 if screen information is unavailable.
     */
    public static Dimension2D calculateInitialSceneDimensions() {
        try {
            Screen primary = Screen.getPrimary();
            if (primary != null) {
                return calculateSceneDimensions(primary.getBounds(), primary.getVisualBounds());
            }
        } catch (Exception e) {
            ActivityLogger.logSystemEvent("WINDOW_INIT_WARN", "Failed to query Screen bounds: " + e.getMessage());
        }
        return new Dimension2D(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Calculates appropriate 16:9 scene dimensions for given screen bounds and usable visual bounds.
     * Pure function for reliable and deterministic testing across diverse monitor setups.
     *
     * @param screenBounds Total monitor bounds (resolution in logical pixels)
     * @param visualBounds Usable monitor bounds (excluding taskbars, menu bars, and docks)
     * @return Dimension2D representing (width, height) with 16:9 aspect ratio
     */
    public static Dimension2D calculateSceneDimensions(Rectangle2D screenBounds, Rectangle2D visualBounds) {
        if (screenBounds == null || visualBounds == null) {
            return new Dimension2D(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }

        double screenHeight = screenBounds.getHeight();
        double visualWidth = visualBounds.getWidth();
        double visualHeight = visualBounds.getHeight();

        double targetWidth;
        double targetHeight;

        // Tier 1: 1080p or higher displays with sufficient usable space (standard 720p window)
        if (screenHeight >= 1080.0 && visualHeight >= 760.0 && visualWidth >= 1340.0) {
            targetWidth = 1280.0;
            targetHeight = 720.0;
        }
        // Tier 2: ~900p displays (e.g. 1600x900)
        else if (screenHeight >= 850.0 && visualHeight >= 700.0 && visualWidth >= 1200.0) {
            targetWidth = 1152.0;
            targetHeight = 648.0;
        }
        // Tier 3: ~768p / 720p displays (standard 1366x768 laptops or 1080p with 125%/150% scaling)
        else if (visualHeight >= 620.0 && visualWidth >= 1050.0) {
            targetWidth = 1024.0;
            targetHeight = 576.0;
        }
        // Tier 4: Compact displays / high scaling
        else if (visualHeight >= 520.0 && visualWidth >= 960.0) {
            targetWidth = 960.0;
            targetHeight = 540.0;
        }
        // Tier 5: Ultra-compact fallback
        else {
            targetWidth = 854.0;
            targetHeight = 480.0;
        }

        // Safety clamp: ensure window does not exceed 94% of usable width or 88% of usable height
        // This guarantees room for OS title bars (~32px), borders, and system taskbars.
        double maxAllowedWidth = visualWidth * 0.94;
        double maxAllowedHeight = visualHeight * 0.88;

        if (targetWidth > maxAllowedWidth || targetHeight > maxAllowedHeight) {
            double scaleW = maxAllowedWidth / targetWidth;
            double scaleH = maxAllowedHeight / targetHeight;
            double scale = Math.min(scaleW, scaleH);

            targetHeight = Math.floor(targetHeight * scale);
            targetWidth = Math.round(targetHeight * ASPECT_RATIO);
        }

        return new Dimension2D(targetWidth, targetHeight);
    }
}
