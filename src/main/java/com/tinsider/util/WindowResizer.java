package com.tinsider.util;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class WindowResizer {
    private static final int BORDER_WIDTH = 6;
    private static double startX = 0;
    private static double startY = 0;
    private static double startStageX = 0;
    private static double startStageY = 0;
    private static double startStageWidth = 0;
    private static double startStageHeight = 0;
    private static Cursor currentCursor = Cursor.DEFAULT;

    public static void makeResizable(Stage stage, Scene scene) {
        scene.setOnMouseMoved(event -> {
            if (stage.isMaximized()) {
                scene.setCursor(Cursor.DEFAULT);
                return;
            }

            double mouseX = event.getSceneX();
            double mouseY = event.getSceneY();
            double width = scene.getWidth();
            double height = scene.getHeight();

            Cursor cursor = Cursor.DEFAULT;
            boolean right = mouseX >= width - BORDER_WIDTH;
            boolean bottom = mouseY >= height - BORDER_WIDTH;
            boolean left = mouseX <= BORDER_WIDTH;
            boolean top = mouseY <= BORDER_WIDTH;

            if (right && bottom) {
                cursor = Cursor.SE_RESIZE;
            } else if (left && bottom) {
                cursor = Cursor.SW_RESIZE;
            } else if (right && top) {
                cursor = Cursor.NE_RESIZE;
            } else if (left && top) {
                cursor = Cursor.NW_RESIZE;
            } else if (right) {
                cursor = Cursor.E_RESIZE;
            } else if (left) {
                cursor = Cursor.W_RESIZE;
            } else if (bottom) {
                cursor = Cursor.S_RESIZE;
            } else if (top) {
                cursor = Cursor.N_RESIZE;
            }

            currentCursor = cursor;
            scene.setCursor(cursor);
        });

        scene.setOnMousePressed(event -> {
            startX = event.getScreenX();
            startY = event.getScreenY();
            startStageX = stage.getX();
            startStageY = stage.getY();
            startStageWidth = stage.getWidth();
            startStageHeight = stage.getHeight();
        });

        scene.setOnMouseDragged(event -> {
            if (currentCursor == Cursor.DEFAULT || stage.isMaximized()) return;

            double deltaX = event.getScreenX() - startX;
            double deltaY = event.getScreenY() - startY;

            if (currentCursor == Cursor.E_RESIZE || currentCursor == Cursor.SE_RESIZE || currentCursor == Cursor.NE_RESIZE) {
                stage.setWidth(Math.max(800, startStageWidth + deltaX));
            }
            if (currentCursor == Cursor.S_RESIZE || currentCursor == Cursor.SE_RESIZE || currentCursor == Cursor.SW_RESIZE) {
                stage.setHeight(Math.max(500, startStageHeight + deltaY));
            }
            if (currentCursor == Cursor.W_RESIZE || currentCursor == Cursor.SW_RESIZE || currentCursor == Cursor.NW_RESIZE) {
                double newWidth = Math.max(800, startStageWidth - deltaX);
                if (newWidth > 800) {
                    stage.setX(startStageX + deltaX);
                    stage.setWidth(newWidth);
                }
            }
            if (currentCursor == Cursor.N_RESIZE || currentCursor == Cursor.NE_RESIZE || currentCursor == Cursor.NW_RESIZE) {
                double newHeight = Math.max(500, startStageHeight - deltaY);
                if (newHeight > 500) {
                    stage.setY(startStageY + deltaY);
                    stage.setHeight(newHeight);
                }
            }
        });
    }
}
