package com.example.clientmod.input;

import com.example.clientmod.ClientMod;
import com.example.clientmod.gui.ClickGuiScreen;
import com.example.clientmod.module.Module;
import com.example.clientmod.module.ModuleManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定。
 *
 * 模块的按键代码存在我们自己的 config 里（config/clientmod.json 的 moduleKeys），
 * 不走原版 options.txt，避免不同版本 GameOptions 的保存接口不一样。
 * 打开界面的默认键是右 Shift。
 */
public final class KeyBindings {

    public static final String CATEGORY = "key.category." + ClientMod.MOD_ID;

    /** 打开设置界面 */
    public static KeyBinding openGui;
    /** 切换 HUD */
    public static KeyBinding toggleHud;

    private KeyBindings() {
    }

    public static void register() {
        int guiKey = ClientMod.CONFIG == null ? GLFW.GLFW_KEY_RIGHT_SHIFT : ClientMod.CONFIG.guiKeyCode;

        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clientmod.openGui",
                InputUtil.Type.KEYSYM,
                guiKey,
                CATEGORY
        ));
        applyKey(openGui, guiKey);

        toggleHud = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.clientmod.toggleHud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));

        // 每个模块一个按键，代码从配置里恢复
        for (Module module : ModuleManager.all()) {
            int code = ClientMod.CONFIG == null
                    ? module.getDefaultKey()
                    : ClientMod.CONFIG.getModuleKey(module.getId(), module.getDefaultKey());

            KeyBinding binding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.clientmod.module." + module.getId(),
                    InputUtil.Type.KEYSYM,
                    code,
                    CATEGORY
            ));
            applyKey(binding, code);
            module.setKeyBinding(binding);
        }

        KeyBinding.updateKeysByCode();
    }

    private static void applyKey(KeyBinding binding, int code) {
        if (code != GLFW.GLFW_KEY_UNKNOWN) {
            binding.setBoundKey(InputUtil.fromKeyCode(code, 0));
        }
    }

    public static void onClientTick(MinecraftClient client) {
        if (openGui != null) {
            while (openGui.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ClickGuiScreen());
                }
            }
        }

        if (toggleHud != null) {
            while (toggleHud.wasPressed()) {
                ClientMod.CONFIG.hudEnabled = !ClientMod.CONFIG.hudEnabled;
                ClientMod.CONFIG.save();
                send(client, "HUD: " + onOff(ClientMod.CONFIG.hudEnabled));
            }
        }

        // 界面打开时不响应模块快捷键，避免和按键重绑冲突
        if (client.currentScreen != null) {
            return;
        }

        for (Module module : ModuleManager.all()) {
            KeyBinding binding = module.getKeyBinding();
            if (binding == null) {
                continue;
            }
            while (binding.wasPressed()) {
                module.toggle();
                send(client, module.getName() + ": " + onOff(module.isEnabled()));
            }
        }
    }

    private static void send(MinecraftClient client, String message) {
        if (client.player != null) {
            ChatHud chat = client.inGameHud.getChatHud();
            chat.addMessage(Text.literal("[ClientMod] " + message));
        }
    }

    private static String onOff(boolean value) {
        return value ? "开" : "关";
    }
}
