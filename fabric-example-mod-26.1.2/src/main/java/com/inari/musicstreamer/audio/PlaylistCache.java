package com.inari.musicstreamer.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * プレイリスト解決結果のキャッシュ。
 * 同じURLの再ロードを高速化する。
 */
public class PlaylistCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_FILE = FabricLoader.getInstance().getConfigDir().resolve("musicstreamer_cache.json");
    private static final long CACHE_EXPIRY_HOURS = 24; // 24時間でキャッシュ期限切れ

    private final Map<String, CacheEntry> cache = new HashMap<>();

    public PlaylistCache() {
        load();
    }

    public List<TrackInfo> get(String url) {
        CacheEntry entry = cache.get(url);
        if (entry == null) {
            return null;
        }

        // キャッシュ期限チェック
        if (entry.timestamp().plus(CACHE_EXPIRY_HOURS, ChronoUnit.HOURS).isBefore(Instant.now())) {
            cache.remove(url);
            save();
            return null;
        }

        return entry.tracks();
    }

    public void put(String url, List<TrackInfo> tracks) {
        cache.put(url, new CacheEntry(tracks, Instant.now()));
        save();
    }

    public void clear() {
        cache.clear();
        save();
    }

    private void load() {
        if (Files.exists(CACHE_FILE)) {
            try {
                String json = Files.readString(CACHE_FILE);
                Type type = new TypeToken<Map<String, CacheEntry>>() {}.getType();
                Map<String, CacheEntry> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    cache.putAll(loaded);
                }
            } catch (IOException e) {
                // エラーの場合は空のキャッシュで続行
            }
        }
    }

    private void save() {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Files.writeString(CACHE_FILE, GSON.toJson(cache));
        } catch (IOException e) {
            // 保存失敗は無視
        }
    }

    private record CacheEntry(List<TrackInfo> tracks, Instant timestamp) {
    }
}
