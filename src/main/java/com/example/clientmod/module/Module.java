package com.example.clientmod.module;

import com.example.clientmod.ClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 所有功能模块的基类。
 *
 * 新增一个功能 = 继承本类 → 在构造里 addSetting(...) 声明可调参数
 *              → 重写 onTick 写逻辑 → 在 ClientMod 里 ModuleManager.register(new Xxx())。
 * 界面上的开关、参数、快捷键都由此自动生成，不需要额外写 GUI 代码。
 */
public abstract class Module {

    private final String name;
    private final int defaultKey;
    private KeyBinding keyBinding;

    private boolean enabled;
    private final List<Setting> settings = new ArrayList<>();

    protected Module(String name, int defaultKey) {
        this.name = name;
        this.defaultKey = defaultKey;
        this.enabled = false;
    }

    /** 用作配置项 key 与语言文件 key，只保留字母数字 */
    public String getId() {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public String getName() {
        return name;
    }

    public int getDefaultKey() {
        return defaultKey;
    }

    public KeyBinding getKeyBinding() {
        return keyBinding;
    }

    /** 由 KeyBindings 在注册按键后回填 */
    public void setKeyBinding(KeyBinding keyBinding) {
        this.keyBinding = keyBinding;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        persistEnabled();
        try {
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        } catch (Exception e) {
            ClientMod.LOGGER.error("[{}] 开关回调异常", name, e);
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** 子类可重写，用于把开关状态写进配置 */
    protected void persistEnabled() {
    }

    protected void addSetting(Setting setting) {
        settings.add(setting);
    }

    /** 声明一个开关参数，界面会自动生成按钮 */
    protected BoolSetting bool(String name, boolean value, Consumer<Boolean> onSet) {
        BoolSetting setting = new BoolSetting(name, value, onSet);
        addSetting(setting);
        return setting;
    }

    /** 声明一个数值参数，界面会自动生成滑块 */
    protected NumberSetting number(String name, double value, double min, double max, double step,
                                   Consumer<Double> onSet) {
        NumberSetting setting = new NumberSetting(name, value, min, max, step, onSet);
        addSetting(setting);
        return setting;
    }

    public List<Setting> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /**
     * 每 tick 调用一次，只在模块开启时触发。
     */
    public void onTick(MinecraftClient client) {
    }
}
