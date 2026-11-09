package com.tinsider;

import com.tinsider.config.TerminalTheme;
import com.tinsider.model.HighlightPackage;
import com.tinsider.model.WordHighlight;
import com.tinsider.storage.StorageManager;
import com.tinsider.ui.components.sidebar.SidebarHighlightsView;
import com.tinsider.ui.components.terminal.split.TerminalPane;
import com.tinsider.ui.components.terminal.split.TerminalSplitPane;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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

public class SidebarHighlightsViewTest {

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
    public void testRightClickDoesNotActivateAndShowsContextMenu() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                ObservableList<WordHighlight> highlights = FXCollections.observableArrayList();
                WordHighlight highlight = new WordHighlight("ERROR", "#FF0000");
                highlight.setId("hl-test-1");
                highlights.add(highlight);

                ObservableList<HighlightPackage> packages = FXCollections.observableArrayList();

                TerminalTheme defaultTheme = new TerminalTheme("Default");
                TerminalSplitPane splitPane = new TerminalSplitPane(() -> defaultTheme, () -> {});
                TerminalPane pane = new TerminalPane(splitPane, "LOCAL", "CMD", null, "CMD");
                splitPane.setActiveTerminalPane(pane);

                AtomicBoolean editTriggered = new AtomicBoolean(false);
                AtomicReference<WordHighlight> editedHighlight = new AtomicReference<>();
                AtomicBoolean deleteTriggered = new AtomicBoolean(false);
                AtomicReference<WordHighlight> deletedHighlight = new AtomicReference<>();

                SidebarHighlightsView view = new SidebarHighlightsView(
                        highlights,
                        packages,
                        () -> splitPane,
                        wh -> {
                            editTriggered.set(true);
                            editedHighlight.set(wh);
                        },
                        wh -> {
                            deleteTriggered.set(true);
                            deletedHighlight.set(wh);
                        }
                );

                Scene scene = new Scene(view, 300, 500);
                stage.setScene(scene);
                stage.show();

                view.rebuild();

                Node cardNode = findHighlightSidebarCard(view);
                assertNotNull(cardNode, "Highlight sidebar card node should exist in view");

                // Initially, highlight is not active
                assertFalse(pane.getActiveHighlightIds().contains(highlight.getId()),
                        "Highlight should initially be inactive");

                // 1. Right click should NOT activate or toggle highlight
                MouseEvent rightClick = new MouseEvent(
                        MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        MouseButton.SECONDARY, 1,
                        false, false, false, false, false, false, false, false, false, false, null
                );
                cardNode.fireEvent(rightClick);
                assertFalse(pane.getActiveHighlightIds().contains(highlight.getId()),
                        "Highlight must NOT be activated on right click");

                // 2. Primary left click SHOULD toggle highlight activation
                MouseEvent leftClick = new MouseEvent(
                        MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        MouseButton.PRIMARY, 1,
                        false, false, false, false, false, false, false, false, false, false, null
                );
                cardNode.fireEvent(leftClick);
                assertTrue(pane.getActiveHighlightIds().contains(highlight.getId()),
                        "Highlight must be activated on left click");

                // Left click again should deactivate
                cardNode.fireEvent(leftClick);
                assertFalse(pane.getActiveHighlightIds().contains(highlight.getId()),
                        "Highlight must be deactivated on second left click");

                // 3. Verify ContextMenu attachment and items
                ContextMenu contextMenu = (ContextMenu) cardNode.getProperties().get("contextMenu");
                assertNotNull(contextMenu, "ContextMenu should be attached to sidebar highlight card");
                assertEquals(2, contextMenu.getItems().size(), "Context menu should have 2 items (Edit, Delete)");

                MenuItem editItem = contextMenu.getItems().getFirst();
                assertEquals("Edit", editItem.getText());
                assertNotNull(editItem.getGraphic(), "Edit item should have an icon");

                MenuItem deleteItem = contextMenu.getItems().get(1);
                assertEquals("Delete", deleteItem.getText());
                assertNotNull(deleteItem.getGraphic(), "Delete item should have an icon");

                // 4. Test Edit menu item callback
                editItem.fire();
                Platform.runLater(() -> {
                    try {
                        assertTrue(editTriggered.get(), "Edit callback should be triggered");
                        assertSame(highlight, editedHighlight.get());

                        // Test Delete menu item callback
                        deleteItem.fire();
                        Platform.runLater(() -> {
                            try {
                                assertTrue(deleteTriggered.get(), "Delete callback should be triggered");
                                assertSame(highlight, deletedHighlight.get());
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
    public void testDefaultHighlightIsNotEditableInSidebar() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                ObservableList<WordHighlight> highlights = FXCollections.observableArrayList();
                WordHighlight defaultHighlight = StorageManager.getDefaultHighlights().getFirst();
                highlights.add(defaultHighlight);

                ObservableList<HighlightPackage> packages = FXCollections.observableArrayList(StorageManager.loadHighlightPackages());

                TerminalTheme defaultTheme = new TerminalTheme("Default");
                TerminalSplitPane splitPane = new TerminalSplitPane(() -> defaultTheme, () -> {});
                TerminalPane pane = new TerminalPane(splitPane, "LOCAL", "CMD", null, "CMD");
                splitPane.setActiveTerminalPane(pane);

                AtomicBoolean editTriggered = new AtomicBoolean(false);
                AtomicBoolean deleteTriggered = new AtomicBoolean(false);

                SidebarHighlightsView view = new SidebarHighlightsView(
                        highlights,
                        packages,
                        () -> splitPane,
                        wh -> editTriggered.set(true),
                        wh -> deleteTriggered.set(true)
                );

                Scene scene = new Scene(view, 300, 500);
                stage.setScene(scene);
                stage.show();

                view.rebuild();

                Node cardNode = findHighlightSidebarCard(view);
                assertNotNull(cardNode, "Highlight sidebar card should exist in view");

                ContextMenu contextMenu = (ContextMenu) cardNode.getProperties().get("contextMenu");
                assertNotNull(contextMenu);
                MenuItem editItem = contextMenu.getItems().get(0);
                MenuItem deleteItem = contextMenu.getItems().get(1);

                assertTrue(editItem.isDisable(), "Edit menu item must be disabled for default highlight in sidebar");
                assertTrue(deleteItem.isDisable(), "Delete menu item must be disabled for default highlight in sidebar");

                // Firing editItem must not trigger callback
                editItem.fire();
                assertFalse(editTriggered.get(), "Edit callback must not be invoked for default highlight");

                // Firing deleteItem must not trigger callback
                deleteItem.fire();
                assertFalse(deleteTriggered.get(), "Delete callback must not be invoked for default highlight");
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
        if (failure.get() != null) {
            throw new AssertionError("Test failed", failure.get());
        }
    }

    private Node findHighlightSidebarCard(SidebarHighlightsView view) {
        for (Node child : view.getChildren()) {
            if (child instanceof ScrollPane sp && sp.getContent() instanceof VBox container) {
                for (Node section : container.getChildren()) {
                    if (section instanceof TitledPane tp && tp.getContent() instanceof VBox contentBox) {
                        for (Node node : contentBox.getChildren()) {
                            if (node instanceof HBox && node.getStyleClass().contains("highlight-sidebar-card")) {
                                return node;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
