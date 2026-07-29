package com.tinsider.ui.components.terminal.dnd;

import com.jediterm.terminal.ui.JediTermWidget;
import com.tinsider.ui.components.terminal.split.TerminalPane;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TabDragManager {

    private enum DragState {
        IDLE,
        PRESSED,
        DRAGGING
    }

    private static final double DRAG_THRESHOLD = 8.0; // pixels

    private final TabPane tabPane;
    private final Pane overlayPane;
    private final Tab addTab;
    private final TabAnimationManager animationManager;

    private DragState state = DragState.IDLE;

    // Mouse positions
    private double mousePressSceneX;
    private double mousePressSceneY;
    private double lastMouseLocalX;

    // Dragged items references
    private Node draggedTabNode;
    private Tab draggedTab;
    private List<DropCalculator.TabInfo> staticTabs;

    // Helpers
    private TabGhost ghost;
    private TabPlaceholder placeholder;
    
    // Split preview hover trackers
    private TerminalPane lastHoveredPane;
    private com.tinsider.ui.components.terminal.split.DropPosition lastDropPos;

    // Auto scroll timer
    private final AnimationTimer scrollTimer = new AnimationTimer() {
        private long lastScrollTime = 0;

        @Override
        public void handle(long now) {
            if (state != DragState.DRAGGING) return;

            // Scroll at most every 150ms
            if (now - lastScrollTime < 150_000_000L) {
                return;
            }

            double scrollThreshold = 40;
            double paneWidth = tabPane.getWidth();

            if (lastMouseLocalX < scrollThreshold) {
                scrollLeft();
                lastScrollTime = now;
            } else if (lastMouseLocalX > paneWidth - scrollThreshold) {
                scrollRight();
                lastScrollTime = now;
            }
        }
    };

    public TabDragManager(TabPane tabPane, Pane overlayPane, Tab addTab) {
        this.tabPane = tabPane;
        this.overlayPane = overlayPane;
        this.addTab = addTab;
        this.animationManager = new TabAnimationManager();

        installEventFilters();

        // Safe abort listener if tabs are changed from outside during a drag
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            if (state == DragState.DRAGGING) {
                abortDrag();
            }
        });
    }

    private void installEventFilters() {
        tabPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;

            Node targetNode = event.getTarget() instanceof Node ? (Node) event.getTarget() : null;
            if (targetNode != null && isCloseButton(targetNode)) {
                // Let the default JavaFX close action handle the close button, do NOT consume!
                return;
            }

            Node tabNode = findTabNode(targetNode);
            if (tabNode != null && !isAddTabNode(tabNode)) {
                // Determine matching Tab index
                int tabIndex = findTabIndexForNode(tabNode);
                if (tabIndex != -1) {
                    this.draggedTabNode = tabNode;
                    this.draggedTab = tabPane.getTabs().get(tabIndex);
                    this.mousePressSceneX = event.getSceneX();
                    this.mousePressSceneY = event.getSceneY();
                    this.state = DragState.PRESSED;
                    
                    // Consume to prevent immediate selection on mouse press
                    event.consume();
                }
            }
        });

        tabPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;

            if (state == DragState.PRESSED) {
                double deltaX = event.getSceneX() - mousePressSceneX;
                double deltaY = event.getSceneY() - mousePressSceneY;
                if (Math.hypot(deltaX, deltaY) > DRAG_THRESHOLD) {
                    startDrag(event);
                }
            }

            if (state == DragState.DRAGGING) {
                Point2D bounds = tabPane.sceneToLocal(event.getSceneX(), event.getSceneY());
                this.lastMouseLocalX = bounds.getX();

                updateDrag(bounds.getX(), bounds.getY());
                event.consume();
            }
        });

        tabPane.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;

            if (state == DragState.DRAGGING) {
                endDrag(event);
                event.consume();
            } else if (state == DragState.PRESSED) {
                // Perform select only on release if no drag was initiated!
                if (draggedTab != null) {
                    tabPane.getSelectionModel().select(draggedTab);
                }
                resetToIdle();
                event.consume();
            }
        });
    }

    private void startDrag(MouseEvent event) {
        this.state = DragState.DRAGGING;

        // Take static snapshot of tab widths & original positions
        List<Tab> normalTabs = tabPane.getTabs().stream()
                .filter(t -> t != addTab)
                .collect(Collectors.toList());

        Set<Node> tabNodes = tabPane.lookupAll(".tab");
        List<Node> normalTabNodes = tabNodes.stream()
                .filter(node -> !isAddTabNode(node))
                .sorted((n1, n2) -> {
                    Bounds b1 = tabPane.sceneToLocal(n1.localToScene(n1.getBoundsInLocal()));
                    Bounds b2 = tabPane.sceneToLocal(n2.localToScene(n2.getBoundsInLocal()));
                    return Double.compare(b1.getMinX(), b2.getMinX());
                })
                .collect(Collectors.toList());

        this.staticTabs = new ArrayList<>();
        int startIndex = -1;

        for (int i = 0; i < normalTabs.size(); i++) {
            Tab t = normalTabs.get(i);
            Node node = (i < normalTabNodes.size()) ? normalTabNodes.get(i) : null;
            if (node == null) continue;

            Bounds bounds = tabPane.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
            staticTabs.add(new DropCalculator.TabInfo(t, node, bounds.getMinX(), bounds.getWidth()));

            if (node == draggedTabNode) {
                startIndex = i;
            }
        }

        if (startIndex == -1) {
            abortDrag();
            return;
        }

        // Initialize ghost and placeholder helpers
        Point2D localMouse = overlayPane.sceneToLocal(event.getSceneX(), event.getSceneY());
        this.ghost = new TabGhost(draggedTabNode, overlayPane, localMouse.getX(), localMouse.getY());
        this.placeholder = new TabPlaceholder(startIndex, draggedTabNode.getBoundsInLocal().getWidth());

        // Hide original tab
        draggedTabNode.setOpacity(0.0);
        tabPane.setCursor(Cursor.CLOSED_HAND);

        // Start scrolling monitoring
        scrollTimer.start();
    }

    private void updateDrag(double mouseLocalX, double mouseLocalY) {
        Point2D overlayMouse = overlayPane.sceneToLocal(tabPane.localToScene(mouseLocalX, mouseLocalY));
        ghost.updatePosition(overlayMouse.getX(), overlayMouse.getY());

        int startIndex = placeholder.getStartIndex();
        int newIndex = DropCalculator.calculateInsertionIndex(staticTabs, startIndex, mouseLocalX);
        newIndex = Math.max(0, Math.min(newIndex, staticTabs.size() - 1));

        if (newIndex != placeholder.getCurrentIndex()) {
            placeholder.setCurrentIndex(newIndex);
            animationManager.animateTabShift(staticTabs, startIndex, newIndex);
        }

        // --- SPLIT PREVIEW HIGHLIGHTING ---
        Point2D scenePt = tabPane.localToScene(mouseLocalX, mouseLocalY);
        Tab activeTab = tabPane.getSelectionModel().getSelectedItem();
        TerminalPane hoveredPane = null;
        if (activeTab != null && activeTab.getContent() != null) {
            hoveredPane = findTerminalPaneAtSceneCoords(activeTab.getContent(), scenePt.getX(), scenePt.getY());
        }

        if (hoveredPane != lastHoveredPane) {
            if (lastHoveredPane != null) {
                lastHoveredPane.hideDragOverlay();
            }
            lastHoveredPane = hoveredPane;
            lastDropPos = null;
        }

        if (hoveredPane != null) {
            Point2D paneLocal = hoveredPane.sceneToLocal(scenePt.getX(), scenePt.getY());
            com.tinsider.ui.components.terminal.split.DropPosition currentPos = hoveredPane.calculateDropPosition(paneLocal.getX(), paneLocal.getY(), hoveredPane.getWidth(), hoveredPane.getHeight());
            
            // If the pane is an empty placeholder, always force CENTER (no need to show edge splitting overlays)
            if ("EMPTY".equals(hoveredPane.getSessionType())) {
                currentPos = com.tinsider.ui.components.terminal.split.DropPosition.CENTER;
            }

            if (currentPos != lastDropPos) {
                lastDropPos = currentPos;
                hoveredPane.showDragOverlay(currentPos);
            }
        }
    }

    private TerminalPane findTerminalPaneAtSceneCoords(Node splitPane, double sceneX, double sceneY) {
        if (splitPane == null) return null;

        Set<Node> panes = splitPane.lookupAll(".terminal-pane");
        for (Node node : panes) {
            if (node instanceof TerminalPane pane && pane.isVisible()) {
                Point2D local = pane.sceneToLocal(sceneX, sceneY);
                if (local != null && pane.getBoundsInLocal().contains(local)) {
                    return pane;
                }
            }
        }
        return null;
    }

    private void endDrag(MouseEvent event) {
        this.state = DragState.IDLE;
        scrollTimer.stop();
        animationManager.stopAll();

        if (lastHoveredPane != null) {
            lastHoveredPane.hideDragOverlay();
        }

        // 1. Detect if released over a TerminalPane using Scene coordinates (DPI-immune and bulletproof!)
        Tab activeTab = tabPane.getSelectionModel().getSelectedItem();
        TerminalPane targetPane = null;
        if (activeTab != null && activeTab.getContent() != null) {
            targetPane = findTerminalPaneAtSceneCoords(activeTab.getContent(), event.getSceneX(), event.getSceneY());
        }

        if (targetPane != null && draggedTab != null) {
            if (draggedTab.getContent() instanceof com.tinsider.ui.components.terminal.split.TerminalSplitPane draggedSplit) {
                com.tinsider.ui.components.terminal.split.TerminalPane sourcePane = draggedSplit.getActiveTerminalPane();
                if (sourcePane != null && sourcePane.getWidget() != null) {
                    
                    if (ghost != null) {
                        ghost.destroy();
                        ghost = null;
                    }

                    // Restore original dragged tab node opacity and reset translation offsets to close the gap!
                    if (draggedTabNode != null) {
                        draggedTabNode.setOpacity(1.0);
                    }

                    if (staticTabs != null) {
                        for (DropCalculator.TabInfo info : staticTabs) {
                            info.node.setTranslateX(0);
                        }
                    }

                    // Calculate final drop position and perform either in-place adoption or edge splitting!
                    Point2D paneLocal = targetPane.sceneToLocal(event.getSceneX(), event.getSceneY());
                    com.tinsider.ui.components.terminal.split.DropPosition dropPos = targetPane.calculateDropPosition(paneLocal.getX(), paneLocal.getY(), targetPane.getWidth(), targetPane.getHeight());

                    if ("EMPTY".equals(targetPane.getSessionType()) || dropPos == com.tinsider.ui.components.terminal.split.DropPosition.CENTER) {
                        // In-place adoption
                        targetPane.adoptWidgetAndSettings(sourcePane);
                    } else {
                        // Edge split and adopt
                        if (activeTab.getContent() instanceof com.tinsider.ui.components.terminal.split.TerminalSplitPane activeSplit) {
                            activeSplit.splitAndAdoptLeaf(targetPane, sourcePane, dropPos);
                        }
                    }

                    final Tab tabToClose = draggedTab; // Capture reference before resetToIdle() nullifies it!
                    // Safely close and remove the dragged tab from tab bar
                    Platform.runLater(() -> {
                        if (tabToClose.getOnCloseRequest() != null) {
                            tabToClose.getOnCloseRequest().handle(new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT));
                        } else {
                            java.util.Map<Tab, com.tinsider.ui.components.terminal.split.TerminalSplitPane> splitMap = com.tinsider.ui.MainApp.getTabSplitPaneMap();
                            if (splitMap != null) {
                                splitMap.remove(tabToClose);
                            }
                            tabPane.getTabs().remove(tabToClose);
                        }
                    });

                    tabPane.setCursor(Cursor.DEFAULT);
                    resetToIdle();
                    return;
                }
            }
        }

        // Default tab-reordering drop behavior
        double finalX = placeholder.calculateTargetX(staticTabs);

        Bounds overlayBounds = overlayPane.localToScene(overlayPane.getBoundsInLocal());
        Bounds tabPaneBounds = tabPane.localToScene(tabPane.getBoundsInLocal());
        double targetXInOverlay = finalX + (tabPaneBounds.getMinX() - overlayBounds.getMinX());

        Bounds tabHeaderBounds = draggedTabNode.localToScene(draggedTabNode.getBoundsInLocal());
        double targetYInOverlay = tabHeaderBounds.getMinY() - overlayBounds.getMinY();

        Node ghostImageView = ghost.getImageView();
        Timeline dropTimeline = new Timeline();
        KeyValue kvX = new KeyValue(ghostImageView.layoutXProperty(), targetXInOverlay, Interpolator.EASE_BOTH);
        KeyValue kvY = new KeyValue(ghostImageView.layoutYProperty(), targetYInOverlay, Interpolator.EASE_BOTH);
        KeyFrame kf = new KeyFrame(Duration.millis(150), kvX, kvY);
        dropTimeline.getKeyFrames().add(kf);
        dropTimeline.setOnFinished(e -> commitDrop());
        dropTimeline.play();
    }

    private void commitDrop() {
        int fromIndex = placeholder.getStartIndex();
        int toIndex = placeholder.getCurrentIndex();

        if (fromIndex != toIndex) {
            Tab tab = tabPane.getTabs().remove(fromIndex);
            tabPane.getTabs().add(toIndex, tab);
            tabPane.getSelectionModel().select(tab);
        }

        // Clean up
        if (draggedTabNode != null) {
            draggedTabNode.setOpacity(1.0);
        }

        if (staticTabs != null) {
            for (DropCalculator.TabInfo info : staticTabs) {
                info.node.setTranslateX(0);
            }
        }

        if (ghost != null) {
            ghost.destroy();
            ghost = null;
        }

        tabPane.setCursor(Cursor.DEFAULT);
        resetToIdle();
    }

    private void abortDrag() {
        scrollTimer.stop();
        animationManager.stopAll();

        if (lastHoveredPane != null) {
            lastHoveredPane.hideDragOverlay();
        }

        if (ghost != null) {
            ghost.destroy();
            ghost = null;
        }

        if (draggedTabNode != null) {
            draggedTabNode.setOpacity(1.0);
        }

        if (staticTabs != null) {
            for (DropCalculator.TabInfo info : staticTabs) {
                info.node.setTranslateX(0);
            }
        }

        tabPane.setCursor(Cursor.DEFAULT);
        resetToIdle();
    }

    private void resetToIdle() {
        this.state = DragState.IDLE;
        this.draggedTabNode = null;
        this.draggedTab = null;
        this.staticTabs = null;
        this.placeholder = null;
        this.lastHoveredPane = null;
        this.lastDropPos = null;
    }

    private void scrollLeft() {
        Node decBtn = tabPane.lookup(".decrement-button");
        if (decBtn != null && decBtn.isVisible()) {
            decBtn.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, false, null));
            decBtn.fireEvent(new MouseEvent(MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, false, null));
        }
    }

    private void scrollRight() {
        Node incBtn = tabPane.lookup(".increment-button");
        if (incBtn != null && incBtn.isVisible()) {
            incBtn.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, false, null));
            incBtn.fireEvent(new MouseEvent(MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, false, null));
        }
    }

    private Node findTabNode(Node target) {
        Node node = target;
        while (node != null && node != tabPane) {
            if (node.getStyleClass().contains("tab")) {
                return node;
            }
            node = node.getParent();
        }
        return null;
    }

    private boolean isCloseButton(Node target) {
        Node node = target;
        while (node != null) {
            if (node.getStyleClass().contains("tab-close-button")) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private boolean isAddTabNode(Node node) {
        if (node == null) return false;
        if (node.getStyleClass().contains("add-tab")) return true;

        Node labelNode = node.lookup(".tab-label");
        if (labelNode instanceof Label label) {
            return "+".equals(label.getText());
        }
        return false;
    }

    private int findTabIndexForNode(Node node) {
        if (node == null) return -1;

        Set<Node> tabNodes = tabPane.lookupAll(".tab");
        List<Node> normalTabNodes = tabNodes.stream()
                .filter(n -> !isAddTabNode(n))
                .sorted((n1, n2) -> {
                    Bounds b1 = tabPane.sceneToLocal(n1.localToScene(n1.getBoundsInLocal()));
                    Bounds b2 = tabPane.sceneToLocal(n2.localToScene(n2.getBoundsInLocal()));
                    return Double.compare(b1.getMinX(), b2.getMinX());
                })
                .collect(Collectors.toList());

        int visualIndex = normalTabNodes.indexOf(node);
        if (visualIndex == -1) return -1;

        int normalCount = 0;
        for (int i = 0; i < tabPane.getTabs().size(); i++) {
            Tab tab = tabPane.getTabs().get(i);
            if (tab != addTab) {
                if (normalCount == visualIndex) {
                    return i;
                }
                normalCount++;
            }
        }
        return -1;
    }
}
