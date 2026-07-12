package com.inari.musicstreamer.playlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 保存されたURLの管理と永続化を行う。
 * タグごとにURLをグループ化し、フォルダのように管理する。
 */
public class PlaylistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("musicstreamer_playlists.json");

    private final Map<String, List<SavedUrl>> playlists = new HashMap<>();

    public PlaylistManager() {
        load();
    }

    public void load() {
        if (Files.exists(FILE)) {
            try {
                String json = Files.readString(FILE);
                Type type = new TypeToken<Map<String, List<SavedUrl>>>() {}.getType();
                Map<String, List<SavedUrl>> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    playlists.putAll(loaded);
                }
            } catch (IOException e) {
                // エラーの場合は空の状態で続行
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(playlists));
        } catch (IOException e) {
            // 保存失敗は無視
        }
    }

    public void addUrl(SavedUrl savedUrl) {
        String tag = savedUrl.tag();
        playlists.computeIfAbsent(tag, k -> new ArrayList<>()).add(savedUrl);
        save();
    }

    public void removeUrl(String tag, String url) {
        List<SavedUrl> list = playlists.get(tag);
        if (list != null) {
            list.removeIf(s -> s.url().equals(url));
            if (list.isEmpty()) {
                playlists.remove(tag);
            }
            save();
        }
    }

    public List<SavedUrl> getUrlsByTag(String tag) {
        return playlists.getOrDefault(tag, new ArrayList<>());
    }

    public List<String> getAllTags() {
        return new ArrayList<>(playlists.keySet());
    }

    public Map<String, List<SavedUrl>> getAllPlaylists() {
        return new HashMap<>(playlists);
    }

    public void updateUrlTag(String oldTag, String url, String newTag) {
        List<SavedUrl> oldList = playlists.get(oldTag);
        if (oldList != null) {
            SavedUrl toMove = oldList.stream()
                    .filter(s -> s.url().equals(url))
                    .findFirst()
                    .orElse(null);
            if (toMove != null) {
                oldList.remove(toMove);
                if (oldList.isEmpty()) {
                    playlists.remove(oldTag);
                }
                SavedUrl moved = toMove.withTag(newTag);
                playlists.computeIfAbsent(newTag, k -> new ArrayList<>()).add(moved);
                save();
            }
        }
    }
}
