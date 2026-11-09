package com.tinsider;

import com.tinsider.util.WindowDimensionHelper;
import javafx.geometry.Dimension2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WindowDimensionHelperTest {

    private static final double EPSILON = 0.05;

    private void assertAspectRatio16by9(Dimension2D dim) {
        double ratio = dim.getWidth() / dim.getHeight();
        double expectedRatio = 16.0 / 9.0;
        assertEquals(expectedRatio, ratio, EPSILON,
                String.format("Expected 16:9 ratio, but was %.4f for dimensions %.0fx%.0f",
                        ratio, dim.getWidth(), dim.getHeight()));
    }

    @Test
    public void test1080pDisplayReturns720p() {
        Rectangle2D screenBounds = new Rectangle2D(0, 0, 1920, 1080);
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 1920, 1040); // 40px taskbar

        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(screenBounds, visualBounds);
        assertEquals(1280.0, dim.getWidth());
        assertEquals(720.0, dim.getHeight());
        assertAspectRatio16by9(dim);
        assertTrue(dim.getWidth() <= visualBounds.getWidth());
        assertTrue(dim.getHeight() <= visualBounds.getHeight());
    }

    @Test
    public void test1440pDisplayReturns720p() {
        Rectangle2D screenBounds = new Rectangle2D(0, 0, 2560, 1440);
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 2560, 1400);

        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(screenBounds, visualBounds);
        assertEquals(1280.0, dim.getWidth());
        assertEquals(720.0, dim.getHeight());
        assertAspectRatio16by9(dim);
    }

    @Test
    public void test900pDisplayReturns648p() {
        Rectangle2D screenBounds = new Rectangle2D(0, 0, 1600, 900);
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 1600, 860);

        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(screenBounds, visualBounds);
        assertEquals(1152.0, dim.getWidth());
        assertEquals(648.0, dim.getHeight());
        assertAspectRatio16by9(dim);
        assertTrue(dim.getHeight() < visualBounds.getHeight());
    }

    @Test
    public void test1366x768LaptopDisplayReturns576p() {
        // Standard 1366x768 laptop
        Rectangle2D screenBounds = new Rectangle2D(0, 0, 1366, 768);
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 1366, 728); // Windows taskbar at bottom

        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(screenBounds, visualBounds);
        assertEquals(1024.0, dim.getWidth());
        assertEquals(576.0, dim.getHeight());
        assertAspectRatio16by9(dim);

        // Height (576) must be substantially less than usable screen height (728) to prevent taskbar clipping
        assertTrue(dim.getHeight() < visualBounds.getHeight() - 100,
                "Window height should leave comfortable margin for OS taskbar and title bar");
    }

    @Test
    public void test1080pLaptopAt150PercentScalingReturns576p() {
        // 1080p screen with 150% Windows scaling results in 1280x720 logical DIPs
        Rectangle2D screenBounds = new Rectangle2D(0, 0, 1280, 720);
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 1280, 680); // 40px taskbar

        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(screenBounds, visualBounds);
        assertEquals(1024.0, dim.getWidth());
        assertEquals(576.0, dim.getHeight());
        assertAspectRatio16by9(dim);

        // Must not overflow 680px visual height
        assertTrue(dim.getHeight() < visualBounds.getHeight(),
                "Window height must not exceed usable visual bounds on scaled 1080p laptops");
    }

    @Test
    public void test1080pLaptopAt125PercentScalingReturns648p() {
        // 1080p screen with 125% Windows scaling results in 1536x864 logical DIPs
        Rectangle2D screenBounds = new Rectangle2D(0, 0, 1536, 864);
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 1536, 824);

        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(screenBounds, visualBounds);
        assertEquals(1152.0, dim.getWidth());
        assertEquals(648.0, dim.getHeight());
        assertAspectRatio16by9(dim);
        assertTrue(dim.getHeight() < visualBounds.getHeight());
    }

    @Test
    public void test1280x800DisplayReturns576p() {
        // 16:10 laptop screen
        Rectangle2D screenBounds = new Rectangle2D(0, 0, 1280, 800);
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 1280, 760);

        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(screenBounds, visualBounds);
        assertEquals(1024.0, dim.getWidth());
        assertEquals(576.0, dim.getHeight());
        assertAspectRatio16by9(dim);
    }

    @Test
    public void testSmallDisplaySafetyClamping() {
        // Small netbook screen
        Rectangle2D screenBounds = new Rectangle2D(0, 0, 1024, 600);
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 1024, 560);

        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(screenBounds, visualBounds);
        assertTrue(dim.getWidth() <= visualBounds.getWidth());
        assertTrue(dim.getHeight() <= visualBounds.getHeight());
        assertAspectRatio16by9(dim);
    }

    @Test
    public void testNullBoundsFallback() {
        Dimension2D dim = WindowDimensionHelper.calculateSceneDimensions(null, null);
        assertEquals(WindowDimensionHelper.DEFAULT_WIDTH, dim.getWidth());
        assertEquals(WindowDimensionHelper.DEFAULT_HEIGHT, dim.getHeight());
    }
}
