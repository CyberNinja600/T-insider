package com.tinsider;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.tinsider.config.TerminalTheme;
import com.tinsider.model.WordHighlight;
import com.tinsider.storage.StorageManager;
import com.tinsider.terminal.HighlightedTextStyle;
import com.tinsider.ui.components.terminal.split.TerminalPane;
import com.tinsider.ui.components.terminal.split.TerminalSplitPane;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalHighlightingTest {

    private List<WordHighlight> savedOriginalHighlights;

    @BeforeAll
    public static void initJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX failed to initialize");
    }

    @BeforeEach
    public void setUp() {
        savedOriginalHighlights = StorageManager.loadHighlights();
    }

    @AfterEach
    public void tearDown() {
        if (savedOriginalHighlights != null) {
            StorageManager.saveHighlights(savedOriginalHighlights);
        }
    }

    @Test
    public void testInactiveHighlightDoesNotStripCliGrepColor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                // Ensure a highlight for "ERROR" exists in storage but is NOT activated
                WordHighlight errorHighlight = new WordHighlight("ERROR", "#E64D4D");
                errorHighlight.setId("test-hl-error");
                List<WordHighlight> list = new ArrayList<>();
                list.add(errorHighlight);
                StorageManager.saveHighlights(list);

                TerminalTheme defaultTheme = new TerminalTheme("Default");
                TerminalSplitPane splitPane = new TerminalSplitPane(() -> defaultTheme, () -> {});
                TerminalPane pane = new TerminalPane(splitPane, "LOCAL", "CMD", null, "CMD");

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        JediTermWidget widget = new JediTermWidget(new DefaultSettingsProvider());
                        pane.setWidget(widget);

                        TerminalTextBuffer textBuffer = widget.getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        // Simulate CLI grep command printing "ERROR" with ANSI red color
                        TextStyle ansiRedStyle = new TextStyle(TerminalColor.rgb(255, 0, 0), null);
                        line.writeString(0, new CharBuffer("ERROR: something failed"), ansiRedStyle);

                        // Highlight is NOT in activeHighlightIds
                        assertFalse(pane.getActiveHighlightIds().contains(errorHighlight.getId()));

                        // Trigger applyHighlights
                        pane.applyHighlights();
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // Wait for any queued SwingUtilities.invokeLater tasks from applyHighlights
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        // Verify that the ANSI red style was NOT stripped or replaced by TextStyle.EMPTY
                        TextStyle styleAt0 = line.getStyleAt(0);
                        assertNotNull(styleAt0, "Style at 0 should not be null");
                        assertEquals(TerminalColor.rgb(255, 0, 0), styleAt0.getForeground(),
                                "Native ANSI color from grep should remain completely intact when highlight is inactive");
                        assertFalse(styleAt0 instanceof HighlightedTextStyle,
                                "Should not be wrapped in HighlightedTextStyle when highlight is inactive");

                        pane.close();
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                latch.countDown();
            } catch (Exception ex) {
                failure.set(ex);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get());
        }
    }

    @Test
    public void testTwoHighlightsWithSameWordWorkWithoutClobbering() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                // Two highlights with the EXACT SAME word "TEST", different colors
                WordHighlight h1 = new WordHighlight("TEST", "#00FF00"); // Green
                h1.setId("test-hl-green");
                WordHighlight h2 = new WordHighlight("TEST", "#0000FF"); // Blue
                h2.setId("test-hl-blue");

                List<WordHighlight> list = new ArrayList<>();
                list.add(h1);
                list.add(h2);
                StorageManager.saveHighlights(list);

                TerminalTheme defaultTheme = new TerminalTheme("Default");
                TerminalSplitPane splitPane = new TerminalSplitPane(() -> defaultTheme, () -> {});
                TerminalPane pane = new TerminalPane(splitPane, "LOCAL", "CMD", null, "CMD");

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        JediTermWidget widget = new JediTermWidget(new DefaultSettingsProvider());
                        pane.setWidget(widget);

                        TerminalTextBuffer textBuffer = widget.getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        // Initial text has normal style
                        TextStyle initialStyle = TextStyle.EMPTY;
                        line.writeString(0, new CharBuffer("Running TEST now"), initialStyle);
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // 1. Activate ONLY H1 (Green). H2 (Blue) remains inactive.
                pane.toggleHighlight(h1.getId(), true);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        // Find "TEST" at offset 8
                        TextStyle styleAt8 = line.getStyleAt(8);
                        assertTrue(styleAt8 instanceof HighlightedTextStyle, "Style should be HighlightedTextStyle");
                        HighlightedTextStyle hts = (HighlightedTextStyle) styleAt8;
                        assertEquals(h1.getId(), hts.getHighlightId(),
                                "H1 must be active and applied, H2 should not clobber it");
                        assertEquals(TerminalColor.rgb(0, 255, 0), hts.getBackground(),
                                "Background must match H1's green color");
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // 2. Deactivate H1 (Green) and activate H2 (Blue)
                pane.toggleHighlight(h1.getId(), false);
                pane.toggleHighlight(h2.getId(), true);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        TextStyle styleAt8 = line.getStyleAt(8);
                        assertTrue(styleAt8 instanceof HighlightedTextStyle, "Style should be HighlightedTextStyle");
                        HighlightedTextStyle hts = (HighlightedTextStyle) styleAt8;
                        assertEquals(h2.getId(), hts.getHighlightId(),
                                "H2 must now be active and applied");
                        assertEquals(TerminalColor.rgb(0, 0, 255), hts.getBackground(),
                                "Background must match H2's blue color");
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // 3. Deactivate both -> text should revert to original style
                pane.toggleHighlight(h2.getId(), false);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        TextStyle styleAt8 = line.getStyleAt(8);
                        assertFalse(styleAt8 instanceof HighlightedTextStyle,
                                "Highlight should be completely removed when deactivated");

                        pane.close();
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                latch.countDown();
            } catch (Exception ex) {
                failure.set(ex);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get());
        }
    }

    @Test
    public void testHighlightOverAnsiTextRestoresAnsiColorWhenDeactivated() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                WordHighlight h1 = new WordHighlight("WARN", "#FFFF00");
                h1.setId("test-hl-warn");

                List<WordHighlight> list = new ArrayList<>();
                list.add(h1);
                StorageManager.saveHighlights(list);

                TerminalTheme defaultTheme = new TerminalTheme("Default");
                TerminalSplitPane splitPane = new TerminalSplitPane(() -> defaultTheme, () -> {});
                TerminalPane pane = new TerminalPane(splitPane, "LOCAL", "CMD", null, "CMD");

                TextStyle ansiCyanStyle = new TextStyle(TerminalColor.rgb(0, 255, 255), null);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        JediTermWidget widget = new JediTermWidget(new DefaultSettingsProvider());
                        pane.setWidget(widget);

                        TerminalTextBuffer textBuffer = widget.getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);
                        line.writeString(0, new CharBuffer("WARN: check configuration"), ansiCyanStyle);
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // Activate highlight
                pane.toggleHighlight(h1.getId(), true);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        TextStyle style = line.getStyleAt(0);
                        assertTrue(style instanceof HighlightedTextStyle);
                        HighlightedTextStyle hts = (HighlightedTextStyle) style;
                        assertEquals(ansiCyanStyle, hts.getOriginalStyle(),
                                "Original ANSI style must be remembered");
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // Deactivate highlight -> verify ANSI cyan is restored
                pane.toggleHighlight(h1.getId(), false);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        TextStyle style = line.getStyleAt(0);
                        assertFalse(style instanceof HighlightedTextStyle);
                        assertEquals(TerminalColor.rgb(0, 255, 255), style.getForeground(),
                                "Original ANSI cyan color must be restored when highlight is turned off");

                        pane.close();
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                latch.countDown();
            } catch (Exception ex) {
                failure.set(ex);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get());
        }
    }

    @Test
    public void testTogglingCaseInsensitiveOffRemovesHighlight() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                // Highlight defined with uppercase "ERROR", initially case-sensitive (caseInsensitive = false)
                WordHighlight h = new WordHighlight("ERROR", "#FF0000", false);
                h.setId("test-case-error");

                List<WordHighlight> list = new ArrayList<>();
                list.add(h);
                StorageManager.saveHighlights(list);

                TerminalTheme defaultTheme = new TerminalTheme("Default");
                TerminalSplitPane splitPane = new TerminalSplitPane(() -> defaultTheme, () -> {});
                TerminalPane pane = new TerminalPane(splitPane, "LOCAL", "CMD", null, "CMD");

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        JediTermWidget widget = new JediTermWidget(new DefaultSettingsProvider());
                        pane.setWidget(widget);

                        TerminalTextBuffer textBuffer = widget.getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        // Lowercase "error" in the terminal log
                        line.writeString(0, new CharBuffer("An error occurred"), TextStyle.EMPTY);
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // 1. Activate the highlight (case-sensitive)
                pane.toggleHighlight(h.getId(), true);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        // At offset 3 ("error"), should NOT be highlighted because capitalization does not match
                        TextStyle styleAt3 = line.getStyleAt(3);
                        assertFalse(styleAt3 instanceof HighlightedTextStyle,
                                "Should not match lowercase 'error' when case-sensitive");
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // 2. Turn ON case insensitivity
                pane.toggleCaseInsensitive(h.getId(), true);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        // At offset 3 ("error"), now it SHOULD be highlighted
                        TextStyle styleAt3 = line.getStyleAt(3);
                        assertTrue(styleAt3 instanceof HighlightedTextStyle,
                                "Should match lowercase 'error' after turning ON case-insensitivity");
                        HighlightedTextStyle hts = (HighlightedTextStyle) styleAt3;
                        assertEquals(h.getId(), hts.getHighlightId());
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                // 3. Turn OFF case insensitivity -> highlight MUST disappear!
                pane.toggleCaseInsensitive(h.getId(), false);

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        TerminalTextBuffer textBuffer = pane.getWidget().getTerminalTextBuffer();
                        TerminalLine line = textBuffer.getLine(0);

                        // At offset 3 ("error"), highlight MUST disappear and revert to original style
                        TextStyle styleAt3 = line.getStyleAt(3);
                        assertFalse(styleAt3 instanceof HighlightedTextStyle,
                                "Highlight must disappear when case insensitivity is turned off");

                        pane.close();
                    } catch (Exception ex) {
                        failure.set(ex);
                    }
                });

                latch.countDown();
            } catch (Exception ex) {
                failure.set(ex);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail(failure.get());
        }
    }
}
