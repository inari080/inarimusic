package com.inari.musicstreamer.gui;

import com.inari.musicstreamer.MusicStreamerMod;
import com.inari.musicstreamer.playlist.SavedUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * プレイリスト（タグ/フォルダ）管理画面。
 * URLの保存、タグ付け、フォルダ間の移動、削除を行う。
 */
public class PlaylistManagerScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int PANEL_HEIGHT = 350;

    private final Screen parent;
    private final List<SavedUrl> currentUrls = new ArrayList<>();
    private String selectedTag = "未分類";
    private int selectedIndex = -1;

    private EditBox urlInput;
    private EditBox tagInput;
    private EditBox titleInput;
    private Button saveButton;
    private Button deleteButton;
    private Button playButton;
    private Button playAllButton;

    public PlaylistManagerScreen(Screen parent) {
        super(Component.literal("Music Streamer - プレイリスト管理"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // URL入力
        urlInput = new EditBox(this.font, panelX + 12, panelY + 12, PANEL_WIDTH - 24, 16,
                Component.literal("URL"));
        urlInput.setHint(Component.literal("YouTube URL"));
        addRenderableWidget(urlInput);

        // タイトル入力
        titleInput = new EditBox(this.font, panelX + 12, panelY + 32, PANEL_WIDTH - 24, 16,
                Component.literal("タイトル"));
        titleInput.setHint(Component.literal("タイトル（任意）"));
        addRenderableWidget(titleInput);

        // タグ入力
        tagInput = new EditBox(this.font, panelX + 12, panelY + 52, PANEL_WIDTH - 24, 16,
                Component.literal("タグ"));
        tagInput.setHint(Component.literal("タグ/フォルダ名"));
        tagInput.setValue(selectedTag);
        addRenderableWidget(tagInput);

        // 保存ボタン
        saveButton = Button.builder(Component.literal("保存"), b -> onSaveClicked())
                .bounds(panelX + 12, panelY + 75, 80, 16)
                .build();
        addRenderableWidget(saveButton);

        // 削除ボタン
        deleteButton = Button.builder(Component.literal("削除"), b -> onDeleteClicked())
                .bounds(panelX + 100, panelY + 75, 80, 16)
                .build();
        deleteButton.active = false;
        addRenderableWidget(deleteButton);

        // 再生ボタン
        playButton = Button.builder(Component.literal("再生"), b -> onPlayClicked())
                .bounds(panelX + 188, panelY + 75, 80, 16)
                .build();
        playButton.active = false;
        addRenderableWidget(playButton);

        // 全曲再生ボタン
        playAllButton = Button.builder(Component.literal("フォルダ全曲再生"), b -> onPlayAllClicked())
                .bounds(panelX + 276, panelY + 75, 120, 16)
                .build();
        addRenderableWidget(playAllButton);

        // 戻るボタン
        addRenderableWidget(Button.builder(Component.literal("戻る"), b ->
                this.minecraft.setScreen(parent)
        ).bounds(panelX + PANEL_WIDTH - 80, panelY + PANEL_HEIGHT - 20, 68, 16).build());

        loadUrlsForTag(selectedTag);
    }

    private void loadUrlsForTag(String tag) {
        currentUrls.clear();
        currentUrls.addAll(MusicStreamerMod.playlistManager.getUrlsByTag(tag));
        selectedIndex = -1;
        updateButtonStates();
    }

    private void onSaveClicked() {
        String url = urlInput.getValue().trim();
        String title = titleInput.getValue().trim();
        String tag = tagInput.getValue().trim();

        if (url.isEmpty()) {
            urlInput.setValue("URLを入力してください");
            return;
        }

        if (tag.isEmpty()) {
            tag = "未分類";
        }

        SavedUrl savedUrl = new SavedUrl(url, title.isEmpty() ? url : title, tag);
        MusicStreamerMod.playlistManager.addUrl(savedUrl);

        selectedTag = tag;
        loadUrlsForTag(tag);
        urlInput.setValue("");
        titleInput.setValue("");
    }

    private void onDeleteClicked() {
        if (selectedIndex >= 0 && selectedIndex < currentUrls.size()) {
            SavedUrl toDelete = currentUrls.get(selectedIndex);
            MusicStreamerMod.playlistManager.removeUrl(selectedTag, toDelete.url());
            loadUrlsForTag(selectedTag);
        }
    }

    private void onPlayClicked() {
        if (selectedIndex >= 0 && selectedIndex < currentUrls.size()) {
            SavedUrl toPlay = currentUrls.get(selectedIndex);
            this.minecraft.setScreen(null); // 画面を閉じる
            // メインプレイヤー画面で再生する処理が必要
            // ここではURLをクリップボードにコピーする簡易実装
            playUrl(toPlay.url());
        }
    }

    private void onPlayAllClicked() {
        if (!currentUrls.isEmpty()) {
            this.minecraft.setScreen(null);
            // フォルダ内の全URLを順次再生する処理
            playPlaylist(currentUrls);
        }
    }

    private void playUrl(String url) {
        // メインプレイヤー画面を開いてURLを設定
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            MusicPlayerScreen playerScreen = new MusicPlayerScreen();
            playerScreen.setPlaylistUrl(url);
            client.setScreen(playerScreen);
        });
    }

    private void playPlaylist(List<SavedUrl> urls) {
        // プレイリスト再生の実装
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            MusicPlayerScreen playerScreen = new MusicPlayerScreen();
            playerScreen.setPlaylistUrls(urls);
            client.setScreen(playerScreen);
        });
    }

    private void updateButtonStates() {
        deleteButton.active = selectedIndex >= 0;
        playButton.active = selectedIndex >= 0;
        playAllButton.active = !currentUrls.isEmpty();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int listY = 100;
        int listHeight = PANEL_HEIGHT - 130;
        double mouseX = event.x();
        double mouseY = event.y();

        // URLリストのクリック判定
        if (mouseX >= panelX + 12 && mouseX <= panelX + PANEL_WIDTH - 12 &&
            mouseY >= listY && mouseY <= listY + listHeight) {
            int clickedIndex = (int) ((mouseY - listY) / 16);
            if (clickedIndex >= 0 && clickedIndex < currentUrls.size()) {
                selectedIndex = clickedIndex;
                SavedUrl selected = currentUrls.get(selectedIndex);
                urlInput.setValue(selected.url());
                titleInput.setValue(selected.title());
                tagInput.setValue(selected.tag());
                selectedTag = selected.tag();
                updateButtonStates();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ThemeColors.refresh();
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, ThemeColors.PANEL_BACKGROUND);
        graphics.horizontalLine(panelX, panelX + PANEL_WIDTH - 1, panelY, ThemeColors.PANEL_BORDER);
        graphics.verticalLine(panelX, panelY, panelY + PANEL_HEIGHT - 1, ThemeColors.PANEL_BORDER);

        graphics.text(this.font, "プレイリスト管理", panelX + 12, panelY - 10, ThemeColors.TEXT_PRIMARY, false);

        // URLリスト描画
        int listY = panelY + 100;
        int listHeight = PANEL_HEIGHT - 130;
        graphics.fill(panelX + 12, listY, panelX + PANEL_WIDTH - 12, listY + listHeight, 0xFF1A1A1A);

        for (int i = 0; i < currentUrls.size(); i++) {
            int y = listY + i * 16;
            if (y + 16 > listY + listHeight) break;

            SavedUrl url = currentUrls.get(i);
            boolean selected = i == selectedIndex;

            if (selected) {
                graphics.fill(panelX + 12, y, panelX + PANEL_WIDTH - 12, y + 16, 0x554FE3D8);
            }

            String displayText = url.title().isEmpty() ? url.url() : url.title();
            String trimmed = trimToWidth(displayText, PANEL_WIDTH - 24);
            graphics.text(this.font, trimmed, panelX + 14, y + 4, selected ? ThemeColors.ACCENT_CYAN : ThemeColors.TEXT_PRIMARY, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(sb.toString() + c + ellipsis) > maxWidth) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
