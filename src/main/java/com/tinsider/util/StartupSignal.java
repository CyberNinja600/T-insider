package com.tinsider.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StartupSignal {
    private static final String PROGRESS_FILE_ENV = "TINSIDER_LOADER_PROGRESS_FILE";

    private StartupSignal() {
    }

    public static void signalApplicationStarting() {
        Path progressPath = getProgressPath();
        if (progressPath == null) {
            return;
        }

        try {
            Files.writeString(progressPath, "100|Starting T-Insider", StandardCharsets.US_ASCII);
        } catch (IOException ignored) {
        }
    }

    public static void signalApplicationShown() {
        Path progressPath = getProgressPath();
        if (progressPath == null) {
            return;
        }

        try {
            Files.writeString(Path.of(progressPath + ".ready"), "ready", StandardCharsets.US_ASCII);
        } catch (IOException ignored) {
        }
    }

    private static Path getProgressPath() {
        String progressFile = System.getenv(PROGRESS_FILE_ENV);
        if (progressFile == null || progressFile.isBlank()) {
            return null;
        }
        return Path.of(progressFile);
    }
}