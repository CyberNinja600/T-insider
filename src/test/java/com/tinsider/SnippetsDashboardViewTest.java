package com.tinsider;

import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import com.tinsider.ui.dashboard.SnippetsDashboardView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SnippetsDashboardViewTest {

    @BeforeAll
    public static void initJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX failed to initialize");
    }

    @Test
    public void testContextMenuAndClickBehavior() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                ObservableList<Snippet> snippets = FXCollections.observableArrayList();
                Snippet snippet = new Snippet("Git Status", "git status");
                snippet.setId("snip-1");
                snippets.add(snippet);

                ObservableList<SnippetPackage> packages = FXCollections.observableArrayList();

                AtomicBoolean editTriggered = new AtomicBoolean(false);
                AtomicReference<Snippet> editedSnippet = new AtomicReference<>();

                SnippetsDashboardView view = new SnippetsDashboardView(snippets, packages) {
                    @Override
                    public void showAddSnippetDialog(Snippet existing) {
                        editTriggered.set(true);
                        editedSnippet.set(existing);
                    }
                };

                Scene scene = new Scene(view, 600, 400);
                stage.setScene(scene);
                stage.show();

                // Find card node in snippet grid
                Node cardNode = findSnippetCard(view);
                assertNotNull(cardNode, "Snippet card node should exist in view");

                // Verify context menu is attached
                ContextMenu contextMenu = (ContextMenu) cardNode.getProperties().get("contextMenu");
                assertNotNull(contextMenu, "ContextMenu should be attached to snippet card");
                assertEquals(2, contextMenu.getItems().size(), "Context menu should have 2 items (Edit, Delete)");

                MenuItem editItem = contextMenu.getItems().get(0);
                assertEquals("Edit", editItem.getText());
                assertNotNull(editItem.getGraphic(), "Edit item should have an icon");

                MenuItem deleteItem = contextMenu.getItems().get(1);
                assertEquals("Delete", deleteItem.getText());
                assertNotNull(deleteItem.getGraphic(), "Delete item should have an icon");

                // 1. Single click should NOT add sticky 'selected' class
                MouseEvent singleClick = new MouseEvent(
                        MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        MouseButton.PRIMARY, 1,
                        false, false, false, false, false, false, false, false, false, false, null
                );
                cardNode.fireEvent(singleClick);
                assertFalse(cardNode.getStyleClass().contains("selected"), "Snippet card should not stay selected on single click");

                // 2. Double click should trigger edit
                MouseEvent doubleClick = new MouseEvent(
                        MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        MouseButton.PRIMARY, 2,
                        false, false, false, false, false, false, false, false, false, false, null
                );
                cardNode.fireEvent(doubleClick);
                assertTrue(editTriggered.get(), "Double click should trigger edit dialog");
                assertSame(snippet, editedProfileOrSnippet(editedSnippet), "Edited snippet should be the clicked snippet");

                // 3. Edit menu item fires and triggers edit dialog
                editTriggered.set(false);
                editedSnippet.set(null);
                editItem.fire();

                Platform.runLater(() -> {
                    try {
                        assertTrue(editTriggered.get(), "Edit menu item action should trigger edit dialog");
                        assertSame(snippet, editedSnippet.get());
                    } catch (Throwable t) {
                        failure.set(t);
                    } finally {
                        stage.close();
                        latch.countDown();
                    }
                });
                return;
            } catch (Throwable t) {
                failure.set(t);
                stage.close();
            }
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
        if (failure.get() != null) {
            throw new AssertionError("Test failed", failure.get());
        }
    }

    @Test
    public void testBottomBarButtons() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ObservableList<Snippet> snippets = FXCollections.observableArrayList();
                ObservableList<SnippetPackage> packages = FXCollections.observableArrayList();
                SnippetsDashboardView view = new SnippetsDashboardView(snippets, packages);

                // Find bottom HBox containing buttons
                HBox bottomButtons = null;
                for (Node child : view.getChildren()) {
                    if (child instanceof HBox hbox && !(child instanceof ScrollPane)) {
                        bottomButtons = hbox;
                    }
                }

                assertNotNull(bottomButtons, "Bottom button bar HBox should exist");
                assertEquals(3, bottomButtons.getChildren().size(), "Bottom bar should contain 3 buttons (Add Snippet, Add Package, Delete Package)");

                Button addSnippetBtn = (Button) bottomButtons.getChildren().get(0);
                assertEquals("Add Snippet", addSnippetBtn.getText());

                Button addPackageBtn = (Button) bottomButtons.getChildren().get(1);
                assertEquals("Add Package", addPackageBtn.getText());

                Button deletePackageBtn = (Button) bottomButtons.getChildren().get(2);
                assertEquals("Delete Package", deletePackageBtn.getText());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
        if (failure.get() != null) {
            throw new AssertionError("Test failed", failure.get());
        }
    }

    private Snippet editedProfileOrSnippet(AtomicReference<Snippet> ref) {
        return ref.get();
    }

    private Node findSnippetCard(SnippetsDashboardView view) {
        for (Node child : view.getChildren()) {
            if (child instanceof ScrollPane sp && sp.getContent() instanceof VBox container) {
                for (Node section : container.getChildren()) {
                    if (section instanceof VBox vbox) {
                        for (Node node : vbox.getChildren()) {
                            if (node instanceof FlowPane fp && !fp.getChildren().isEmpty()) {
                                return fp.getChildren().get(0);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
