package com.inari.musicstreamer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * yt-dlp / ffmpeg の実行ファイルパスを config/musicstreamer.json に保存する。
 * PATHが通っていない環境向けに、絶対パスを指定できるようにするための設定。
 */
public final class MusicStreamerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("musicstreamer.json");

    public String ytDlpPath = "yt-dlp";
    public String ffmpegPath = "ffmpeg";

    public static MusicStreamerConfig load() {
        if (Files.exists(FILE)) {
            try {
                String json = Files.readString(FILE);
                MusicStreamerConfig loaded = GSON.fromJson(json, MusicStreamerConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException ignored) {
            }
        }
        MusicStreamerConfig defaults = new MusicStreamerConfig();
        defaults.save(); // 初回はデフォルト値でファイルを作っておく(ユーザーが編集しやすいように)
        return defaults;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}