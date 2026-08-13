package com.tinsider.util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityLogger {
    private static final String LOG_DIR = "./logs";
    private static final String LOG_FILE = LOG_DIR + "/activity.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "activity-logger-thread");
        thread.setDaemon(true);
        return thread;
    });

    static {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // Clear the activity log file on launch so it doesn't stack across runs!
            File logFile = new File(LOG_FILE);
            if (logFile.exists()) {
                logFile.delete();
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize log: " + e.getMessage());
        }
    }

    public static void log(String level, String category, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String threadName = Thread.currentThread().getName();
        String logEntry = String.format("[%s] [%s] [%s] [%s] - %s", timestamp, level, threadName, category, message);
        
        // Print to standard out as well so it's visible in console
        System.out.println(logEntry);

        EXECUTOR.submit(() -> {
            try (FileWriter fw = new FileWriter(LOG_FILE, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(logEntry);
            } catch (Exception e) {
                System.err.println("Failed to write to activity log file: " + e.getMessage());
            }
        });
    }

    public static void logUserAction(String action, String details) {
        log("INFO", "USER_ACTION", String.format("%s: %s", action, details));
    }

    public static void logTerminalFocusChange(String terminalName, String details) {
        log("INFO", "TERMINAL_FOCUS", String.format("[%s] %s", terminalName, details));
    }

    public static void logTerminalDeath(String terminalName, String reason) {
        log("WARN", "TERMINAL_DEATH", String.format("[%s] %s", terminalName, reason));
    }

    public static void logTerminalChange(String terminalName, String action, String details) {
        log("INFO", "TERMINAL_CHANGE", String.format("[%s] %s: %s", terminalName, action, details));
    }

    public static void logSystemEvent(String event, String details) {
        log("INFO", "SYSTEM_EVENT", String.format("%s: %s", event, details));
    }
}
