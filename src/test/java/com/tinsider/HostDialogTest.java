package com.tinsider;

import com.tinsider.model.HostProfile;
import com.tinsider.ui.dialogs.HostDialog;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class HostDialogTest {

    @BeforeAll
    public static void initJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            // Toolkit already initialized
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX initialization timed out");
    }

    @Test
    public void testNewHostDialogValidation_ipPortMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                HostDialog dialog = new HostDialog(null);

                // Initial state: Save button is enabled (never disabled upfront)
                assertTrue(dialog.getIpPortRadio().isSelected());
                assertFalse(dialog.getSaveButton().isDisable(), "Save button should always remain enabled");

                // Verify clean placeholders without '(Required)' or '(Optional)'
                assertEquals("My Server", dialog.getNameField().getPromptText());
                assertEquals("192.168.1.100", dialog.getIpField().getPromptText());
                assertEquals("22", dialog.getPortField().getPromptText());
                assertEquals("username", dialog.getUserField().getPromptText());
                assertEquals("password", dialog.getPassField().getPromptText());

                // Click Save / trigger validation when empty
                boolean valid = dialog.validateAndHighlight();
                assertFalse(valid, "Validation must fail when required fields are empty");

                // Fields should show "Required" with error style
                assertEquals("Required", dialog.getNameField().getPromptText());
                assertTrue(dialog.getNameField().getStyle().contains("#ff5555"));

                assertEquals("Required", dialog.getIpField().getPromptText());
                assertTrue(dialog.getIpField().getStyle().contains("#ff5555"));

                assertEquals("Required", dialog.getUserField().getPromptText());
                assertTrue(dialog.getUserField().getStyle().contains("#ff5555"));

                // Fill name -> error style on name should clear
                dialog.getNameField().setText("My Production Host");
                assertEquals("", dialog.getNameField().getStyle());
                assertEquals("My Server", dialog.getNameField().getPromptText());

                // Validation should still fail for IP and username
                assertFalse(dialog.validateAndHighlight());

                // Fill IP -> error style on IP should clear
                dialog.getIpField().setText("192.168.1.150");
                assertEquals("", dialog.getIpField().getStyle());

                // Validation should still fail for username
                assertFalse(dialog.validateAndHighlight());

                // Fill Username
                dialog.getUserField().setText("admin");
                assertEquals("", dialog.getUserField().getStyle());

                // Now all fields are valid
                assertTrue(dialog.validateAndHighlight(), "When all required fields are filled, validation must pass");

                // Test invalid port (e.g. non-numeric)
                dialog.getPortField().setText("invalid");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getPortField().getPromptText());
                assertTrue(dialog.getPortField().getStyle().contains("#ff5555"));

                // Restore valid port
                dialog.getPortField().setText("2222");
                assertEquals("", dialog.getPortField().getStyle());
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
    public void testNewHostDialogValidation_urlMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                HostDialog dialog = new HostDialog(null);

                // Switch to Connection URL mode
                dialog.getUrlRadio().fire();
                assertTrue(dialog.getUrlRadio().isSelected());
                assertTrue(dialog.getIpField().isDisable());
                assertTrue(dialog.getPortField().isDisable());
                assertFalse(dialog.getUrlField().isDisable());

                // Placeholders clean
                assertEquals("user@domain.com:port", dialog.getUrlField().getPromptText());

                // Trigger validation when empty
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getNameField().getPromptText());
                assertEquals("Required", dialog.getUserField().getPromptText());
                assertEquals("Required", dialog.getUrlField().getPromptText());

                // Fill name
                dialog.getNameField().setText("Cloud Server");
                assertFalse(dialog.validateAndHighlight());

                // Fill username
                dialog.getUserField().setText("admin");
                assertFalse(dialog.validateAndHighlight());

                // Fill URL
                dialog.getUrlField().setText("ssh://user@cloud.example.com:22");
                assertTrue(dialog.validateAndHighlight(), "With name, username, and url filled, validation must pass");

                // Clear URL -> validation fails
                dialog.getUrlField().setText("");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getUrlField().getPromptText());

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
    public void testEditExistingHostDialogValidation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                HostProfile existing = new HostProfile("Prod Server", "192.168.1.100", 22, "admin", "secret");
                HostDialog dialog = new HostDialog(existing);

                // Existing valid profile should pass validation immediately
                assertTrue(dialog.validateAndHighlight());
                assertFalse(dialog.getSaveButton().isDisable());

                // Clear name and validate -> fails and marks name as Required
                dialog.getNameField().setText("");
                assertFalse(dialog.validateAndHighlight());
                assertEquals("Required", dialog.getNameField().getPromptText());

                // Restore name -> passes validation
                dialog.getNameField().setText("Prod Server Updated");
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

