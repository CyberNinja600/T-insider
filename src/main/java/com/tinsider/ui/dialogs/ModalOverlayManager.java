package com.tinsider.ui.dialogs;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Callback;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages the embedded in-scene modal overlay for the T-Insider application.
 * All modal dialogs (Host, Snippet, Highlight, Settings, Packages, and Alerts)
 * are rendered directly inside the root scene graph within a darkened backdrop layer,
 * keeping dialogs strictly within the app window bounds.
 */
public class ModalOverlayManager {

    private static StackPane overlayContainer;
    private static Node backgroundNode;
    private static final Deque<ModalSession<?>> sessionStack = new ArrayDeque<>();

    private static class ModalSession<R> {
        final Dialog<R> dialog;
        final Object loopKey;
        final StackPane layerPane;
        final StackPane modalCard;
        R result = null;
        boolean closed = false;

        ModalSession(Dialog<R> dialog, Object loopKey, StackPane layerPane, StackPane modalCard) {
            this.dialog = dialog;
            this.loopKey = loopKey;
            this.layerPane = layerPane;
            this.modalCard = modalCard;
        }
    }

    /**
     * Initializes the modal overlay manager with the root overlay container and background layout.
     *
     * @param container     The StackPane overlay layer positioned on top of the main UI
     * @param appBackground The background UI node (e.g. root BorderPane) to disable during active modals
     */
    public static void init(StackPane container, Node appBackground) {
        overlayContainer = container;
        backgroundNode = appBackground;
        overlayContainer.setVisible(false);
        overlayContainer.setManaged(false);
        overlayContainer.setPickOnBounds(true);
    }

    /**
     * Re-applies updated stylesheets to all currently open in-scene modal dialogs.
     *
     * @param styleCssUri   Base style CSS URI
     * @param dynamicCssUri Dynamic theme CSS URI
     */
    public static void applyThemeToActiveModals(String styleCssUri, String dynamicCssUri) {
        for (ModalSession<?> session : sessionStack) {
            DialogPane dp = session.dialog.getDialogPane();
            if (dp != null) {
                dp.getStylesheets().clear();
                if (styleCssUri != null && !styleCssUri.isEmpty()) {
                    dp.getStylesheets().add(styleCssUri);
                }
                if (dynamicCssUri != null && !dynamicCssUri.isEmpty()) {
                    dp.getStylesheets().add(dynamicCssUri);
                }
                dp.applyCss();
            }
            if (session.layerPane != null) {
                session.layerPane.applyCss();
            }
        }
        if (overlayContainer != null) {
            overlayContainer.applyCss();
        }
    }

    /**
     * Displays a dialog inside the in-scene modal overlay and synchronously returns the result.
     *
     * @param dialog The Dialog instance to display
     * @param <R>    The return type of the dialog
     * @return Optional containing the dialog result if confirmed, or empty if canceled
     */
    @SuppressWarnings("unchecked")
    public static <R> Optional<R> showModal(Dialog<R> dialog) {
        if (dialog == null) {
            return Optional.empty();
        }

        // If overlay container is not initialized, fallback to standard showAndWait
        if (overlayContainer == null) {
            return dialog.showAndWait();
        }

        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("showModal must be invoked on the JavaFX Application Thread");
        }

        if (!Platform.canStartNestedEventLoop()) {
            throw new IllegalStateException("Cannot start nested event loop in current state");
        }

        // Ensure theme and custom title bar styling are applied
        AlertHelper.themeDialog(dialog);

        DialogPane dialogPane = dialog.getDialogPane();
        Object loopKey = new Object();

        // Disconnect dialogPane from previous parent if necessary
        if (dialogPane.getParent() instanceof Pane parentPane) {
            parentPane.getChildren().remove(dialogPane);
        }

        // Synchronize dialogPane stylesheets from active scene so it starts with the current theme
        if (overlayContainer.getScene() != null) {
            dialogPane.getStylesheets().clear();
            dialogPane.getStylesheets().addAll(overlayContainer.getScene().getStylesheets());
        }

        // Build layer pane for this modal instance
        StackPane layerPane = new StackPane();
        layerPane.setPickOnBounds(true);

        // Darkened backdrop covering full viewport
        Region backdrop = new Region();
        backdrop.setStyle("-fx-background-color: rgba(0, 0, 0, 0.55);");
        backdrop.prefWidthProperty().bind(overlayContainer.widthProperty());
        backdrop.prefHeightProperty().bind(overlayContainer.heightProperty());
        backdrop.setPickOnBounds(true);
        // Consume backdrop clicks to prevent passing to underlying controls
        backdrop.setOnMouseClicked(Event::consume);

        // Center card container
        StackPane modalCard = new StackPane();
        modalCard.setAlignment(Pos.CENTER);
        modalCard.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        modalCard.getChildren().add(dialogPane);
        StackPane.setAlignment(modalCard, Pos.CENTER);

