package com.inari.musicstreamer.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/**
 * 再生位置シークバー。0.0〜1.0の進捗を表示し、クリック/ドラッグでシーク位置を通知する。
 *
 * 注意: Minecraft 26.1系のGUIレンダリング刷新(GuiRenderState導入)の影響で、
 * AbstractWidgetの正確なメソッド名/シグネチャが将来のマイナー更新で変わる可能性がある。
 * ビルド時にIDEの補完/decompileソースで renderWidget / updateWidgetNarration の
 * シグネチャを確認すること。
 */
public class SeekBarWidget extends AbstractWidget {
    private double progress = 0.0; // 0.0 - 1.0
    private boolean dragging = false;
    private final DoubleConsumer onSeekRequested;

    public SeekBarWidget(int x, int y, int width, int height, DoubleConsumer onSeekRequested) {
        super(x, y, width, height, Component.empty());
        this.onSeekRequested = onSeekRequested;
    }

    /** 再生中の実際の進捗を外部(AudioStreamEngine)から反映させる。ドラッグ中は上書きしない。 */
    public void updateProgress(double progress) {
        if (!dragging) {
            this.progress = Math.max(0.0, Math.min(1.0, progress));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int trackY = y + h / 2;

        graphics.hLine(x, x + w - 1, trackY, ThemeColors.SEEK_TRACK);

        int filledEndX = x + (int) (w * progress);
        if (filledEndX > x) {
            graphics.hLine(x, filledEndX, trackY, ThemeColors.SEEK_FILLED);
        }

        int handleX = filledEndX;
        int handleSize = 4;
        graphics.fill(handleX - handleSize / 2, trackY - handleSize / 2,
                handleX + handleSize / 2, trackY + handleSize / 2, ThemeColors.SEEK_HANDLE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            dragging = true;
            updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (dragging) {
            updateFromMouse(mouseX);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (dragging) {
            dragging = false;
            updateFromMouse(mouseX);
            onSeekRequested.accept(progress);
        }
    }

    private void updateFromMouse(double mouseX) {
        double relative = (mouseX - getX()) / (double) getWidth();
        progress = Math.max(0.0, Math.min(1.0, relative));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal("Seek bar"));
    }
}