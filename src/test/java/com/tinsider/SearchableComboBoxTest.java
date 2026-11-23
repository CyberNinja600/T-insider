package com.tinsider;

import com.tinsider.model.HostProfile;
import com.tinsider.ui.components.toolbar.SearchableComboBox;
import com.tinsider.ui.components.toolbar.SearchableComboBoxSkin;
import com.tinsider.ui.components.toolbar.TopToolBar;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SearchableComboBoxTest {

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
    public void testSearchableComboBoxFilteringAndSelection() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                SearchableComboBox<TopToolBar.DropdownItem> comboBox = new SearchableComboBox<>();
                ObservableList<TopToolBar.DropdownItem> items = FXCollections.observableArrayList();

                items.add(new TopToolBar.DropdownItem("CMD"));
                items.add(new TopToolBar.DropdownItem("PowerShell"));
                items.add(new TopToolBar.DropdownItem("Bash"));

                HostProfile host1 = new HostProfile("Server Alpha", "192.168.1.10", 22, "admin", "pass");
                host1.setId("h-1");
                items.add(new TopToolBar.DropdownItem(host1));

                HostProfile host2 = new HostProfile("CT, North Region, DDS", "10.0.0.5", 22, "root", "pass");
                host2.setId("h-2");
                items.add(new TopToolBar.DropdownItem(host2));

                comboBox.setItems(items);

                // Render into a real scene to trigger skin creation
                Stage stage = new Stage();
                StackPane root = new StackPane(comboBox);
                Scene scene = new Scene(root, 400, 300);
                stage.setScene(scene);
                stage.show();

                assertTrue(comboBox.getSkin() instanceof SearchableComboBoxSkin<?>,
                        "Skin should be an instance of SearchableComboBoxSkin");

                SearchableComboBoxSkin<TopToolBar.DropdownItem> skin =
                        (SearchableComboBoxSkin<TopToolBar.DropdownItem>) comboBox.getSkin();

                // 1. Initial state: all 5 items present
                assertEquals(5, skin.getFilteredList().size(), "Initial list should contain all items");

                // 2. Filter by shell name "power"
                skin.getSearchField().setText("power");
                assertEquals(1, skin.getFilteredList().size(), "Filtered list should match 'PowerShell'");
                assertEquals("PowerShell", skin.getFilteredList().get(0).getLabel());

                // 3. Filter by host name "DDS"
                skin.getSearchField().setText("DDS");
                assertEquals(1, skin.getFilteredList().size(), "Filtered list should match 'CT, North Region, DDS'");
                assertEquals("CT, North Region, DDS", skin.getFilteredList().get(0).getLabel());

                // 4. Filter by host IP "192.168.1.10"
                skin.getSearchField().setText("192.168.1.10");
                assertEquals(1, skin.getFilteredList().size(), "Filtered list should match host by IP");
                assertEquals("Server Alpha", skin.getFilteredList().get(0).getLabel());

                // 5. Clear search: all items return
                skin.getSearchField().setText("");
                assertEquals(5, skin.getFilteredList().size(), "Cleared search should restore all items");

                // 6. Test selection and action triggering
                AtomicBoolean actionFired = new AtomicBoolean(false);
                comboBox.setOnAction(e -> actionFired.set(true));

                comboBox.setValue(items.get(1)); // PowerShell
                assertEquals("PowerShell", comboBox.getValue().getLabel());

                // 7. Verify trigger button styling & padding (previous style alignment)
                assertNotNull(skin.getDisplayLabel(), "Skin must provide displayLabel");
                assertTrue(skin.getDisplayLabel().getStyleClass().contains("list-cell"), "Display label must have list-cell style class");
                assertEquals(Pos.CENTER_LEFT, skin.getDisplayLabel().getAlignment(), "Display label must align to CENTER_LEFT");
                assertTrue(skin.getDisplayLabel().getPadding().getLeft() >= 8.0,
                        "Display label must have at least 8px left padding to prevent being flushed against the left border");
                assertNotNull(skin.getArrowButton(), "Skin must provide arrowButton");
                assertTrue(skin.getArrowButton().getStyleClass().contains("arrow-button"), "Arrow button must have arrow-button style class");
                assertFalse(skin.getArrowButton().getChildren().isEmpty(), "Arrow button must contain arrow node");
                assertTrue(skin.getArrowButton().getChildren().get(0) instanceof Label, "Arrow must be a Label");
                Label arrowLabel = (Label) skin.getArrowButton().getChildren().get(0);
                assertEquals("\uEAB4", arrowLabel.getText(), "Arrow label must contain codicon chevron-down glyph (\\uEAB4)");
                assertTrue(arrowLabel.getStyleClass().contains("codicon-arrow"), "Arrow label must have codicon-arrow style class");
                assertTrue(arrowLabel.getStyle().contains("codicon"), "Arrow label must use codicon font-family");

                // 8. Verify popup opens with full height and does NOT shrink based on prior search result
                comboBox.show();
                double initialPrefHeight = skin.getListView().getPrefHeight();
                assertTrue(initialPrefHeight >= 5 * 32, "Initial dropdown height must accommodate all master items");

                // Filter down to 1 item
                skin.getSearchField().setText("power");
                assertEquals(1, skin.getFilteredList().size());

                // Close and reopen popup
                comboBox.hide();
                comboBox.show();

                // Check that reopened popup restores full list and maintains full height (does not shrink to 1 item)
                assertEquals(5, skin.getFilteredList().size(), "Reopened popup must reset search and display all items");
                assertEquals(initialPrefHeight, skin.getListView().getPrefHeight(), 0.1,
                        "Dropdown height must remain at full height and not shrink based on previous search result");

                comboBox.hide();

                stage.close();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    public void testTopToolBarIntegration() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ObservableList<HostProfile> hosts = FXCollections.observableArrayList();
                HostProfile host = new HostProfile("Production Host", "prod.example.com", 22, "deploy", "secret");
                host.setId("prod-1");
                hosts.add(host);

                AtomicReference<String> changedShell = new AtomicReference<>();

                TopToolBar topToolBar = new TopToolBar(
                        "CMD",
                        hosts,
                        changedShell::set,
                        null, null, null, null, null, null, null, null
                );

                Stage stage = new Stage();
                Scene scene = new Scene(topToolBar, 800, 60);
                stage.setScene(scene);
                stage.show();

                // Verify "Shell / Host:" label is present
                boolean labelFound = false;
                for (Node child : topToolBar.getChildren()) {
                    if (child instanceof Label label && "Shell / Host:".equals(label.getText())) {
                        labelFound = true;
                        break;
                    }
                }
                assertTrue(labelFound, "TopToolBar must contain 'Shell / Host:' label");

                // Verify selected item is defaulted
                assertNotNull(topToolBar.getSelectedItem(), "TopToolBar should have a selected item");

                // Select host
                topToolBar.selectShellOrHost("SSH", host);
                assertNotNull(topToolBar.getSelectedItem());
                assertTrue(topToolBar.getSelectedItem().isHost());
                assertEquals("Production Host", topToolBar.getSelectedItem().getLabel());

                stage.close();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
