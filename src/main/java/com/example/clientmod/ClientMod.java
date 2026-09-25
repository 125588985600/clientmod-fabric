package com.example.clientmod;

import com.example.clientmod.config.ModConfig;
import com.example.clientmod.input.KeyBindings;
import com.example.clientmod.module.KillAura;
import com.example.clientmod.module.ModuleManager;
import com.example.clientmod.render.HudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口：所有初始化都在这里集中注册。
 *
 * 新增模块：写一个 Module 子类 → 在这里 ModuleManager.register(new Xxx())
 *          → 界面里的开关、参数、快捷键会自动出现。
 */
public class ClientMod implements ClientModInitializer {

    public static final String MOD_ID = "clientmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 全局配置实例，运行中可直接读写。参数改动建议用 markDirty()，由本类定时统一落盘 */
    public static ModConfig CONFIG;

    /** 落盘计时，每 20 tick 检查一次 */
    private int flushCounter;

    @Override
    public void onInitializeClient() {
        // 配置必须先加载：模块的初始值要从配置里读
        CONFIG = ModConfig.load();

        // 注册模块，用配置恢复上次开关状态
        ModuleManager.register(KillAura.INSTANCE);
        KillAura.INSTANCE.setEnabled(CONFIG.killAuraEnabled);

        // 按键注册要在模块注册之后，才能给每个模块建绑定
        KeyBindings.register();

        // 每 tick：先处理按键，再驱动所有开启的模块；每 20 tick 统一落盘一次配置
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeyBindings.onClientTick(client);
            ModuleManager.tick(client);
            if (++flushCounter >= 20) {
                flushCounter = 0;
                CONFIG.flushIfDirty();
            }
        });

        // 用 lambda 包一层，避免不同版本 Fabric API 的 HUD 回调签名差异
        HudRenderCallback.EVENT.register((context, tickDelta) -> HudOverlay.INSTANCE.render(context));

        LOGGER.info("[{}] 客户端初始化完成", MOD_ID);
    }
}
