package com.tinsider.ui.components.terminal.split;

import javafx.scene.Node;

public abstract class SplitNode {
    protected SplitParentNode parent;

    public SplitParentNode getParent() {
        return parent;
    }

    public void setParent(SplitParentNode parent) {
        this.parent = parent;
    }

    public abstract Node getView();
    public abstract void close();
}
