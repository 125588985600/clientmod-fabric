package com.example.clientmod.module;

import java.util.function.Consumer;

/**
 * 开关型参数。set() 会同时触发持久化回调。
 */
public class BoolSetting extends Setting {

    private boolean value;
    private final Consumer<Boolean> onSet;

    public BoolSetting(String name, boolean value, Consumer<Boolean> onSet) {
        super(name);
        this.value = value;
        this.onSet = onSet;
    }

    public boolean get() {
        return value;
    }

    /** 同 NumberSetting.getValue()，供 Kotlin 侧以 .value 访问 */
    public boolean getValue() {
        return value;
    }

    public void set(boolean value) {
        this.value = value;
        onSet.accept(value);
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public String getValueText() {
        return value ? "开" : "关";
    }
}
