package com.inari.musicstreamer.gui;

import com.inari.musicstreamer.audio.TrackInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/** プレイリストのトラック一覧をスクロール表示し、クリックで選択/再生要求を通知する。 */
public class PlaylistWidget extends AbstractWidget {
    private static final int ROW_HEIGHT = 12;

    private final Font font;
    private List<TrackInfo> tracks = List.of();
    private int selectedIndex = -1;
    private int scrollOffsetRows = 0;
    private final IntConsumer onTrackChosen;

    public PlaylistWidget(int x, int y, int width, int height, Font font, IntConsumer onTrackChosen) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.onTrackChosen = onTrackChosen;
    }

    public void setTracks(List<TrackInfo> tracks) {
        this.tracks = tracks;
        this.selectedIndex = -1;
        this.scrollOffsetRows = 0;
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        graphics.fill(x, y, x + w, y + h, ThemeColors.PANEL_BACKGROUND);
        graphics.enableScissor(x, y, x + w, y + h);

        int visibleRows = h / ROW_HEIGHT + 1;
        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffsetRows + row;
            if (index < 0 || index >= tracks.size()) continue;

            int rowY = y + row * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean selected = index == selectedIndex;

            if (selected) {
                graphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT, 0x554FE3D8);
            } else if (hovered) {
                graphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT, 0x33FFFFFF);
            }

            TrackInfo track = tracks.get(index);
            int color = selected ? ThemeColors.ACCENT_CYAN : ThemeColors.TEXT_PRIMARY;
            graphics.drawString(font, trimToWidth(track.title(), w - 6), x + 3, rowY + 2, color, false);
        }

        graphics.disableScissor();
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        int row = (int) ((mouseY - getY()) / ROW_HEIGHT);
        int index = scrollOffsetRows + row;
        if (index >= 0 && index < tracks.size()) {
            selectedIndex = index;
            onTrackChosen.accept(index);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        int maxOffset = Math.max(0, tracks.size() - getHeight() / ROW_HEIGHT);
        scrollOffsetRows = (int) Math.max(0, Math.min(maxOffset, scrollOffsetRows - scrollY));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal("Playlist"));
    }
}