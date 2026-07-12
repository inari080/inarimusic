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
    private Button setupButton; // 処理完了後に再アクティブ化するためフィールドに保持
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

        // ワンクリック自動ダウンロード（Mac / Linux にも対応）
        this.setupButton = this.addRenderableWidget(Button.builder(Component.literal("自動セットアップ (yt-dlp + ffmpeg)"), button -> {
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
        String currentYtDlp = ytDlpBox.getValue().trim();
        String currentFfmpeg = ffmpegBox.getValue().trim();

        sendChat("Music Streamer: 自動セットアップを開始します...");

        Thread thread = new Thread(() -> {
            String ytDlpResult = null;
            String ffmpegResult = null;
            String error = null;
            boolean ytDlpSkipped = false;
            boolean ffmpegSkipped = false;

            if (BinarySetup.isYtDlpWorking(currentYtDlp)) {
                ytDlpSkipped = true;
            } else {
                try {
                    sendChat("yt-dlpをダウンロード中...");
                    ytDlpResult = BinarySetup.downloadYtDlp(percent ->
                            postProgress("yt-dlp", percent));
                    sendChat("yt-dlpのダウンロードが完了しました");
                } catch (Exception e) {
                    error = "yt-dlpのダウンロード失敗: " + e.getMessage();
                    sendChat(error);
                }
            }

            if (error == null && BinarySetup.isWindows()) {
                if (BinarySetup.isFfmpegWorking(currentFfmpeg)) {
                    ffmpegSkipped = true;
                } else {
                    try {
                        sendChat("ffmpegをダウンロード中...");
                        ffmpegResult = BinarySetup.downloadFfmpegWindows(percent ->
                                postProgress("ffmpeg", percent));
                        sendChat("ffmpegのダウンロードが完了しました");
                    } catch (Exception e) {
                        error = "ffmpegのダウンロード失敗: " + e.getMessage();
                        sendChat(error);
                    }
                }
            }

            String finalYtDlp = ytDlpResult;
            String finalFfmpeg = ffmpegResult;
            String finalError = error;
            boolean finalYtDlpSkipped = ytDlpSkipped;
            boolean finalFfmpegSkipped = ffmpegSkipped;

            client.execute(() -> {
                if (finalYtDlp != null) ytDlpBox.setValue(finalYtDlp);
                if (finalFfmpeg != null) ffmpegBox.setValue(finalFfmpeg);

                MusicStreamerConfig config = MusicStreamerMod.config;
                config.ytDlpPath = ytDlpBox.getValue().trim();
                config.ffmpegPath = ffmpegBox.getValue().trim();
                config.save();
                applyConfig(config);

                if (finalError != null) {
                    statusText = finalError;
                } else if (finalYtDlpSkipped && (finalFfmpegSkipped || !BinarySetup.isWindows())) {
                    statusText = "既に両方とも利用可能です。変更はありません";
                    sendChat("Music Streamer: 既に両方とも利用可能です");
                } else {
                    StringBuilder sb = new StringBuilder("完了しました。");
                    sb.append(finalYtDlpSkipped ? "yt-dlp: 既存を利用 / " : "yt-dlp: 新規取得 / ");
                    if (BinarySetup.isWindows()) {
                        sb.append(finalFfmpegSkipped ? "ffmpeg: 既存を利用" : "ffmpeg: 新規取得");
                    } else {
                        sb.append("ffmpeg: 手動インストールが必要です");
                    }
                    statusText = sb.toString();
                    sendChat("Music Streamer: セットアップ完了 (" + statusText.replace("完了しました。", "") + ")");
                }
            });
        }, "musicstreamer-binary-setup");
        thread.setDaemon(true);
        thread.start();
    }

    // 10%刻みでのみチャットに出す(1%ごとだとスパムになるため)
    private int lastPostedYtDlpPercent = -1;
    private int lastPostedFfmpegPercent = -1;

    private void postProgress(String label, int percent) {
        int rounded = (percent / 10) * 10;
        boolean isYtDlp = label.equals("yt-dlp");
        int last = isYtDlp ? lastPostedYtDlpPercent : lastPostedFfmpegPercent;
        if (rounded == last) return;
        if (isYtDlp) lastPostedYtDlpPercent = rounded; else lastPostedFfmpegPercent = rounded;

        Minecraft.getInstance().execute(() -> {
            statusText = label + " ダウンロード中... " + percent + "%";
            sendChat(label + ": " + rounded + "%");
        });
    }

    private void sendChat(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Music Streamer] " + message));
        }
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
