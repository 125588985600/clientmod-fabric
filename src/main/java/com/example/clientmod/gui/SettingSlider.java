package com.example.clientmod.gui;

import com.example.clientmod.module.NumberSetting;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * 数值参数滑块。把滑块的 0~1 值映射到参数自己的 min~max 区间。
 */
public class SettingSlider extends SliderWidget {

    private final NumberSetting setting;

    public SettingSlider(int x, int y, int width, int height, NumberSetting setting) {
        super(x, y, width, height, Text.literal(setting.getName() + ": " + setting.getValueText()),
                setting.getNormalized());
        this.setting = setting;
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal(setting.getName() + ": " + setting.getValueText()));
    }

    @Override
    protected void applyValue() {
        setting.setNormalized(this.value);
    }
}
