package com.tinsider.ui.components.sidebar;

import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Sidebar view for Snippet execution in Terminal Mode.
 * Displays searchable packages and standalone snippets, with run/paste hover buttons.
 */
public class SidebarSnippetsView extends VBox {

    private final ObservableList<Snippet> snippets;
    private final ObservableList<SnippetPackage> packages;
    private final BiConsumer<Snippet, Boolean> onRunSnippet;
    private final Consumer<Snippet> onEditSnippet;
    private final Runnable onAddSnippet;

    private final TextField sidebarSearchField;
    private final VBox terminalSnippetsContainer;
    private Node currentlySelectedCardNode;

    public SidebarSnippetsView(
        ObservableList<Snippet> snippets,
        ObservableList<SnippetPackage> packages,
        BiConsumer<Snippet, Boolean> onRunSnippet,
        Consumer<Snippet> onEditSnippet,
        Runnable onAddSnippet
    ) {
        super(10);
        this.snippets = snippets;
        this.packages = packages;
        this.onRunSnippet = onRunSnippet;
        this.onEditSnippet = onEditSnippet;
        this.onAddSnippet = onAddSnippet;

        VBox.setVgrow(this, Priority.ALWAYS);

        sidebarSearchField = new TextField();
        sidebarSearchField.setPromptText("🔍 Search snippets or packages...");
        sidebarSearchField.getStyleClass().add("text-field");
        sidebarSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuild());

        Button addSnippetSidebarBtn = new Button("Add Snippet");
        Label addIcon = new Label("\uEA60");
        addIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addIcon.getStyleClass().add("button-icon");
        addSnippetSidebarBtn.setGraphic(addIcon);
        addSnippetSidebarBtn.getStyleClass().add("button");
        addSnippetSidebarBtn.setMaxWidth(Double.MAX_VALUE);
        addSnippetSidebarBtn.setOnAction(e -> {
            if (this.onAddSnippet != null) {
                this.onAddSnippet.run();
            }
        });

        terminalSnippetsContainer = new VBox(10);
        terminalSnippetsContainer.setStyle("-fx-background-color: transparent;");
        terminalSnippetsContainer.setPadding(new Insets(0, 5, 0, 0));

        ScrollPane sidebarScrollPane = new ScrollPane(terminalSnippetsContainer);
        sidebarScrollPane.setFitToWidth(true);
        sidebarScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(sidebarScrollPane, Priority.ALWAYS);

        getChildren().addAll(sidebarSearchField, addSnippetSidebarBtn, sidebarScrollPane);

        // Listen for data changes
        snippets.addListener((ListChangeListener<Snippet>) change -> rebuild());
        packages.addListener((ListChangeListener<SnippetPackage>) change -> rebuild());

