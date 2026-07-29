package com.tinsider.ui.components.terminal.dnd;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

public class TabGhost {
    private final ImageView imageView;
    private final Pane overlayPane;
    private final double dragOffsetX;
    private final double dragOffsetY;

    public TabGhost(Node tabNode, Pane overlayPane, double mousePressX, double mousePressY) {
        this.overlayPane = overlayPane;

        // Take snapshot of the actual tab node
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage snapshot = tabNode.snapshot(params, null);

        this.imageView = new ImageView(snapshot);
        this.imageView.setMouseTransparent(true);
        this.imageView.setOpacity(0.85);

        // Add a premium drop shadow
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.color(0, 0, 0, 0.25));
        shadow.setRadius(8);
        shadow.setOffsetX(0);
        shadow.setOffsetY(4);
        this.imageView.setEffect(shadow);

        // Calculate offset from the top-left of the tab node to the mouse coordinates
        Bounds boundsInScene = tabNode.localToScene(tabNode.getBoundsInLocal());
        Bounds overlayBounds = overlayPane.localToScene(overlayPane.getBoundsInLocal());

        double tabOverlayX = boundsInScene.getMinX() - overlayBounds.getMinX();
        double tabOverlayY = boundsInScene.getMinY() - overlayBounds.getMinY();

        this.dragOffsetX = mousePressX - tabOverlayX;
        this.dragOffsetY = mousePressY - tabOverlayY;

        // Position it initially
        updatePosition(mousePressX, mousePressY);

        overlayPane.getChildren().add(imageView);
    }

    public void updatePosition(double mouseX, double mouseY) {
        double newX = mouseX - dragOffsetX;
        double newY = mouseY - dragOffsetY;

        // Clamp ghost position to the boundaries of the overlay pane so it never detaches
        double maxX = overlayPane.getWidth() - imageView.getImage().getWidth();
        double maxY = overlayPane.getHeight() - imageView.getImage().getHeight();

        newX = Math.max(0, Math.min(newX, maxX));
        newY = Math.max(0, Math.min(newY, maxY));

        imageView.setLayoutX(newX);
        imageView.setLayoutY(newY);
    }

    public double getLayoutX() {
        return imageView.getLayoutX();
    }

    public double getLayoutY() {
        return imageView.getLayoutY();
    }

    public double getWidth() {
        return imageView.getImage().getWidth();
    }

    public Node getImageView() {
        return imageView;
    }

    public void destroy() {
        overlayPane.getChildren().remove(imageView);
    }
}
