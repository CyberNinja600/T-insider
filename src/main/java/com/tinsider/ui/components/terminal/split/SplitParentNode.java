package com.tinsider.ui.components.terminal.split;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;

public class SplitParentNode extends SplitNode {
    private SplitNode firstChild;
    private SplitNode secondChild;
    private final SplitOrientation orientation;
    private final SplitPane splitPane;

    public SplitParentNode(SplitNode firstChild, SplitNode secondChild, SplitOrientation orientation) {
        this.firstChild = firstChild;
        this.secondChild = secondChild;
        this.orientation = orientation;

        this.firstChild.setParent(this);
        this.secondChild.setParent(this);

        this.splitPane = new SplitPane();
        this.splitPane.getStyleClass().add("split-pane");

        if (orientation == SplitOrientation.SIDE_BY_SIDE) {
            this.splitPane.setOrientation(Orientation.HORIZONTAL);
        } else {
            this.splitPane.setOrientation(Orientation.VERTICAL);
        }

        this.splitPane.getItems().addAll(firstChild.getView(), secondChild.getView());
        this.splitPane.setDividerPositions(0.5);
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
