package com.example.clientmod.module

import com.example.clientmod.ClientMod
import com.example.clientmod.config.ModConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Hand
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.lwjgl.glfw.GLFW
import java.util.function.Predicate

/**
 * KillAura —— 自动锁定并攻击范围内符合条件的生物。
 *
 * 本文件是工程里唯一的 Kotlin 代码：业务模块（目标筛选、距离比较、瞄准计算）
 * 用 Kotlin 的空安全和 when 表达最省事；框架层（配置、按键、GUI、渲染）仍是 Java。
 * 依赖是单向的（Kotlin → Java），删掉本文件并移除 Kotlin 插件后框架照样能编译。
 *
 * 性能约定（为低配手机与优化模组环境而设）：
 * - 实体扫描默认每 2 tick 一次，中间 tick 复用缓存目标
 * - Predicate 只创建一次，避免每次扫描分配 lambda
 * - 缓存目标校验不做 canSee 射线检测，射线只在真正扫描时才计算
 *
 * 请用在你自己的单人世界，或朋友之间彼此知情同意的私人服务器。
 */
object KillAura : Module("KillAura", GLFW.GLFW_KEY_K) {

    /** 复用同一个 predicate，避免每次扫描都 new 一个 lambda */
    private val ALWAYS_TRUE = Predicate<LivingEntity> { true }

    val range: NumberSetting
    val delay: NumberSetting
    /** 实体扫描间隔 tick。调大更省 CPU，调小锁定更快 */
    val scanInterval: NumberSetting
    val autoAim: BoolSetting
    val requireLineOfSight: BoolSetting
    val targetPlayers: BoolSetting
    val targetHostile: BoolSetting
    val targetPassive: BoolSetting
    val targetOther: BoolSetting

    private var cooldown = 0
    private var scanTimer = 0
    private var cachedTarget: LivingEntity? = null

    init {
        val cfg = ClientMod.CONFIG

        range = number("距离", cfg?.killAuraRange ?: 3.0, 1.0, 6.0, 0.1) { v ->
            setConfig { it.killAuraRange = v }
        }
        delay = number("间隔 tick", (cfg?.killAuraDelay ?: 12).toDouble(), 0.0, 40.0, 1.0) { v ->
            setConfig { it.killAuraDelay = v.toInt() }
        }
        scanInterval = number("扫描间隔", (cfg?.killAuraScanInterval ?: 2).toDouble(), 1.0, 10.0, 1.0) { v ->
            setConfig { it.killAuraScanInterval = v.toInt() }
        }
        autoAim = bool("自动瞄准", cfg?.killAuraAutoAim ?: true) { v ->
            setConfig { it.killAuraAutoAim = v }
        }
        requireLineOfSight = bool("需要视线", cfg?.killAuraRequireLineOfSight ?: true) { v ->
            setConfig { it.killAuraRequireLineOfSight = v }
        }
        targetPlayers = bool("打玩家", cfg?.killAuraTargetPlayers ?: true) { v ->
            setConfig { it.killAuraTargetPlayers = v }
        }
        targetHostile = bool("打敌对生物", cfg?.killAuraTargetHostile ?: true) { v ->
            setConfig { it.killAuraTargetHostile = v }
        }
        targetPassive = bool("打被动生物", cfg?.killAuraTargetPassive ?: false) { v ->
            setConfig { it.killAuraTargetPassive = v }
        }
        targetOther = bool("打其他生物", cfg?.killAuraTargetOther ?: false) { v ->
            setConfig { it.killAuraTargetOther = v }
        }
    }

    /** 参数改动只标记 dirty，由 tick 定时统一落盘，避免拖滑块时疯狂写文件 */
    private fun setConfig(action: (ModConfig) -> Unit) {
        val cfg = ClientMod.CONFIG ?: return
        action(cfg)
        cfg.markDirty()
    }

    override fun persistEnabled() {
        setConfig { it.killAuraEnabled = isEnabled() }
    }

    fun getCurrentTarget(): LivingEntity? = cachedTarget

    override fun onDisable() {
        cachedTarget = null
        cooldown = 0
        scanTimer = 0
    }

    override fun onTick(client: MinecraftClient) {
        val player = client.player ?: return
        val world = client.world ?: return
        val interaction = client.interactionManager ?: return

        // 死亡、旁观模式、打开任何界面时一律不工作
        if (player.isDead || player.isSpectator || client.currentScreen != null) return

        if (cooldown > 0) {
            cooldown--
            return
        }

        // 缓存目标仍然有效就直接用，省掉一次实体扫描
        var target = cachedTarget
        if (scanTimer > 0 && stillValid(player, target)) {
            scanTimer--
        } else {
            target = scan(world, player)
            cachedTarget = target
            scanTimer = scanInterval.value.toInt()
        }

        if (target == null) return

        if (autoAim.value) aimAt(player, target)

        interaction.attackEntity(player, target)
        player.swingHand(Hand.MAIN_HAND)
        cooldown = delay.value.toInt()
    }

    /** 轻量校验：不做射线检测，用于判断缓存目标能否继续用 */
    private fun stillValid(player: ClientPlayerEntity, entity: LivingEntity?): Boolean {
        if (entity == null) return false
        return entity.isAlive
                && !entity.isRemoved
                && player.distanceTo(entity) <= range.value
    }

    private fun scan(world: ClientWorld, player: ClientPlayerEntity): LivingEntity? {
        val maxDist = range.value
        val box: Box = player.boundingBox.expand(maxDist)
        val candidates = world.getEntitiesByClass(LivingEntity::class.java, box, ALWAYS_TRUE)

        val needSight = requireLineOfSight.value
        var best: LivingEntity? = null
        var bestDist = Double.MAX_VALUE

        for (entity in candidates) {
            if (entity === player || !entity.isAlive || entity.isRemoved) continue
            if (!matchesFilter(entity)) continue
            if (player.distanceTo(entity) > maxDist) continue
            // 射线检测较重，只在真正扫描时做
            if (needSight && !player.canSee(entity)) continue

            val dist = player.squaredDistanceTo(entity)
            if (dist < bestDist) {
                bestDist = dist
                best = entity
            }
        }
        return best
    }

    private fun matchesFilter(entity: LivingEntity): Boolean = when (entity) {
        is PlayerEntity -> targetPlayers.value
        is HostileEntity -> targetHostile.value
        is AnimalEntity -> targetPassive.value
        else -> targetOther.value
    }

    /** 把视角转向目标身体中心 */
    private fun aimAt(player: ClientPlayerEntity, target: LivingEntity) {
        val eye: Vec3d = player.eyePos
        val center: Vec3d = target.boundingBox.center

        val dx = center.x - eye.x
        val dy = center.y - eye.y
        val dz = center.z - eye.z
        val horizontal = Math.sqrt(dx * dx + dz * dz)

        val yaw = (Math.toDegrees(Math.atan2(dz, dx)) - 90.0).toFloat()
        val pitch = (-Math.toDegrees(Math.atan2(dy, horizontal))).toFloat()
            .coerceIn(-90.0f, 90.0f)

        player.yaw = yaw
        player.pitch = pitch
    }
}
