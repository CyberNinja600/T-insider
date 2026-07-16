package com.tinsider.ui.components.sidebar;

import com.tinsider.model.Snippet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class SnippetListCell extends ListCell<Snippet> {
    @Override
    protected void updateItem(Snippet item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            HBox card = new HBox(12);
            card.setPadding(new Insets(8, 10, 8, 10));
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("snippet-card");

            Label badge = new Label(">_");
            badge.setAlignment(Pos.CENTER);
            badge.setPrefSize(32, 32);
            badge.setMinSize(32, 32);
            badge.setMaxSize(32, 32);
            badge.getStyleClass().add("snippet-badge");

            VBox details = new VBox(2);
            String title = item.getTitle() != null ? item.getTitle() : "";
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("snippet-title");

            String code = item.getCode() != null ? item.getCode() : "";
            String preview = code.trim().replace("\n", " ");
            if (preview.length() > 22) {
                preview = preview.substring(0, 20) + "...";
            }
            Label previewLabel = new Label(preview);
            previewLabel.getStyleClass().add("snippet-preview");

            details.getChildren().addAll(titleLabel, previewLabel);
            card.getChildren().addAll(badge, details);

            setGraphic(card);
            setText(null);
        }
    }
}
