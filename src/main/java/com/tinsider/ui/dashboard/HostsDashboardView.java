package com.tinsider.ui.dashboard;

import com.tinsider.model.HostProfile;
import com.tinsider.storage.StorageManager;
import com.tinsider.ui.dialogs.AlertHelper;
import com.tinsider.ui.dialogs.HostDialog;
import com.tinsider.util.ActivityLogger;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Dashboard view for managing SSH host profiles.
 */
public class HostsDashboardView extends VBox {

    private final ObservableList<HostProfile> hosts;
    private final TextField hostsSearchField;
    private final FlowPane hostsGrid;
    private final ObjectProperty<HostProfile> selectedHost = new SimpleObjectProperty<>(null);

    public HostsDashboardView(ObservableList<HostProfile> hosts) {
        super(15);
        this.hosts = hosts;

        setPadding(new Insets(25));
        getStyleClass().add("dashboard-view");

        Label hostsTitle = new Label("Hosts Management");
        hostsTitle.getStyleClass().add("dashboard-title");
        hostsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        hostsSearchField = new TextField();
        hostsSearchField.setPromptText("🔍 Search hosts by name, IP, or username...");
        hostsSearchField.getStyleClass().add("text-field");
        hostsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuild());

        hostsGrid = new FlowPane(15, 15);
        hostsGrid.setPadding(new Insets(10));
        ScrollPane hostsGridScrollPane = new ScrollPane(hostsGrid);
        hostsGridScrollPane.setFitToWidth(true);
        hostsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        hostsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        hostsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(hostsGridScrollPane, Priority.ALWAYS);

        HBox hostButtons = new HBox(10);
        Button addHostBtn = new Button("Add");
        Label addHostIcon = new Label("\uEA60");
        addHostIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addHostIcon.getStyleClass().add("button-icon");
        addHostBtn.setGraphic(addHostIcon);

        hostButtons.getChildren().addAll(addHostBtn);
        hostButtons.setAlignment(Pos.CENTER_LEFT);

        addHostBtn.setOnAction(e -> showAddHostDialog(null));

        hosts.addListener((ListChangeListener<HostProfile>) c -> rebuild());

        getChildren().addAll(hostsTitle, hostsSearchField, hostsGridScrollPane, hostButtons);
        rebuild();
    }

    public void rebuild() {
        if (hostsGrid == null) return;
        hostsGrid.getChildren().clear();
        String query = hostsSearchField != null ? hostsSearchField.getText().toLowerCase().trim() : "";
        for (HostProfile item : hosts) {
            if (!query.isEmpty()) {
                boolean match = (item.getName() != null && item.getName().toLowerCase().contains(query)) ||
                                (item.getIp() != null && item.getIp().toLowerCase().contains(query)) ||
                                (item.getUsername() != null && item.getUsername().toLowerCase().contains(query)) ||
                                (item.getUrl() != null && item.getUrl().toLowerCase().contains(query));
                if (!match) continue;
            }
            HBox card = new HBox(12);
            card.setPadding(new Insets(12));
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("host-card");
            card.setPrefWidth(220);
            card.setMaxWidth(220);
            card.setCursor(javafx.scene.Cursor.HAND);

            Label badge = new Label();
            String firstLetter = (item.getName() == null || item.getName().isEmpty()) ? "?" : item.getName().substring(0, 1).toUpperCase();
            badge.setText(firstLetter);
            badge.setAlignment(Pos.CENTER);
            badge.setPrefSize(32, 32);
            badge.setMinSize(32, 32);
            badge.setMaxSize(32, 32);
            badge.getStyleClass().add("host-badge");

            VBox details = new VBox(2);
            String pName = item.getName() != null ? item.getName() : "";
            if (pName.length() > 21) {
                pName = pName.substring(0, 19) + "...";
            }
            Label nameLabel = new Label(pName);
            nameLabel.getStyleClass().add("host-name");

            String username = item.getUsername() != null ? item.getUsername() : "";
            String ip = item.getIp() != null ? item.getIp() : "";
            String subText = username + "@" + ip;
            if (item.getUrl() != null && !item.getUrl().isEmpty()) {
                subText = username + "@" + item.getUrl();
            }
            if (subText.length() > 26) {
                subText = subText.substring(0, 24) + "...";
            }
            Label userLabel = new Label(subText);
            userLabel.getStyleClass().add("host-username");

            details.getChildren().addAll(nameLabel, userLabel);
            card.getChildren().addAll(badge, details);

            // Double click to edit
            card.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    showAddHostDialog(item);
                }
            });

            // Right-click context menu
            ContextMenu contextMenu = new ContextMenu();
            contextMenu.addEventHandler(ActionEvent.ACTION, e -> contextMenu.hide());

            MenuItem editItem = new MenuItem("Edit");
            Label editIcon = new Label("\uEA73");
            editIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
            editIcon.getStyleClass().add("button-icon");
            editItem.setGraphic(editIcon);
            editItem.setOnAction(e -> {
                contextMenu.hide();
                Platform.runLater(() -> showAddHostDialog(item));
            });

            MenuItem deleteItem = new MenuItem("Delete");
            Label deleteIcon = new Label("\uEA81");
            deleteIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
            deleteIcon.getStyleClass().add("button-icon");
            deleteItem.setGraphic(deleteIcon);
            deleteItem.setOnAction(e -> {
                contextMenu.hide();
                Platform.runLater(() -> handleDeleteHost(item));
            });

            contextMenu.getItems().addAll(editItem, deleteItem);
            card.getProperties().put("contextMenu", contextMenu);

            contextMenu.setOnHidden(e -> card.getStyleClass().remove("selected"));

            card.setOnContextMenuRequested(event -> {
                for (Node node : hostsGrid.getChildren()) {
                    node.getStyleClass().remove("selected");
                }
                card.getStyleClass().add("selected");
                contextMenu.show(card, event.getScreenX(), event.getScreenY());
                event.consume();
            });

            hostsGrid.getChildren().add(card);
        }
    }

    public void showAddHostDialog(HostProfile existing) {
        HostDialog dialog = new HostDialog(existing);
        Optional<HostProfile> result = AlertHelper.showModal(dialog);
        result.ifPresent(profile -> {
            if (existing != null) {
                ActivityLogger.logUserAction("EDIT_HOST", "User edited host profile: " + profile.getName() + " (" + profile.getIp() + ")");
                hosts.remove(existing);
            } else {
                ActivityLogger.logUserAction("ADD_HOST", "User added host profile: " + profile.getName() + " (" + profile.getIp() + ")");
            }
            hosts.add(profile);
            StorageManager.saveHosts(new ArrayList<>(hosts));
        });
    }

    public void handleDeleteHost(HostProfile item) {
        if (item == null) return;
        String hostName = (item.getName() != null && !item.getName().trim().isEmpty())
                ? item.getName()
                : (item.getIp() != null ? item.getIp() : "this host");
        boolean confirmed = AlertHelper.showConfirmationAlert(
                "Delete Host",
                "Are you sure you want to delete '" + hostName + "'?"
        );
        if (confirmed) {
            hosts.remove(item);
            StorageManager.saveHosts(new ArrayList<>(hosts));
            ActivityLogger.logUserAction("DELETE_HOST", "User deleted host profile: " + item.getName() + " (" + item.getIp() + ")");
            if (selectedHost.get() == item) {
                selectedHost.set(null);
            }
        }
    }

    public ObjectProperty<HostProfile> selectedHostProperty() {
        return selectedHost;
    }
}

