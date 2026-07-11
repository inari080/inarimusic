package com.inari.musicstreamer.gui;

import com.inari.musicstreamer.util.SimpleFFT;
import net.minecraft.client.gui.GuiGraphics;

/**
 * AudioStreamEngine#getVisualSnapshot() が返す生PCM(short, LRインターリーブ)から
 * 簡易波形バーとスペクトラムバーを描画する。
 *
 * 入力は VISUAL_BUFFER_SAMPLES=4096 shorts (= 2048ステレオフレーム) を前提。
 */
public final class VisualizerPanel {
    private static final int MONO_SAMPLES = 2048; // 2048 = 2^11、FFTに使う長さ

    private VisualizerPanel() {
    }

    /**
     * @param x,y,width,height 描画領域。上半分に波形バー、下半分にスペクトラムバーを描く
     * @param pcmSnapshot AudioStreamEngine#getVisualSnapshot() の戻り値
     */
    public static void render(GuiGraphics graphics, int x, int y, int width, int height, short[] pcmSnapshot) {
        float[] mono = downmixToMono(pcmSnapshot);

        int waveformHeight = height / 2;
        renderWaveformBars(graphics, x, y, width, waveformHeight, mono);

        int spectrumY = y + waveformHeight;
        int spectrumHeight = height - waveformHeight;
        float[] magnitude = SimpleFFT.magnitude(mono);
        float[] bars = SimpleFFT.toBars(magnitude, Math.max(8, width / 8));
        renderSpectrumBars(graphics, x, spectrumY, width, spectrumHeight, bars);
    }

    private static float[] downmixToMono(short[] interleavedStereo) {
        float[] mono = new float[MONO_SAMPLES];
        int frames = Math.min(MONO_SAMPLES, interleavedStereo.length / 2);
        for (int i = 0; i < frames; i++) {
            short l = interleavedStereo[i * 2];
            short r = interleavedStereo[i * 2 + 1];
            mono[i] = ((l + r) / 2f) / 32768f; // -1.0 .. 1.0 に正規化
        }
        return mono;
    }

    private static void renderWaveformBars(GuiGraphics graphics, int x, int y, int width, int height, float[] mono) {
        int barCount = Math.max(8, width / 3);
        int samplesPerBar = Math.max(1, mono.length / barCount);
        int centerY = y + height / 2;

        for (int b = 0; b < barCount; b++) {
            float peak = 0f;
            int start = b * samplesPerBar;
            int end = Math.min(mono.length, start + samplesPerBar);
            for (int i = start; i < end; i++) {
                peak = Math.max(peak, Math.abs(mono[i]));
            }
            int barHeight = (int) (peak * (height / 2f));
            int barX = x + b * width / barCount;
            int barW = Math.max(1, width / barCount - 1);
            int color = ThemeColors.spectrumColorAt(b / (float) barCount);
            graphics.fill(barX, centerY - barHeight, barX + barW, centerY + barHeight, color);
        }
    }

    private static void renderSpectrumBars(GuiGraphics graphics, int x, int y, int width, int height, float[] bars) {
        int barCount = bars.length;
        int bottom = y + height;
        for (int b = 0; b < barCount; b++) {
            int barHeight = (int) (bars[b] * height);
            int barX = x + b * width / barCount;
            int barW = Math.max(1, width / barCount - 1);
            int color = ThemeColors.spectrumColorAt(b / (float) barCount);
            graphics.fill(barX, bottom - barHeight, barX + barW, bottom, color);
        }
    }
}