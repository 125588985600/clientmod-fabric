package com.example.clientmod.render;

import com.example.clientmod.ClientMod;
import com.example.clientmod.module.KillAura;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

/**
 * HUD 叠加层：每帧在屏幕上绘制信息。
 *
 * 性能约定：HUD 每帧都跑，所以这里刻意避免了每帧的对象分配——
 * - 复用同一个 StringBuilder 拼字符串
 * - 每行文本只在内容真的变化时才重建 Text 对象（坐标一秒可能变好几次，
 *   但"朝向""时间"这类往往几十帧都不变，缓存收益明显）
 * - 时间字符串按分钟缓存，不做 String.format
 * - 只在渲染，不做任何世界查询
 */
public class HudOverlay {

    public static final HudOverlay INSTANCE = new HudOverlay();
    private static final int MAX_LINES = 8;

    private final StringBuilder sb = new StringBuilder(128);
    private final String[] lastLines = new String[MAX_LINES];
    private final Text[] cachedTexts = new Text[MAX_LINES];

    /** 时间字符串缓存：只在分钟变化时重算 */
    private String timeCache = "";
    private long timeCacheKey = -1;

    /** 目标名缓存：getName() 每次都建对象，缓存掉 */
    private String targetNameCache = "";
    private LivingEntity lastTarget;

    public void render(DrawContext context) {
        if (!ClientMod.CONFIG.hudEnabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        int x = ClientMod.CONFIG.hudX;
        int y = ClientMod.CONFIG.hudY;
        int color = ClientMod.CONFIG.hudColor;
        boolean shadow = ClientMod.CONFIG.hudShadow;

        int index = 0;

        index = drawLine(context, renderer, index, x, y, color, shadow,
                buildXyz(player));
        y += renderer.fontHeight + 2;

        index = drawLine(context, renderer, index, x, y, color, shadow,
                buildFacing(player));
        y += renderer.fontHeight + 2;

        index = drawLine(context, renderer, index, x, y, color, shadow,
                buildHealth(player));
        y += renderer.fontHeight + 2;

        index = drawLine(context, renderer, index, x, y, color, shadow,
                buildTime(client.world.getTimeOfDay()));
        y += renderer.fontHeight + 2;

        KillAura killAura = KillAura.INSTANCE;
        if (killAura.isEnabled()) {
            drawLine(context, renderer, index, x, y, color, shadow,
                    buildTargetLine(killAura.getCurrentTarget()));
        }
    }

    /** 内容没变就复用上次的 Text 对象 */
    private int drawLine(DrawContext context, TextRenderer renderer, int index,
                         int x, int y, int color, boolean shadow, String text) {
        if (index >= MAX_LINES) {
            return index;
        }
        if (!text.equals(lastLines[index])) {
            lastLines[index] = text;
            cachedTexts[index] = Text.literal(text);
        }
        context.drawText(renderer, cachedTexts[index], x, y, color, shadow);
        return index + 1;
    }

    private String buildXyz(ClientPlayerEntity player) {
        sb.setLength(0);
        sb.append("XYZ: ").append(player.getBlockX())
                .append(' ').append(player.getBlockY())
                .append(' ').append(player.getBlockZ());
        return sb.toString();
    }

    private String buildFacing(ClientPlayerEntity player) {
        sb.setLength(0);
        sb.append("朝向: ");
        switch (player.getHorizontalFacing()) {
            case NORTH -> sb.append("北 (-Z)");
            case SOUTH -> sb.append("南 (+Z)");
            case WEST -> sb.append("西 (-X)");
            case EAST -> sb.append("东 (+X)");
            default -> sb.append('-');
        }
        return sb.toString();
    }

    private String buildHealth(ClientPlayerEntity player) {
        sb.setLength(0);
        sb.append("生命: ").append(Math.round(player.getHealth()))
                .append(" / ").append(Math.round(player.getMaxHealth()));
        return sb.toString();
    }

    /** 时间按分钟缓存，避免每帧算除法与格式化 */
    private String buildTime(long timeOfDay) {
        long dayTime = timeOfDay % 24000L;
        long key = dayTime / 17L;
        if (key == timeCacheKey) {
            return timeCache;
        }
        timeCacheKey = key;

        long hours = (dayTime / 1000L + 6L) % 24L;
        long minutes = dayTime % 1000L * 60L / 1000L;

        sb.setLength(0);
        sb.append("时间: ");
        if (hours < 10) {
            sb.append('0');
        }
        sb.append(hours).append(':');
        if (minutes < 10) {
            sb.append('0');
        }
        sb.append(minutes);

        timeCache = sb.toString();
        return timeCache;
    }

    private String buildTargetLine(LivingEntity target) {
        if (target != lastTarget) {
            lastTarget = target;
            targetNameCache = target == null ? "无" : target.getName().getString();
        }
        sb.setLength(0);
        sb.append("KillAura: 开 | 目标: ").append(targetNameCache);
        return sb.toString();
    }
}
