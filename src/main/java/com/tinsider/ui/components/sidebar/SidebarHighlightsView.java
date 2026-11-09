package com.tinsider.ui.components.sidebar;

import com.tinsider.config.TerminalTheme;
import com.tinsider.model.HighlightPackage;
import com.tinsider.model.WordHighlight;
import com.tinsider.storage.StorageManager;
import com.tinsider.ui.components.terminal.split.TerminalPane;
import com.tinsider.ui.components.terminal.split.TerminalSplitPane;
import com.tinsider.ui.dialogs.AlertHelper;
import com.tinsider.ui.dialogs.HighlightDialog;
import com.tinsider.ui.theme.ThemeApplicator;
import com.tinsider.util.ActivityLogger;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sidebar view for Word Highlight management and per-terminal toggles in Terminal Mode.
 * Enables live toggling of highlights, case sensitivity, and package activation on the active terminal pane.
 */
public class SidebarHighlightsView extends VBox {

    private final ObservableList<WordHighlight> highlights;
    private final ObservableList<HighlightPackage> highlightPackages;
    private final Supplier<TerminalSplitPane> activeSplitPaneSupplier;
    private final Consumer<WordHighlight> onEditHighlight;
    private final Consumer<WordHighlight> onDeleteHighlight;
    private final Set<String> expandedPackageIds = new HashSet<>();

    private final TextField sidebarHighlightsSearchField;
    private final VBox terminalHighlightsContainer;

    public SidebarHighlightsView(
        ObservableList<WordHighlight> highlights,
        ObservableList<HighlightPackage> highlightPackages,
        Supplier<TerminalSplitPane> activeSplitPaneSupplier
    ) {
        this(highlights, highlightPackages, activeSplitPaneSupplier, null, null);
    }

    public SidebarHighlightsView(
        ObservableList<WordHighlight> highlights,
        ObservableList<HighlightPackage> highlightPackages,
        Supplier<TerminalSplitPane> activeSplitPaneSupplier,
        Consumer<WordHighlight> onEditHighlight,
        Consumer<WordHighlight> onDeleteHighlight
    ) {
        super(10);
        this.highlights = highlights;
        this.highlightPackages = highlightPackages;
        this.activeSplitPaneSupplier = activeSplitPaneSupplier;
        this.onEditHighlight = onEditHighlight;
        this.onDeleteHighlight = onDeleteHighlight;

        VBox.setVgrow(this, Priority.ALWAYS);

        sidebarHighlightsSearchField = new TextField();
        sidebarHighlightsSearchField.setPromptText("🔍 Search highlights or packages...");
        sidebarHighlightsSearchField.getStyleClass().add("text-field");
        sidebarHighlightsSearchField.textProperty().addListener((obs, oldVal, newVal) -> rebuild());

        terminalHighlightsContainer = new VBox(8);
        terminalHighlightsContainer.setStyle("-fx-background-color: transparent;");
        terminalHighlightsContainer.setPadding(new Insets(5, 5, 5, 0));

        ScrollPane highlightsScrollPane = new ScrollPane(terminalHighlightsContainer);
        highlightsScrollPane.setFitToWidth(true);
        highlightsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        highlightsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        highlightsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(highlightsScrollPane, Priority.ALWAYS);

        getChildren().addAll(sidebarHighlightsSearchField, highlightsScrollPane);

        highlights.addListener((ListChangeListener<WordHighlight>) change -> rebuild());
        highlightPackages.addListener((ListChangeListener<HighlightPackage>) change -> rebuild());
    }

    public void rebuild() {
        if (terminalHighlightsContainer == null) return;
        terminalHighlightsContainer.getChildren().clear();

        TerminalSplitPane splitPane = (activeSplitPaneSupplier != null) ? activeSplitPaneSupplier.get() : null;
        if (splitPane == null) return;

        TerminalPane activePane = splitPane.getActiveTerminalPane();
        if (activePane == null) return;

        Set<String> activeIds = activePane.getActiveHighlightIds();
        String query = sidebarHighlightsSearchField != null ? sidebarHighlightsSearchField.getText().toLowerCase().trim() : "";

        // 1. Grouped by Package
        for (HighlightPackage pkg : highlightPackages) {
            boolean pkgMatches = pkg.getName() != null && pkg.getName().toLowerCase().contains(query);

            List<WordHighlight> packageHighlights = new ArrayList<>();
            for (WordHighlight wh : highlights) {
                if (pkg.getId().equals(wh.getPackageId())) {
                    boolean whMatches = wh.getWord() != null && wh.getWord().toLowerCase().contains(query);
                    if (query.isEmpty() || pkgMatches || whMatches) {
                        packageHighlights.add(wh);
                    }
                }
            }

            if (!packageHighlights.isEmpty()) {
                TitledPane pane = new TitledPane();
                pane.getStyleClass().add("package-titled-pane");
                pane.setExpanded(!query.isEmpty() || expandedPackageIds.contains(pkg.getId()));

                pane.expandedProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        expandedPackageIds.add(pkg.getId());
                    } else {
                        expandedPackageIds.remove(pkg.getId());
                    }
                });

