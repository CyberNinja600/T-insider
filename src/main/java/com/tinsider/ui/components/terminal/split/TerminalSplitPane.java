package com.tinsider.ui.components.terminal.split;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.tinsider.config.TerminalTheme;
import com.tinsider.model.HostProfile;
import com.tinsider.model.ParsedUrl;
import com.tinsider.terminal.ConnectionManager;
import com.tinsider.terminal.SshTtyConnector;
import com.tinsider.terminal.TerminalTabFactory;
import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TerminalSplitPane extends BorderPane {
    private static TerminalPane draggedPane; // global tracker for Drag & Drop

    private final Supplier<TerminalTheme> themeSupplier;
    private final Runnable closeTabCallback;
    private SplitNode rootNode;
    private TerminalPane activeTerminalPane;

    public static TerminalPane getDraggedPane() {
        return draggedPane;
    }

    public static void setDraggedPane(TerminalPane pane) {
        draggedPane = pane;
    }

    public TerminalSplitPane(Supplier<TerminalTheme> themeSupplier, Runnable closeTabCallback) {
        this.themeSupplier = themeSupplier;
        this.closeTabCallback = closeTabCallback;
        getStyleClass().add("terminal-split-pane");
    }

    public void setRootNode(SplitNode rootNode) {
        this.rootNode = rootNode;
        setCenter(rootNode.getView());
    }

    public SplitNode getRootNode() {
        return rootNode;
    }

    public TerminalPane getActiveTerminalPane() {
        return activeTerminalPane;
    }

    public void setActiveTerminalPane(TerminalPane pane) {
        if (this.activeTerminalPane == pane) {
            if (pane != null) {
                pane.requestFocusOnTerminal();
            }
            return;
        }

        String oldPaneTitle = (this.activeTerminalPane != null) ? this.activeTerminalPane.getTitle() : "None";
        String newPaneTitle = (pane != null) ? pane.getTitle() : "None";
        com.tinsider.util.ActivityLogger.logTerminalFocusChange(newPaneTitle, "Active terminal sub-pane switched from [" + oldPaneTitle + "] to [" + newPaneTitle + "]");

        this.activeTerminalPane = pane;
        updatePaneThemeStyles(rootNode);

        if (pane != null) {
            pane.requestFocusOnTerminal();
        }
    }

    private void updatePaneThemeStyles(SplitNode node) {
        if (node instanceof SplitLeafNode) {
            ((SplitLeafNode) node).getTerminalPane().updateThemeStyles(themeSupplier.get());
        } else if (node instanceof SplitParentNode) {
            updatePaneThemeStyles(((SplitParentNode) node).getFirstChild());
            updatePaneThemeStyles(((SplitParentNode) node).getSecondChild());
        }
    }

    public void splitLeaf(SplitLeafNode leaf, SplitOrientation orientation) {
        com.tinsider.util.ActivityLogger.logUserAction("SPLIT_PANE", "User split terminal pane [" + leaf.getTerminalPane().getTitle() + "] " + (orientation == SplitOrientation.SIDE_BY_SIDE ? "vertically" : "horizontally"));
        // Create new empty partition pane
        TerminalPane newPane = new TerminalPane(this, "EMPTY", null, null, "Drop Tab Here");
        SplitLeafNode newLeaf = new SplitLeafNode(newPane);

        SplitParentNode parent = leaf.getParent();
        SplitParentNode newParent = new SplitParentNode(leaf, newLeaf, orientation);

        if (parent == null) {
            rootNode = newParent;
            setCenter(newParent.getView());
        } else {
            if (parent.getFirstChild() == leaf) {
                parent.setFirstChild(newParent);
            } else {
                parent.setSecondChild(newParent);
            }
        }

        // Instantly focus and active highlight the newly split empty pane
        setActiveTerminalPane(newPane);
    }

    public void initiatePaneConnectionForLocalCmd(TerminalPane pane) {
        try {
            TtyConnector connector = ConnectionManager.openLocalTerminal("CMD");
            JediTermWidget widget = TerminalTabFactory.createJediTermWidget(connector, themeSupplier);
            pane.setWidget(widget);
        } catch (Exception e) {
            e.printStackTrace();
            pane.showErrorState("Failed to open local CMD: " + e.getMessage());
        }
    }

    public void removeLeaf(SplitLeafNode leaf) {
        com.tinsider.util.ActivityLogger.logUserAction("CLOSE_PANE", "User manually closed terminal sub-pane [" + leaf.getTerminalPane().getTitle() + "]");
        SplitParentNode p = leaf.getParent();
        if (p == null) {
            // Last node in the split tree -> close entire tab!
            closeTabCallback.run();
            return;
        }

        SplitNode sibling = (p.getFirstChild() == leaf) ? p.getSecondChild() : p.getFirstChild();
        SplitParentNode gp = p.getParent();

        if (gp == null) {
            rootNode = sibling;
            sibling.setParent(null);
            setCenter(sibling.getView());
        } else {
            if (gp.getFirstChild() == p) {
                gp.setFirstChild(sibling);
            } else {
                gp.setSecondChild(sibling);
            }
        }

        // Stop the closed pane's process and release memory
        leaf.close();

        // Select and focus the remaining sibling or first available leaf
        TerminalPane nextActive = findFirstLeaf(rootNode);
        if (nextActive != null) {
            setActiveTerminalPane(nextActive);
        }
    }

    public void moveLeaf(SplitLeafNode draggedLeaf, SplitLeafNode targetLeaf, DropPosition dropPos) {
        if (draggedLeaf == targetLeaf) return;

        // 1. Remove draggedLeaf from its current location
        SplitParentNode dragParent = draggedLeaf.getParent();
        if (dragParent == null) return; // Cannot move root leaf if it's the only one

        SplitNode dragSibling = (dragParent.getFirstChild() == draggedLeaf) ? dragParent.getSecondChild() : dragParent.getFirstChild();
        SplitParentNode dragGrandParent = dragParent.getParent();

        if (dragGrandParent == null) {
            // dragParent was the root, so sibling becomes root
            rootNode = dragSibling;
            dragSibling.setParent(null);
            setCenter(dragSibling.getView());
        } else {
            if (dragGrandParent.getFirstChild() == dragParent) {
                dragGrandParent.setFirstChild(dragSibling);
            } else {
                dragGrandParent.setSecondChild(dragSibling);
            }
        }

        // 2. Insert draggedLeaf into its new target position
        SplitParentNode targetParent = targetLeaf.getParent();
        SplitOrientation orient = (dropPos == DropPosition.LEFT || dropPos == DropPosition.RIGHT) ? SplitOrientation.SIDE_BY_SIDE : SplitOrientation.TOP_AND_BOTTOM;
        
        SplitParentNode newParent;
        if (dropPos == DropPosition.LEFT || dropPos == DropPosition.TOP) {
            newParent = new SplitParentNode(draggedLeaf, targetLeaf, orient);
        } else {
            newParent = new SplitParentNode(targetLeaf, draggedLeaf, orient);
        }

        if (targetParent == null) {
            // targetLeaf was the root
            rootNode = newParent;
            setCenter(newParent.getView());
        } else {
            if (targetParent.getFirstChild() == targetLeaf) {
                targetParent.setFirstChild(newParent);
            } else {
                targetParent.setSecondChild(newParent);
            }
        }

        // 3. Highlight and focus the moved leaf
        setActiveTerminalPane(draggedLeaf.getTerminalPane());
    }

    public void splitAndAdoptLeaf(TerminalPane targetPane, TerminalPane sourcePane, DropPosition dropPos) {
        if (targetPane == null || sourcePane == null) return;

        // 1. Create a brand new TerminalPane
        TerminalPane newPane = new TerminalPane(this, sourcePane.getSessionType(), sourcePane.getLocalShellType(), sourcePane.getHostProfile(), sourcePane.getTitle());
        SplitLeafNode newLeaf = new SplitLeafNode(newPane);
        
        // 2. Adopt the widget and settings from sourcePane
        newPane.adoptWidgetAndSettings(sourcePane);

        // 3. Insert newLeaf into targetPane's split structure
        SplitLeafNode targetLeaf = targetPane.getLeafNode();
        if (targetLeaf == null) return;

        SplitParentNode targetParent = targetLeaf.getParent();
        SplitOrientation orient = (dropPos == DropPosition.LEFT || dropPos == DropPosition.RIGHT) ? SplitOrientation.SIDE_BY_SIDE : SplitOrientation.TOP_AND_BOTTOM;

        SplitParentNode newParent;
        if (dropPos == DropPosition.LEFT || dropPos == DropPosition.TOP) {
            newParent = new SplitParentNode(newLeaf, targetLeaf, orient);
        } else {
            newParent = new SplitParentNode(targetLeaf, newLeaf, orient);
        }

        if (targetParent == null) {
            rootNode = newParent;
            setCenter(newParent.getView());
        } else {
            if (targetParent.getFirstChild() == targetLeaf) {
                targetParent.setFirstChild(newParent);
            } else {
                targetParent.setSecondChild(newParent);
            }
        }

        // 4. Highlight and focus the new leaf
        setActiveTerminalPane(newPane);
    }

    private TerminalPane findFirstLeaf(SplitNode node) {
        if (node instanceof SplitLeafNode) {
            return ((SplitLeafNode) node).getTerminalPane();
        } else if (node instanceof SplitParentNode) {
            return findFirstLeaf(((SplitParentNode) node).getFirstChild());
        }
        return null;
    }

    public void initiatePaneConnection(TerminalPane pane) {
        if ("SSH".equals(pane.getSessionType()) && pane.getHostProfile() != null) {
            HostProfile host = pane.getHostProfile();
            pane.showConnectingState();
            com.tinsider.util.ActivityLogger.logUserAction("CONNECT_TO_HOST_ATTEMPT", "Split pane requested connection to host: " + host.getName() + " (" + host.getIp() + ")");

            Thread sshThread = new Thread(() -> {
                SshClient client = null;
                ClientSession session = null;
                ChannelShell channel = null;
                try {
                    client = SshClient.setUpDefaultClient();
                    
                    // Configure keep-alive / ServerAliveInterval so connections don't hang and freeze after being idle
                    org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, java.time.Duration.ofSeconds(15));
                    org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_REQUEST.set(client, "keepalive@openssh.com");
                    
                    client.start();

                    final String connectUser;
                    final String connectIp;
                    final int connectPort;

                    if (host.getUrl() != null && !host.getUrl().isEmpty()) {
                        ParsedUrl parsed = ParsedUrl.parseSshUrl(host.getUrl(), host.getUsername());
                        connectUser = parsed.getUsername() != null ? parsed.getUsername() : host.getUsername();
                        connectIp = parsed.getHost() != null ? parsed.getHost() : host.getIp();
                        connectPort = parsed.getPort() > 0 ? parsed.getPort() : host.getPort();
                    } else {
                        connectUser = host.getUsername();
                        connectIp = host.getIp();
                        connectPort = host.getPort();
                    }

                    session = client.connect(connectUser, connectIp, connectPort)
                            .verify(5000)
                            .getSession();

                    session.addPasswordIdentity(host.getPassword());
                    session.auth().verify(5000);

                    channel = session.createShellChannel();
                    channel.setPtyType("xterm-256color");
                    channel.open().verify(5000);

                    final SshClient finalClient = client;
                    final ClientSession finalSession = session;
                    final ChannelShell finalChannel = channel;

                    Platform.runLater(() -> {
                        com.tinsider.util.ActivityLogger.logSystemEvent("SSH_CONNECTION_SUCCESS", "Successfully established split pane SSH connection to host: " + host.getName());
                        SshTtyConnector connector = new SshTtyConnector(finalClient, finalSession, finalChannel, host.getName());
                        JediTermWidget widget = TerminalTabFactory.createJediTermWidget(connector, themeSupplier);
                        pane.setWidget(widget);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    if (channel != null) { try { channel.close(true); } catch (Exception ignored) {} }
                    if (session != null) { try { session.close(true); } catch (Exception ignored) {} }
                    if (client != null) { try { client.stop(); } catch (Exception ignored) {} }

                    Platform.runLater(() -> {
                        com.tinsider.util.ActivityLogger.logSystemEvent("SSH_CONNECTION_FAILURE", "Split pane failed to connect to host: " + host.getName() + ". Error: " + e.getMessage());
                        pane.showErrorState("SSH Connection Failed: " + e.getMessage());
                    });
                }
            });
            sshThread.setDaemon(true);
            sshThread.start();
        } else {
            // Local Shell Connection
            try {
                com.tinsider.util.ActivityLogger.logUserAction("OPEN_LOCAL_TERMINAL", "Split pane requested open local terminal: " + pane.getLocalShellType());
                TtyConnector connector = ConnectionManager.openLocalTerminal(pane.getLocalShellType());
                JediTermWidget widget = TerminalTabFactory.createJediTermWidget(connector, themeSupplier);
                pane.setWidget(widget);
            } catch (Exception e) {
                e.printStackTrace();
                pane.showErrorState("Failed to open local terminal: " + e.getMessage());
            }
        }
    }

    public List<JediTermWidget> getAllWidgets() {
        List<JediTermWidget> widgets = new ArrayList<>();
        collectWidgets(rootNode, widgets);
        return widgets;
    }

    private void collectWidgets(SplitNode node, List<JediTermWidget> widgets) {
        if (node == null) return;
        if (node instanceof SplitLeafNode) {
            JediTermWidget w = ((SplitLeafNode) node).getTerminalPane().getWidget();
            if (w != null) {
                widgets.add(w);
            }
        } else if (node instanceof SplitParentNode) {
            collectWidgets(((SplitParentNode) node).getFirstChild(), widgets);
            collectWidgets(((SplitParentNode) node).getSecondChild(), widgets);
        }
    }

    public JediTermWidget getActiveWidget() {
        if (activeTerminalPane != null) {
            return activeTerminalPane.getWidget();
        }
        return null;
    }

    public void requestFocusOnActive() {
        if (activeTerminalPane != null) {
            activeTerminalPane.requestFocusOnTerminal();
        }
    }

    public void updateActiveTheme(TerminalTheme theme) {
        updatePaneThemeStyles(rootNode);
    }

    public TerminalTheme getCurrentTheme() {
        return themeSupplier.get();
    }

    public void close() {
        if (rootNode != null) {
            rootNode.close();
        }
    }
}
