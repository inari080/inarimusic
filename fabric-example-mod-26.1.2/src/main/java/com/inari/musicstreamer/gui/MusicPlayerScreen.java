package com.inari.musicstreamer.gui;

import com.inari.musicstreamer.MusicStreamerMod;
import com.inari.musicstreamer.audio.AudioStreamEngine;
import com.inari.musicstreamer.audio.PlaybackState;
import com.inari.musicstreamer.audio.TrackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * メインの音楽プレイヤー画面。
 *
 * 現状はthemedguimodと未接続(ThemeColorsのデフォルト配色を使用)。
 * 26.1.2のGUIレンダリング刷新の影響で、AbstractWidget/Screenの一部メソッド名が
 * 将来的に変わる可能性がある点は留意(現時点のFabric公式ドキュメント記載のAPIに基づく)。
 */
public class MusicPlayerScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 280;

    private final AudioStreamEngine engine = MusicStreamerMod.audioEngine;

    private EditBox playlistUrlInput;
    private Button loadButton;
    private Button playPauseButton;
    private SeekBarWidget seekBar;
    private VolumeSliderWidget volumeSlider;
    private PlaylistWidget playlistWidget;

    private List<TrackInfo> currentPlaylist = List.of();
    private String statusMessage = "";

    public MusicPlayerScreen() {
        super(Component.literal("Music Streamer"));
    }

    @Override
    protected void init() {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        int inputY = panelY + 28;
        playlistUrlInput = new EditBox(this.font, panelX + 12, inputY, PANEL_WIDTH - 100, 16, Component.literal("playlist url"));
        playlistUrlInput.setHint(Component.literal("YouTube playlist or video URL"));
        addRenderableWidget(playlistUrlInput);

        loadButton = Button.builder(Component.literal("Load"), b -> onLoadClicked())
                .bounds(panelX + PANEL_WIDTH - 80, inputY, 68, 16)
                .build();
        addRenderableWidget(loadButton);

        int visualizerY = inputY + 26;
        int visualizerHeight = 90;

        int seekY = visualizerY + visualizerHeight + 10;
        seekBar = new SeekBarWidget(panelX + 12, seekY, PANEL_WIDTH - 24, 12, this::onSeekRequested);
        addRenderableWidget(seekBar);

        int controlsY = seekY + 20;
        playPauseButton = Button.builder(Component.literal("Play"), b -> onPlayPauseClicked())
                .bounds(panelX + 12, controlsY, 60, 16)
                .build();
        addRenderableWidget(playPauseButton);

        volumeSlider = new VolumeSliderWidget(panelX + 90, controlsY, 120, 16, 1.0, v -> engine.setVolume((float) v));
        addRenderableWidget(volumeSlider);

        int playlistY = controlsY + 26;
        int playlistHeight = panelY + PANEL_HEIGHT - 12 - playlistY;
        playlistWidget = new PlaylistWidget(panelX + 12, playlistY, PANEL_WIDTH - 24, playlistHeight, this.font, this::onTrackChosen);
        playlistWidget.setTracks(currentPlaylist);
        addRenderableWidget(playlistWidget);
    }

    private void onLoadClicked() {
        String url = playlistUrlInput.getValue().trim();
        if (url.isEmpty()) {
            statusMessage = "URLを入力してください";
            return;
        }
        statusMessage = "読み込み中...";
        loadButton.active = false;

        Thread resolveThread = new Thread(() -> {
            try {
                List<TrackInfo> tracks = MusicStreamerMod.resolver.resolvePlaylist(url);
                Minecraft.getInstance().execute(() -> {
                    currentPlaylist = tracks;
                    playlistWidget.setTracks(tracks);
                    statusMessage = tracks.size() + "曲読み込み完了";
                    loadButton.active = true;
                });
            } catch (Exception e) {
                MusicStreamerMod.LOGGER.error("Failed to resolve playlist: {}", url, e);
                Minecraft.getInstance().execute(() -> {
                    statusMessage = "読み込み失敗: " + e.getMessage();
                    loadButton.active = true;
                });
            }
        }, "musicstreamer-resolve-playlist");
        resolveThread.setDaemon(true);
        resolveThread.start();
    }

    private void onTrackChosen(int index) {
        if (index < 0 || index >= currentPlaylist.size()) return;
        TrackInfo track = currentPlaylist.get(index);
        engine.play(track);
        playPauseButton.setMessage(Component.literal("Pause"));
    }

    private void onPlayPauseClicked() {
        PlaybackState state = engine.getState();
        if (state == PlaybackState.PLAYING) {
            engine.pause();
            playPauseButton.setMessage(Component.literal("Play"));
        } else if (state == PlaybackState.PAUSED) {
            engine.resume();
            playPauseButton.setMessage(Component.literal("Pause"));
        } else if (engine.getCurrentTrack() != null) {
            engine.play(engine.getCurrentTrack());
            playPauseButton.setMessage(Component.literal("Pause"));
        }
    }

    private void onSeekRequested(double progress) {
        TrackInfo track = engine.getCurrentTrack();
        if (track == null || track.durationSeconds() <= 0) return;
        engine.seekTo(progress * track.durationSeconds());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, ThemeColors.PANEL_BACKGROUND);
        graphics.hLine(panelX, panelX + PANEL_WIDTH - 1, panelY, ThemeColors.PANEL_BORDER);
        graphics.hLine(panelX, panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT - 1, ThemeColors.PANEL_BORDER);
        graphics.vLine(panelX, panelY, panelY + PANEL_HEIGHT - 1, ThemeColors.PANEL_BORDER);
        graphics.vLine(panelX + PANEL_WIDTH - 1, panelY, panelY + PANEL_HEIGHT - 1, ThemeColors.PANEL_BORDER);

        graphics.drawString(this.font, "Music Streamer", panelX + 12, panelY + 8, ThemeColors.TEXT_PRIMARY, false);

        TrackInfo current = engine.getCurrentTrack();
        String trackLabel = current != null ? current.title() : statusMessage;
        graphics.drawString(this.font, trackLabel, panelX + 12, panelY + 44, ThemeColors.ACCENT_CYAN, false);

        int visualizerY = panelY + 54;
        int visualizerHeight = 90;
        short[] snapshot = engine.getVisualSnapshot();
        VisualizerPanel.render(graphics, panelX + 12, visualizerY, PANEL_WIDTH - 24, visualizerHeight, snapshot);

        double position = engine.getPositionSeconds();
        int duration = current != null ? current.durationSeconds() : -1;
        if (duration > 0) {
            seekBar.updateProgress(position / duration);
        }
        String posLabel = formatSeconds(position);
        String durLabel = duration > 0 ? formatSeconds(duration) : "--:--";
        int seekY = visualizerY + visualizerHeight + 10;
        graphics.drawString(this.font, posLabel, panelX + 12, seekY - 10, ThemeColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, durLabel, panelX + PANEL_WIDTH - 12 - this.font.width(durLabel), seekY - 10, ThemeColors.TEXT_SECONDARY, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String formatSeconds(double totalSeconds) {
        int t = Math.max(0, (int) totalSeconds);
        return String.format("%d:%02d", t / 60, t % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}