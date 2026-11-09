package com.tinsider.ui.dashboard;

import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import com.tinsider.storage.StorageManager;
import com.tinsider.ui.dialogs.AlertHelper;
import com.tinsider.ui.dialogs.SnippetDialog;
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
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dashboard view for managing Code Snippets and Snippet Packages.
 */
public class SnippetsDashboardView extends VBox {

    private final ObservableList<Snippet> snippets;
    private final ObservableList<SnippetPackage> packages;

    private final TextField snippetsSearchField;
    private final VBox snippetsDashboardContainer;
    private final ObjectProperty<Snippet> selectedSnippet = new SimpleObjectProperty<>(null);

    public SnippetsDashboardView(ObservableList<Snippet> snippets, ObservableList<SnippetPackage> packages) {
        super(15);
        this.snippets = snippets;
        this.packages = packages;

        setPadding(new Insets(25));
        getStyleClass().add("dashboard-view");

        Label snippetsTitle = new Label("Code Snippets");
        snippetsTitle.getStyleClass().add("dashboard-title");
        snippetsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        snippetsSearchField = new TextField();
        snippetsSearchField.setPromptText("🔍 Search snippets by title/code or packages by name...");
        snippetsSearchField.getStyleClass().add("text-field");
        snippetsSearchField.textProperty().addListener((obs, oldVal, newVal)    -> rebuild());

        snippetsDashboardContainer = new VBox(20);
        snippetsDashboardContainer.setPadding(new Insets(10));
        ScrollPane snippetsGridScrollPane = new ScrollPane(snippetsDashboardContainer);
        snippetsGridScrollPane.setFitToWidth(true);
        snippetsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        snippetsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        snippetsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(snippetsGridScrollPane, Priority.ALWAYS);

        HBox snippetButtons = new HBox(10);
        Button addSnippetBtn = new Button("Add Snippet");
        Label addSnippetIcon = new Label("\uEA60");
        addSnippetIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addSnippetIcon.getStyleClass().add("button-icon");
        addSnippetBtn.setGraphic(addSnippetIcon);

        Button addPackageBtn = new Button("Add Package");
        Label addPackageIcon = new Label("\uEA60");
        addPackageIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addPackageIcon.getStyleClass().add("button-icon");
        addPackageBtn.setGraphic(addPackageIcon);

        Button deletePackageBtn = new Button("Delete Package");
        Label deletePackageIcon = new Label("\uEA81");
        deletePackageIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deletePackageIcon.getStyleClass().add("button-icon");
        deletePackageBtn.setGraphic(deletePackageIcon);

        snippetButtons.getChildren().addAll(addSnippetBtn, addPackageBtn, deletePackageBtn);
        snippetButtons.setAlignment(Pos.CENTER_LEFT);

        addSnippetBtn.setOnAction(e -> showAddSnippetDialog(null));

        addPackageBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add Package");
            dialog.setContentText("Package Name:");
            Optional<String> result = AlertHelper.showModal(dialog);
            result.ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    SnippetPackage pkg = new SnippetPackage(name.trim());
                    packages.add(pkg);
                    StorageManager.savePackages(new ArrayList<>(packages));
                    rebuild();
                }
            });
        });

        deletePackageBtn.setOnAction(e -> {
            if (packages.isEmpty()) {
                AlertHelper.showErrorAlert("No Packages", "There are no packages to delete.");
                return;
            }
            ChoiceDialog<SnippetPackage> dialog = new ChoiceDialog<>(packages.get(0), packages);
            dialog.setTitle("Delete Package");
            dialog.setContentText("Package:");
            Optional<SnippetPackage> result = AlertHelper.showModal(dialog);
            result.ifPresent(pkg -> {
                packages.remove(pkg);
                for (Snippet s : snippets) {
                    if (pkg.getId().equals(s.getPackageId())) {
                        s.setPackageId(null);
                    }
                }
                StorageManager.saveSnippets(new ArrayList<>(snippets));
                StorageManager.savePackages(new ArrayList<>(packages));
                rebuild();
            });
        });

        snippets.addListener((ListChangeListener<Snippet>) c -> rebuild());
        packages.addListener((ListChangeListener<SnippetPackage>) c -> rebuild());

        getChildren().addAll(snippetsTitle, snippetsSearchField, snippetsGridScrollPane, snippetButtons);
        rebuild();
    }

    public void rebuild() {
        if (snippetsDashboardContainer == null) return;
        snippetsDashboardContainer.getChildren().clear();
        String query = snippetsSearchField != null ? snippetsSearchField.getText().toLowerCase().trim() : "";

        // 1. Render Packages on top
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
                VBox packageSection = new VBox(10);
                packageSection.getStyleClass().add("dashboard-package-section");

                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);
                Label folderIcon = new Label("\uEA83");
                folderIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 16px; ");
                folderIcon.getStyleClass().add("global-text");
                Label title = new Label(pkg.getName());
                title.getStyleClass().add("dashboard-package-title");
                header.getChildren().addAll(folderIcon, title);

                FlowPane grid = new FlowPane(15, 15);
                grid.setPadding(new Insets(10, 0, 0, 0));

                for (Snippet item : matchingSnippetsInPkg) {
                    Node card = createDashboardSnippetCard(item);
                    grid.getChildren().add(card);
                }

                if (matchingSnippetsInPkg.isEmpty()) {
                    Label emptyLabel = new Label("No snippets in this package");
                    emptyLabel.setStyle(" -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 5px 0 0 5px;");
                    emptyLabel.getStyleClass().add("global-text");
                    grid.getChildren().add(emptyLabel);
                }

                packageSection.getChildren().addAll(header, grid);
                snippetsDashboardContainer.getChildren().add(packageSection);
            }
        }

        // 2. Render Snippets without package below
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
            VBox standaloneSection = new VBox(10);
            standaloneSection.getStyleClass().add("dashboard-package-section");

            HBox standaloneHeader = new HBox(10);
            standaloneHeader.setAlignment(Pos.CENTER_LEFT);
            Label standaloneIcon = new Label("\uEAE9");
            standaloneIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 16px; ");
            standaloneIcon.getStyleClass().add("global-text");
            Label standaloneTitle = new Label("Standalone Snippets (No Package)");
            standaloneTitle.getStyleClass().add("dashboard-standalone-title");
            standaloneHeader.getChildren().addAll(standaloneIcon, standaloneTitle);

            FlowPane standaloneGrid = new FlowPane(15, 15);
            standaloneGrid.setPadding(new Insets(10, 0, 0, 0));

            for (Snippet item : matchingStandalone) {
                Node card = createDashboardSnippetCard(item);
                standaloneGrid.getChildren().add(card);
            }

            if (matchingStandalone.isEmpty()) {
                Label emptyLabel = new Label("No standalone snippets");
                emptyLabel.setStyle(" -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 5px 0 0 5px;");
                emptyLabel.getStyleClass().add("global-text");
                standaloneGrid.getChildren().add(emptyLabel);
            }

            standaloneSection.getChildren().addAll(standaloneHeader, standaloneGrid);
            snippetsDashboardContainer.getChildren().add(standaloneSection);
        }
    }

    private Node createDashboardSnippetCard(Snippet item) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("snippet-card");
        card.setPrefWidth(220);
        card.setMaxWidth(220);
        card.setCursor(javafx.scene.Cursor.HAND);

        Label badge = new Label(">_");
        badge.setAlignment(Pos.CENTER);
        badge.setPrefSize(32, 32);
        badge.setMinSize(32, 32);
        badge.setMaxSize(32, 32);
        badge.getStyleClass().add("snippet-badge");

        VBox details = new VBox(2);
        Label titleLabel = new Label(item.getTitle());
        titleLabel.getStyleClass().add("snippet-title");

        String preview = item.getCode().trim().replace("\n", " ");
        if (preview.length() > 22) {
            preview = preview.substring(0, 20) + "...";
        }
        Label previewLabel = new Label(preview);
        previewLabel.getStyleClass().add("snippet-preview");

        details.getChildren().addAll(titleLabel, previewLabel);
        card.getChildren().addAll(badge, details);

        // Double click to edit
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                showAddSnippetDialog(item);
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
            Platform.runLater(() -> showAddSnippetDialog(item));
        });

        MenuItem deleteItem = new MenuItem("Delete");
        Label deleteIcon = new Label("\uEA81");
        deleteIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deleteIcon.getStyleClass().add("button-icon");
        deleteItem.setGraphic(deleteIcon);
        deleteItem.setOnAction(e -> {
            contextMenu.hide();
            Platform.runLater(() -> handleDeleteSnippet(item));
        });

        contextMenu.getItems().addAll(editItem, deleteItem);
        card.getProperties().put("contextMenu", contextMenu);

        contextMenu.setOnHidden(e -> card.getStyleClass().remove("selected"));

        card.setOnContextMenuRequested(event -> {
            clearDashboardSnippetSelection();
            card.getStyleClass().add("selected");
            contextMenu.show(card, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        return card;
    }

    private void clearDashboardSnippetSelection() {
        for (Node section : snippetsDashboardContainer.getChildren()) {
            if (section instanceof VBox) {
                for (Node child : ((VBox) section).getChildren()) {
                    if (child instanceof FlowPane) {
                        for (Node node : ((FlowPane) child).getChildren()) {
                            if (node instanceof HBox) {
                                node.getStyleClass().remove("selected");
                            }
                        }
                    }
                }
            }
        }
    }

    public void showAddSnippetDialog(Snippet existing) {
        SnippetDialog dialog = new SnippetDialog(existing, packages);
        Optional<Snippet> result = AlertHelper.showModal(dialog);
        result.ifPresent(snippet -> {
            if (existing != null) {
                ActivityLogger.logUserAction("EDIT_SNIPPET", "User edited snippet: " + snippet.getTitle());
                snippets.remove(existing);
            } else {
                ActivityLogger.logUserAction("ADD_SNIPPET", "User added snippet: " + snippet.getTitle());
            }
            snippets.add(snippet);
            StorageManager.saveSnippets(new ArrayList<>(snippets));
        });
    }

    protected void handleDeleteSnippet(Snippet item) {
        if (item == null) return;
        String snippetTitle = (item.getTitle() != null && !item.getTitle().trim().isEmpty())
                ? item.getTitle()
                : "this snippet";
        boolean confirmed = AlertHelper.showConfirmationAlert(
                "Delete Snippet",
                "Are you sure you want to delete '" + snippetTitle + "'?"
        );
        if (confirmed) {
            snippets.remove(item);
            StorageManager.saveSnippets(new ArrayList<>(snippets));
            ActivityLogger.logUserAction("DELETE_SNIPPET", "User deleted snippet: " + item.getTitle());
            if (selectedSnippet.get() == item) {
                selectedSnippet.set(null);
            }
        }
    }

    public ObjectProperty<Snippet> selectedSnippetProperty() {
        return selectedSnippet;
    }
}
