package com.inari.musicstreamer.gui;

import com.example.themedgui.client.ui.UiPalette;
import com.example.themedgui.client.ui.UiSettings;
import com.inari.musicstreamer.MusicStreamerMod;

/**
 * MusicPlayerScreenで使う配色。
 *
 * themedguimod(mod id: "themedgui")がロードされている場合は、
 * UiSettings.INSTANCE.getTheme().palette() から現在のテーマ配色を反映する。
 * 未導入の場合は下記のデフォルト値のまま動作する。
 *
 * refresh() は MusicPlayerScreen#extractRenderState の先頭で毎フレーム呼ぶこと。
 */
public final class ThemeColors {
    private ThemeColors() {
    }

    // ARGB (0xAARRGGBB) — themedguimod未導入時のデフォルト値
    public static int PANEL_BACKGROUND = 0xE0141824;
    public static int PANEL_BORDER = 0x50FFFFFF;
    public static int TEXT_PRIMARY = 0xFFFFFFFF;
    public static int TEXT_SECONDARY = 0xFFAAB0C0;
    public static int ACCENT_CYAN = 0xFF4FE3D8;
    public static int ACCENT_WHITE_GLOW = 0xFFF5F8FF;
    public static int SEEK_TRACK = 0xFF303645;
    public static int SEEK_FILLED = 0xFF4FE3D8;
    public static int SEEK_HANDLE = 0xFFF5F8FF;
    public static int SPECTRUM_LOW = 0xFF4FE3D8;   // 低域(左側)
    public static int SPECTRUM_MID = 0xFFB7C77A;   // 中域
    public static int SPECTRUM_HIGH = 0xFFE38A4F;  // 高域(右側)

    /**
     * themedguimodが導入されていれば現在のテーマ配色を反映する。
     * 未導入なら何もしない(デフォルト値のまま)。
     */
    public static void refresh() {
        if (!MusicStreamerMod.THEMED_GUI_PRESENT) {
            return;
        }
        UiPalette palette = UiSettings.INSTANCE.getTheme().palette();
        PANEL_BACKGROUND = palette.panel();
        PANEL_BORDER = palette.panelBorder();
        TEXT_PRIMARY = palette.text();
        TEXT_SECONDARY = palette.mutedText();
        ACCENT_CYAN = palette.accent();
        ACCENT_WHITE_GLOW = palette.text();
        SEEK_TRACK = palette.field();
        SEEK_FILLED = palette.accent();
        SEEK_HANDLE = palette.text();
        // スペクトラムバーの3色グラデーションは装飾的な要素が強いため、
        // 汎用UIテーマの色にはあえて対応させず固定のままにしている。
    }

    /** 3色のグラデーションを比率tで補間する(スペクトラムバー用) */
    public static int spectrumColorAt(float t) {
        if (t < 0.5f) {
            return lerpArgb(SPECTRUM_LOW, SPECTRUM_MID, t / 0.5f);
        } else {
            return lerpArgb(SPECTRUM_MID, SPECTRUM_HIGH, (t - 0.5f) / 0.5f);
        }
    }

    private static int lerpArgb(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}