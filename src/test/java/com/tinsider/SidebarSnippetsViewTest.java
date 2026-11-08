package com.tinsider;

import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import com.tinsider.ui.components.sidebar.SidebarSnippetsView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SidebarSnippetsViewTest {

    @BeforeAll
    public static void initJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            // JavaFX Toolkit already initialized
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX failed to initialize");
    }

    @Test
    public void testContextMenuDismissesOnEditItemAction() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                ObservableList<Snippet> snippets = FXCollections.observableArrayList();
                Snippet testSnippet = new Snippet("Deploy Script", "bash deploy.sh");
                testSnippet.setId("snip-1");
                snippets.add(testSnippet);

                ObservableList<SnippetPackage> packages = FXCollections.observableArrayList();
                AtomicBoolean editTriggered = new AtomicBoolean(false);
                AtomicReference<Snippet> editedSnippet = new AtomicReference<>();

                SidebarSnippetsView view = new SidebarSnippetsView(
                        snippets,
                        packages,
                        (s, autoEnter) -> {},
                        s -> {
                            editTriggered.set(true);
                            editedSnippet.set(s);
                        },
                        () -> {}
                );

                Scene scene = new Scene(view, 400, 600);
                stage.setScene(scene);
                stage.show();

                // Find the snippet card node in the container hierarchy
                Node cardNode = findSnippetCard(view);
                assertNotNull(cardNode, "Snippet card should exist in the view hierarchy");

                ContextMenu contextMenu = (ContextMenu) cardNode.getProperties().get("contextMenu");
                assertNotNull(contextMenu, "ContextMenu should be attached to the card node");
                assertEquals(1, contextMenu.getItems().size());

                MenuItem editItem = contextMenu.getItems().get(0);
                assertEquals("Edit", editItem.getText());

                // Show the context menu
                contextMenu.show(cardNode, 100, 100);
                assertTrue(contextMenu.isShowing(), "ContextMenu should be showing");

                // Fire the edit item action
                editItem.fire();

                // Verify the context menu closes immediately upon clicking
                assertFalse(contextMenu.isShowing(), "ContextMenu must be hidden immediately when Edit is clicked");

                // Platform.runLater was used to defer edit callback; flush runLater queue
                Platform.runLater(() -> {
                    try {
                        assertTrue(editTriggered.get(), "onEditSnippet callback should have been invoked");
                        assertEquals(testSnippet, editedSnippet.get(), "Edited snippet must match the card snippet");
                        assertFalse(contextMenu.isShowing(), "ContextMenu should remain closed after callback");
                    } catch (Throwable t) {
                        failure.set(t);
                    } finally {
                        stage.close();
                        latch.countDown();
                    }
                });

            } catch (Throwable t) {
                failure.set(t);
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
        if (failure.get() != null) {
            fail(failure.get());
        }
    }

    @Test
    public void testGlobalPopupWindowDismissalSafeguard() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                StackPane root = new StackPane();
                Scene scene = new Scene(root, 300, 300);
                stage.setScene(scene);
                stage.show();

                // Create and show a standalone ContextMenu popup
                ContextMenu testPopup = new ContextMenu();
                testPopup.getItems().add(new MenuItem("Test Option"));
                testPopup.show(stage, 200, 200);
                assertTrue(testPopup.isShowing(), "Test popup should initially be showing");

                // Simulate the ModalOverlayManager safeguard:
                for (Window window : new ArrayList<>(Window.getWindows())) {
                    if (window instanceof PopupWindow popupWindow) {
                        popupWindow.hide();
                    }
                }

                assertFalse(testPopup.isShowing(), "All open popups should be dismissed by the safeguard");
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
        if (failure.get() != null) {
            fail(failure.get());
        }
    }

    private Node findSnippetCard(SidebarSnippetsView view) {
        // Hierarchy: view -> ScrollPane (child index 2) -> VBox terminalSnippetsContainer -> TitledPane -> VBox -> HBox (card)
        for (Node child : view.getChildren()) {
            if (child instanceof ScrollPane scrollPane) {
                if (scrollPane.getContent() instanceof VBox container) {
                    for (Node paneNode : container.getChildren()) {
                        if (paneNode instanceof TitledPane titledPane) {
                            if (titledPane.getContent() instanceof VBox cardBox) {
                                for (Node card : cardBox.getChildren()) {
                                    if (card.getStyleClass().contains("snippet-card")) {
                                        return card;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
