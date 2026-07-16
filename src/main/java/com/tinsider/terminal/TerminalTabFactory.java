package com.tinsider.terminal;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.tinsider.config.TerminalTheme;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.swing.*;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TerminalTabFactory {

    public static void createTerminalTab(
            String title, 
            TtyConnector connector, 
            TabPane tabPane, 
            Map<Tab, JediTermWidget> tabWidgetMap, 
            Supplier<TerminalTheme> themeSupplier,
            Consumer<Tab> closeTabHandler) {
        
        Tab tab = new Tab(title);
        SwingNode swingNode = new SwingNode();

        // 1. Intercept and Consume ALL JavaFX key events for Ctrl+C to send 0x03 and prevent '^@' leak!
        swingNode.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.C) {
                try {
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

        // 2. Instantiate and Configure JediTermWidget inside Swing EDT for thread-safety and rendering stability
        SwingUtilities.invokeLater(() -> {
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
                    return new java.awt.Font("Consolas", java.awt.Font.PLAIN, 13);
                }

                @Override
                public float getTerminalFontSize() {
                    return 13.0f;
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
                        public void updateUI() {
                            if (!initialized) {
                                super.updateUI();
                                return;
                            }
                            setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
                                @Override
                                protected void configureScrollBarColors() {
                                    // Colors dynamically resolved during paint
                                }
                                
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
                                protected void paintTrack(java.awt.Graphics g, JComponent c, java.awt.Rectangle trackBounds) {
                                    // Background is fully painted in paint(), preventing artifacts when disabled
                                }

                                @Override
                                protected void paintThumb(java.awt.Graphics g, JComponent c, java.awt.Rectangle thumbBounds) {
                                    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
                                    com.jediterm.core.Color thumbColor = themeSupplier.get().getColor("scrollbar.thumb", themeSupplier.get().getCursorColor());
                                    
                                    float opacity = 0.3f;
                                    try {
                                        opacity = Float.parseFloat(com.tinsider.config.ThemeManager.getProperty("scrollbar.thumb.opacity", "0.3"));
                                    } catch (Exception ignored) {}
                                    
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
                    
                    // Force the background color immediately on creation before the first paint cycle
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

            widget.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        setAllBackgrounds(widget, awtBg);
                        widget.revalidate();
                        widget.repaint();
                    });
                }

                @Override
                public void componentShown(java.awt.event.ComponentEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        setAllBackgrounds(widget, awtBg);
                        widget.revalidate();
                        widget.repaint();
                    });
                }
            });

            widget.setTtyConnector(connector);
            widget.start();

            Platform.runLater(() -> {
                swingNode.setContent(widget);
                tabWidgetMap.put(tab, widget);
                
                // Force a layout pass and repaint on the Swing thread to ensure perfect sync
                SwingUtilities.invokeLater(() -> {
                    setAllBackgrounds(widget, awtBg);
                    widget.revalidate();
                    widget.repaint();
                });
            });
        });

        tab.setContent(swingNode);

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
}

