package com.inari.musicstreamer;

import com.example.themedgui.client.config.Setting;

public class MusicStreamerHubConfig {
    @Setting(category = "General", label = "Open Music Player",
            tooltip = "Music Streamerのプレイヤー画面を開く")
    public Runnable openPlayer = () -> {};

    @Setting(category = "General", label = "Change Keybind",
            tooltip = "Music Streamerを開くキー(デフォルト: M)を変更する")
    public Runnable openKeyBinds = () -> {};

    @Setting(category = "General", label = "Configure yt-dlp / ffmpeg Paths",
            tooltip = "yt-dlp / ffmpeg の実行ファイルパスを設定する")
    public Runnable openPaths = () -> {};
}