package com.tinsider.ui.components.terminal.dnd;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TabAnimationManager {
    private final Map<Node, TranslateTransition> activeTransitions = new HashMap<>();

    /**
     * Animates the surrounding tabs to slide left or right to create a gap/placeholder.
     *
     * @param tabs         The original static tab info list
     * @param startIndex   The starting/original index of the dragged tab
     * @param currentIndex The currently calculated insertion index
     */
    public void animateTabShift(List<DropCalculator.TabInfo> tabs, int startIndex, int currentIndex) {
        double shiftWidth = tabs.get(startIndex).width;

        for (int i = 0; i < tabs.size(); i++) {
            if (i == startIndex) continue;

            DropCalculator.TabInfo info = tabs.get(i);
            double targetTranslateX = 0;

            if (startIndex < currentIndex) {
                if (i > startIndex && i <= currentIndex) {
                    targetTranslateX = -shiftWidth;
                }
            } else if (startIndex > currentIndex) {
                if (i >= currentIndex && i < startIndex) {
                    targetTranslateX = shiftWidth;
                }
            }

            animateNode(info.node, targetTranslateX);
        }
    }

    private void animateNode(Node node, double targetX) {
        TranslateTransition existing = activeTransitions.get(node);
        if (existing != null) {
            if (existing.getToX() == targetX) {
                return; // Already animating to this target
            }
            existing.stop();
        }

        if (node.getTranslateX() == targetX) {
            activeTransitions.remove(node);
            return; // Already at the target
        }

        TranslateTransition transition = new TranslateTransition(Duration.millis(150), node);
        transition.setToX(targetX);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        transition.setOnFinished(e -> activeTransitions.remove(node));
        activeTransitions.put(node, transition);
        transition.play();
    }

    /**
     * Stops all active animations.
     */
    public void stopAll() {
        for (TranslateTransition transition : activeTransitions.values()) {
            transition.stop();
        }
        activeTransitions.clear();
    }
}
