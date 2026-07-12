package com.inari.musicstreamer;

import com.example.themedgui.client.api.AddonRegistration;
import com.example.themedgui.client.api.ThemedGuiAddon;
import com.inari.musicstreamer.gui.MusicPlayerScreen;
import com.inari.musicstreamer.gui.MusicStreamerPathsScreen;
import com.inari.musicstreamer.gui.MusicStreamerKeybindScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

public class MusicStreamerThemedGuiAddon implements ThemedGuiAddon {
    private static final MusicStreamerHubConfig CONFIG = new MusicStreamerHubConfig();

    static {
        CONFIG.openPlayer = () -> Minecraft.getInstance().setScreen(new MusicPlayerScreen());

        CONFIG.openKeyBinds = () -> {
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new MusicStreamerKeybindScreen(client.screen)); // KeyBindsScreenから変更
        };

        CONFIG.openPaths = () -> {
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new MusicStreamerPathsScreen(client.screen));
        };
    }

    @Override
    public void register(AddonRegistration registration) {
        registration.registerMod("musicstreamer", "Music Streamer", CONFIG, null);
    }
}