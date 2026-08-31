package com.tinsider.ui.components.terminal.split;

import com.jediterm.terminal.ui.JediTermWidget;
import com.tinsider.config.TerminalTheme;
import com.tinsider.model.HostProfile;
import com.tinsider.model.WordHighlight;
import com.tinsider.storage.StorageManager;
import com.tinsider.terminal.TerminalTabFactory;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import javax.swing.SwingUtilities;

public class TerminalPane extends StackPane {
    private final TerminalSplitPane splitPane;
    private String sessionType;     // "LOCAL" or "SSH"
    private String localShellType;  // "CMD", "PowerShell", "Bash"
    private HostProfile hostProfile;// for SSH
    private String title;

    // Parent/Template details for empty placeholders
    private String parentSessionType;
    private String parentLocalShellType;
    private HostProfile parentHostProfile;
    private String parentTitle;

    private SplitLeafNode leafNode;
    private JediTermWidget widget;
    private SwingNode swingNode;

    private javax.swing.Timer highlightTimer;
    private final java.util.Set<String> activeHighlightIds = new java.util.HashSet<>();
    private final java.util.Set<String> caseInsensitiveHighlightIds = new java.util.HashSet<>();

    private BorderPane mainLayout;
    private HBox headerBar;
    private Label titleLabel;
    private Region dragOverlay;

    // Dynamic theming variables
    private TerminalTheme currentTheme;
    private Button splitVertBtn;
    private Button splitHorizBtn;
    private Button closeBtn;

    private VBox emptyContainer;
    private Label emptyDragLabel;
    private Label emptyDescLabel;
    private Button emptyLocalShellBtn;

    public TerminalPane(TerminalSplitPane splitPane, String sessionType, String localShellType, HostProfile hostProfile, String title) {
        this(splitPane, sessionType, localShellType, hostProfile, title, null);
    }

