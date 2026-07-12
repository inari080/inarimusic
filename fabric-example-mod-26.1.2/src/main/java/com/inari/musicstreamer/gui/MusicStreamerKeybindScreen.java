package com.inari.musicstreamer.gui;

import com.inari.musicstreamer.MusicStreamerMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Music Streamerのキーバインドをその場で変更できる画面。
 * 「Change Keybind: M」ボタンを押すと入力待ちになり、次に押したキーがそのまま割り当てられる。
 * Escでキャンセル(現在の割り当てのまま変更しない)。
 */
public class MusicStreamerKeybindScreen extends Screen {
    private final Screen parent;
    private Button rebindButton;
    private boolean waitingForKey = false;

    public MusicStreamerKeybindScreen(Screen parent) {
        super(Component.literal("Music Streamer - キー設定"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        rebindButton = Button.builder(currentLabel(), button -> {
            waitingForKey = true;
            button.setMessage(Component.literal("> キーを押してください (Escでキャンセル) <"));
        }).bounds(centerX - 150, 80, 300, 20).build();
        this.addRenderableWidget(rebindButton);

        this.addRenderableWidget(Button.builder(Component.literal("戻る"), button ->
                this.minecraft.setScreen(parent)
        ).bounds(centerX - 100, 115, 200, 20).build());
    }

    private Component currentLabel() {
        String keyName = MusicStreamerMod.openScreenKey.getTranslatedKeyMessage().getString();
        return Component.literal("Change Keybind: " + keyName);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (waitingForKey) {
            waitingForKey = false;
            if (event.key() != GLFW.GLFW_KEY_ESCAPE) {
                InputConstants.Key key = InputConstants.getKey(event);
                MusicStreamerMod.openScreenKey.setKey(key);
                net.minecraft.client.KeyMapping.resetMapping();
                this.minecraft.options.save();
            }
            rebindButton.setMessage(currentLabel());
            return true;
        }
        return super.keyPressed(event);
    }
}