                // Calculate package active state
                int activeCount = 0;
                for (WordHighlight wh : packageHighlights) {
                    if (activeIds.contains(wh.getId())) {
                        activeCount++;
                    }
                }
                boolean pkgAnyActive = (activeCount > 0);

                if (pkgAnyActive) {
                    pane.getStyleClass().add("active-package");
                } else {
                    pane.getStyleClass().add("inactive-package");
                }

                // Build custom header with title and eye icon for package activation
                HBox header = new HBox(8);
                header.setAlignment(Pos.CENTER_LEFT);
                header.setMaxWidth(Double.MAX_VALUE);
                header.prefWidthProperty().bind(pane.widthProperty().subtract(45));

                Label folderIcon = new Label("\uEA83");
                folderIcon.getStyleClass().add("package-folder-icon");
                folderIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 14px;");

                Label titleLabel = new Label(pkg.getName());
                titleLabel.getStyleClass().add("package-title-label");
                titleLabel.setStyle("-fx-font-weight: bold;");
                titleLabel.setMinWidth(0);
                titleLabel.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(titleLabel, Priority.ALWAYS);

                Label pkgEyeIcon = new Label(pkgAnyActive ? "\uEB79" : "\uEB7A");
                pkgEyeIcon.getStyleClass().addAll("package-eye-icon", pkgAnyActive ? "active" : "inactive");
                pkgEyeIcon.setTooltip(new Tooltip(pkgAnyActive ? "Deactivate Package" : "Activate Package"));

                pkgEyeIcon.setOnMousePressed(ev -> ev.consume());
                pkgEyeIcon.setOnMouseReleased(ev -> ev.consume());
                pkgEyeIcon.setOnMouseClicked(ev -> {
                    ev.consume();
                    boolean nextState = !pkgAnyActive;
                    for (WordHighlight wh : packageHighlights) {
                        activePane.toggleHighlight(wh.getId(), nextState);
                    }
                    rebuild();
                });

                header.getChildren().addAll(folderIcon, titleLabel, pkgEyeIcon);
                pane.setGraphic(header);

                VBox packageContent = new VBox(8);
                packageContent.setPadding(new Insets(10));
                packageContent.setStyle("-fx-background-color: transparent;");

                for (WordHighlight wh : packageHighlights) {
                    Node card = createSidebarHighlightCardNode(wh, activePane, activeIds, splitPane);
                    packageContent.getChildren().add(card);
                }

