package com.tinsider.util;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class LiveReloader {
    public static void startWatching(String dirPath, Runnable onFileChanged) {
        Thread watcherThread = new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path path = Paths.get(dirPath);
                if (!Files.exists(path)) return;
                
                path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                
                long lastReload = 0;
                while (true) {
                    WatchKey key = watchService.take();
                    boolean changed = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            changed = true;
                        }
                    }
                    if (changed) {
                        long now = System.currentTimeMillis();
                        if (now - lastReload > 500) { // 500ms debounce
                            lastReload = now;
                            // Wait a tiny bit for the file write to finish completely before reading
                            TimeUnit.MILLISECONDS.sleep(100);
                            onFileChanged.run();
                        }
                    }
                    if (!key.reset()) break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
    }
}