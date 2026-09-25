package com.example.clientmod.config;

import com.example.clientmod.ClientMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 模组配置：GSON 持久化到 .minecraft/config/clientmod.json
 *
 * 大部分参数推荐在游戏内按右 Shift 打开界面改；直接改这个文件也可以，重启生效。
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve(ClientMod.MOD_ID + ".json");

    // ---------- 界面 ----------
    /** 打开设置界面的按键代码，默认右 Shift（344） */
    public int guiKeyCode = GLFW.GLFW_KEY_RIGHT_SHIFT;

    // ---------- HUD ----------
    /** 是否显示 HUD 叠加层 */
    public boolean hudEnabled = true;
    /** HUD 左上角位置 */
    public int hudX = 4;
    public int hudY = 4;
    /** 文字颜色（0xRRGGBB） */
    public int hudColor = 0xFFFFFF;
    /** 是否绘制文字阴影 */
    public boolean hudShadow = true;

    // ---------- KillAura ----------
    /** KillAura 开关（默认关，进游戏后用快捷键或在界面里打开） */
    public boolean killAuraEnabled = false;
    /** 攻击距离。原版生存只有 3 格，调大只会挥空手 */
    public double killAuraRange = 3.0;
    /** 两次攻击之间间隔多少 tick。20 tick = 1 秒，剑的冷却约 12~13 tick */
    public int killAuraDelay = 12;
    /** 实体扫描间隔 tick。调大更省 CPU（低配机器建议 3~5），调小锁定更跟手 */
    public int killAuraScanInterval = 2;
    /** 自动把视角转向目标 */
    public boolean killAuraAutoAim = true;
    /** 需要视线才能打。关掉 = 隔墙攻击 */
    public boolean killAuraRequireLineOfSight = true;
    /** 是否攻击玩家 */
    public boolean killAuraTargetPlayers = true;
    /** 是否攻击敌对生物（僵尸、骷髅、苦力怕等） */
    public boolean killAuraTargetHostile = true;
    /** 是否攻击被动生物（牛、猪、羊等） */
    public boolean killAuraTargetPassive = false;
    /** 是否攻击其他生物（铁傀儡、蝙蝠、盔甲架等其他 LivingEntity） */
    public boolean killAuraTargetOther = false;

    // ---------- 模块快捷键 ----------
    /** 模块 id -> 按键代码。界面里改完快捷键会写到这里 */
    public Map<String, Integer> moduleKeys = new HashMap<>();

    /** 有未落盘的改动。transient：不写进 json */
    private transient boolean dirty;

    /**
     * 标记"配置已改动"。不立即写盘——拖滑块时 onSet 每帧都会触发，
     * 每次都写文件会把 IO 打满。真正的写入由 flushIfDirty() 定时统一做。
     */
    public void markDirty() {
        dirty = true;
    }

    /** 有改动才写盘，没改动直接返回 */
    public void flushIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        save();
    }

    public int getModuleKey(String id, int fallback) {
        if (moduleKeys == null) {
            return fallback;
        }
        Integer code = moduleKeys.get(id);
        return code == null ? fallback : code;
    }

    public void setModuleKey(String id, int code) {
        if (moduleKeys == null) {
            moduleKeys = new HashMap<>();
        }
        moduleKeys.put(id, code);
    }

    public static ModConfig load() {
        if (Files.exists(PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    if (loaded.moduleKeys == null) {
                        loaded.moduleKeys = new HashMap<>();
                    }
                    return loaded;
                }
            } catch (Exception e) {
                ClientMod.LOGGER.warn("读取配置失败，将使用默认值", e);
            }
        }
        ModConfig fresh = new ModConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ClientMod.LOGGER.error("保存配置失败", e);
        }
    }
}
