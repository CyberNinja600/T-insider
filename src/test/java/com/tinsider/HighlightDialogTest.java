package com.tinsider;

import com.tinsider.model.HighlightPackage;
import com.tinsider.model.WordHighlight;
import com.tinsider.ui.dialogs.HighlightDialog;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class HighlightDialogTest {

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
    public void testNewHighlightDialogValidation_wordRegexOnlyRequired() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ObservableList<HighlightPackage> packages = FXCollections.observableArrayList();
                HighlightDialog dialog = new HighlightDialog(null, packages);

                // Initial state: Save button is enabled
                assertFalse(dialog.getSaveButton().isDisable(), "Save button must be enabled initially");
                assertEquals("Enter word to highlight...", dialog.getWordField().getPromptText());
                assertEquals("Friendly name (optional)...", dialog.getNameField().getPromptText());

                // Click Save when empty -> fails validation
                assertFalse(dialog.validateAndHighlight(), "Validation must fail when Word/Regex field is empty");

                // Only Word/Regex field should show "Required" with red border
                assertEquals("Required", dialog.getWordField().getPromptText());
                assertTrue(dialog.getWordField().getStyle().contains("#ff5555"));

                // Friendly Name is optional and must NOT have error styling
                assertEquals("Friendly name (optional)...", dialog.getNameField().getPromptText());
                assertEquals("", dialog.getNameField().getStyle());

                // Fill Word/Regex field without filling Friendly Name
                dialog.getWordField().setText("FATAL_ERROR");
                assertEquals("", dialog.getWordField().getStyle(), "Typing into wordField must clear red border");
                assertEquals("Enter word to highlight...", dialog.getWordField().getPromptText());

                // Validation must pass because only Word/Regex is required
                assertTrue(dialog.validateAndHighlight(), "Validation must pass when Word/Regex is filled, even with empty Friendly Name");

                // Clear Word/Regex -> validation fails again
                dialog.getWordField().setText("   ");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getWordField().getPromptText());
                assertTrue(dialog.getWordField().getStyle().contains("#ff5555"));

                // Restore Word/Regex
                dialog.getWordField().setText("WARN");
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
    public void testEditExistingHighlightValidation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                WordHighlight existing = new WordHighlight("INFO", "#00FF00");
                existing.setId("custom-info-1");
                existing.setName("Info logs");

                HighlightDialog dialog = new HighlightDialog(existing, null);

                // Initial state of valid highlight passes validation
                assertTrue(dialog.validateAndHighlight());
                assertFalse(dialog.getSaveButton().isDisable());

                // Clear Word/Regex -> validation fails and flags Required
                dialog.getWordField().setText("");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getWordField().getPromptText());
                assertTrue(dialog.getWordField().getStyle().contains("#ff5555"));

                // Restore Word/Regex -> passes validation
                dialog.getWordField().setText("DEBUG");
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
