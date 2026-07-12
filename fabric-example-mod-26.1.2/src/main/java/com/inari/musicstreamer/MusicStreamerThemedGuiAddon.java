package com.inari.musicstreamer;

import com.example.themedgui.client.api.AddonRegistration;
import com.example.themedgui.client.api.ThemedGuiAddon;
import com.inari.musicstreamer.gui.MusicPlayerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

public class MusicStreamerThemedGuiAddon implements ThemedGuiAddon {
    private static final MusicStreamerHubConfig CONFIG = new MusicStreamerHubConfig();

    static {
        CONFIG.openPlayer = () -> Minecraft.getInstance().setScreen(new MusicPlayerScreen());

        CONFIG.openKeyBinds = () -> {
            Minecraft client = Minecraft.getInstance();
            // lastScreenに現在の画面(ハブの設定画面)を渡すので、
            // キーバインド画面の「完了」を押すと元の設定画面に戻る
            client.setScreen(new KeyBindsScreen(client.screen, client.options));
        };
    }

    @Override
    public void register(AddonRegistration registration) {
        registration.registerMod("musicstreamer", "Music Streamer", CONFIG, null);
    }
}