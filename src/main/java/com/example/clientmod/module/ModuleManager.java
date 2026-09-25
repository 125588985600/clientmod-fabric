package com.example.clientmod.module;

import com.example.clientmod.ClientMod;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模块注册表：统一驱动所有已注册模块。
 */
public final class ModuleManager {

    private static final List<Module> MODULES = new ArrayList<>();

    private ModuleManager() {
    }

    public static void register(Module module) {
        MODULES.add(module);
    }

    public static void tick(MinecraftClient client) {
        for (Module module : MODULES) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onTick(client);
            } catch (Exception e) {
                // 单个模块出错不能把整个游戏拖崩
                ClientMod.LOGGER.error("[{}] tick 异常", module.getName(), e);
            }
        }
    }

    public static List<Module> all() {
        return Collections.unmodifiableList(MODULES);
    }
}
