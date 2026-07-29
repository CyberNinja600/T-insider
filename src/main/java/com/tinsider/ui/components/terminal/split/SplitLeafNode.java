package com.tinsider.ui.components.terminal.split;

import javafx.scene.Node;

public class SplitLeafNode extends SplitNode {
    private final TerminalPane terminalPane;

    public SplitLeafNode(TerminalPane terminalPane) {
        this.terminalPane = terminalPane;
        this.terminalPane.setLeafNode(this);
    }

    public TerminalPane getTerminalPane() {
        return terminalPane;
    }

    @Override
    public Node getView() {
        return terminalPane;
    }

    @Override
    public void close() {
        terminalPane.close();
    }
}
