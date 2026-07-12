package com.inari.musicstreamer.gui;

import com.inari.musicstreamer.BinarySetup;
import com.inari.musicstreamer.MusicStreamerConfig;
import com.inari.musicstreamer.MusicStreamerMod;
import com.inari.musicstreamer.audio.AudioStreamEngine;
import com.inari.musicstreamer.audio.YtDlpResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MusicStreamerPathsScreen extends Screen {
    private final Screen parent;
    private EditBox ytDlpBox;
    private EditBox ffmpegBox;
    private String statusText = "";

    public MusicStreamerPathsScreen(Screen parent) {
        super(Component.literal("Music Streamer - パス設定"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int boxWidth = 300;
        int centerX = this.width / 2;

        ytDlpBox = new EditBox(this.font, centerX - boxWidth / 2, 50, boxWidth, 20,
                Component.literal("yt-dlpのパス"));
        ytDlpBox.setMaxLength(300);
        ytDlpBox.setValue(MusicStreamerMod.config.ytDlpPath);
        this.addRenderableWidget(ytDlpBox);

        ffmpegBox = new EditBox(this.font, centerX - boxWidth / 2, 90, boxWidth, 20,
                Component.literal("ffmpegのパス"));
        ffmpegBox.setMaxLength(300);
        ffmpegBox.setValue(MusicStreamerMod.config.ffmpegPath);
        this.addRenderableWidget(ffmpegBox);

        // ここが新規: ワンクリック自動ダウンロード
        this.addRenderableWidget(Button.builder(Component.literal("自動セットアップ (yt-dlp + ffmpeg)"), button -> {
            button.active = false;
            statusText = "ダウンロード中... (数十秒かかることがあります)";
            runAutoSetup();
        }).bounds(centerX - 150, 125, 300, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("保存して閉じる"), button -> {
            MusicStreamerConfig config = MusicStreamerMod.config;
            config.ytDlpPath = ytDlpBox.getValue().trim();
            config.ffmpegPath = ffmpegBox.getValue().trim();
            config.save();
            applyConfig(config);
            this.minecraft.setScreen(parent);
        }).bounds(centerX - 100, 160, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("キャンセル"), button ->
                this.minecraft.setScreen(parent)
        ).bounds(centerX - 100, 185, 200, 20).build());
    }

    private void runAutoSetup() {
        Minecraft client = Minecraft.getInstance();
        Thread thread = new Thread(() -> {
            String ytDlpResult = null;
            String ffmpegResult = null;
            String error = null;
            try {
                ytDlpResult = BinarySetup.downloadYtDlp();
            } catch (Exception e) {
                error = "yt-dlpのダウンロード失敗: " + e.getMessage();
            }
            if (error == null && BinarySetup.isWindows()) {
                try {
                    ffmpegResult = BinarySetup.downloadFfmpegWindows();
                } catch (Exception e) {
                    error = "ffmpegのダウンロード失敗: " + e.getMessage();
                }
            }

            String finalYtDlp = ytDlpResult;
            String finalFfmpeg = ffmpegResult;
            String finalError = error;
            client.execute(() -> { // レンダースレッドに戻して安全にUI/設定を更新する
                if (finalError != null) {
                    statusText = finalError;
                } else {
                    if (finalYtDlp != null) ytDlpBox.setValue(finalYtDlp);
                    if (finalFfmpeg != null) {
                        ffmpegBox.setValue(finalFfmpeg);
                    } else if (!BinarySetup.isWindows()) {
                        statusText = "yt-dlpは自動取得しました。ffmpegはWindows以外は手動インストールが必要です";
                    }
                    if (finalFfmpeg != null) {
                        statusText = "完了しました。「保存して閉じる」を押してください";
                    }
                    MusicStreamerConfig config = MusicStreamerMod.config;
                    config.ytDlpPath = ytDlpBox.getValue().trim();
                    config.ffmpegPath = ffmpegBox.getValue().trim();
                    config.save();
                    applyConfig(config);
                }
            });
        }, "musicstreamer-binary-setup");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyConfig(MusicStreamerConfig config) {
        MusicStreamerMod.resolver = new YtDlpResolver(config.ytDlpPath);
        MusicStreamerMod.audioEngine = new AudioStreamEngine(MusicStreamerMod.resolver, config.ffmpegPath);
        MusicStreamerMod.audioEngine.setOnError(msg ->
                MusicStreamerMod.LOGGER.warn("AudioStreamEngine error: {}", msg));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!statusText.isEmpty()) {
            int textWidth = this.font.width(statusText);
            graphics.text(this.font, statusText, this.width / 2 - textWidth / 2, 215, 0xFFFFFF, false);
        }
    }
}