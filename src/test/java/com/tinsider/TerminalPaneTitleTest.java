package com.tinsider;

import com.tinsider.config.TerminalTheme;
import com.tinsider.model.HostProfile;
import com.tinsider.ui.components.terminal.split.TerminalPane;
import com.tinsider.ui.components.terminal.split.TerminalSplitPane;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalPaneTitleTest {

    @BeforeAll
    public static void initJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            // Toolkit already started
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testTemporaryRenameAndSplitPreservesOriginalTitle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TerminalTheme defaultTheme = new TerminalTheme("Default");
                TerminalSplitPane splitPane = new TerminalSplitPane(() -> defaultTheme, () -> {});

                // 1. Create a local shell terminal pane (e.g. CMD)
                TerminalPane pane = new TerminalPane(splitPane, "LOCAL", "CMD", null, "CMD");
                assertEquals("CMD", pane.getTitle());
                assertEquals("CMD", pane.getOriginalTitle());

                // 2. User temporarily renames the terminal pane title
                pane.setTitle("my-temp-job");
                assertEquals("my-temp-job", pane.getTitle());
                assertEquals("CMD", pane.getOriginalTitle());

                // 3. User splits the terminal pane -> new empty partition pane created
                TerminalPane splitEmptyPane = new TerminalPane(splitPane, "EMPTY", null, null, "Drop Tab Here", pane);
                assertEquals("CMD", splitEmptyPane.getParentTitle());

                // 4. User opens/connects the new split terminal
                splitPane.initiatePaneConnectionFromTemplate(
                        splitEmptyPane,
                        splitEmptyPane.getParentSessionType(),
                        splitEmptyPane.getParentLocalShellType(),
                        splitEmptyPane.getParentHostProfile(),
                        splitEmptyPane.getParentTitle()
                );

                // The new terminal pane title MUST be the original "CMD", not "my-temp-job"
                assertEquals("CMD", splitEmptyPane.getTitle());
                assertEquals("CMD", splitEmptyPane.getOriginalTitle());
                // The original pane still retains its temporary name
                assertEquals("my-temp-job", pane.getTitle());

                // 5. Test resetting title back to original
                pane.resetTitleToOriginal();
                assertEquals("CMD", pane.getTitle());

            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testSshTemporaryRenameAndSplitPreservesOriginalTitle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TerminalTheme defaultTheme = new TerminalTheme("Default");
                TerminalSplitPane splitPane = new TerminalSplitPane(() -> defaultTheme, () -> {});

                HostProfile host = new HostProfile("prod-server", "192.168.1.50", 22, "root", "secret");
                TerminalPane sshPane = new TerminalPane(splitPane, "SSH", null, host, "prod-server");
                assertEquals("prod-server", sshPane.getTitle());
                assertEquals("prod-server", sshPane.getOriginalTitle());

                // User temporarily changes title to "worker-node-1"
                sshPane.setTitle("worker-node-1");
                assertEquals("worker-node-1", sshPane.getTitle());
                assertEquals("prod-server", sshPane.getOriginalTitle());

                // Split creates new pane from template
                TerminalPane newSplit = new TerminalPane(splitPane, "EMPTY", null, null, "Drop Tab Here", sshPane);
                assertEquals("prod-server", newSplit.getParentTitle());

                // Emulate connecting via initiatePaneConnectionFromTemplate
                splitPane.initiatePaneConnectionFromTemplate(
                        newSplit,
                        newSplit.getParentSessionType(),
                        newSplit.getParentLocalShellType(),
                        newSplit.getParentHostProfile(),
                        newSplit.getParentTitle()
                );

                assertEquals("prod-server", newSplit.getTitle());
                assertEquals("prod-server", newSplit.getOriginalTitle());
                assertEquals("worker-node-1", sshPane.getTitle());
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
