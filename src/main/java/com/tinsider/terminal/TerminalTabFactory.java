package com.tinsider.terminal;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.tinsider.config.TerminalTheme;
import com.tinsider.model.HostProfile;
import com.tinsider.ui.components.terminal.split.*;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.swing.*;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TerminalTabFactory {

    private static float globalFontSize = 13.0f;

    public static float getGlobalFontSize() {
        return globalFontSize;
    }

    public static SwingNode createSwingNode(JediTermWidget widget) {
        SwingNode swingNode = new SwingNode();
        swingNode.setFocusTraversable(true);

        // Intercept and Consume ALL JavaFX key events for Ctrl+C to send 0x03 and prevent '^@' leak!
        swingNode.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.C) {
                try {
                    TtyConnector connector = widget.getTtyConnector();
                    if (connector != null && connector.isConnected()) {
                        connector.write(new byte[]{3}); // Send Ctrl+C (ETX) byte
                        event.consume();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        swingNode.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, event -> {
            if (event.isControlDown() && (event.getCharacter().equals("\u0003") || event.getCharacter().equalsIgnoreCase("c"))) {
                event.consume();
            }
        });

        swingNode.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.C) {
                event.consume();
            }
        });

        swingNode.setContent(widget);
        return swingNode;
    }

    public static JediTermWidget createJediTermWidget(
            TtyConnector connector, 
            Supplier<TerminalTheme> themeSupplier) {
        
        JediTermWidget widget = new JediTermWidget(new DefaultSettingsProvider() {
            @Override
            public com.jediterm.terminal.ui.TerminalActionPresentation getCopyActionPresentation() {
                return new com.jediterm.terminal.ui.TerminalActionPresentation(
                    "Copy", 
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK)
                );
            }

            @Override
            public com.jediterm.terminal.ui.TerminalActionPresentation getPasteActionPresentation() {
                return new com.jediterm.terminal.ui.TerminalActionPresentation(
                    "Paste", 
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK)
                );
            }

            @Override
            public boolean altSendsEscape() {
                return true;
            }

            @Override
            public boolean scrollToBottomOnTyping() {
                return true;
            }

            @Override
            public int getBufferMaxLinesCount() {
                return 5000;
            }

            @Override
            public boolean audibleBell() {
                return false;
            }

            @Override
            public java.awt.Font getTerminalFont() {
                String os = System.getProperty("os.name").toLowerCase();
                String fontName = os.contains("win") ? "Consolas" : "Monospaced";
                return new java.awt.Font(fontName, java.awt.Font.PLAIN, (int) globalFontSize);
            }

            @Override
            public float getTerminalFontSize() {
                return globalFontSize;
            }

            @Override
            public com.jediterm.terminal.emulator.ColorPalette getTerminalColorPalette() {
                return new com.jediterm.terminal.emulator.ColorPalette() {
                    @Override
                    public com.jediterm.core.Color getForeground(com.jediterm.terminal.TerminalColor color) {
                        if (color.isIndexed()) {
                            return getForegroundByColorIndex(color.getColorIndex());
                        }
                        return themeSupplier.get().getTextColor();
                    }

                    @Override
                    public com.jediterm.core.Color getBackground(com.jediterm.terminal.TerminalColor color) {
                        if (color.isIndexed()) {
                            return getBackgroundByColorIndex(color.getColorIndex());
                        }
                        return themeSupplier.get().getBackgroundColor();
                    }

                    @Override
                    protected com.jediterm.core.Color getForegroundByColorIndex(int index) {
                        return themeSupplier.get().getAnsiColor(index);
                    }

                    @Override
                    protected com.jediterm.core.Color getBackgroundByColorIndex(int index) {
                        return themeSupplier.get().getAnsiColor(index);
                    }
                };
            }

            @Override
            public com.jediterm.terminal.TextStyle getSelectionColor() {
                return new com.jediterm.terminal.TextStyle(
                    com.jediterm.terminal.TerminalColor.fromColor(themeSupplier.get().getTextColor()),
                    com.jediterm.terminal.TerminalColor.fromColor(themeSupplier.get().getSelectionColor())
                );
            }

            @Override
            public com.jediterm.terminal.TerminalColor getDefaultForeground() {
                return com.jediterm.terminal.TerminalColor.fromColor(themeSupplier.get().getTextColor());
            }

            @Override
            public com.jediterm.terminal.TerminalColor getDefaultBackground() {
                return com.jediterm.terminal.TerminalColor.fromColor(themeSupplier.get().getBackgroundColor());
            }

            @Override
            public com.jediterm.terminal.TextStyle getDefaultStyle() {
                return com.jediterm.terminal.TextStyle.EMPTY;
            }
        }) {
            @Override
            protected JScrollBar createScrollBar() {
                JScrollBar scrollBar = new JScrollBar() {
                    private boolean initialized = false;
                    
                    {
                        initialized = true;
                    }

                    @Override
                    public java.awt.Dimension getPreferredSize() {
                        java.awt.Dimension dim = super.getPreferredSize();
                        int scrollWidth = 10;
                        if (getOrientation() == JScrollBar.VERTICAL) {
                            dim.width = scrollWidth;
                        } else {
                            dim.height = scrollWidth;
                        }
                        return dim;
                    }

                    @Override
                    public void updateUI() {
                        if (!initialized) {
                            super.updateUI();
                            return;
                        }
                        setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
                            @Override
                            protected void configureScrollBarColors() {}
                            
                            @Override
                            public void paint(java.awt.Graphics g, JComponent c) {
                                com.jediterm.core.Color bg = themeSupplier.get().getBackgroundColor();
                                g.setColor(new java.awt.Color(bg.getRed(), bg.getGreen(), bg.getBlue()));
                                g.fillRect(0, 0, c.getWidth(), c.getHeight());
                                super.paint(g, c);
                            }
                            
                            @Override
                            public void update(java.awt.Graphics g, JComponent c) {
                                paint(g, c);
                            }
                            
                            @Override
                            protected void paintTrack(java.awt.Graphics g, JComponent c, java.awt.Rectangle trackBounds) {}

                            @Override
                            protected void paintThumb(java.awt.Graphics g, JComponent c, java.awt.Rectangle thumbBounds) {
                                if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
                                com.jediterm.core.Color thumbColor = themeSupplier.get().getColor("scrollbar.thumb", themeSupplier.get().getCursorColor());
                                
                                float opacity = 0.3f;
                                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                                g2.setColor(new java.awt.Color(thumbColor.getRed() / 255f, thumbColor.getGreen() / 255f, thumbColor.getBlue() / 255f, opacity));
                                g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 8, 8);
                                g2.dispose();
                            }

                            private JButton createZeroSizeButton() {
                                JButton button = new JButton();
                                button.setPreferredSize(new java.awt.Dimension(0, 0));
                                button.setMinimumSize(new java.awt.Dimension(0, 0));
                                button.setMaximumSize(new java.awt.Dimension(0, 0));
                                return button;
                            }

                            @Override
                            protected JButton createDecreaseButton(int orientation) {
                                return createZeroSizeButton();
                            }

                            @Override
                            protected JButton createIncreaseButton(int orientation) {
                                return createZeroSizeButton();
                            }
                        });
                    }
                };
                scrollBar.setOpaque(true);
                scrollBar.setBorder(BorderFactory.createEmptyBorder());
                
                com.jediterm.core.Color initialBg = themeSupplier.get().getBackgroundColor();
                scrollBar.setBackground(new java.awt.Color(initialBg.getRed(), initialBg.getGreen(), initialBg.getBlue()));
                
                scrollBar.updateUI();
                return scrollBar;
            }
        };

        com.jediterm.core.Color bgColor = themeSupplier.get().getBackgroundColor();
        java.awt.Color awtBg = new java.awt.Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
        com.jediterm.core.Color fgColor = themeSupplier.get().getTextColor();
        java.awt.Color awtFg = new java.awt.Color(fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue());

        setAllBackgrounds(widget, awtBg);
        widget.setForeground(awtFg);
        if (widget.getTerminalPanel() != null) {
            widget.getTerminalPanel().setForeground(awtFg);
        }

        widget.setTtyConnector(connector);
        widget.start();

        // Repaint the terminal widget to fix scrollbar race condition
        SwingUtilities.invokeLater(() -> {
            javax.swing.Timer timer = new javax.swing.Timer(1, e -> {
                try {
                    com.jediterm.core.Color bg = themeSupplier.get().getBackgroundColor();
                    java.awt.Color delayedAwtBg = new java.awt.Color(bg.getRed(), bg.getGreen(), bg.getBlue());
                    com.jediterm.core.Color fg = themeSupplier.get().getTextColor();
                    java.awt.Color delayedAwtFg = new java.awt.Color(fg.getRed(), fg.getGreen(), fg.getBlue());

                    setAllBackgrounds(widget, delayedAwtBg);
                    widget.setForeground(delayedAwtFg);
                    if (widget.getTerminalPanel() != null) {
                        widget.getTerminalPanel().setBackground(delayedAwtBg);
                        widget.getTerminalPanel().setForeground(delayedAwtFg);
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

        // Set both widget and its internal TerminalPanel to have resizable small minimum/preferred sizes
        // so JavaFX SwingNode is allowed to shrink it and perfectly fit the layout without centering offsets!
        widget.setMinimumSize(new java.awt.Dimension(10, 10));
        widget.setPreferredSize(new java.awt.Dimension(10, 10));
        if (widget.getTerminalPanel() != null) {
            widget.getTerminalPanel().setMinimumSize(new java.awt.Dimension(10, 10));
            widget.getTerminalPanel().setPreferredSize(new java.awt.Dimension(10, 10));
        }

        return widget;
    }

    public static void createTerminalTab(
            String title, 
            TtyConnector connector, 
            TabPane tabPane, 
            Map<Tab, TerminalSplitPane> tabSplitPaneMap, 
            Supplier<TerminalTheme> themeSupplier,
            Consumer<Tab> closeTabHandler,
            String sessionType,
            String localShellType,
            HostProfile hostProfile) {
        
        Tab tab = new Tab(title);
        TerminalSplitPane splitPane = new TerminalSplitPane(themeSupplier, () -> closeTabHandler.accept(tab));
        
        TerminalPane terminalPane = new TerminalPane(splitPane, sessionType, localShellType, hostProfile, title);
        SplitLeafNode leaf = new SplitLeafNode(terminalPane);
        splitPane.setRootNode(leaf);

        // Instantiate and Configure JediTermWidget inside Swing EDT
        SwingUtilities.invokeLater(() -> {
            JediTermWidget widget = createJediTermWidget(connector, themeSupplier);
            Platform.runLater(() -> {
                terminalPane.setWidget(widget);
                tabSplitPaneMap.put(tab, splitPane);
                splitPane.setActiveTerminalPane(terminalPane);
            });
        });

        tab.setContent(splitPane);

        tab.setOnCloseRequest(event -> {
            event.consume();
            closeTabHandler.accept(tab);
        });

        int addIndex = tabPane.getTabs().size();
        if (addIndex > 0 && tabPane.getTabs().get(addIndex - 1).getText().equals("+")) {
            tabPane.getTabs().add(addIndex - 1, tab);
        } else {
            tabPane.getTabs().add(tab);
        }
        tabPane.getSelectionModel().select(tab);
    }

    public static void setAllBackgrounds(java.awt.Container container, java.awt.Color color) {
        container.setBackground(color);
        if (container instanceof JScrollPane) {
            ((JScrollPane) container).getViewport().setBackground(color);
            if (((JScrollPane) container).getCorner(JScrollPane.LOWER_RIGHT_CORNER) != null) {
                 ((JScrollPane) container).getCorner(JScrollPane.LOWER_RIGHT_CORNER).setBackground(color);
            }
        }
        for (java.awt.Component c : container.getComponents()) {
            c.setBackground(color);
            if (c instanceof java.awt.Container) {
                setAllBackgrounds((java.awt.Container) c, color);
            }
        }
    }

    public static void setGlobalFontSize(float newSize, Collection<TerminalSplitPane> splitPanes) {
        if (newSize < 6.0f) newSize = 6.0f;
        if (newSize > 72.0f) newSize = 72.0f;
        if (globalFontSize == newSize) return;
        globalFontSize = newSize;

        for (TerminalSplitPane splitPane : splitPanes) {
            for (JediTermWidget widget : splitPane.getAllWidgets()) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        com.jediterm.terminal.ui.TerminalPanel panel = widget.getTerminalPanel();
                        if (panel != null) {
                            java.lang.reflect.Method m = com.jediterm.terminal.ui.TerminalPanel.class.getDeclaredMethod("reinitFontAndResize");
                            m.setAccessible(true);
                            m.invoke(panel);
                            panel.revalidate();
                            panel.repaint();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }
        }
    }

    public static void increaseZoom(Collection<TerminalSplitPane> splitPanes) {
        setGlobalFontSize(globalFontSize + 1.0f, splitPanes);
    }

    public static void decreaseZoom(Collection<TerminalSplitPane> splitPanes) {
        setGlobalFontSize(globalFontSize - 1.0f, splitPanes);
    }

    public static void resetZoom(Collection<TerminalSplitPane> splitPanes) {
        setGlobalFontSize(13.0f, splitPanes);
    }
}

