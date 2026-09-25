package com.example.clientmod.module;

import java.util.function.Consumer;

/**
 * 数值型参数。界面上渲染为滑块，按 step 取整。
 */
public class NumberSetting extends Setting {

    private final double min;
    private final double max;
    private final double step;
    private double value;
    private final Consumer<Double> onSet;

    public NumberSetting(String name, double value, double min, double max, double step, Consumer<Double> onSet) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step > 0 ? step : 1.0;
        this.onSet = onSet;
        this.value = clamp(value);
    }

    public double get() {
        return value;
    }

    /**
     * Kotlin 侧的 getValue()。Kotlin 会把 getX() 合成为 .x 属性，
     * 所以 KillAura.kt 里写 range.value 走的就是这个方法。
     * 没有它，Kotlin 只能看到 private 字段，会报
     * "Cannot access 'field value': it is private in ..."。
     */
    public double getValue() {
        return value;
    }

    public void set(double value) {
        this.value = clamp(value);
        onSet.accept(this.value);
    }

    private double clamp(double raw) {
        double v = Math.max(min, Math.min(max, raw));
        if (step > 0) {
            v = Math.round(v / step) * step;
        }
        // 消除浮点误差，避免出现 3.0000000000000004
        return Math.round(v * 1000.0) / 1000.0;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    /** 滑块用的 0~1 归一化值 */
    public double getNormalized() {
        if (max <= min) {
            return 0;
        }
        return (value - min) / (max - min);
    }

    /** 从滑块的 0~1 值反推实际值 */
    public void setNormalized(double normalized) {
        set(min + (max - min) * normalized);
    }

    @Override
    public String getValueText() {
        if (step >= 1) {
            return String.valueOf((int) Math.round(value));
        }
        return String.valueOf(value);
    }
}
