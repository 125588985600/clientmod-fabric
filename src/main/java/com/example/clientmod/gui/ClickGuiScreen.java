package com.example.clientmod.gui;

import com.example.clientmod.ClientMod;
import com.example.clientmod.module.BoolSetting;
import com.example.clientmod.module.Module;
import com.example.clientmod.module.ModuleManager;
import com.example.clientmod.module.NumberSetting;
import com.example.clientmod.module.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 模组设置界面，默认按右 Shift 打开。
 *
 * 左栏：模块列表，点一下切换开关
 * 右栏：选中模块的快捷键 + 各项参数
 *
 * 新增模块和参数时这里不用改，界面会自动列出来。
 */
public class ClickGuiScreen extends Screen {

    private static final int PANEL_LEFT = 10;
    private static final int LIST_WIDTH = 130;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;

    /** 当前选中的模块 */
    private Module selected;
    /** 正在等待重新绑定按键的模块，null 表示没有 */
    private Module listening;

    public ClickGuiScreen() {
        super(Text.literal("ClientMod"));
        selected = null;
        listening = null;
    }

    @Override
    protected void init() {
        List<Module> modules = ModuleManager.all();
        if (selected == null && !modules.isEmpty()) {
            selected = modules.get(0);
        }

        int rightX = PANEL_LEFT + LIST_WIDTH + 10;
        int rightWidth = Math.max(120, width - rightX - 10);

        // ---- 左栏：模块列表 ----
        int y = 28;
        for (Module module : modules) {
            boolean on = module.isEnabled();
            String label = module.getName() + "  " + (on ? "[开]" : "[关]");
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                module.toggle();
                selected = module;
                refresh();
            }).dimensions(PANEL_LEFT, y, LIST_WIDTH, ROW_HEIGHT).build());
            y += ROW_HEIGHT + ROW_GAP;
        }

        // ---- 右栏：选中模块的设置 ----
        if (selected == null) {
            return;
        }

        int ry = 28;

        addDrawableChild(ButtonWidget.builder(
                Text.literal(selected.getName() + "：" + (selected.isEnabled() ? "开启中" : "已关闭")),
                button -> {
                    selected.toggle();
                    refresh();
                }).dimensions(rightX, ry, rightWidth, ROW_HEIGHT).build());
        ry += ROW_HEIGHT + ROW_GAP + 4;

        KeyBinding binding = selected.getKeyBinding();
        String keyLabel = listening == selected
                ? "按下新按键...（Esc 取消）"
                : "快捷键：" + (binding == null ? "无" : binding.getBoundKeyLocalizedText().getString());
        addDrawableChild(ButtonWidget.builder(Text.literal(keyLabel), button -> {
            listening = selected;
            refresh();
        }).dimensions(rightX, ry, rightWidth, ROW_HEIGHT).build());
        ry += ROW_HEIGHT + ROW_GAP + 4;

        for (Setting setting : selected.getSettings()) {
            if (setting instanceof BoolSetting boolSetting) {
                addDrawableChild(ButtonWidget.builder(
                        Text.literal(setting.getName() + "：" + setting.getValueText()),
                        button -> {
                            boolSetting.toggle();
                            refresh();
                        }).dimensions(rightX, ry, rightWidth, ROW_HEIGHT).build());
                ry += ROW_HEIGHT + ROW_GAP;

            } else if (setting instanceof NumberSetting numberSetting) {
                addDrawableChild(new SettingSlider(rightX, ry, rightWidth, ROW_HEIGHT, numberSetting));
                ry += ROW_HEIGHT + ROW_GAP;
            }
        }
    }

    /** 重建整个界面。init(client, w, h) 会先清掉旧控件再调 init()。 */
    private void refresh() {
        init(this.client, this.width, this.height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawText(client.textRenderer, Text.literal("ClientMod 模块（右 Shift 关闭）"),
                PANEL_LEFT, 10, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("左键点击切换 / 拖动滑块调数值"),
                PANEL_LEFT + LIST_WIDTH + 10, 10, 0xAAAAAA, true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 正在等待按键重绑：吞掉这一次按键
        if (listening != null) {
            Module module = listening;
            listening = null;
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
                KeyBinding binding = module.getKeyBinding();
                if (binding != null) {
                    binding.setBoundKey(key);
                    KeyBinding.updateKeysByCode();
                }
                if (ClientMod.CONFIG != null) {
                    ClientMod.CONFIG.setModuleKey(module.getId(), keyCode);
                    ClientMod.CONFIG.save();
                }
            }
            refresh();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        // 关界面时把拖动滑块攒下的改动一次性写盘
        if (ClientMod.CONFIG != null) {
            ClientMod.CONFIG.flushIfDirty();
        }
        super.close();
    }
}