        layerPane.getChildren().addAll(backdrop, modalCard);

        ModalSession<R> session = new ModalSession<>(dialog, loopKey, layerPane, modalCard);
        sessionStack.push(session);

        overlayContainer.getChildren().add(layerPane);
        overlayContainer.setVisible(true);
        overlayContainer.setManaged(true);

        if (backgroundNode != null) {
            backgroundNode.setDisable(true);
        }

        setupInteractions(session);

        // Enter nested event loop to block synchronously while keeping JavaFX UI responsive
        Object rawResult = Platform.enterNestedEventLoop(loopKey);

        return Optional.ofNullable((R) rawResult);
    }

    @SuppressWarnings("unchecked")
    private static <R> void setupInteractions(ModalSession<R> session) {
        DialogPane dialogPane = session.dialog.getDialogPane();

        // 1. ButtonBar buttons
        for (ButtonType bt : dialogPane.getButtonTypes()) {
            Node btnNode = dialogPane.lookupButton(bt);
            if (btnNode instanceof Button button) {
                button.addEventHandler(ActionEvent.ACTION, event -> {
                    // Respect validation filters that consume the event (e.g. required field checks)
                    if (event.isConsumed()) {
                        return;
                    }

                    R converted = null;
                    Callback<ButtonType, R> converter = session.dialog.getResultConverter();
                    if (converter != null) {
                        converted = converter.call(bt);
                    } else if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE || bt == ButtonType.OK) {
                        converted = (R) bt;
                    }

                    closeModal(session, converted);
                });
            }
        }

        // 2. Custom Title Bar Close Button (lookup by class title-btn-close)
        Node headerNode = dialogPane.getHeader();
        if (headerNode != null) {
            Button closeBtn = (Button) headerNode.lookup(".title-btn-close");
            if (closeBtn != null) {
                closeBtn.setOnAction(event -> closeModal(session, null));
            }

            // 3. In-Scene Title Bar Dragging bounded within app window
            final double[] dragStart = new double[4];
            headerNode.setOnMousePressed(event -> {
                dragStart[0] = event.getSceneX();
                dragStart[1] = event.getSceneY();
                dragStart[2] = session.modalCard.getTranslateX();
                dragStart[3] = session.modalCard.getTranslateY();
            });

            headerNode.setOnMouseDragged(event -> {
                double deltaX = event.getSceneX() - dragStart[0];
                double deltaY = event.getSceneY() - dragStart[1];
                double targetX = dragStart[2] + deltaX;
                double targetY = dragStart[3] + deltaY;

                double maxTranslateX = Math.max(0, (overlayContainer.getWidth() - session.modalCard.getWidth()) / 2.0);
                double maxTranslateY = Math.max(0, (overlayContainer.getHeight() - session.modalCard.getHeight()) / 2.0);

                session.modalCard.setTranslateX(Math.clamp(maxTranslateX, -maxTranslateX, targetX));
                session.modalCard.setTranslateY(Math.clamp(maxTranslateY, -maxTranslateY, targetY));
            });

            // Double-click on header resets centering
            headerNode.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    session.modalCard.setTranslateX(0);
                    session.modalCard.setTranslateY(0);
                }
            });
        }

        // 4. Escape Key closes modal
        session.modalCard.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                closeModal(session, null);
            }
        });

        // 5. Result and Close listeners
        session.dialog.resultProperty().addListener((obs, oldVal, newVal) -> {
            if (!session.closed) {
                closeModal(session, newVal);
            }
        });

        // 6. Initial Focus Handling
        Platform.runLater(() -> {
            if (session.dialog instanceof TextInputDialog textDialog) {
                textDialog.getEditor().requestFocus();
            } else if (session.dialog instanceof ChoiceDialog) {
                session.modalCard.requestFocus();
            } else {
                Node initial = findFirstFocusable(dialogPane.getContent());
                Objects.requireNonNullElseGet(initial, () -> session.modalCard).requestFocus();
            }
        });
    }

    private static Node findFirstFocusable(Node parent) {
        if (parent instanceof TextInputControl || parent instanceof ComboBox) {
            return parent;
        }
        if (parent instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                Node found = findFirstFocusable(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <R> void closeModal(ModalSession<R> session, R result) {
        if (session.closed) {
            return;
        }
        session.closed = true;
        session.result = result;

        overlayContainer.getChildren().remove(session.layerPane);
        sessionStack.remove(session);

        if (sessionStack.isEmpty()) {
            overlayContainer.setVisible(false);
            overlayContainer.setManaged(false);
            if (backgroundNode != null) {
                backgroundNode.setDisable(false);
            }
        } else {
            ModalSession<?> topSession = sessionStack.peek();
            if (topSession != null) {
                topSession.modalCard.requestFocus();
            }
        }

        if (Platform.isNestedLoopRunning()) {
            Platform.exitNestedEventLoop(session.loopKey, result);
        }
    }
}
