package com.tinsider.ui.dashboard;

import com.tinsider.ui.components.terminal.TerminalWorkspace;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard view displaying all active terminal sessions.
 * Allows users to review open sessions or double-click to jump directly into a terminal tab.
 */
public class SessionsDashboardView extends VBox {

    private final TerminalWorkspace workspace;
    private final Runnable onOpenTerminalMode;

    private final Label noActiveSessionsLabel;
    private final FlowPane terminalsGrid;
    private final ScrollPane terminalsGridScrollPane;
    private final ObjectProperty<Tab> selectedSession = new SimpleObjectProperty<>(null);

    public SessionsDashboardView(TerminalWorkspace workspace, Runnable onOpenTerminalMode) {
        super(15);
        this.workspace = workspace;
        this.onOpenTerminalMode = onOpenTerminalMode;

        setPadding(new Insets(25));
        getStyleClass().add("dashboard-view");

        Label terminalsTitle = new Label("Active Sessions");
        terminalsTitle.getStyleClass().add("dashboard-title");
        terminalsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        noActiveSessionsLabel = new Label("No active terminals. Click a host to connect.");
        noActiveSessionsLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");

        terminalsGrid = new FlowPane(15, 15);
        terminalsGrid.setPadding(new Insets(10));
        terminalsGridScrollPane = new ScrollPane(terminalsGrid);
        terminalsGridScrollPane.setFitToWidth(true);
        terminalsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        terminalsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        terminalsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(terminalsGridScrollPane, Priority.ALWAYS);

        getChildren().addAll(terminalsTitle, noActiveSessionsLabel, terminalsGridScrollPane);

        rebuild();
    }

    public void rebuild() {
        Platform.runLater(() -> {
            terminalsGrid.getChildren().clear();
            List<Tab> terminalsOnly = new ArrayList<>();
            for (Tab t : workspace.getTabPane().getTabs()) {
                if (t != workspace.getAddTab()) {
                    terminalsOnly.add(t);
                }
            }

            if (terminalsOnly.isEmpty()) {
                noActiveSessionsLabel.setVisible(true);
                terminalsGridScrollPane.setVisible(false);
            } else {
                noActiveSessionsLabel.setVisible(false);
                terminalsGridScrollPane.setVisible(true);

                for (Tab tab : terminalsOnly) {
                    HBox card = new HBox(12);
                    card.setPadding(new Insets(12));
                    card.setAlignment(Pos.CENTER_LEFT);
                    card.getStyleClass().add("session-cell");
                    card.setPrefWidth(220);
                    card.setMaxWidth(220);
                    card.setCursor(javafx.scene.Cursor.HAND);

                    Label badge = new Label("S");
                    badge.setAlignment(Pos.CENTER);
                    badge.setPrefSize(32, 32);
                    badge.setMinSize(32, 32);
                    badge.setMaxSize(32, 32);
                    badge.getStyleClass().add("host-badge");

                    VBox details = new VBox(2);
                    Label titleLabel = new Label(tab.getText());
                    titleLabel.getStyleClass().add("host-name");

                    Label statusLabel = new Label("Active Session");
                    statusLabel.getStyleClass().add("host-username");

                    details.getChildren().addAll(titleLabel, statusLabel);
                    card.getChildren().addAll(badge, details);

                    card.setOnMouseClicked(event -> {
                        selectedSession.set(tab);
                        for (Node node : terminalsGrid.getChildren()) {
                            node.getStyleClass().remove("selected");
                            if (node == card) {
                                node.getStyleClass().add("selected");
                            }
                        }
                        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                            workspace.getTabPane().getSelectionModel().select(tab);
                            if (onOpenTerminalMode != null) {
                                onOpenTerminalMode.run();
                            }
                        }
                    });

                    if (selectedSession.get() == tab) {
                        card.getStyleClass().add("selected");
                    }

                    terminalsGrid.getChildren().add(card);
                }
            }
        });
    }

    public ObjectProperty<Tab> selectedSessionProperty() {
        return selectedSession;
    }
}
