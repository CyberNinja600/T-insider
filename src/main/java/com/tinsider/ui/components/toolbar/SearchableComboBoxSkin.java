package com.tinsider.ui.components.toolbar;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.PopupControl;
import javafx.scene.control.OverrunStyle;
import javafx.stage.Window;
import javafx.util.StringConverter;

public class SearchableComboBoxSkin<T> extends SkinBase<SearchableComboBox<T>> {

    private final Label displayLabel;
    private final StackPane arrowButton;
    private final PopupControl popup;
    private final VBox popupRoot;
    private final TextField searchField;
    private final ListView<T> listView;
    private FilteredList<T> filteredList;

    public SearchableComboBoxSkin(SearchableComboBox<T> comboBox) {
        super(comboBox);

        // 1. Trigger button elements (matches standard ComboBox visual structure and styling)
        displayLabel = new Label();
        displayLabel.getStyleClass().addAll("list-cell", "combo-box-cell");
        displayLabel.setAlignment(Pos.CENTER_LEFT);
        displayLabel.setPadding(new Insets(4, 8, 4, 8));
        displayLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        arrowButton = new StackPane();
        arrowButton.getStyleClass().add("arrow-button");
        arrowButton.setAlignment(Pos.CENTER);
        arrowButton.setPadding(new Insets(0, 8, 0, 8));

        Label arrow = new Label("\uEAB4");
        arrow.getStyleClass().addAll("arrow", "codicon-arrow");
        arrow.setStyle("-fx-font-family: 'codicon'; -fx-font-size: 11px;");
        arrowButton.getChildren().add(arrow);

        getChildren().addAll(displayLabel, arrowButton);

        updateDisplayLabel();

        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateDisplayLabel());
        comboBox.promptTextProperty().addListener((obs, oldVal, newVal) -> updateDisplayLabel());
        comboBox.converterProperty().addListener((obs, oldVal, newVal) -> updateDisplayLabel());