                pane.setContent(packageContent);
                terminalHighlightsContainer.getChildren().add(pane);
            }
        }

        // 2. Standalone highlights
        List<WordHighlight> standaloneHighlights = new ArrayList<>();
        for (WordHighlight wh : highlights) {
            if (wh.getPackageId() == null || wh.getPackageId().isEmpty()) {
                boolean whMatches = wh.getWord() != null && wh.getWord().toLowerCase().contains(query);
                if (query.isEmpty() || whMatches) {
                    standaloneHighlights.add(wh);
                }
            }
        }

        if (!standaloneHighlights.isEmpty()) {
            TitledPane pane = new TitledPane();
            pane.getStyleClass().add("package-titled-pane");
            pane.setExpanded(!query.isEmpty() || expandedPackageIds.contains("standalone"));

            pane.expandedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    expandedPackageIds.add("standalone");
                } else {
                    expandedPackageIds.remove("standalone");
                }
            });

            int activeCount = 0;
            for (WordHighlight wh : standaloneHighlights) {
                if (activeIds.contains(wh.getId())) {
                    activeCount++;
                }
            }
            boolean pkgAnyActive = (activeCount > 0);

            if (pkgAnyActive) {
                pane.getStyleClass().add("active-package");
            } else {
                pane.getStyleClass().add("inactive-package");
            }

            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setMaxWidth(Double.MAX_VALUE);
            header.prefWidthProperty().bind(pane.widthProperty().subtract(45));

            Label standaloneIcon = new Label("\uEAE9");
            standaloneIcon.getStyleClass().add("package-folder-icon");
            standaloneIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 14px;");

            Label titleLabel = new Label("Highlights");
            titleLabel.getStyleClass().add("package-title-label");
            titleLabel.setStyle("-fx-font-weight: bold;");
            titleLabel.setMinWidth(0);
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            Label pkgEyeIcon = new Label(pkgAnyActive ? "\uEB79" : "\uEB7A");
            pkgEyeIcon.getStyleClass().addAll("package-eye-icon", pkgAnyActive ? "active" : "inactive");
            pkgEyeIcon.setTooltip(new Tooltip(pkgAnyActive ? "Deactivate Standalone" : "Activate Standalone"));

            pkgEyeIcon.setOnMousePressed(Event::consume);
            pkgEyeIcon.setOnMouseReleased(Event::consume);
            pkgEyeIcon.setOnMouseClicked(ev -> {
                ev.consume();
                boolean nextState = !pkgAnyActive;
                for (WordHighlight wh : standaloneHighlights) {
                    activePane.toggleHighlight(wh.getId(), nextState);
                }
                rebuild();
            });

            header.getChildren().addAll(standaloneIcon, titleLabel, pkgEyeIcon);
            pane.setGraphic(header);

            VBox standaloneContent = new VBox(8);
            standaloneContent.setPadding(new Insets(10));
            standaloneContent.setStyle("-fx-background-color: transparent;");

            for (WordHighlight wh : standaloneHighlights) {
                Node card = createSidebarHighlightCardNode(wh, activePane, activeIds, splitPane);
                standaloneContent.getChildren().add(card);
            }

            pane.setContent(standaloneContent);
            terminalHighlightsContainer.getChildren().add(pane);
        }
    }

    private Node createSidebarHighlightCardNode(
        WordHighlight wh,
        TerminalPane activePane,
        Set<String> activeIds,
        TerminalSplitPane splitPane
    ) {
        boolean active = activeIds.contains(wh.getId());

        HBox card = new HBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("highlight-sidebar-card");
        if (active) {
            card.getStyleClass().add("active");
        }

        Label indicator = new Label();
        indicator.setPrefSize(8, 8);
        indicator.setMinSize(8, 8);
        indicator.setMaxSize(8, 8);
        indicator.setStyle("-fx-background-color: " + wh.getColorHex() + "; -fx-background-radius: 4px;");

        String displayName = wh.getName() != null && !wh.getName().isEmpty() ? wh.getName() : wh.getWord();
        Label wordLabel = new Label(displayName);
        wordLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Tooltip patternTooltip = new Tooltip(wh.getWord());
        wordLabel.setTooltip(patternTooltip);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label eyeIcon = new Label(active ? "\uEB79" : "\uEB7A");
        eyeIcon.getStyleClass().add("toggle-icon");
        eyeIcon.setMinWidth(14);
        eyeIcon.setPrefWidth(14);
        eyeIcon.setMaxWidth(14);

        Button caseIcon = new Button("\uEAB1");
        caseIcon.getStyleClass().add("snippet-hover-btn");
        caseIcon.setMinWidth(22);
        caseIcon.setPrefWidth(22);
        caseIcon.setMaxWidth(22);
        caseIcon.setMinHeight(22);
        caseIcon.setPrefHeight(22);
        caseIcon.setMaxHeight(22);

        Runnable updateCaseIconStyle = () -> {
            boolean isInsensitive = activePane.getCaseInsensitiveHighlightIds().contains(wh.getId());
            TerminalTheme currentTheme = splitPane.getCurrentTheme();
            com.jediterm.core.Color accentColor = currentTheme.getCursorColor();
            com.jediterm.core.Color btnBg = currentTheme.getColor("controls.snippet.hover.btn.background", currentTheme.getBackgroundColor());
            com.jediterm.core.Color btnBorder = currentTheme.getColor("controls.snippet.hover.btn.border", currentTheme.getSelectionColor());
            com.jediterm.core.Color btnHoverBg = currentTheme.getColor("controls.snippet.hover.btn.hover.background", currentTheme.getSelectionColor());
            com.jediterm.core.Color btnHoverBorder = currentTheme.getColor("controls.snippet.hover.btn.hover.border", currentTheme.getCursorColor());

            String hexAccent = ThemeApplicator.toHex(accentColor);
            String hexBtnBg = ThemeApplicator.toHex(btnBg);
            String hexBtnBorder = ThemeApplicator.toHex(btnBorder);
            String hexBtnHoverBg = ThemeApplicator.toHex(btnHoverBg);
            String hexBtnHoverBorder = ThemeApplicator.toHex(btnHoverBorder);

            if (isInsensitive) {
                caseIcon.setStyle(
                    "-fx-font-family: 'codicon'; " +
                    "-fx-font-size: 11px; " +
                    "-fx-text-fill: " + hexAccent + "; " +
                    "-fx-background-color: " + hexBtnHoverBg + "; " +
                    "-fx-border-color: " + hexBtnHoverBorder + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-background-radius: 3px; " +
                    "-fx-border-radius: 3px; " +
                    "-fx-opacity: 1.0; " +
                    "-fx-cursor: hand;"
                );
            } else {
                caseIcon.setStyle(
                    "-fx-font-family: 'codicon'; " +
                    "-fx-font-size: 11px; " +
                    "-fx-text-fill: #94a3b8; " +
                    "-fx-background-color: " + hexBtnBg + "; " +
                    "-fx-border-color: " + hexBtnBorder + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-background-radius: 3px; " +
                    "-fx-border-radius: 3px; " +
                    "-fx-opacity: 0.5; " +
                    "-fx-cursor: hand;"
                );
            }
        };
        updateCaseIconStyle.run();

        caseIcon.setTooltip(new Tooltip("Toggle Case Insensitive Match"));
        caseIcon.setOnAction(e -> e.consume());
        caseIcon.setOnMouseClicked(e -> {
            e.consume();
            boolean nextState = !activePane.getCaseInsensitiveHighlightIds().contains(wh.getId());
            activePane.toggleCaseInsensitive(wh.getId(), nextState);

            wh.setCaseInsensitive(nextState);
            StorageManager.saveHighlights(new ArrayList<>(highlights));

            updateCaseIconStyle.run();
        });

        card.getChildren().addAll(indicator, wordLabel, spacer, caseIcon, eyeIcon);

        // Only PRIMARY (left) click toggles highlight activation
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                boolean currentlyActive = activePane.getActiveHighlightIds().contains(wh.getId());
                boolean nextState = !currentlyActive;
                activePane.toggleHighlight(wh.getId(), nextState);

                if (nextState) {
                    if (!card.getStyleClass().contains("active")) {
                        card.getStyleClass().add("active");
                    }
                    eyeIcon.setText("\uEB79");
                } else {
                    card.getStyleClass().remove("active");
                    eyeIcon.setText("\uEB7A");
                }
            }
        });

        // Right-click ContextMenu for Editing and Deleting Highlights
        boolean isDefault = StorageManager.isDefaultHighlight(wh.getId());
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.addEventHandler(ActionEvent.ACTION, e -> contextMenu.hide());

        MenuItem editItem = new MenuItem("Edit");
        Label editIcon = new Label("\uEA73");
        editIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        editItem.setGraphic(editIcon);
        if (isDefault) {
            editItem.setDisable(true);
        }
        editItem.setOnAction(e -> {
            contextMenu.hide();
            if (isDefault) {
                return;
            }
            Platform.runLater(() -> editHighlight(wh));
        });

        MenuItem deleteItem = new MenuItem("Delete");
        Label deleteIcon = new Label("\uEA81");
        deleteIcon.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 12px;");
        deleteItem.setGraphic(deleteIcon);
        if (isDefault) {
            deleteItem.setDisable(true);
        }
        deleteItem.setOnAction(e -> {
            contextMenu.hide();
            if (isDefault) {
                return;
            }
            Platform.runLater(() -> deleteHighlight(wh));
        });

        contextMenu.getItems().addAll(editItem, deleteItem);
        card.getProperties().put("contextMenu", contextMenu);

        contextMenu.setOnHidden(e -> card.getStyleClass().remove("selected"));

        card.setOnContextMenuRequested(event -> {
            card.getStyleClass().add("selected");
            contextMenu.show(card, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        return card;
    }

    private void editHighlight(WordHighlight wh) {
        if (wh != null && StorageManager.isDefaultHighlight(wh.getId())) {
            return;
        }
        if (onEditHighlight != null) {
            onEditHighlight.accept(wh);
        } else {
            HighlightDialog dialog = new HighlightDialog(wh, highlightPackages);
            Optional<WordHighlight> result = AlertHelper.showModal(dialog);
            result.ifPresent(updated -> {
                ActivityLogger.logUserAction("EDIT_HIGHLIGHT", "User edited highlight: " + updated.getWord());
                highlights.remove(wh);
                highlights.add(updated);
                StorageManager.saveHighlights(new ArrayList<>(highlights));
            });
        }
    }

    private void deleteHighlight(WordHighlight wh) {
        if (wh != null && StorageManager.isDefaultHighlight(wh.getId())) {
            return;
        }
        if (onDeleteHighlight != null) {
            onDeleteHighlight.accept(wh);
        } else {
            String displayName = wh.getName() != null && !wh.getName().trim().isEmpty()
                    ? wh.getName()
                    : (wh.getWord() != null ? wh.getWord() : "this highlight");
            boolean confirmed = AlertHelper.showConfirmationAlert(
                    "Delete Highlight",
                    "Are you sure you want to delete '" + displayName + "'?"
            );
            if (confirmed) {
                highlights.remove(wh);
                StorageManager.saveHighlights(new ArrayList<>(highlights));
                ActivityLogger.logUserAction("DELETE_HIGHLIGHT", "User deleted highlight: " + wh.getWord());
            }
        }
    }
}
