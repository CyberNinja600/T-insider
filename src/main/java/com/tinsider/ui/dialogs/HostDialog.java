package com.tinsider.ui.dialogs;

import com.tinsider.model.HostProfile;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.util.UUID;

public class HostDialog extends Dialog<HostProfile> {
    
    public HostDialog(HostProfile existing) {
        setTitle(existing == null ? "Add Host Profile" : "Edit Host Profile");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        Button saveBtn = (Button) getDialogPane().lookupButton(saveButtonType);
        if (saveBtn != null) {
            Label saveIcon = new Label("\uEB4B");
            saveIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 14px;");
            saveIcon.getStyleClass().add("button-icon");
            saveBtn.setGraphic(saveIcon);
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("My Server");
        TextField ipField = new TextField();
        ipField.setPromptText("192.168.1.100");
        TextField portField = new TextField("22");
        portField.setPromptText("22");
        TextField userField = new TextField();
        userField.setPromptText("username");
        PasswordField passField = new PasswordField();
        passField.setPromptText("password");

        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton ipPortRadio = new RadioButton("IP / Port");
        ipPortRadio.setToggleGroup(typeGroup);
        RadioButton urlRadio = new RadioButton("Connection URL");
        urlRadio.setToggleGroup(typeGroup);

        TextField urlField = new TextField();
        urlField.setPromptText("user@domain.com:port");

        // Listeners to enable/disable fields
        ipPortRadio.setOnAction(e -> {
            ipField.setDisable(false);
            portField.setDisable(false);
            userField.setDisable(false);
            urlField.setDisable(true);
        });

        urlRadio.setOnAction(e -> {
            ipField.setDisable(true);
            portField.setDisable(true);
            userField.setDisable(false);
            urlField.setDisable(false);
        });

        if (existing != null) {
            nameField.setText(existing.getName());
            passField.setText(existing.getPassword());
            userField.setText(existing.getUsername());
            if (existing.getUrl() != null && !existing.getUrl().isEmpty()) {
                urlRadio.setSelected(true);
                urlField.setText(existing.getUrl());
                ipField.setDisable(true);
                portField.setDisable(true);
                userField.setDisable(false);
                urlField.setDisable(false);
            } else {
                ipPortRadio.setSelected(true);
                ipField.setText(existing.getIp());
                portField.setText(String.valueOf(existing.getPort()));
                urlField.setDisable(true);
            }
        } else {
            ipPortRadio.setSelected(true);
            urlField.setDisable(true);
        }

        grid.add(new Label("Profile Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        
        HBox typeBox = new HBox(10, ipPortRadio, urlRadio);
        grid.add(new Label("Connection Type:"), 0, 1);
        grid.add(typeBox, 1, 1);

        grid.add(new Label("IP Address:"), 0, 2);
        grid.add(ipField, 1, 2);
        grid.add(new Label("Port:"), 0, 3);
        grid.add(portField, 1, 3);
        grid.add(new Label("Username:"), 0, 4);
        grid.add(userField, 1, 4);
        
        grid.add(new Label("Connection URL:"), 0, 5);
        grid.add(urlField, 1, 5);
        
        grid.add(new Label("Password:"), 0, 6);
        grid.add(passField, 1, 6);

        getDialogPane().setContent(grid);
        AlertHelper.themeDialog(this);

        Platform.runLater(nameField::requestFocus);

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String id = existing == null ? UUID.randomUUID().toString() : existing.getId();
                HostProfile profile = new HostProfile();
                profile.setId(id);
                profile.setName(nameField.getText().trim());
                profile.setUsername(userField.getText().trim());
                profile.setPassword(passField.getText());

                if (urlRadio.isSelected()) {
                    profile.setUrl(urlField.getText().trim());
                } else {
                    profile.setUrl(null);
                    profile.setIp(ipField.getText().trim());
                    int port = 22;
                    try {
                        port = Integer.parseInt(portField.getText().trim());
                    } catch (NumberFormatException ignored) {}
                    profile.setPort(port);
                }
                return profile;
            }
            return null;
        });
    }
}
