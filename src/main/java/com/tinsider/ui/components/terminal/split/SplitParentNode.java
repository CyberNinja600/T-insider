package com.tinsider.ui.components.terminal.split;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;

public class SplitParentNode extends SplitNode {
    private SplitNode firstChild;
    private SplitNode secondChild;
    private final SplitOrientation orientation;
    private final SplitPane splitPane;
    private boolean adjustingDivider = false;

    public SplitParentNode(SplitNode firstChild, SplitNode secondChild, SplitOrientation orientation) {
        this.firstChild = firstChild;
        this.secondChild = secondChild;
        this.orientation = orientation;

        this.firstChild.setParent(this);
        this.secondChild.setParent(this);

        this.splitPane = new SplitPane();
        this.splitPane.getStyleClass().add("split-pane");
        this.splitPane.setMinWidth(0);
        this.splitPane.setMinHeight(0);

        if (orientation == SplitOrientation.SIDE_BY_SIDE) {
            this.splitPane.setOrientation(Orientation.HORIZONTAL);
        } else {
            this.splitPane.setOrientation(Orientation.VERTICAL);
        }

        this.splitPane.getItems().addAll(firstChild.getView(), secondChild.getView());
        SplitPane.setResizableWithParent(firstChild.getView(), Boolean.TRUE);
        SplitPane.setResizableWithParent(secondChild.getView(), Boolean.TRUE);
        this.splitPane.setDividerPositions(0.5);

        setupDividerConstraint();
    }

    private void setupDividerConstraint() {
        for (SplitPane.Divider divider : splitPane.getDividers()) {
            attachDividerListener(divider);
        }
        splitPane.getDividers().addListener((javafx.collections.ListChangeListener.Change<? extends SplitPane.Divider> c) -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (SplitPane.Divider d : c.getAddedSubList()) {
                        attachDividerListener(d);
                    }
                }
            }
        });
    }

    private void attachDividerListener(SplitPane.Divider divider) {
        divider.positionProperty().addListener((obs, oldVal, newVal) -> {
            if (adjustingDivider) return;
            double currentPos = newVal.doubleValue();
            double totalSize = (orientation == SplitOrientation.SIDE_BY_SIDE) ? splitPane.getWidth() : splitPane.getHeight();
            if (totalSize <= 0) return;

            double desiredMin = (orientation == SplitOrientation.SIDE_BY_SIDE) ? TerminalPane.MIN_SPLIT_WIDTH : TerminalPane.MIN_SPLIT_HEIGHT;
            // Cap minimum boundary to at most 40% of this partition so multiple/dense splits never lock
            double maxAllowedMin = totalSize * 0.40;
            double effectiveMin = Math.min(desiredMin, maxAllowedMin);
            double minPos = effectiveMin / totalSize;
            double maxPos = 1.0 - minPos;

            if (minPos < maxPos) {
                if (currentPos < minPos) {
                    adjustingDivider = true;
                    divider.setPosition(minPos);
                    adjustingDivider = false;
                } else if (currentPos > maxPos) {
                    adjustingDivider = true;
                    divider.setPosition(maxPos);
                    adjustingDivider = false;
                }
            }
        });
    }

    public SplitNode getFirstChild() {
        return firstChild;
    }

    public void setFirstChild(SplitNode firstChild) {
        this.firstChild = firstChild;
        this.firstChild.setParent(this);
        this.splitPane.getItems().set(0, firstChild.getView());
    }

    public SplitNode getSecondChild() {
        return secondChild;
    }

    public void setSecondChild(SplitNode secondChild) {
        this.secondChild = secondChild;
        this.secondChild.setParent(this);
        this.splitPane.getItems().set(1, secondChild.getView());
    }

    public SplitOrientation getOrientation() {
        return orientation;
    }

    @Override
    public Node getView() {
        return splitPane;
    }

    @Override
    public void close() {
        firstChild.close();
        secondChild.close();
    }
}
