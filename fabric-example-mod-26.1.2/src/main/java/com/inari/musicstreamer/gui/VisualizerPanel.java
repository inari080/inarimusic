package com.inari.musicstreamer.gui;

import com.inari.musicstreamer.util.SimpleFFT;
import net.minecraft.client.gui.GuiGraphicsExtractor; // GuiGraphicsから変更


/**
 * AudioStreamEngine#getVisualSnapshot() が返す生PCM(short, LRインターリーブ)から
 * 簡易波形バーとスペクトラムバーを描画する。
 *
 * 入力は VISUAL_BUFFER_SAMPLES=4096 shorts (= 2048ステレオフレーム) を前提。
 */
public final class VisualizerPanel {
    private static final int MONO_SAMPLES = 2048; // 2048 = 2^11、FFTに使う長さ
    private static final int FFT_SKIP_FRAMES = 3; // FFT計算をスキップするフレーム数（FPS改善用）
    private static int frameCounter = 0;
    private static float[] cachedBars = null;
    private static float[] cachedMono = null;

    private VisualizerPanel() {
    }

    /**
     * @param x,y,width,height 描画領域。中央対称のスペクトラムバーを描く
     * @param pcmSnapshot AudioStreamEngine#getVisualSnapshot() の戻り値
     */
    public static void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, short[] pcmSnapshot) {
        float[] mono = downmixToMono(pcmSnapshot);

        // FFT計算をスキップしてFPS改善（数フレームに1回のみ計算）
        frameCounter++;
        if (frameCounter % FFT_SKIP_FRAMES == 0 || cachedBars == null) {
            float[] magnitude = SimpleFFT.magnitude(mono);
            cachedBars = SimpleFFT.toBars(magnitude, Math.max(16, width / 4)); // バー数を増やして滑らかに
            cachedMono = mono.clone();
        }

        renderMirroredSpectrumBars(graphics, x, y, width, height, cachedBars);
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

    /**
     * 中央対称のスペクトラムバーを描画（モダンな見た目）
     */
    private static void renderMirroredSpectrumBars(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float[] bars) {
        int barCount = bars.length;
        int centerX = x + width / 2;
        int bottom = y + height;

        for (int b = 0; b < barCount; b++) {
            float value = bars[b];
            int barHeight = (int) (value * height * 0.9f); // 高さを90%にして余白を作る
            int barWidth = Math.max(2, width / (barCount * 2));

            // グラデーションカラー（低域は青、高域は赤）
            float hue = 0.6f - (b / (float) barCount) * 0.6f; // 青(0.6)から赤(0.0)
            int color = hsvToRgb(hue, 1.0f, 1.0f);

            // 左側（中央から左へ）
            int leftX = centerX - (b + 1) * barWidth - 1;
            graphics.fill(leftX, bottom - barHeight, leftX + barWidth, bottom, color);

            // 右側（中央から右へ）
            int rightX = centerX + b * barWidth + 1;
            graphics.fill(rightX, bottom - barHeight, rightX + barWidth, bottom, color);
        }

        // 中央の基準線
        graphics.fill(centerX - 1, y, centerX + 1, bottom, 0xFFFFFFFF);
    }

    /**
     * HSVからRGB色に変換
     */
    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        float r, g, b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }

        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}