package com.inari.musicstreamer;

import com.inari.musicstreamer.audio.AudioStreamEngine;
import com.inari.musicstreamer.audio.YtDlpResolver;
import com.inari.musicstreamer.gui.MusicPlayerScreen;
import com.inari.musicstreamer.playlist.PlaylistManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicStreamerMod implements ClientModInitializer {
    public static final String MOD_ID = "musicstreamer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static boolean THEMED_GUI_PRESENT = false;
    public static YtDlpResolver resolver;
    public static AudioStreamEngine audioEngine;

    // カテゴリ定義(26.1以降、KeyMappingのカテゴリは文字列ではなくCategoryオブジェクト)
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "category"));

    public static KeyMapping openScreenKey;

    public static MusicStreamerConfig config;
    public static PlaylistManager playlistManager;

    @Override
    public void onInitializeClient() {
        THEMED_GUI_PRESENT = FabricLoader.getInstance().isModLoaded("themedgui");
        LOGGER.info("themedgui present: {}", THEMED_GUI_PRESENT);

        config = MusicStreamerConfig.load();
        playlistManager = new PlaylistManager();

        resolver = new YtDlpResolver(config.ytDlpPath);
        audioEngine = new AudioStreamEngine(resolver, config.ffmpegPath);
        audioEngine.setOnError(msg -> LOGGER.warn("AudioStreamEngine error: {}", msg));

        // KeyMappingHelperのメソッド名も26.1でregisterKeyMappingに変更
        openScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
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