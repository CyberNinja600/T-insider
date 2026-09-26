package com.tinsider.ui.dashboard;

import com.tinsider.model.HighlightPackage;
import com.tinsider.model.WordHighlight;
import com.tinsider.storage.StorageManager;
import com.tinsider.ui.dialogs.AlertHelper;
import com.tinsider.ui.dialogs.HighlightDialog;
import com.tinsider.util.ActivityLogger;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dashboard view for Word Highlights management (packages, rules, colors, and regex patterns).
 */
public class HighlightsDashboardView extends VBox {

    private final ObservableList<WordHighlight> highlights;
    private final ObservableList<HighlightPackage> highlightPackages;
    private final Runnable onHighlightsChangedCallback;

    private final TextField highlightsSearchField;
    private final VBox highlightsDashboardContainer;
    private final ObjectProperty<WordHighlight> selectedHighlight = new SimpleObjectProperty<>(null);

    public HighlightsDashboardView(
        ObservableList<WordHighlight> highlights,
        ObservableList<HighlightPackage> highlightPackages,
        Runnable onHighlightsChangedCallback
    ) {
        super(15);
        this.highlights = highlights;
        this.highlightPackages = highlightPackages;
        this.onHighlightsChangedCallback = onHighlightsChangedCallback;

        setPadding(new Insets(25));
        getStyleClass().add("dashboard-view");

        Label highlightsTitle = new Label("Word Highlights Management");
        highlightsTitle.getStyleClass().add("dashboard-title");
        highlightsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        highlightsSearchField = new TextField();
        highlightsSearchField.setPromptText("🔍 Search highlights by word or packages by name...");
        highlightsSearchField.getStyleClass().add("text-field");
        highlightsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuild());

        highlightsDashboardContainer = new VBox(20);
        highlightsDashboardContainer.setPadding(new Insets(10));
        ScrollPane highlightsGridScrollPane = new ScrollPane(highlightsDashboardContainer);
        highlightsGridScrollPane.setFitToWidth(true);
        highlightsGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        highlightsGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        highlightsGridScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(highlightsGridScrollPane, Priority.ALWAYS);

        HBox highlightButtons = new HBox(10);
        Button addHighlightBtn = new Button("Add Highlight");
        Label addHighlightIcon = new Label("\uEA60");
        addHighlightIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addHighlightIcon.getStyleClass().add("button-icon");
        addHighlightBtn.setGraphic(addHighlightIcon);

        Button editHighlightBtn = new Button("Edit");
        Label editHighlightIcon = new Label("\uEA73");
        editHighlightIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        editHighlightIcon.getStyleClass().add("button-icon");
        editHighlightBtn.setGraphic(editHighlightIcon);

        Button deleteHighlightBtn = new Button("Delete");
        Label deleteHighlightIcon = new Label("\uEA81");
        deleteHighlightIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deleteHighlightIcon.getStyleClass().add("button-icon");
        deleteHighlightBtn.setGraphic(deleteHighlightIcon);

        Button addHighlightPackageBtn = new Button("Add Package");
        Label addHighlightPackageIcon = new Label("\uEA60");
        addHighlightPackageIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        addHighlightPackageIcon.getStyleClass().add("button-icon");
        addHighlightPackageBtn.setGraphic(addHighlightPackageIcon);

        Button deleteHighlightPackageBtn = new Button("Delete Package");
        Label deleteHighlightPackageIcon = new Label("\uEA81");
        deleteHighlightPackageIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deleteHighlightPackageIcon.getStyleClass().add("button-icon");
        deleteHighlightPackageBtn.setGraphic(deleteHighlightPackageIcon);

        highlightButtons.getChildren().addAll(addHighlightBtn, editHighlightBtn, deleteHighlightBtn, addHighlightPackageBtn, deleteHighlightPackageBtn);
        highlightButtons.setAlignment(Pos.CENTER_LEFT);

        editHighlightBtn.disableProperty().bind(selectedHighlight.isNull());
        deleteHighlightBtn.disableProperty().bind(selectedHighlight.isNull());

        addHighlightBtn.setOnAction(e -> showAddHighlightDialog(null));
        editHighlightBtn.setOnAction(e -> {
            WordHighlight selected = selectedHighlight.get();
            if (selected != null) {
                if (StorageManager.isDefaultHighlight(selected.getId())) {
                    AlertHelper.showErrorAlert("Built-in Highlight", "Cannot edit built-in highlights.");
                    return;
                }
                showAddHighlightDialog(selected);
            }
        });
        deleteHighlightBtn.setOnAction(e -> {
            WordHighlight selected = selectedHighlight.get();
            if (selected != null) {
                if (StorageManager.isDefaultHighlight(selected.getId())) {
                    AlertHelper.showErrorAlert("Built-in Highlight", "Cannot delete built-in highlights.");
                    return;
                }
                highlights.remove(selected);
                StorageManager.saveHighlights(new ArrayList<>(highlights));
                selectedHighlight.set(null);
                notifyHighlightsChanged();
            }
        });

        addHighlightPackageBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New Highlight Package");
            dialog.setContentText("Package Name:");
            AlertHelper.themeDialog(dialog);
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                if (name.length() > 12) {
                    AlertHelper.showErrorAlert("Package name too long", "Package name cannot be more than 12 letters.");
                    return;
                }
                String trimmedName = name.trim();
                if (!trimmedName.isEmpty()) {
                    boolean duplicate = false;
                    if (highlightPackages != null) {
                        for (HighlightPackage pkg : highlightPackages) {
                            if (pkg.getName() != null && pkg.getName().trim().equalsIgnoreCase(trimmedName)) {
                                duplicate = true;
                                break;
                            }
                        }
                    }
                    if (duplicate) {
                        AlertHelper.showErrorAlert("Duplicate Package", "A highlight package with this name already exists.");
                        return;
                    }
                    HighlightPackage pkg = new HighlightPackage(trimmedName);
                    if (highlightPackages != null) {
                        highlightPackages.add(pkg);
                        StorageManager.saveHighlightPackages(new ArrayList<>(highlightPackages));
                    }
                }
            });
        });

        deleteHighlightPackageBtn.setOnAction(e -> {
            List<HighlightPackage> deletablePackages = highlightPackages.stream().filter(pkg -> pkg.getName() == null || !pkg.getName().equalsIgnoreCase("default")).toList();

            if (deletablePackages.isEmpty()) {
                AlertHelper.showErrorAlert("No Packages", "There are no packages available to delete.");
                return;
            }

            ChoiceDialog<HighlightPackage> dialog = new ChoiceDialog<>(deletablePackages.getFirst(), deletablePackages);
            dialog.setTitle("Delete Highlight Package");
            dialog.setContentText("Package:");
            AlertHelper.themeDialog(dialog);
            Optional<HighlightPackage> result = dialog.showAndWait();
            result.ifPresent(pkg -> {
                highlightPackages.remove(pkg);
                for (WordHighlight wh : highlights) {
                    if (pkg.getId().equals(wh.getPackageId())) {
                        wh.setPackageId(null);
                    }
                }
                StorageManager.saveHighlights(new ArrayList<>(highlights));
                StorageManager.saveHighlightPackages(new ArrayList<>(highlightPackages));
                notifyHighlightsChanged();
            });
        });

        highlights.addListener((ListChangeListener<WordHighlight>) c -> rebuild());
        highlightPackages.addListener((ListChangeListener<HighlightPackage>) c -> rebuild());

        getChildren().addAll(highlightsTitle, highlightsSearchField, highlightsGridScrollPane, highlightButtons);
        rebuild();
    }

    public void rebuild() {
        if (highlightsDashboardContainer == null) return;
        highlightsDashboardContainer.getChildren().clear();
        String query = highlightsSearchField != null ? highlightsSearchField.getText().toLowerCase().trim() : "";

        // 1. Render Packages on top
        for (HighlightPackage pkg : highlightPackages) {
            boolean pkgMatches = pkg.getName() != null && pkg.getName().toLowerCase().contains(query);

            List<WordHighlight> matchingHighlightsInPkg = new ArrayList<>();
            for (WordHighlight item : highlights) {
                if (pkg.getId().equals(item.getPackageId())) {
                    boolean highlightMatches = (item.getWord() != null && item.getWord().toLowerCase().contains(query));
                    if (query.isEmpty() || highlightMatches || pkgMatches) {
                        matchingHighlightsInPkg.add(item);
                    }
                }
            }

            if (query.isEmpty() || pkgMatches || !matchingHighlightsInPkg.isEmpty()) {
                VBox packageSection = new VBox(10);
                packageSection.getStyleClass().add("dashboard-package-section");

                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);
                Label folderIcon = new Label("\uEA83");
                folderIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 16px; -fx-text-fill: #94a3b8;");
                Label title = new Label(pkg.getName());
                title.getStyleClass().add("dashboard-package-title");
                header.getChildren().addAll(folderIcon, title);

                FlowPane grid = new FlowPane(15, 15);
                grid.setPadding(new Insets(10, 0, 0, 0));

                for (WordHighlight item : matchingHighlightsInPkg) {
                    Node card = createDashboardHighlightCard(item);
                    grid.getChildren().add(card);
                }

                if (matchingHighlightsInPkg.isEmpty()) {
                    Label emptyLabel = new Label("No highlights in this package");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 5px 0 0 5px;");
                    grid.getChildren().add(emptyLabel);
                }

                packageSection.getChildren().addAll(header, grid);
                highlightsDashboardContainer.getChildren().add(packageSection);
            }
        }

        // 2. Render Highlights without package below
        List<WordHighlight> matchingStandalone = new ArrayList<>();
        for (WordHighlight item : highlights) {
            if (item.getPackageId() == null || item.getPackageId().isEmpty()) {
                boolean highlightMatches = item.getWord().toLowerCase().contains(query);
                if (query.isEmpty() || highlightMatches) {
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
            standaloneIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 16px; -fx-text-fill: #94a3b8;");
            Label standaloneTitle = new Label("Standalone Highlights (No Package)");
            standaloneTitle.getStyleClass().add("dashboard-standalone-title");
            standaloneHeader.getChildren().addAll(standaloneIcon, standaloneTitle);

            FlowPane standaloneGrid = new FlowPane(15, 15);
            standaloneGrid.setPadding(new Insets(10, 0, 0, 0));

            for (WordHighlight item : matchingStandalone) {
                Node card = createDashboardHighlightCard(item);
                standaloneGrid.getChildren().add(card);
            }

            if (matchingStandalone.isEmpty()) {
                Label emptyLabel = new Label("No standalone highlights");
                emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 5px 0 0 5px;");
                standaloneGrid.getChildren().add(emptyLabel);
            }

            standaloneSection.getChildren().addAll(standaloneHeader, standaloneGrid);
            highlightsDashboardContainer.getChildren().add(standaloneSection);
        }
    }

    private Node createDashboardHighlightCard(WordHighlight wh) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("highlight-card");
        card.setPrefWidth(220);
        card.setMaxWidth(220);
        card.setCursor(javafx.scene.Cursor.HAND);

        Label colorBox = new Label();
        colorBox.setPrefSize(20, 20);
        colorBox.setMinSize(20, 20);
        colorBox.setMaxSize(20, 20);
        colorBox.setStyle("-fx-background-color: " + wh.getColorHex() + "; -fx-background-radius: 10px;");

        VBox details = new VBox(2);
        String displayName = wh.getName() != null && !wh.getName().isEmpty() ? wh.getName() : wh.getWord();
        Label wordLabel = new Label(displayName);
        wordLabel.getStyleClass().add("host-name");
        wordLabel.setStyle("-fx-font-weight: bold;");

        Label patternLabel = null;
        if (wh.getName() != null && !wh.getName().isEmpty()) {
            patternLabel = new Label(wh.getWord());
            patternLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8; -fx-font-style: italic;");
        }

        Label colorLabel = new Label(wh.getColorHex());
        colorLabel.getStyleClass().add("host-username");

        if (patternLabel != null) {
            details.getChildren().addAll(wordLabel, patternLabel, colorLabel);
        } else {
            details.getChildren().addAll(wordLabel, colorLabel);
        }

        card.getChildren().addAll(colorBox, details);

        card.setOnMouseClicked(event -> {
            selectedHighlight.set(wh);
            clearDashboardHighlightSelection();
            card.getStyleClass().add("selected");
        });

        if (selectedHighlight.get() == wh) {
            card.getStyleClass().add("selected");
        }

        return card;
    }

    private void clearDashboardHighlightSelection() {
        if (highlightsDashboardContainer == null) return;
        for (Node section : highlightsDashboardContainer.getChildren()) {
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

    private void showAddHighlightDialog(WordHighlight existing) {
        HighlightDialog dialog = new HighlightDialog(existing, highlightPackages);
        Optional<WordHighlight> result = dialog.showAndWait();
        result.ifPresent(wh -> {
            if (existing != null) {
                ActivityLogger.logUserAction("EDIT_HIGHLIGHT", "User edited highlight: " + wh.getWord());
                highlights.remove(existing);
                selectedHighlight.set(wh);
            } else {
                ActivityLogger.logUserAction("ADD_HIGHLIGHT", "User added highlight: " + wh.getWord());
            }
            highlights.add(wh);
            StorageManager.saveHighlights(new ArrayList<>(highlights));
            rebuild();
            notifyHighlightsChanged();
        });
    }

    private void notifyHighlightsChanged() {
        if (onHighlightsChangedCallback != null) {
            onHighlightsChangedCallback.run();
        }
    }

    public ObjectProperty<WordHighlight> selectedHighlightProperty() {
        return selectedHighlight;
    }
}