        rebuild();
    }

    public void rebuild() {
        if (terminalSnippetsContainer == null) return;
        terminalSnippetsContainer.getChildren().clear();
        String query = sidebarSearchField != null ? sidebarSearchField.getText().toLowerCase().trim() : "";

        // 1. Grouped by Package
        for (SnippetPackage pkg : packages) {
            boolean pkgMatches = pkg.getName() != null && pkg.getName().toLowerCase().contains(query);

            List<Snippet> matchingSnippetsInPkg = new ArrayList<>();
            for (Snippet item : snippets) {
                if (pkg.getId().equals(item.getPackageId())) {
                    boolean snippetMatches = (item.getTitle() != null && item.getTitle().toLowerCase().contains(query)) ||
                                             (item.getCode() != null && item.getCode().toLowerCase().contains(query));
                    if (query.isEmpty() || snippetMatches || pkgMatches) {
                        matchingSnippetsInPkg.add(item);
                    }
                }
            }

            if (query.isEmpty() || pkgMatches || !matchingSnippetsInPkg.isEmpty()) {
                TitledPane pane = new TitledPane();
                pane.setText(pkg.getName());
                // Auto-expand packages when searching to display results instantly
                pane.setExpanded(!query.isEmpty());
                pane.getStyleClass().add("package-titled-pane");

                VBox packageContent = new VBox(8);
                packageContent.setPadding(new Insets(10));
                packageContent.setStyle("-fx-background-color: transparent;");

                for (Snippet item : matchingSnippetsInPkg) {
                    Node card = createSidebarSnippetCardNode(item);
                    packageContent.getChildren().add(card);
                }

                if (matchingSnippetsInPkg.isEmpty()) {
                    Label emptyLabel = new Label("No snippets inside");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px;");
                    packageContent.getChildren().add(emptyLabel);
                }

                pane.setContent(packageContent);
                terminalSnippetsContainer.getChildren().add(pane);
            }
        }

        // 2. Standalone snippets
        List<Snippet> matchingStandalone = new ArrayList<>();
        for (Snippet item : snippets) {
            if (item.getPackageId() == null || item.getPackageId().isEmpty()) {
                boolean snippetMatches = (item.getTitle() != null && item.getTitle().toLowerCase().contains(query)) ||
                                         (item.getCode() != null && item.getCode().toLowerCase().contains(query));
                if (query.isEmpty() || snippetMatches) {
                    matchingStandalone.add(item);
                }
            }
        }

        if (query.isEmpty() || !matchingStandalone.isEmpty()) {
            TitledPane pane = new TitledPane();
            pane.setText("Standalone Snippets");
            pane.setExpanded(true); // Default open but fully collapsible
            pane.getStyleClass().add("package-titled-pane");

            VBox standaloneContent = new VBox(8);
            standaloneContent.setPadding(new Insets(10));
            standaloneContent.setStyle("-fx-background-color: transparent;");

            for (Snippet item : matchingStandalone) {
                Node card = createSidebarSnippetCardNode(item);
                standaloneContent.getChildren().add(card);
            }

            if (matchingStandalone.isEmpty()) {
                Label emptyLabel = new Label("No standalone snippets");
                emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px;");
                standaloneContent.getChildren().add(emptyLabel);
            }

            pane.setContent(standaloneContent);
            terminalSnippetsContainer.getChildren().add(pane);
        }
    }

    private Node createSidebarSnippetCardNode(Snippet item) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(8, 10, 8, 10));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("snippet-card");
        card.setCursor(javafx.scene.Cursor.HAND);

        Label badge = new Label(">_");
        badge.setAlignment(Pos.CENTER);
        badge.setPrefSize(32, 32);
        badge.setMinSize(32, 32);
        badge.setMaxSize(32, 32);
        badge.getStyleClass().add("snippet-badge");

        VBox details = new VBox(2);
        VBox.setVgrow(details, Priority.ALWAYS);
        details.setMinWidth(0);
        HBox.setHgrow(details, Priority.ALWAYS);

        HBox titleRow = new HBox(5);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleRow, Priority.ALWAYS);
        titleRow.setMinHeight(22);
        titleRow.setPrefHeight(22);
        titleRow.setMaxHeight(22);

        Label titleLabel = new Label(item.getTitle());
        titleLabel.getStyleClass().add("snippet-title");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // Hover buttons
        HBox hoverButtons = new HBox(4);
        hoverButtons.setAlignment(Pos.CENTER_RIGHT);
        hoverButtons.setVisible(false);
        hoverButtons.setManaged(false);

        Button runBtn = new Button("\uEAD3");
        runBtn.getStyleClass().add("snippet-hover-btn");
        runBtn.setMinWidth(22);
        runBtn.setPrefWidth(22);
        runBtn.setMaxWidth(22);
        runBtn.setMinHeight(22);
        runBtn.setPrefHeight(22);
        runBtn.setMaxHeight(22);
        runBtn.setOnAction(e -> {
            e.consume();
            if (onRunSnippet != null) {
                onRunSnippet.accept(item, true);
            }
        });
        runBtn.setOnMouseClicked(e -> e.consume());

        Button pasteBtn = new Button("\uEC36");
        pasteBtn.getStyleClass().add("snippet-hover-btn");
        pasteBtn.setMinWidth(22);
        pasteBtn.setPrefWidth(22);
        pasteBtn.setMaxWidth(22);
        pasteBtn.setMinHeight(22);
        pasteBtn.setPrefHeight(22);
        pasteBtn.setMaxHeight(22);
        pasteBtn.setOnAction(e -> {
            e.consume();
            if (onRunSnippet != null) {
                onRunSnippet.accept(item, false);
            }
        });
        pasteBtn.setOnMouseClicked(e -> e.consume());

        hoverButtons.getChildren().addAll(runBtn, pasteBtn);
        titleRow.getChildren().addAll(titleLabel, hoverButtons);

        String preview = item.getCode().trim().replace("\n", " ");
        if (preview.length() > 22) {
            preview = preview.substring(0, 20) + "...";
        }
        Label previewLabel = new Label(preview);
        previewLabel.getStyleClass().add("snippet-preview");

        details.getChildren().addAll(titleRow, previewLabel);
        card.getChildren().addAll(badge, details);

        // Hover behaviors
        card.setOnMouseEntered(e -> {
            hoverButtons.setVisible(true);
            hoverButtons.setManaged(true);
        });
        card.setOnMouseExited(e -> {
            hoverButtons.setVisible(false);
            hoverButtons.setManaged(false);
        });

        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (event.getClickCount() == 2) {
                    if (onRunSnippet != null) {
                        onRunSnippet.accept(item, true);
                    }
                } else {
                    clearSidebarSelection();
                    currentlySelectedCardNode = card;
                    card.getStyleClass().add("selected");
                }
            }
        });

        // Right-click ContextMenu for Editing Snippets
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.addEventHandler(ActionEvent.ACTION, e -> contextMenu.hide());

        MenuItem editItem = new MenuItem("Edit");
        Label editIcon = new Label("\uEC68");
        editIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        editItem.setGraphic(editIcon);
        editItem.setOnAction(e -> {
            contextMenu.hide();
            if (onEditSnippet != null) {
                Platform.runLater(() -> onEditSnippet.accept(item));
            }
        });
        contextMenu.getItems().add(editItem);
        card.getProperties().put("contextMenu", contextMenu);

        card.setOnContextMenuRequested(event -> {
            clearSidebarSelection();
            currentlySelectedCardNode = card;
            card.getStyleClass().add("selected");
            contextMenu.show(card, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        return card;
    }

    private void clearSidebarSelection() {
        if (currentlySelectedCardNode != null) {
            currentlySelectedCardNode.getStyleClass().remove("selected");
        }
    }
}
