package com.tinsider.ui.components.terminal.dnd;

import java.util.List;

public class TabPlaceholder {
    private final int startIndex;
    private int currentIndex;
    private final double width;

    public TabPlaceholder(int startIndex, double width) {
        this.startIndex = startIndex;
        this.currentIndex = startIndex;
        this.width = width;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public double getWidth() {
        return width;
    }

    /**
     * Calculates the target visual X coordinate where the dropped tab should land relative to the TabPane.
     *
     * @param tabs The list of original static tab info objects
     * @return The final X coordinate
     */
    public double calculateTargetX(List<DropCalculator.TabInfo> tabs) {
        if (currentIndex == startIndex) {
            return tabs.get(startIndex).originalX;
        } else if (currentIndex > startIndex) {
            DropCalculator.TabInfo currentTab = tabs.get(currentIndex);
            return currentTab.originalX + currentTab.width - width;
        } else {
            return tabs.get(currentIndex).originalX;
        }
    }
}
