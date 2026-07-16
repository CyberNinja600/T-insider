package com.tinsider.ui.components.sidebar;

import com.tinsider.model.HostProfile;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class HostListCell extends ListCell<HostProfile> {
    @Override
    protected void updateItem(HostProfile item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            HBox card = new HBox(12);
            card.setPadding(new Insets(8, 10, 8, 10));
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("host-card");

            Label badge = new Label();
            String firstLetter = item.getName().isEmpty() ? "?" : item.getName().substring(0, 1).toUpperCase();
            badge.setText(firstLetter);
            badge.setAlignment(Pos.CENTER);
            badge.setPrefSize(32, 32);
            badge.setMinSize(32, 32);
            badge.setMaxSize(32, 32);
            badge.getStyleClass().add("host-badge");

            VBox details = new VBox(2);
            
            String pName = item.getName();
            if (pName.length() > 21) {
                pName = pName.substring(0, 19) + "...";
            }
            Label nameLabel = new Label(pName);
            nameLabel.getStyleClass().add("host-name");

            String subText = item.getUsername() + "@" + item.getIp();
            if (item.getUrl() != null && !item.getUrl().isEmpty()) {
                subText = item.getUsername() + "@" + item.getUrl();
            }
            if (subText.length() > 26) {
                subText = subText.substring(0, 24) + "...";
            }
            Label userLabel = new Label(subText);
            userLabel.getStyleClass().add("host-username");

            details.getChildren().addAll(nameLabel, userLabel);
            card.getChildren().addAll(badge, details);

            setGraphic(card);
            setText(null);
        }
    }
}
