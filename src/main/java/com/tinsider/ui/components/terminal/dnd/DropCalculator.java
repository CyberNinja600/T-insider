package com.tinsider.ui.components.terminal.dnd;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import java.util.List;

public class DropCalculator {

    public static class TabInfo {
        public final Tab tab;
        public final Node node;
        public final double originalX;
        public final double width;

        public TabInfo(Tab tab, Node node, double originalX, double width) {
            this.tab = tab;
            this.node = node;
            this.originalX = originalX;
            this.width = width;
        }

        public double getCenterX() {
            return originalX + width / 2.0;
        }
    }

    /**
     * Calculates the new insertion index based on the mouse X coordinate
     * and the list of static TabInfo objects representing tab positions at drag start.
     *
     * @param tabs         The list of original static tab info objects
     * @param startIndex   The index of the dragged tab at the start of dragging
     * @param mouseX       The current mouse X coordinate relative to the TabPane
     * @return The calculated insertion index
     */
    public static int calculateInsertionIndex(List<TabInfo> tabs, int startIndex, double mouseX) {
        int newIndex = startIndex;

        for (int i = 0; i < tabs.size(); i++) {
            if (i == startIndex) continue;
            TabInfo other = tabs.get(i);
            double center = other.getCenterX();

            if (i < startIndex) {
                // If we are to the left of the original position, the mouse must cross the center to swap
                if (mouseX < center) {
                    newIndex = Math.min(newIndex, i);
                }
            } else {
                // If we are to the right of the original position, the mouse must cross the center to swap
                if (mouseX > center) {
                    newIndex = Math.max(newIndex, i);
                }
            }
        }

        return newIndex;
    }
}