        EventHandler<MouseEvent> clickHandler = e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (getSkinnable().isShowing()) {
                    getSkinnable().hide();
                } else {
                    getSkinnable().show();
                }
                e.consume();
            }
        };

        displayLabel.setOnMouseClicked(clickHandler);
        arrowButton.setOnMouseClicked(clickHandler);
        comboBox.setOnMouseClicked(clickHandler);

        comboBox.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.DOWN) {
                if (!getSkinnable().isShowing()) {
                    getSkinnable().show();
                    e.consume();
                }
            }
        });

        // 2. Searchable Popup Container
        popup = new PopupControl();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);

        popupRoot = new VBox();
        popupRoot.getStyleClass().addAll("searchable-combo-popup", "combo-box-popup", "th-sp-option-list-view");

        // 3. Search input header
        HBox searchContainer = new HBox();
        searchContainer.getStyleClass().add("search-input-container");
        searchContainer.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.getStyleClass().addAll("search-input", "text-field");
        searchField.setPromptText("Search");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchContainer.getChildren().add(searchField);

        // 4. Options List container
        ObservableList<T> masterItems = comboBox.getItems();
        filteredList = new FilteredList<>(masterItems != null ? masterItems : FXCollections.emptyObservableList(), p -> true);

        listView = new ListView<>(filteredList);
        listView.getStyleClass().addAll("option-list-container", "list-view");
        VBox.setVgrow(listView, Priority.ALWAYS);

        Label noMatchesLabel = new Label("No matches found");
        noMatchesLabel.getStyleClass().add("no-matches-label");
        listView.setPlaceholder(noMatchesLabel);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    StringConverter<T> converter = getSkinnable().getConverter();
                    setText(converter != null ? converter.toString(item) : item.toString());
                    if (!getStyleClass().contains("th-sp-option")) {
                        getStyleClass().add("th-sp-option");
                    }
                }
            }
        });

        popupRoot.getChildren().addAll(searchContainer, listView);

        // Listen for items change on parent ComboBox
        comboBox.itemsProperty().addListener((obs, oldItems, newItems) -> {
            filteredList = new FilteredList<>(newItems != null ? newItems : FXCollections.emptyObservableList(), p -> true);
            listView.setItems(filteredList);
        });

        // 5. Dynamic search filtering
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isBlank()) {
                filteredList.setPredicate(p -> true);
            } else {
                String[] words = newText.trim().toLowerCase().split("\\s+");
                filteredList.setPredicate(item -> {
                    if (item == null) return false;

                    if (item instanceof TopToolBar.DropdownItem dropItem) {
                        String label = dropItem.getLabel() != null ? dropItem.getLabel().toLowerCase() : "";
                        String hostInfo = "";
                        if (dropItem.isHost() && dropItem.getHostProfile() != null) {
                            String ip = dropItem.getHostProfile().getIp();
                            String user = dropItem.getHostProfile().getUsername();
                            String url = dropItem.getHostProfile().getUrl();
                            hostInfo = (ip != null ? ip.toLowerCase() : "") + " " +
                                       (user != null ? user.toLowerCase() : "") + " " +
                                       (url != null ? url.toLowerCase() : "");
                        }
                        String fullText = label + " " + hostInfo;
                        for (String word : words) {
                            if (!fullText.contains(word)) {
                                return false;
                            }
                        }
                        return true;
                    }

                    String text = item.toString().toLowerCase();
                    for (String word : words) {
                        if (!text.contains(word)) {
                            return false;
                        }
                    }
                    return true;
                });
            }
            if (!filteredList.isEmpty()) {
                listView.getSelectionModel().select(0);
            }
        });

        // 6. Keyboard navigation within searchField
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN) {
                int curr = listView.getSelectionModel().getSelectedIndex();
                if (curr < filteredList.size() - 1) {
                    listView.getSelectionModel().select(curr + 1);
                    listView.scrollTo(curr + 1);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                int curr = listView.getSelectionModel().getSelectedIndex();
                if (curr > 0) {
                    listView.getSelectionModel().select(curr - 1);
                    listView.scrollTo(curr - 1);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                T selected = listView.getSelectionModel().getSelectedItem();
                if (selected == null && !filteredList.isEmpty()) {
                    selected = filteredList.get(0);
                }
                if (selected != null) {
                    selectItemAndHide(selected);
                } else {
                    getSkinnable().hide();
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                getSkinnable().hide();
                e.consume();
            }
        });

        // Mouse click on listView option
        listView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                T selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectItemAndHide(selected);
                }
            }
        });

        // Keyboard on listView
        listView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                T selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectItemAndHide(selected);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                getSkinnable().hide();
                e.consume();
            }
        });

        // Sync popup showing state
        comboBox.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                showPopup();
            } else {
                hidePopup();
            }
        });

        popup.setOnHidden(e -> {
            searchField.clear();
            filteredList.setPredicate(p -> true);
            if (getSkinnable().isShowing()) {
                getSkinnable().hide();
            }
        });
    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        double arrowWidth = arrowButton.prefWidth(contentHeight);
        if (arrowWidth <= 0) {
            arrowWidth = 24;
        }
        double labelWidth = Math.max(0, contentWidth - arrowWidth);
        displayLabel.resizeRelocate(contentX, contentY, labelWidth, contentHeight);
        arrowButton.resizeRelocate(contentX + labelWidth, contentY, arrowWidth, contentHeight);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        double labelPref = displayLabel.prefWidth(height);
        double arrowPref = arrowButton.prefWidth(height);
        if (arrowPref <= 0) arrowPref = 24;
        return leftInset + labelPref + arrowPref + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset + Math.max(displayLabel.prefHeight(width), arrowButton.prefHeight(width)) + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        double arrowMin = arrowButton.minWidth(height);
        if (arrowMin <= 0) arrowMin = 24;
        return leftInset + displayLabel.minWidth(height) + arrowMin + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset + Math.max(displayLabel.minHeight(width), arrowButton.minHeight(width)) + bottomInset;
    }

    private void updateDisplayLabel() {
        T value = getSkinnable().getValue();
        if (value != null) {
            StringConverter<T> converter = getSkinnable().getConverter();
            displayLabel.setText(converter != null ? converter.toString(value) : value.toString());
        } else {
            String prompt = getSkinnable().getPromptText();
            displayLabel.setText(prompt != null ? prompt : "");
        }
    }

    private void selectItemAndHide(T item) {
        getSkinnable().setValue(item);
        getSkinnable().hide();
        if (getSkinnable().getOnAction() != null) {
            getSkinnable().getOnAction().handle(new ActionEvent(getSkinnable(), null));
        }
    }

    private void showPopup() {
        if (getSkinnable().getScene() == null) return;
        Window window = getSkinnable().getScene().getWindow();
        if (window == null) return;

        Bounds bounds = getSkinnable().localToScreen(getSkinnable().getBoundsInLocal());
        if (bounds == null) return;

        // Propagate stylesheets so theme colors apply inside popup
        if (!getSkinnable().getScene().getStylesheets().isEmpty()) {
            popupRoot.getStylesheets().setAll(getSkinnable().getScene().getStylesheets());
        }

        double minWidth = Math.max(300, getSkinnable().getWidth());
        popupRoot.setMinWidth(minWidth);
        popupRoot.setPrefWidth(minWidth);

        // 1. Reset search text and filter predicate first, ensuring full items are present
        searchField.clear();
        filteredList.setPredicate(p -> true);

        // 2. Open with full height based on master items count (never based on previous search result)
        ObservableList<T> masterItems = getSkinnable().getItems();
        int totalItems = masterItems != null ? masterItems.size() : 0;
        int maxRows = getSkinnable().getVisibleRowCount() > 0 ? getSkinnable().getVisibleRowCount() : 10;
        int itemCount = Math.max(1, Math.min(totalItems > 0 ? totalItems : maxRows, Math.max(maxRows, 8)));
        double fullHeight = itemCount * 32 + 10;
        listView.setPrefHeight(fullHeight);
        listView.setMinHeight(Region.USE_PREF_SIZE);
        listView.setMaxHeight(fullHeight);

        // 3. Highlight current value or first item
        T currentVal = getSkinnable().getValue();
        if (currentVal != null && filteredList.contains(currentVal)) {
            listView.getSelectionModel().select(currentVal);
            listView.scrollTo(currentVal);
        } else if (!filteredList.isEmpty()) {
            listView.getSelectionModel().select(0);
        }

        popup.getScene().setRoot(popupRoot);
        popup.show(getSkinnable(), bounds.getMinX(), bounds.getMaxY() + 2);

        Platform.runLater(searchField::requestFocus);
    }

    private void hidePopup() {
        if (popup.isShowing()) {
            popup.hide();
        }
        searchField.clear();
        filteredList.setPredicate(p -> true);
    }

    public Label getDisplayLabel() {
        return displayLabel;
    }

    public StackPane getArrowButton() {
        return arrowButton;
    }

    public TextField getSearchField() {
        return searchField;
    }

    public ListView<T> getListView() {
        return listView;
    }

    public FilteredList<T> getFilteredList() {
        return filteredList;
    }

    @Override
    public void dispose() {
        hidePopup();
        super.dispose();
    }
}
