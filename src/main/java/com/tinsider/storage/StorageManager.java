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

    public static final String DEFAULT_PACKAGE_ID = "f5e18c03-9b15-4855-b39a-fa8892dad35f";
    private static final HighlightPackage DEFAULT_PACKAGE;
    private static final List<WordHighlight> DEFAULT_HIGHLIGHTS = new ArrayList<>();

    static {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Initialize DEFAULT_PACKAGE
        DEFAULT_PACKAGE = new HighlightPackage();
        DEFAULT_PACKAGE.setId(DEFAULT_PACKAGE_ID);
        DEFAULT_PACKAGE.setName("Default");

        // Initialize DEFAULT_HIGHLIGHTS
        WordHighlight h1 = new WordHighlight();
        h1.setId("676135c5-4714-46e8-8792-7af62181095e");
        h1.setWord("ERROR");
        h1.setColorHex("#E64D4D");
        h1.setCaseInsensitive(true);
        h1.setPackageId(DEFAULT_PACKAGE_ID);
        h1.setRegex(false);
        h1.setName("");
        DEFAULT_HIGHLIGHTS.add(h1);

        WordHighlight h2 = new WordHighlight();
        h2.setId("df445c6d-72f6-4083-b9ed-7313a452007f");
        h2.setWord("WARN");
        h2.setColorHex("#FFFF00");
        h2.setCaseInsensitive(true);
        h2.setPackageId(DEFAULT_PACKAGE_ID);
        h2.setRegex(false);
        h2.setName("");
        DEFAULT_HIGHLIGHTS.add(h2);

        WordHighlight h3 = new WordHighlight();
        h3.setId("bbddfd51-72f7-4430-b58c-3783bf1f0782");
        h3.setWord("\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2},\\d{3}");
        h3.setColorHex("#996699");
        h3.setCaseInsensitive(true);
        h3.setPackageId(DEFAULT_PACKAGE_ID);
        h3.setRegex(true);
        h3.setName("Time");
        DEFAULT_HIGHLIGHTS.add(h3);

        WordHighlight h4 = new WordHighlight();
        h4.setId("b6751afd-bf76-49ca-9513-ee013c322448");
        h4.setWord("DEBUG");
        h4.setColorHex("#FFB366");
        h4.setCaseInsensitive(true);
        h4.setPackageId(DEFAULT_PACKAGE_ID);
        h4.setRegex(false);
        h4.setName("");
        DEFAULT_HIGHLIGHTS.add(h4);

        WordHighlight h5 = new WordHighlight();
        h5.setId("b917d8cf-6367-42bd-83d2-aec058c10720");
        h5.setWord("INFO");
        h5.setColorHex("#00FF00");
        h5.setCaseInsensitive(true);
        h5.setPackageId(DEFAULT_PACKAGE_ID);
        h5.setRegex(false);
        h5.setName("");
        DEFAULT_HIGHLIGHTS.add(h5);
    }

    public static boolean isDefaultHighlight(String id) {
        if (id == null) return false;
        for (WordHighlight wh : DEFAULT_HIGHLIGHTS) {
            if (id.equals(wh.getId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDefaultPackage(String id) {
        return DEFAULT_PACKAGE_ID.equals(id);
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
        List<WordHighlight> list = new ArrayList<>();
        list.addAll(DEFAULT_HIGHLIGHTS);

        File file = new File(HIGHLIGHTS_FILE);
        if (!file.exists()) {
            return list;
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<WordHighlight>>(){}.getType();
            List<WordHighlight> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                for (WordHighlight wh : loaded) {
                    if (!isDefaultHighlight(wh.getId())) {
                        list.add(wh);
                    }
                }
            }
            return list;
        } catch (Exception e) {
            e.printStackTrace();
            return list;
        }
    }

    public static void saveHighlights(List<WordHighlight> highlights) {
        List<WordHighlight> toSave = new ArrayList<>();
        if (highlights != null) {
            for (WordHighlight wh : highlights) {
                if (!isDefaultHighlight(wh.getId())) {
                    toSave.add(wh);
                }
            }
        }
        try (Writer writer = new FileWriter(HIGHLIGHTS_FILE)) {
            gson.toJson(toSave, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<HighlightPackage> loadHighlightPackages() {
        List<HighlightPackage> list = new ArrayList<>();
        list.add(DEFAULT_PACKAGE);

        File file = new File(HIGHLIGHT_PACKAGES_FILE);
        if (!file.exists()) {
            return list;
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<HighlightPackage>>(){}.getType();
            List<HighlightPackage> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                for (HighlightPackage hp : loaded) {
                    if (!isDefaultPackage(hp.getId())) {
                        list.add(hp);
                    }
                }
            }
            return list;
        } catch (Exception e) {
            e.printStackTrace();
            return list;
        }
    }

    public static void saveHighlightPackages(List<HighlightPackage> highlightPackages) {
        List<HighlightPackage> toSave = new ArrayList<>();
        if (highlightPackages != null) {
            for (HighlightPackage hp : highlightPackages) {
                if (!isDefaultPackage(hp.getId())) {
                    toSave.add(hp);
                }
            }
        }
        try (Writer writer = new FileWriter(HIGHLIGHT_PACKAGES_FILE)) {
            gson.toJson(toSave, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
