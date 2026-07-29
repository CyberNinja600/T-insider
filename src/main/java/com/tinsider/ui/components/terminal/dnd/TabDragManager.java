package com.tinsider.ui.components.terminal.dnd;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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

            Node tabNode = findTabNode(event.getTarget() instanceof Node ? (Node) event.getTarget() : null);
            if (tabNode != null && !isAddTabNode(tabNode)) {
                // Determine matching Tab index
                int tabIndex = findTabIndexForNode(tabNode);
                if (tabIndex != -1) {
                    this.draggedTabNode = tabNode;
                    this.draggedTab = tabPane.getTabs().get(tabIndex);
                    this.mousePressSceneX = event.getSceneX();
                    this.mousePressSceneY = event.getSceneY();
                    this.state = DragState.PRESSED;
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
                endDrag();
                event.consume();
            } else if (state == DragState.PRESSED) {
                resetToIdle();
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
    }

    private void endDrag() {
        this.state = DragState.IDLE;
        scrollTimer.stop();
        animationManager.stopAll();

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
