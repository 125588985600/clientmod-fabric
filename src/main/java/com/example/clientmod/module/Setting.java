package com.example.clientmod.module;

/**
 * 模块参数的基类。界面会根据子类类型自动生成对应控件。
 */
public abstract class Setting {

    private final String name;

    protected Setting(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** 界面上按钮/滑块显示的文字 */
    public abstract String getValueText();
}
