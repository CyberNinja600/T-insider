package com.tinsider.ui.components.sidebar;

import com.tinsider.model.HostProfile;
import com.tinsider.model.Snippet;
import com.tinsider.storage.StorageManager;
import com.tinsider.ui.dialogs.HostDialog;
import com.tinsider.ui.dialogs.SnippetDialog;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;

public class SidebarPane extends VBox {
    private final ListView<HostProfile> hostListView;
    private final ListView<Snippet> snippetListView;

    public SidebarPane(
            ObservableList<HostProfile> hosts, 
            ObservableList<Snippet> snippets,
            Consumer<HostProfile> onHostConnect,
            Consumer<Snippet> onSnippetExecute) {
        
        super(10);
        setPadding(new Insets(10));
        setPrefWidth(260);
        getStyleClass().add("sidebar");

        Label appTitle = new Label("T-Insider");
        appTitle.getStyleClass().add("app-title");

        // --- Hosts section ---
        Label hostsHeader = new Label("Hosts");
        hostsHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        hostListView = new ListView<>(hosts);
        hostListView.setPrefHeight(250);
        hostListView.setCellFactory(lv -> new HostListCell());

        hostListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                HostProfile selected = hostListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onHostConnect.accept(selected);
                }
            }
        });

        HBox hostButtons = new HBox(5);
        Button addHostBtn = new Button("Add");
        Button editHostBtn = new Button("Edit");
        Button deleteHostBtn = new Button("Delete");
        hostButtons.getChildren().addAll(addHostBtn, editHostBtn, deleteHostBtn);
        hostButtons.setAlignment(Pos.CENTER);

        editHostBtn.disableProperty().bind(hostListView.getSelectionModel().selectedItemProperty().isNull());
        deleteHostBtn.disableProperty().bind(hostListView.getSelectionModel().selectedItemProperty().isNull());

        addHostBtn.setOnAction(e -> showAddHostDialog(hosts, null));
        editHostBtn.setOnAction(e -> {
            HostProfile selected = hostListView.getSelectionModel().getSelectedItem();
            if (selected != null) showAddHostDialog(hosts, selected);
        });
        deleteHostBtn.setOnAction(e -> {
            HostProfile selected = hostListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                hosts.remove(selected);
                StorageManager.saveHosts(new ArrayList<>(hosts));
            }
        });

        // --- Snippets section ---
        Label snippetsHeader = new Label("Snippets");
        snippetsHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        snippetListView = new ListView<>(snippets);
        snippetListView.setPrefHeight(250);
        snippetListView.setCellFactory(lv -> new SnippetListCell());

        snippetListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Snippet selected = snippetListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onSnippetExecute.accept(selected);
                }
            }
        });

        HBox snippetButtons = new HBox(5);
        Button addSnippetBtn = new Button("Add");
        Button editSnippetBtn = new Button("Edit");
        Button deleteSnippetBtn = new Button("Delete");
        snippetButtons.getChildren().addAll(addSnippetBtn, editSnippetBtn, deleteSnippetBtn);
        snippetButtons.setAlignment(Pos.CENTER);

        editSnippetBtn.disableProperty().bind(snippetListView.getSelectionModel().selectedItemProperty().isNull());
        deleteSnippetBtn.disableProperty().bind(snippetListView.getSelectionModel().selectedItemProperty().isNull());

        addSnippetBtn.setOnAction(e -> showAddSnippetDialog(snippets, null));
        editSnippetBtn.setOnAction(e -> {
            Snippet selected = snippetListView.getSelectionModel().getSelectedItem();
            if (selected != null) showAddSnippetDialog(snippets, selected);
        });
        deleteSnippetBtn.setOnAction(e -> {
            Snippet selected = snippetListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                snippets.remove(selected);
                StorageManager.saveSnippets(new ArrayList<>(snippets));
            }
        });

        getChildren().addAll(
                appTitle, 
                hostsHeader, hostListView, hostButtons, 
                snippetsHeader, snippetListView, snippetButtons
        );
    }

    private void showAddHostDialog(ObservableList<HostProfile> hosts, HostProfile existing) {
        HostDialog dialog = new HostDialog(existing);
        Optional<HostProfile> result = dialog.showAndWait();
        result.ifPresent(profile -> {
            if (existing != null) {
                hosts.remove(existing);
            }
            hosts.add(profile);
            StorageManager.saveHosts(new ArrayList<>(hosts));
        });
    }

    private void showAddSnippetDialog(ObservableList<Snippet> snippets, Snippet existing) {
        SnippetDialog dialog = new SnippetDialog(existing, StorageManager.loadPackages());
        Optional<Snippet> result = dialog.showAndWait();
        result.ifPresent(snippet -> {
            if (existing != null) {
                snippets.remove(existing);
            }
            snippets.add(snippet);
            StorageManager.saveSnippets(new ArrayList<>(snippets));
        });
    }
}
