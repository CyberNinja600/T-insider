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
        if (type.equalsIgnoreCase("PowerShell")) {
            cmd = new String[]{"powershell.exe"};
        } else if (type.equalsIgnoreCase("Bash")) {
            File gitBash = new File("C:\\Program Files\\Git\\bin\\bash.exe");
            if (gitBash.exists()) {
                cmd = new String[]{gitBash.getAbsolutePath(), "--login"};
            } else {
                cmd = new String[]{"bash.exe", "--login"};
            }
        } else {
            cmd = new String[]{"cmd.exe"};
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
        VBox vBox = new VBox(15, new Label("Connecting to " + connectIp + ":" + connectPort + "..."), spinner);
        vBox.setAlignment(Pos.CENTER);
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
