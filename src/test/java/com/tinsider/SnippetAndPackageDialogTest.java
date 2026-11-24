package com.tinsider;

import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import com.tinsider.ui.dialogs.PackageDialog;
import com.tinsider.ui.dialogs.SnippetDialog;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SnippetAndPackageDialogTest {

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
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX initialization timed out");
    }

    @Test
    public void testNewSnippetDialogValidation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ObservableList<SnippetPackage> packages = FXCollections.observableArrayList();
                SnippetDialog dialog = new SnippetDialog(null, packages);

                // Initial state: Save button enabled, clean placeholders
                assertFalse(dialog.getSaveButton().isDisable(), "Save button must be enabled initially");
                assertEquals("Update System", dialog.getTitleField().getPromptText());
                assertEquals("sudo apt-get update && sudo apt-get upgrade -y", dialog.getCodeArea().getPromptText());

                // Click Save when empty -> fails validation
                assertFalse(dialog.validateAndHighlight(), "Validation must fail when title and command/code are empty");

                // Both fields should show "Required" with red border
                assertEquals("Required", dialog.getTitleField().getPromptText());
                assertTrue(dialog.getTitleField().getStyle().contains("#ff5555"));

                assertEquals("Required", dialog.getCodeArea().getPromptText());
                assertTrue(dialog.getCodeArea().getStyle().contains("#ff5555"));

                // Fill Title only
                dialog.getTitleField().setText("Git Log");
                assertEquals("", dialog.getTitleField().getStyle(), "Typing into title should clear red error border");
                assertEquals("Update System", dialog.getTitleField().getPromptText());

                // Validation should still fail because command/code is empty
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getCodeArea().getPromptText());
                assertTrue(dialog.getCodeArea().getStyle().contains("#ff5555"));

                // Fill Command/Code
                dialog.getCodeArea().setText("git log --oneline -n 10");
                assertEquals("", dialog.getCodeArea().getStyle(), "Typing into code area should clear red error border");
                assertEquals("sudo apt-get update && sudo apt-get upgrade -y", dialog.getCodeArea().getPromptText());

                // Now both are filled -> validation passes
                assertTrue(dialog.validateAndHighlight(), "Validation must pass when title and code are both filled");

                // Clear Command/Code -> fails validation and flags Required
                dialog.getCodeArea().setText("   ");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getCodeArea().getPromptText());
                assertTrue(dialog.getCodeArea().getStyle().contains("#ff5555"));

                // Restore Command/Code
                dialog.getCodeArea().setText("git status");
                assertTrue(dialog.validateAndHighlight());

                // Clear Title -> fails validation and flags Required
                dialog.getTitleField().setText("");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getTitleField().getPromptText());
                assertTrue(dialog.getTitleField().getStyle().contains("#ff5555"));

                // Restore Title -> passes
                dialog.getTitleField().setText("Git Status");
                assertTrue(dialog.validateAndHighlight());

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
    public void testEditExistingSnippetValidation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Snippet existing = new Snippet("Docker PS", "docker ps -a");
                SnippetDialog dialog = new SnippetDialog(existing, null);

                // Initial state of valid snippet passes validation
                assertTrue(dialog.validateAndHighlight());
                assertFalse(dialog.getSaveButton().isDisable());

                // Clear title -> validation fails
                dialog.getTitleField().setText("");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getTitleField().getPromptText());

                // Restore title -> passes
                dialog.getTitleField().setText("Docker Containers");
                assertTrue(dialog.validateAndHighlight());

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
    public void testPackageDialogValidation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                PackageDialog dialog = new PackageDialog("Add Package");

                // Initial state
                assertFalse(dialog.getOkButton().isDisable(), "OK button must be enabled initially");
                assertEquals("Package name", dialog.getEditorField().getPromptText());

                // Click OK when empty -> fails validation
                assertFalse(dialog.validateAndHighlight(), "Validation must fail when package name is empty");

                // Editor should show "Required" with red border
                assertEquals("Required", dialog.getEditorField().getPromptText());
                assertTrue(dialog.getEditorField().getStyle().contains("#ff5555"));

                // Typing a package name clears error style
                dialog.getEditorField().setText("DevOps Tools");
                assertEquals("", dialog.getEditorField().getStyle());
                assertEquals("Package name", dialog.getEditorField().getPromptText());

                // Validation now passes
                assertTrue(dialog.validateAndHighlight());

                // Whitespace only -> fails validation
                dialog.getEditorField().setText("   ");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getEditorField().getPromptText());
                assertTrue(dialog.getEditorField().getStyle().contains("#ff5555"));

                // Valid text -> passes
                dialog.getEditorField().setText("Kubernetes");
                assertTrue(dialog.validateAndHighlight());

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
