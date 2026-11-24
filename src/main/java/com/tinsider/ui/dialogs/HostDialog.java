package com.tinsider.ui.dialogs;

import com.tinsider.model.HostProfile;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.util.UUID;

public class HostDialog extends Dialog<HostProfile> {

    private final TextField nameField;
    private final TextField ipField;
    private final TextField portField;
    private final TextField userField;
    private final PasswordField passField;
    private final RadioButton ipPortRadio;
    private final RadioButton urlRadio;
    private final TextField urlField;
    private final Button saveBtn;

    public HostDialog(HostProfile existing) {
        setTitle(existing == null ? "Add Host Profile" : "Edit Host Profile");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        saveBtn = (Button) getDialogPane().lookupButton(saveButtonType);
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

        nameField = new TextField();
        nameField.setPromptText("My Server");
        ipField = new TextField();
        ipField.setPromptText("192.168.1.100");
        portField = new TextField("22");
        portField.setPromptText("22");
        userField = new TextField();
        userField.setPromptText("username");
        passField = new PasswordField();
        passField.setPromptText("password");

        ToggleGroup typeGroup = new ToggleGroup();
        ipPortRadio = new RadioButton("IP / Port");
        ipPortRadio.setToggleGroup(typeGroup);
        urlRadio = new RadioButton("Connection URL");
        urlRadio.setToggleGroup(typeGroup);

        urlField = new TextField();
        urlField.setPromptText("user@domain.com:port");

        // Listeners to enable/disable fields and clear errors on mode switch
        ipPortRadio.setOnAction(e -> {
            ipField.setDisable(false);
            portField.setDisable(false);
            userField.setDisable(false);
            urlField.setDisable(true);
            urlField.setStyle("");
            urlField.setPromptText("user@domain.com:port");
        });

        urlRadio.setOnAction(e -> {
            ipField.setDisable(true);
            portField.setDisable(true);
            userField.setDisable(false);
            urlField.setDisable(false);
            ipField.setStyle("");
            ipField.setPromptText("192.168.1.100");
            portField.setStyle("");
            portField.setPromptText("22");
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

        // Clear error styling when the user types in any field
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                nameField.setStyle("");
                nameField.setPromptText("My Server");
            }
        });
        ipField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                ipField.setStyle("");
                ipField.setPromptText("192.168.1.100");
            }
        });
        portField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isPortValid(newVal)) {
                portField.setStyle("");
                portField.setPromptText("22");
            }
        });
        userField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                userField.setStyle("");
                userField.setPromptText("username");
            }
        });
        urlField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                urlField.setStyle("");
                urlField.setPromptText("user@domain.com:port");
            }
        });

        // Intercept Save action: validate fields and display "Required" on empty fields
        if (saveBtn != null) {
            saveBtn.addEventFilter(ActionEvent.ACTION, event -> {
                if (!validateAndHighlight()) {
                    event.consume();
                }
            });
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
            if (dialogButton == saveButtonType && isInputValid()) {
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

    public boolean isInputValid() {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        if (name.isEmpty()) {
            return false;
        }

        String username = userField.getText() != null ? userField.getText().trim() : "";
        if (username.isEmpty()) {
            return false;
        }

        if (urlRadio.isSelected()) {
            String url = urlField.getText() != null ? urlField.getText().trim() : "";
            return !url.isEmpty();
        } else {
            String ip = ipField.getText() != null ? ipField.getText().trim() : "";
            return !ip.isEmpty() && isPortValid(portField.getText());
        }
    }

    public boolean validateAndHighlight() {
        boolean valid = true;
        TextField firstInvalid = null;

        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        if (name.isEmpty()) {
            nameField.setText("");
            nameField.setPromptText("Required");
            nameField.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
            valid = false;
            if (firstInvalid == null) firstInvalid = nameField;
        } else {
            nameField.setStyle("");
            nameField.setPromptText("My Server");
        }

        String username = userField.getText() != null ? userField.getText().trim() : "";
        if (username.isEmpty()) {
            userField.setText("");
            userField.setPromptText("Required");
            userField.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
            valid = false;
            if (firstInvalid == null) firstInvalid = userField;
        } else {
            userField.setStyle("");
            userField.setPromptText("username");
        }

        if (urlRadio.isSelected()) {
            String url = urlField.getText() != null ? urlField.getText().trim() : "";
            if (url.isEmpty()) {
                urlField.setText("");
                urlField.setPromptText("Required");
                urlField.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
                valid = false;
                if (firstInvalid == null) firstInvalid = urlField;
            } else {
                urlField.setStyle("");
                urlField.setPromptText("user@domain.com:port");
            }
        } else {
            String ip = ipField.getText() != null ? ipField.getText().trim() : "";
            if (ip.isEmpty()) {
                ipField.setText("");
                ipField.setPromptText("Required");
                ipField.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
                valid = false;
                if (firstInvalid == null) firstInvalid = ipField;
            } else {
                ipField.setStyle("");
                ipField.setPromptText("192.168.1.100");
            }

            if (!isPortValid(portField.getText())) {
                portField.setText("");
                portField.setPromptText("Required");
                portField.setStyle("-fx-border-color: #ff5555; -fx-prompt-text-fill: #ff5555;");
                valid = false;
                if (firstInvalid == null) firstInvalid = portField;
            } else {
                portField.setStyle("");
                portField.setPromptText("22");
            }
        }

        if (firstInvalid != null) {
            firstInvalid.requestFocus();
        }

        return valid;
    }

    private boolean isPortValid(String portText) {
        if (portText == null || portText.trim().isEmpty()) {
            return false;
        }
        try {
            int port = Integer.parseInt(portText.trim());
            return port > 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public TextField getNameField() {
        return nameField;
    }

    public TextField getIpField() {
        return ipField;
    }

    public TextField getPortField() {
        return portField;
    }

    public TextField getUserField() {
        return userField;
    }

    public PasswordField getPassField() {
        return passField;
    }

    public RadioButton getIpPortRadio() {
        return ipPortRadio;
    }

    public RadioButton getUrlRadio() {
        return urlRadio;
    }

    public TextField getUrlField() {
        return urlField;
    }

    public Button getSaveButton() {
        return saveBtn;
    }
}
