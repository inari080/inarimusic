package com.inari.musicstreamer.util;

/**
 * 2の累乗長の実数入力に対するCooley-Tukey radix-2 FFT。
 * スペクトラムバー描画用に振幅(magnitude)配列を返すだけの簡易版。
 */
public final class SimpleFFT {
    private SimpleFFT() {
    }

    /**
     * @param real 長さが2の累乗である必要がある実数サンプル配列(破壊的に変更しない)
     * @return 長さ real.length/2 の振幅配列 (0Hz〜ナイキスト周波数)
     */
    public static float[] magnitude(float[] real) {
        int n = real.length;
        if (Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("FFT input length must be a power of 2, got " + n);
        }
        float[] re = real.clone();
        float[] im = new float[n];

        // ビット反転並べ替え
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float tmpR = re[i];
                re[i] = re[j];
                re[j] = tmpR;
                float tmpI = im[i];
                im[i] = im[j];
                im[j] = tmpI;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            float wR = (float) Math.cos(angle);
            float wI = (float) Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float curR = 1f, curI = 0f;
                for (int k = 0; k < len / 2; k++) {
                    float uR = re[i + k];
                    float uI = im[i + k];
                    float vR = re[i + k + len / 2] * curR - im[i + k + len / 2] * curI;
                    float vI = re[i + k + len / 2] * curI + im[i + k + len / 2] * curR;

                    re[i + k] = uR + vR;
                    im[i + k] = uI + vI;
                    re[i + k + len / 2] = uR - vR;
                    im[i + k + len / 2] = uI - vI;

                    float nextR = curR * wR - curI * wI;
                    float nextI = curR * wI + curI * wR;
                    curR = nextR;
                    curI = nextI;
                }
            }
        }

        float[] mag = new float[n / 2];
        for (int i = 0; i < n / 2; i++) {
            mag[i] = (float) Math.sqrt(re[i] * re[i] + im[i] * im[i]);
        }
        return mag;
    }

    /** magnitude配列を指定本数のバーにビニングし、0..1に正規化する(スペクトラムバー描画用) */
    public static float[] toBars(float[] magnitude, int barCount) {
        float[] bars = new float[barCount];
        int binsPerBar = Math.max(1, magnitude.length / barCount);
        float max = 1f;
        for (int b = 0; b < barCount; b++) {
            float sum = 0f;
            int start = b * binsPerBar;
            int end = Math.min(magnitude.length, start + binsPerBar);
            for (int i = start; i < end; i++) {
                sum += magnitude[i];
            }
            bars[b] = end > start ? sum / (end - start) : 0f;
            if (bars[b] > max) max = bars[b];
        }
        for (int b = 0; b < barCount; b++) {
            bars[b] = bars[b] / max;
        }
        return bars;
    }
}