    public TerminalPane(TerminalSplitPane splitPane, String sessionType, String localShellType, HostProfile hostProfile, String title, TerminalPane parentPane) {
        this.splitPane = splitPane;
        this.sessionType = sessionType;
        this.currentTheme = splitPane.getCurrentTheme();

        if (parentPane != null) {
            if ("EMPTY".equals(parentPane.getSessionType())) {
                this.parentSessionType = parentPane.getParentSessionType();
                this.parentLocalShellType = parentPane.getParentLocalShellType();
                this.parentHostProfile = parentPane.getParentHostProfile();
                this.parentTitle = parentPane.getParentTitle();
            } else {
                this.parentSessionType = parentPane.getSessionType();
                this.parentLocalShellType = parentPane.getLocalShellType();
                this.parentHostProfile = parentPane.getHostProfile();
                this.parentTitle = parentPane.getTitle();
            }
        }

        if ("EMPTY".equals(sessionType)) {
            this.title = "Drop Tab Here";
        } else {
            this.localShellType = localShellType;
            this.hostProfile = hostProfile;
            this.title = title;
        }

        getStyleClass().add("terminal-pane");
        setStyle("-fx-border-color: #2a3356; -fx-border-width: 1.5px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-background-color: #090F22;");

        // Main content layout
        mainLayout = new BorderPane();
        
        // 1. Build Header Bar
        headerBar = new HBox(8);
        headerBar.getStyleClass().add("terminal-pane-header");
        headerBar.setStyle("-fx-background-color: #11172a; -fx-padding: 4px 8px; -fx-alignment: center-left; -fx-border-color: transparent transparent #2a3356 transparent; -fx-border-width: 0 0 1px 0; -fx-background-radius: 5px 5px 0 0;");

        titleLabel = new Label(this.title);
        titleLabel.getStyleClass().add("terminal-pane-title");
        titleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        splitVertBtn = new Button("Split");
        Label splitVertIcon = new Label("\uEB56");
        splitVertIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 10px; -fx-text-fill: inherit;");
        splitVertBtn.setGraphic(splitVertIcon);
        splitVertBtn.getStyleClass().add("terminal-pane-btn");
        splitVertBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
        splitVertBtn.setOnAction(e -> splitPane.splitLeaf(this.leafNode, SplitOrientation.SIDE_BY_SIDE));

        splitHorizBtn = new Button("Split");
        Label splitHorizIcon = new Label("\uEB57");
        splitHorizIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 10px; -fx-text-fill: inherit;");
        splitHorizBtn.setGraphic(splitHorizIcon);
        splitHorizBtn.getStyleClass().add("terminal-pane-btn");
        splitHorizBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
        splitHorizBtn.setOnAction(e -> splitPane.splitLeaf(this.leafNode, SplitOrientation.TOP_AND_BOTTOM));

        closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("terminal-pane-btn", "terminal-pane-close-btn");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
        closeBtn.setOnAction(e -> splitPane.removeLeaf(this.leafNode));

        // Hover styles programmatically
        setupButtonHover(splitVertBtn, false);
        setupButtonHover(splitHorizBtn, false);
        setupButtonHover(closeBtn, true);

        headerBar.getChildren().addAll(titleLabel, splitVertBtn, splitHorizBtn, closeBtn);
        mainLayout.setTop(headerBar);

        getChildren().add(mainLayout);

        // Apply a geometric clip to the main layout to round all corners and prevent terminal content overflow
        Rectangle clip = new Rectangle();
        clip.setArcWidth(12); // Matches 6px border radius (6 * 2)
        clip.setArcHeight(12);
        clip.widthProperty().bind(mainLayout.widthProperty());
        clip.heightProperty().bind(mainLayout.heightProperty());
        mainLayout.setClip(clip);

        // 2. Build Drag Overlay (Initially Hidden)
        dragOverlay = new Region();
        dragOverlay.getStyleClass().add("drag-overlay");
        dragOverlay.setStyle("-fx-background-color: rgba(0, 173, 181, 0.2);");
        dragOverlay.setMouseTransparent(true);
        dragOverlay.setVisible(false);
        getChildren().add(dragOverlay);

        // 3. Setup mouse click on TerminalPane to request focus and set active
        setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                splitPane.setActiveTerminalPane(this);
            }
        });

        // Hover state for the entire TerminalPane to light up header buttons
        setOnMouseEntered(event -> {
            if (currentTheme != null) {
                com.jediterm.core.Color hoverFg = currentTheme.getColor("terminal.pane.header.button.hover.foreground", currentTheme.getCursorColor());
                if (splitVertBtn != null && !splitVertBtn.isHover()) {
                    splitVertBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(hoverFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
                }
                if (splitHorizBtn != null && !splitHorizBtn.isHover()) {
                    splitHorizBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(hoverFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
                }
                if (closeBtn != null && !closeBtn.isHover()) {
                    closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(hoverFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
                }
            }
        });

        setOnMouseExited(event -> {
            if (currentTheme != null) {
                com.jediterm.core.Color normalFg = currentTheme.getColor("terminal.pane.header.button.foreground", currentTheme.getTextColor());
                if (splitVertBtn != null && !splitVertBtn.isHover()) {
                    splitVertBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(normalFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
                }
                if (splitHorizBtn != null && !splitHorizBtn.isHover()) {
                    splitHorizBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(normalFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
                }
                if (closeBtn != null && !closeBtn.isHover()) {
                    closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(normalFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
                }
            }
        });

        // Width and height listeners to trigger terminal re-fitting and responsive layout!
        widthProperty().addListener((obs, oldVal, newVal) -> triggerTerminalResize());
        heightProperty().addListener((obs, oldVal, newVal) -> triggerTerminalResize());

        // 4. Setup Drag & Drop handlers on Header
        setupDragAndDrop();

        // Initialize caseInsensitiveHighlightIds from saved highlights
        try {
            java.util.List<WordHighlight> saved = StorageManager.loadHighlights();
            if (saved != null) {
                for (WordHighlight wh : saved) {
                    if (wh.isCaseInsensitive()) {
                        caseInsensitiveHighlightIds.add(wh.getId());
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if ("EMPTY".equals(sessionType)) {
            showEmptyPlaceholderState();
        } else {
            showConnectingState();
        }

        // Initialize dynamic colors using currentTheme if available
        if (currentTheme != null) {
            updateThemeStyles(currentTheme);
        }
    }

    public void showEmptyPlaceholderState() {
        emptyContainer = new VBox(15);
        emptyContainer.setAlignment(Pos.CENTER);
        emptyContainer.setPadding(new Insets(20));
        emptyContainer.setStyle("-fx-border-style: none; -fx-border-width: 0px; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-border-insets: 20px; -fx-padding: 20px;");

        emptyDragLabel = new Label("Drop Tab Here");
        emptyDragLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        emptyDescLabel = new Label("Drag & drop any active tab here to split");
        emptyDescLabel.setStyle("-fx-font-size: 11px;");

        String buttonText = "Or Open Local CMD";
        if ("SSH".equals(parentSessionType) && parentHostProfile != null) {
            buttonText = "Duplicate Session (" + parentTitle + ")";
        } else if ("LOCAL".equals(parentSessionType) && parentLocalShellType != null) {
            buttonText = "Duplicate Session (" + parentLocalShellType + ")";
        }

        emptyLocalShellBtn = new Button(buttonText);
        emptyLocalShellBtn.getStyleClass().add("terminal-pane-btn");
        emptyLocalShellBtn.setStyle("-fx-cursor: hand; -fx-padding: 6px 14px; -fx-background-radius: 4px;");
        emptyLocalShellBtn.setOnAction(e -> {
            splitPane.initiatePaneConnectionFromTemplate(this, parentSessionType, parentLocalShellType, parentHostProfile, parentTitle);
        });

        // Setup hover behavior for local CMD button
        emptyLocalShellBtn.setOnMouseEntered(e -> {
            if (currentTheme != null) {
                com.jediterm.core.Color hoverBg = currentTheme.getColor("terminal.pane.empty.button.hover.background", currentTheme.getCursorColor());
                com.jediterm.core.Color hoverFg = currentTheme.getColor("terminal.pane.empty.button.hover.foreground", currentTheme.getBackgroundColor());
                emptyLocalShellBtn.setStyle("-fx-background-color: " + toHex(hoverBg) + "; -fx-text-fill: " + toHex(hoverFg) + "; -fx-cursor: hand; -fx-padding: 6px 14px; -fx-background-radius: 4px;");
            }
        });
        emptyLocalShellBtn.setOnMouseExited(e -> {
            if (currentTheme != null) {
                com.jediterm.core.Color normalBg = currentTheme.getColor("terminal.pane.empty.button.background", currentTheme.getSelectionColor());
                com.jediterm.core.Color normalFg = currentTheme.getColor("terminal.pane.empty.button.foreground", currentTheme.getTextColor());
                emptyLocalShellBtn.setStyle("-fx-background-color: " + toHex(normalBg) + "; -fx-text-fill: " + toHex(normalFg) + "; -fx-cursor: hand; -fx-padding: 6px 14px; -fx-background-radius: 4px;");
            }
        });

        if (currentTheme != null) {
            applyEmptyStateStyles(currentTheme);
        }

        emptyContainer.getChildren().addAll(emptyDragLabel, emptyDescLabel, emptyLocalShellBtn);
        mainLayout.setCenter(emptyContainer);
    }

    private void setupButtonHover(Button btn, boolean isClose) {
        btn.setOnMouseEntered(e -> {
            if (currentTheme == null) return;
            com.jediterm.core.Color hoverFg = currentTheme.getColor("terminal.pane.header.button.hover.foreground", currentTheme.getCursorColor());
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(hoverFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px; -fx-effect: dropshadow(three-pass-box, " + toHex(hoverFg) + "66, 8, 0, 0, 0);");
        });
        btn.setOnMouseExited(e -> {
            if (currentTheme == null) return;
            com.jediterm.core.Color hoverFg = currentTheme.getColor("terminal.pane.header.button.hover.foreground", currentTheme.getCursorColor());
            com.jediterm.core.Color normalFg = currentTheme.getColor("terminal.pane.header.button.foreground", currentTheme.getTextColor());
            com.jediterm.core.Color resetFg = TerminalPane.this.isHover() ? hoverFg : normalFg;
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(resetFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px; -fx-effect: null;");
        });
    }

    private void setupDragAndDrop() {
        headerBar.setOnDragDetected(event -> {
            Dragboard db = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("TERMINAL_PANE_DRAG");
            db.setContent(content);

            TerminalSplitPane.setDraggedPane(this);
            event.consume();
        });

        setOnDragOver(event -> {
            if (event.getGestureSource() != this && TerminalSplitPane.getDraggedPane() != null) {
                event.acceptTransferModes(TransferMode.MOVE);
                DropPosition pos = calculateDropPosition(event.getX(), event.getY(), getWidth(), getHeight());
                showDragOverlay(pos);
                event.consume();
            }
        });

        setOnDragExited(event -> {
            hideDragOverlay();
            event.consume();
        });

        setOnDragDropped(event -> {
            TerminalPane dragged = TerminalSplitPane.getDraggedPane();
            if (dragged != null && dragged != this) {
                DropPosition pos = calculateDropPosition(event.getX(), event.getY(), getWidth(), getHeight());
                splitPane.moveLeaf(dragged.getLeafNode(), this.getLeafNode(), pos);
                event.setDropCompleted(true);
                TerminalSplitPane.setDraggedPane(null);
                event.consume();
            }
        });
    }

    public DropPosition calculateDropPosition(double x, double y, double w, double h) {
        if ("EMPTY".equals(getSessionType())) {
            return DropPosition.CENTER;
        }

        // Divide active pane into 4 split zones based on the closest edge to the cursor (no deadzones, VS Code style)
        double distLeft = x;
        double distRight = w - x;
        double distTop = y;
        double distBottom = h - y;

        double minDist = Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));

        if (minDist == distLeft) return DropPosition.LEFT;
        if (minDist == distRight) return DropPosition.RIGHT;
        if (minDist == distTop) return DropPosition.TOP;
        return DropPosition.BOTTOM;
    }

    public void showDragOverlay(DropPosition pos) {
        dragOverlay.setVisible(true);
        double w = getWidth();
        double h = getHeight();

        switch (pos) {
            case LEFT:
                StackPane.setMargin(dragOverlay, new Insets(0, w / 2.0, 0, 0));
                break;
            case RIGHT:
                StackPane.setMargin(dragOverlay, new Insets(0, 0, 0, w / 2.0));
                break;
            case TOP:
                StackPane.setMargin(dragOverlay, new Insets(0, 0, h / 2.0, 0));
                break;
            case BOTTOM:
                StackPane.setMargin(dragOverlay, new Insets(h / 2.0, 0, 0, 0));
                break;
            case CENTER:
            default:
                StackPane.setMargin(dragOverlay, Insets.EMPTY);
                break;
        }
    }

    public void hideDragOverlay() {
        dragOverlay.setVisible(false);
    }

    public void showConnectingState() {
        VBox container = new VBox(15);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(20));
        container.getStyleClass().add("connecting-pane");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(40, 40);
        spinner.getStyleClass().add("connecting-spinner");
        Label msgLabel = new Label("Connecting " + title + "...");
        msgLabel.getStyleClass().add("connecting-label");

        container.getChildren().addAll(spinner, msgLabel);
        mainLayout.setCenter(container);
    }

    public void showErrorState(String errorMsg) {
        VBox container = new VBox(15);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(20));

        Label errLabel = new Label("✖ Connection Error");
        errLabel.setStyle("-fx-text-fill: #ff5555; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label detailLabel = new Label(errorMsg);
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(300);
        detailLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-alignment: center;");

        Button retryBtn = new Button("Close Split");
        retryBtn.getStyleClass().add("terminal-pane-btn");
        retryBtn.setStyle("-fx-background-color: #ff5555; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 6px 16px; -fx-background-radius: 4px;");
        retryBtn.setOnAction(e -> splitPane.removeLeaf(this.leafNode));

        container.getChildren().addAll(errLabel, detailLabel, retryBtn);
        mainLayout.setCenter(container);
    }

    public void setWidget(JediTermWidget widget) {
        this.widget = widget;
        this.swingNode = TerminalTabFactory.createSwingNode(widget);

        // Register Swing-side mouse click listener to sync active pane focus with JavaFX!
        SwingUtilities.invokeLater(() -> {
            java.awt.Component panel = widget.getTerminalPanel();
            if (panel != null) {
                panel.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        Platform.runLater(() -> {
                            splitPane.setActiveTerminalPane(TerminalPane.this);
                        });
                    }
                });
            }
        });

        // Dynamic focused listener inside TerminalPane that ONLY forwards focus if this pane is active
        this.swingNode.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                if (splitPane.getActiveWidget() == widget) {
                    SwingUtilities.invokeLater(() -> {
                        if (widget.getTerminalPanel() != null && !widget.getTerminalPanel().isFocusOwner()) {
                            widget.getTerminalPanel().requestFocus();
                        }
                        if (!widget.isFocusOwner()) {
                            widget.requestFocus();
                        }
                    });
                }
            }
        });

        // Click focus handler on SwingNode side
        this.swingNode.setOnMousePressed(event -> {
            splitPane.setActiveTerminalPane(this);
            if (!this.swingNode.isFocused()) {
                this.swingNode.requestFocus();
            }
            SwingUtilities.invokeLater(() -> {
                if (widget.getTerminalPanel() != null && !widget.getTerminalPanel().isFocusOwner()) {
                    widget.getTerminalPanel().requestFocus();
                }
                if (!widget.isFocusOwner()) {
                    widget.requestFocus();
                }
            });
        });

        mainLayout.setCenter(new SwingNodeWrapper(swingNode));
        
        // Start highlights timer
        highlightTimer = new javax.swing.Timer(150, e -> {
            applyHighlights();
        });
        highlightTimer.start();
        
        Platform.runLater(() -> {
            updateThemeStyles(splitPane.getCurrentTheme());
            requestFocus();
            triggerTerminalResize();
        });
    }

    public void setTitle(String title) {
        this.title = title;
        if (titleLabel != null) {
            titleLabel.setText(title);
        }
    }

    public void adoptWidgetAndSettings(TerminalPane sourcePane) {
        if (this.widget != null) {
            final JediTermWidget oldWidget = this.widget;
            SwingUtilities.invokeLater(() -> {
                try {
                    com.jediterm.terminal.TtyConnector connector = oldWidget.getTtyConnector();
                    if (connector != null) {
                        connector.close();
                    }
                    try {
                        oldWidget.stop();
                    } catch (Exception ignored) {}
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        this.widget = sourcePane.getWidget();
        this.sessionType = sourcePane.getSessionType();
        this.localShellType = sourcePane.getLocalShellType();
        this.hostProfile = sourcePane.getHostProfile();
        
        setTitle(sourcePane.getTitle());
        
        sourcePane.clearWidgetReference();
        setWidget(this.widget);
    }

    public void clearWidgetReference() {
        this.widget = null;
        this.swingNode = null;
    }

    public void triggerTerminalResize() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 100 || h <= 100) {
            return; // Skip collapsing on initial zero-size layout passes before rendering
        }
        if (widget != null) {
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

    private String toHex(com.jediterm.core.Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void applyEmptyStateStyles(TerminalTheme theme) {
        if (emptyContainer == null) return;
        
        com.jediterm.core.Color containerBg = theme.getColor("terminal.pane.empty.background", theme.getSelectionColor());
        com.jediterm.core.Color containerBorder = theme.getColor("terminal.pane.empty.border", theme.getCursorColor());
        com.jediterm.core.Color dragFg = theme.getColor("terminal.pane.empty.title", theme.getCursorColor());
        com.jediterm.core.Color descFg = theme.getColor("terminal.pane.empty.description", theme.getTextColor());
        com.jediterm.core.Color btnBg = theme.getColor("terminal.pane.empty.button.background", theme.getSelectionColor());
        com.jediterm.core.Color btnFg = theme.getColor("terminal.pane.empty.button.foreground", theme.getTextColor());

        emptyContainer.setStyle("-fx-border-style: none; -fx-border-width: 0px; -fx-background-color: " + toHex(containerBg) + "; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-border-insets: 20px; -fx-padding: 20px;");
        emptyDragLabel.setStyle("-fx-text-fill: " + toHex(dragFg) + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        emptyDescLabel.setStyle("-fx-text-fill: " + toHex(descFg) + "; -fx-font-size: 11px;");
        
        if (emptyLocalShellBtn != null && !emptyLocalShellBtn.isHover()) {
            emptyLocalShellBtn.setStyle("-fx-background-color: " + toHex(btnBg) + "; -fx-text-fill: " + toHex(btnFg) + "; -fx-cursor: hand; -fx-padding: 6px 14px; -fx-background-radius: 4px;");
        }
    }

    public void updateThemeStyles(TerminalTheme theme) {
        if (theme == null) return;
        this.currentTheme = theme;

        com.jediterm.core.Color paneBg = theme.getColor("terminal.pane.background", theme.getBackgroundColor());
        com.jediterm.core.Color headerBg = theme.getColor("terminal.pane.header.background", theme.getSelectionColor());
        com.jediterm.core.Color headerBorderColor = theme.getColor("terminal.pane.header.border", theme.getSelectionColor());
        com.jediterm.core.Color activeBorderColor = theme.getColor("terminal.pane.border", theme.getCursorColor());
        com.jediterm.core.Color inactiveBorderColor = theme.getColor("terminal.pane.border", theme.getSelectionColor());

        com.jediterm.core.Color activeTitleColor = theme.getColor("terminal.pane.header.foreground", theme.getCursorColor());
        com.jediterm.core.Color inactiveTitleColor = theme.getColor("terminal.pane.header.foreground", theme.getTextColor());
        com.jediterm.core.Color buttonFg = theme.getColor("terminal.pane.header.button.foreground", theme.getTextColor());

        boolean active = (splitPane.getActiveTerminalPane() == this);
        
        // 1. Pane itself (border highlighting active pane)
        if (active) {
            setStyle("-fx-border-color: " + toHex(activeBorderColor) + "; -fx-border-width: 1.5px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-background-color: " + toHex(paneBg) + ";");
            titleLabel.setStyle("-fx-text-fill: " + toHex(activeTitleColor) + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            setStyle("-fx-border-color: " + toHex(inactiveBorderColor) + "; -fx-border-width: 1.5px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-background-color: " + toHex(paneBg) + ";");
            titleLabel.setStyle("-fx-text-fill: " + toHex(inactiveTitleColor) + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        }

        // 2. Header Bar
        headerBar.setStyle("-fx-background-color: " + toHex(headerBg) + "; -fx-padding: 4px 8px; -fx-alignment: center-left; -fx-border-color: transparent transparent " + toHex(headerBorderColor) + " transparent; -fx-border-width: 0 0 1px 0; -fx-background-radius: 5px 5px 0 0;");

        // 3. Header Buttons (if not hovered, set base styles)
        if (splitVertBtn != null && !splitVertBtn.isHover()) {
            splitVertBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(buttonFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
        }
        if (splitHorizBtn != null && !splitHorizBtn.isHover()) {
            splitHorizBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(buttonFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
        }
        if (closeBtn != null && !closeBtn.isHover()) {
            closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + toHex(buttonFg) + "; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2px 6px;");
        }

        // 4. Update empty state if currently in placeholder state
        if (emptyContainer != null) {
            applyEmptyStateStyles(theme);
        }
    }

    public void requestFocusOnTerminal() {
        if (swingNode != null) {
            if (!swingNode.isFocused()) {
                swingNode.requestFocus();
            }
            SwingUtilities.invokeLater(() -> {
                if (widget != null) {
                    if (widget.getTerminalPanel() != null && !widget.getTerminalPanel().isFocusOwner()) {
                        widget.getTerminalPanel().requestFocus();
                    }
                    if (!widget.isFocusOwner()) {
                        widget.requestFocus();
                    }
                }
            });
        }
    }

    public void close() {
        if (highlightTimer != null) {
            highlightTimer.stop();
        }
        if (widget != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    com.jediterm.terminal.TtyConnector connector = widget.getTtyConnector();
                    if (connector != null) {
                        connector.close();
                    }
                    try {
                        widget.stop();
                    } catch (Exception ignored) {}
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        if (swingNode != null) {
            swingNode.setContent(null);
        }
    }

    public java.util.Set<String> getActiveHighlightIds() {
        return activeHighlightIds;
    }

    public void toggleHighlight(String id, boolean active) {
        if (active) {
            activeHighlightIds.add(id);
        } else {
            activeHighlightIds.remove(id);
        }
        applyHighlights();
    }

    public java.util.Set<String> getCaseInsensitiveHighlightIds() {
        return caseInsensitiveHighlightIds;
    }

    public void toggleCaseInsensitive(String id, boolean insensitive) {
        if (insensitive) {
            caseInsensitiveHighlightIds.add(id);
        } else {
            caseInsensitiveHighlightIds.remove(id);
        }
        applyHighlights();
    }

    public void applyHighlights() {
        if (widget == null) return;
        java.util.List<WordHighlight> allHighlights = StorageManager.loadHighlights();
        if (allHighlights.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            try {
                com.jediterm.terminal.model.TerminalTextBuffer textBuffer = widget.getTerminalTextBuffer();
                if (textBuffer == null) return;

                textBuffer.lock();
                try {
                    int lineCount = textBuffer.getHeight();
                    for (int i = 0; i < lineCount; i++) {
                        com.jediterm.terminal.model.TerminalLine line = textBuffer.getLine(i);
                        if (line == null) continue;

                        String text = line.getText();
                        if (text == null || text.trim().isEmpty()) continue;

                        for (WordHighlight wh : allHighlights) {
                            String word = wh.getWord();
                            if (word == null || word.isEmpty()) continue;

                            boolean isInsensitive = caseInsensitiveHighlightIds.contains(wh.getId());

                            if (wh.isRegex()) {
                                try {
                                    int flags = isInsensitive ? java.util.regex.Pattern.CASE_INSENSITIVE : 0;
                                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(word, flags);
                                    java.util.regex.Matcher matcher = pattern.matcher(text);
                                    while (matcher.find()) {
                                        int start = matcher.start();
                                        int end = matcher.end();
                                        if (start >= end) continue; // safety check
                                        
                                        com.jediterm.terminal.TextStyle textStyle;
                                        if (activeHighlightIds.contains(wh.getId())) {
                                            com.jediterm.core.Color bg = parseHexColor(wh.getColorHex());
                                            double luminance = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
                                            com.jediterm.core.Color fg = (luminance > 0.5) ? new com.jediterm.core.Color(0, 0, 0) : new com.jediterm.core.Color(255, 255, 255);
                                            textStyle = new com.jediterm.terminal.TextStyle(
                                                com.jediterm.terminal.TerminalColor.fromColor(fg),
                                                com.jediterm.terminal.TerminalColor.fromColor(bg)
                                            );
                                        } else {
                                            textStyle = com.jediterm.terminal.TextStyle.EMPTY;
                                        }
                                        
                                        String screenWord = text.substring(start, end);
                                        line.writeString(start, new com.jediterm.terminal.model.CharBuffer(screenWord), textStyle);
                                    }
                                } catch (Exception e) {
                                    // Ignored or logged (e.g. invalid pattern)
                                }
                            } else {
                                String searchWord = isInsensitive ? word.toLowerCase() : word;
                                String searchText = isInsensitive ? text.toLowerCase() : text;

                                int idx = searchText.indexOf(searchWord);
                                while (idx != -1) {
                                    com.jediterm.terminal.TextStyle textStyle;
                                    if (activeHighlightIds.contains(wh.getId())) {
                                        com.jediterm.core.Color bg = parseHexColor(wh.getColorHex());
                                        double luminance = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
                                        com.jediterm.core.Color fg = (luminance > 0.5) ? new com.jediterm.core.Color(0, 0, 0) : new com.jediterm.core.Color(255, 255, 255);
                                        textStyle = new com.jediterm.terminal.TextStyle(
                                            com.jediterm.terminal.TerminalColor.fromColor(fg),
                                            com.jediterm.terminal.TerminalColor.fromColor(bg)
                                        );
                                    } else {
                                        textStyle = com.jediterm.terminal.TextStyle.EMPTY;
                                    }

                                    // Get original text on terminal screen to preserve casing
                                    String screenWord = text.substring(idx, Math.min(idx + word.length(), text.length()));
                                    line.writeString(idx, new com.jediterm.terminal.model.CharBuffer(screenWord), textStyle);
                                    idx = searchText.indexOf(searchWord, idx + word.length());
                                }
                            }
                        }
                    }

                    if (widget.getTerminalPanel() != null) {
                        widget.getTerminalPanel().repaint();
                    }
                } finally {
                    textBuffer.unlock();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private com.jediterm.core.Color parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new com.jediterm.core.Color(255, 255, 255);
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new com.jediterm.core.Color(r, g, b);
        } catch (Exception e) {
            return new com.jediterm.core.Color(255, 255, 255);
        }
    }

    public TerminalSplitPane getSplitPane() {
        return splitPane;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getParentSessionType() {
        return parentSessionType;
    }

    public String getLocalShellType() {
        return localShellType;
    }

    public void setLocalShellType(String localShellType) {
        this.localShellType = localShellType;
    }

    public String getParentLocalShellType() {
        return parentLocalShellType;
    }

    public HostProfile getHostProfile() {
        return hostProfile;
    }

    public void setHostProfile(HostProfile hostProfile) {
        this.hostProfile = hostProfile;
    }

    public HostProfile getParentHostProfile() {
        return parentHostProfile;
    }

    public String getParentTitle() {
        return parentTitle;
    }

    public String getTitle() {
        return title;
    }

    public SplitLeafNode getLeafNode() {
        return leafNode;
    }

    public void setLeafNode(SplitLeafNode leafNode) {
        this.leafNode = leafNode;
    }

    public JediTermWidget getWidget() {
        return widget;
    }
}

class SwingNodeWrapper extends javafx.scene.layout.Pane {
    private final SwingNode swingNode;

    public SwingNodeWrapper(SwingNode swingNode) {
        this.swingNode = swingNode;
        getChildren().add(swingNode);
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (swingNode != null) {
            swingNode.resize(w, h);
            swingNode.relocate(0, 0);
        }
    }
}
