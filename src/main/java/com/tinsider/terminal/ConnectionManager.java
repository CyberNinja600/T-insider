package com.tinsider.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.tinsider.model.HostProfile;
import com.tinsider.model.ParsedUrl;
import com.tinsider.ui.dialogs.AlertHelper;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ConnectionManager {

    public static PtyTtyConnector openLocalTerminal(String type) throws Exception {
        String[] cmd;
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        if (!isWindows) {
            String shell = System.getenv("SHELL");
            if (shell == null || shell.isEmpty()) {
                shell = "/bin/bash";
            }
            if (shell.endsWith("bash") || shell.endsWith("zsh") || shell.endsWith("sh")) {
                cmd = new String[]{shell, "-i", "-l"};
            } else {
                cmd = new String[]{shell, "-i"};
            }
        } else {
            if (type.equalsIgnoreCase("PowerShell")) {
                cmd = new String[]{"powershell.exe"};
            } else if (type.equalsIgnoreCase("Bash")) {
                String bashPath = findWindowsBash();
                cmd = new String[]{bashPath, "--login"};
            } else {
                cmd = new String[]{"cmd.exe"};
            }
        }

        Map<String, String> envs = new HashMap<>(System.getenv());
        envs.put("TERM", "xterm-256color");

        PtyProcess process = new PtyProcessBuilder()
                .setCommand(cmd)
                .setEnvironment(envs)
                .start();

        String tabTitle = truncate(type, 16);
        return new PtyTtyConnector(process, tabTitle);
    }

    private static String findWindowsBash() {
        // 1. Check C:\Program Files\Git\bin\bash.exe
        File path1 = new File("C:\\Program Files\\Git\\bin\\bash.exe");
        if (path1.exists()) return path1.getAbsolutePath();

        // 2. Check C:\Program Files (x86)\Git\bin\bash.exe
        File path2 = new File("C:\\Program Files (x86)\\Git\\bin\\bash.exe");
        if (path2.exists()) return path2.getAbsolutePath();

        // 3. Check user-local AppData Git Bash
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            File path3 = new File(userHome + "\\AppData\\Local\\Programs\\Git\\bin\\bash.exe");
            if (path3.exists()) return path3.getAbsolutePath();
        }

        // 4. Try to resolve via environment PATH of git.exe or bash.exe
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String part : pathEnv.split(File.pathSeparator)) {
                File candidate = new File(part, "git.exe");
                if (candidate.exists()) {
                    File parent = candidate.getParentFile();
                    if (parent != null) {
                        File gitRoot = parent.getParentFile();
                        if (gitRoot != null) {
                            File bash1 = new File(gitRoot, "bin\\bash.exe");
                            if (bash1.exists()) return bash1.getAbsolutePath();
                            File bash2 = new File(gitRoot, "usr\\bin\\bash.exe");
                            if (bash2.exists()) return bash2.getAbsolutePath();
                        }
                    }
                }
                
                File bashCandidate = new File(part, "bash.exe");
                if (bashCandidate.exists()) {
                    return bashCandidate.getAbsolutePath();
                }
            }
        }

        // 5. Check if WSL is available
        File wslBash = new File("C:\\Windows\\System32\\bash.exe");
        if (wslBash.exists()) return wslBash.getAbsolutePath();

        return "bash.exe"; // Fallback to PATH resolution at runtime
    }

    public static void connectToHost(HostProfile host, TabPane tabPane, BiConsumer<String, SshTtyConnector> onSuccess) {
        final String connectUser;
        final String connectIp;
        final int connectPort;

        if (host.getUrl() != null && !host.getUrl().isEmpty()) {
            ParsedUrl parsed = ParsedUrl.parseSshUrl(host.getUrl(), host.getUsername());
            connectUser = parsed.getUsername();
            connectIp = parsed.getHost();
            connectPort = parsed.getPort();
        } else {
            connectUser = host.getUsername();
            connectIp = host.getIp();
            connectPort = host.getPort();
        }

        Tab previousTab = tabPane.getSelectionModel().getSelectedItem();
        Tab loadingTab = new Tab("Connecting " + truncate(host.getName(), 5) + "...");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("connecting-spinner");
        Label connLabel = new Label("Connecting to " + connectIp + ":" + connectPort + "...");
        connLabel.getStyleClass().add("connecting-label");
        VBox vBox = new VBox(15, connLabel, spinner);
        vBox.setAlignment(Pos.CENTER);
        vBox.getStyleClass().add("connecting-pane");
        loadingTab.setContent(vBox);

        // Insert loadingTab before the "+" Tab
        int addTabIndex = -1;
        for (int i = 0; i < tabPane.getTabs().size(); i++) {
            if (tabPane.getTabs().get(i).getText().equals("+")) {
                addTabIndex = i;
                break;
            }
        }
        if (addTabIndex >= 0) {
            tabPane.getTabs().add(addTabIndex, loadingTab);
        } else {
            tabPane.getTabs().add(loadingTab);
        }
        tabPane.getSelectionModel().select(loadingTab);

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
                    com.tinsider.util.ActivityLogger.logSystemEvent("SSH_CONNECTION_SUCCESS", "Successfully established SSH connection to host: " + host.getName());
                    // Create the new SSH tab first and select it
                    SshTtyConnector connector = new SshTtyConnector(finalClient, finalSession, finalChannel, host.getName());
                    onSuccess.accept(truncate(host.getName(), 16), connector);
                    // Only then remove the loading tab to prevent fallback selection of "+"
                    tabPane.getTabs().remove(loadingTab);
                });

            } catch (Exception e) {
                e.printStackTrace();
                if (channel != null) {
                    try { channel.close(true); } catch (Exception ignored) {}
                }
                if (session != null) {
                    try { session.close(true); } catch (Exception ignored) {}
                }
                if (client != null) {
                    try { client.stop(); } catch (Exception ignored) {}
                }

                Platform.runLater(() -> {
                    com.tinsider.util.ActivityLogger.logSystemEvent("SSH_CONNECTION_FAILURE", "Failed to connect to host: " + host.getName() + ". Error: " + e.getMessage());
                    // Re-select previous tab if valid before removing loadingTab on failure
                    if (previousTab != null && tabPane.getTabs().contains(previousTab)) {
                        tabPane.getSelectionModel().select(previousTab);
                    }
                    tabPane.getTabs().remove(loadingTab);
                    AlertHelper.showErrorAlert("SSH Connection Failed", 
                            "Could not connect to " + host.getIp() + ":" + host.getPort() + "\n\nError: " + e.getMessage());
                });
            }
        });
        sshThread.setDaemon(true);
        sshThread.start();
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
