package com.tinsider;

import com.tinsider.model.HostProfile;
import com.tinsider.ui.dashboard.HostsDashboardView;
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
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class HostsDashboardViewTest {

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
                ObservableList<HostProfile> hosts = FXCollections.observableArrayList();
                HostProfile host = new HostProfile("Prod Server", "192.168.1.100", 22, "admin", "secret");
                host.setId("host-1");
                hosts.add(host);

                AtomicBoolean editTriggered = new AtomicBoolean(false);
                AtomicReference<HostProfile> editedProfile = new AtomicReference<>();

                HostsDashboardView view = new HostsDashboardView(hosts) {
                    @Override
                    public void showAddHostDialog(HostProfile existing) {
                        editTriggered.set(true);
                        editedProfile.set(existing);
                    }
                };

                Scene scene = new Scene(view, 600, 400);
                stage.setScene(scene);
                stage.show();

                // Find card node in hostsGrid
                Node cardNode = findHostCard(view);
                assertNotNull(cardNode, "Host card node should exist in hostsGrid");

                // Verify context menu is attached
                ContextMenu contextMenu = (ContextMenu) cardNode.getProperties().get("contextMenu");
                assertNotNull(contextMenu, "ContextMenu should be attached to host card");
                assertEquals(2, contextMenu.getItems().size(), "Context menu should have 2 items (Edit, Delete)");

                MenuItem editItem = contextMenu.getItems().getFirst();
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
                assertFalse(cardNode.getStyleClass().contains("selected"), "Host card should not stay selected on single click");

                // 2. Double click should trigger edit
                MouseEvent doubleClick = new MouseEvent(
                        MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        MouseButton.PRIMARY, 2,
                        false, false, false, false, false, false, false, false, false, false, null
                );
                cardNode.fireEvent(doubleClick);
                assertTrue(editTriggered.get(), "Double click should trigger edit dialog");
                assertSame(host, editedProfile.get(), "Edited profile should be the clicked host");

                // 3. Edit menu item fires and triggers edit dialog
                editTriggered.set(false);
                editedProfile.set(null);
                editItem.fire();
                // Platform.runLater handles edit action
                Platform.runLater(() -> {
                    try {
                        assertTrue(editTriggered.get(), "Edit menu item action should trigger edit dialog");
                        assertSame(host, editedProfile.get());
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
    public void testBottomBarOnlyHasAddButton() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ObservableList<HostProfile> hosts = FXCollections.observableArrayList();
                HostsDashboardView view = new HostsDashboardView(hosts);

                // Find bottom HBox containing buttons
                HBox bottomButtons = null;
                for (Node child : view.getChildren()) {
                    if (child instanceof HBox hbox) {
                        bottomButtons = hbox;
                    }
                }

                assertNotNull(bottomButtons, "Bottom button bar HBox should exist");
                assertEquals(1, bottomButtons.getChildren().size(), "Bottom bar should only contain 1 button");

                Button addBtn = (Button) bottomButtons.getChildren().getFirst();
                assertEquals("Add", addBtn.getText(), "Button should be 'Add'");
                assertNotNull(addBtn.getGraphic(), "Add button should have an icon");
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

    private Node findHostCard(HostsDashboardView view) {
        for (Node child : view.getChildren()) {
            if (child instanceof ScrollPane sp && sp.getContent() instanceof FlowPane fp) {
                if (!fp.getChildren().isEmpty()) {
                    return fp.getChildren().getFirst();
                }
            }
        }
        return null;
    }
}
