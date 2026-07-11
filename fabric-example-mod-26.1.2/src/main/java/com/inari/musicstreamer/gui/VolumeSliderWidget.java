package com.inari.musicstreamer.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/** 水平ボリュームスライダー(0.0-1.0)。ドラッグ中もリアルタイムでコールバックする。 */
public class VolumeSliderWidget extends AbstractWidget {
    private double value; // 0.0 - 1.0
    private final DoubleConsumer onValueChanged;

    public VolumeSliderWidget(int x, int y, int width, int height, double initialValue, DoubleConsumer onValueChanged) {
        super(x, y, width, height, Component.empty());
        this.value = initialValue;
        this.onValueChanged = onValueChanged;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int trackY = y + h / 2;

        graphics.hLine(x, x + w - 1, trackY, ThemeColors.SEEK_TRACK);
        int filledEndX = x + (int) (w * value);
        if (filledEndX > x) {
            graphics.hLine(x, filledEndX, trackY, ThemeColors.ACCENT_CYAN);
        }
        int handleSize = 4;
        graphics.fill(filledEndX - handleSize / 2, trackY - handleSize / 2,
                filledEndX + handleSize / 2, trackY + handleSize / 2, ThemeColors.SEEK_HANDLE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        updateFromMouse(mouseX);
    }

    private void updateFromMouse(double mouseX) {
        double relative = (mouseX - getX()) / (double) getWidth();
        value = Math.max(0.0, Math.min(1.0, relative));
        onValueChanged.accept(value);
    }

    public double getValue() {
        return value;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal("Volume slider"));
    }
}