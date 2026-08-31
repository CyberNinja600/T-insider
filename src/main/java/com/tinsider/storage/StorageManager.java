package com.tinsider.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tinsider.model.HostProfile;
import com.tinsider.model.Snippet;
import com.tinsider.model.SnippetPackage;
import com.tinsider.model.WordHighlight;
import com.tinsider.model.HighlightPackage;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageManager {
    private static final String CONFIG_DIR = "./config";
    private static final String HOSTS_FILE = CONFIG_DIR + "/hosts.json";
    private static final String SNIPPETS_FILE = CONFIG_DIR + "/snippets.json";
    private static final String SETTINGS_FILE = CONFIG_DIR + "/settings.json";
    private static final String PACKAGES_FILE = CONFIG_DIR + "/packages.json";
    private static final String HIGHLIGHT_PACKAGES_FILE = CONFIG_DIR + "/highlight_packages.json";
    private static final String HIGHLIGHTS_FILE = CONFIG_DIR + "/highlights.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    static {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) {
            return new HashMap<>();
        }
        try (Reader reader = new FileReader(file)) {
            Type mapType = new TypeToken<HashMap<String, String>>(){}.getType();
            Map<String, String> map = gson.fromJson(reader, mapType);
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static void saveSettings(Map<String, String> settings) {
        try (Writer writer = new FileWriter(SETTINGS_FILE)) {
            gson.toJson(settings, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<HostProfile> loadHosts() {
        File file = new File(HOSTS_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<HostProfile>>(){}.getType();
            List<HostProfile> list = gson.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveHosts(List<HostProfile> hosts) {
        try (Writer writer = new FileWriter(HOSTS_FILE)) {
            gson.toJson(hosts, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Snippet> loadSnippets() {
        File file = new File(SNIPPETS_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<Snippet>>(){}.getType();
            List<Snippet> list = gson.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveSnippets(List<Snippet> snippets) {
        try (Writer writer = new FileWriter(SNIPPETS_FILE)) {
            gson.toJson(snippets, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<SnippetPackage> loadPackages() {
        File file = new File(PACKAGES_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<SnippetPackage>>(){}.getType();
            List<SnippetPackage> list = gson.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void savePackages(List<SnippetPackage> packages) {
        try (Writer writer = new FileWriter(PACKAGES_FILE)) {
            gson.toJson(packages, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<WordHighlight> loadHighlights() {
        File file = new File(HIGHLIGHTS_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<WordHighlight>>(){}.getType();
            List<WordHighlight> list = gson.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveHighlights(List<WordHighlight> highlights) {
        try (Writer writer = new FileWriter(HIGHLIGHTS_FILE)) {
            gson.toJson(highlights, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<HighlightPackage> loadHighlightPackages() {
        File file = new File(HIGHLIGHT_PACKAGES_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<HighlightPackage>>(){}.getType();
            List<HighlightPackage> list = gson.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveHighlightPackages(List<HighlightPackage> highlightPackages) {
        try (Writer writer = new FileWriter(HIGHLIGHT_PACKAGES_FILE)) {
            gson.toJson(highlightPackages, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
