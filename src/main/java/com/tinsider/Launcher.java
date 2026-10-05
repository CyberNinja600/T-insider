package com.tinsider;

import com.tinsider.ui.MainApp;
import com.tinsider.util.StartupSignal;

public class Launcher {
    public static void main(String[] args) {
        // Force Java Swing/AWT to use Windows ClearType / native subpixel anti-aliasing for incredibly crisp, sharp font rendering
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Silence redundant Apache MINA SSHD log warnings to keep console output perfectly clean
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.sshd", "error");
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier", "error");
        System.setProperty("javafx.embed.singleThread", "true");

        StartupSignal.signalApplicationStarting();
        MainApp.main(args);
    }
}
