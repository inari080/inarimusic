package com.inari.musicstreamer;

import com.inari.musicstreamer.audio.AudioStreamEngine;
import com.inari.musicstreamer.audio.YtDlpResolver;
import com.inari.musicstreamer.gui.MusicPlayerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicStreamerMod implements ClientModInitializer {
    public static final String MOD_ID = "musicstreamer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static boolean THEMED_GUI_PRESENT = false;
    public static YtDlpResolver resolver;
    public static AudioStreamEngine audioEngine;

    // カテゴリ名を定数として定義（一括管理しやすくするため）
    private static final String CATEGORY = "category.musicstreamer";

    private static KeyMapping openScreenKey;

    @Override
    public void onInitializeClient() {
        THEMED_GUI_PRESENT = FabricLoader.getInstance().isModLoaded("themedguimod");
        LOGGER.info("themedguimod present: {}", THEMED_GUI_PRESENT);

        resolver = new YtDlpResolver("yt-dlp");
        audioEngine = new AudioStreamEngine(resolver, "ffmpeg");
        audioEngine.setOnError(msg -> LOGGER.warn("AudioStreamEngine error: {}", msg));

        // 第4引数に定数 CATEGORY を指定
        openScreenKey = KeyMappingHelper.registerKeyBinding(new KeyMapping(
                "key.musicstreamer.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new MusicPlayerScreen());
                }
            }
        });

        LOGGER.info("MusicStreamer initialized (press M to open the player)");
    }
}
