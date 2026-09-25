# ClientMod — Fabric 客户端模组骨架

一个可直接编译运行的 Fabric **客户端**模组空壳。功能还没写，等你点单；骨架已经把最常见的几块地基铺好了：
按键绑定、HUD 叠加渲染、配置读写与持久化、Mixin 预留。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.8 |
| Fabric Loader | 0.17.2 |
| Fabric API | 0.133.4+1.21.8 |
| Loom | 1.11.8 |
| Kotlin | 2.1.20 |
| JDK | 21（必须，1.21.x 硬性要求） |

## 快速开始

```bash
./gradlew build          # 产物在 build/libs/clientmod-1.0.0.jar
./gradlew runClient      # 直接启动带模组的游戏客户端
./gradlew genSources     # 生成反编译源码，IDE 里点进 MC 类看实现
```

把 `build/libs/*.jar` 丢进 `.minecraft/mods/`，同时装好 Fabric Loader 和 Fabric API 即可。
（本模组依赖 Fabric API，因为它用了 Fabric 的按键与 HUD 事件。）

## 目录结构

```
src/main/java/com/example/clientmod/     ← 框架层（Java）
├── ClientMod.java              客户端入口，所有功能在这里注册
├── config/ModConfig.java       配置：GSON 持久化 + 防抖落盘
├── input/KeyBindings.java      按键绑定：注册 + 按下时触发
├── render/HudOverlay.java      HUD 绘制：带文本缓存，每帧零分配
├── module/
│   ├── Module.java             模块基类：开关 + onTick + 参数声明
│   ├── ModuleManager.java      模块注册表，一个模块报错不会拖崩游戏
│   ├── Setting.java            参数基类
│   ├── BoolSetting.java        开关型参数（界面生成按钮）
│   └── NumberSetting.java      数值型参数（界面生成滑块）
└── gui/
    ├── ClickGuiScreen.java     设置界面，右 Shift 打开
    └── SettingSlider.java      数值参数滑块

src/main/kotlin/com/example/clientmod/   ← 业务层（Kotlin）
└── module/KillAura.kt          自动战斗模块
src/main/resources/
├── fabric.mod.json         模组描述、入口、依赖声明
├── clientmod.mixins.json   Mixin 配置（client 数组目前为空）
└── assets/clientmod/lang/  按键名与分类名的中英文显示
```

## 现在能做什么

| 操作 | 效果 |
| --- | --- |
| **右 Shift** | 打开设置界面（左栏模块列表，右栏参数与快捷键） |
| **G** | 切换左上角 HUD |
| **K** | 切换 KillAura（可在界面里改键） |

HUD 显示：坐标、朝向、生命值、游戏内时间；KillAura 开启时多一行显示当前锁定目标。
配置存在 `.minecraft/config/clientmod.json`，可以直接手改（改完重启生效）。

## 语言构成：Java + Kotlin

依赖是**单向的**：Kotlin 依赖 Java，Java 完全不知道 Kotlin 的存在。

| 层 | 语言 | 内容 |
| --- | --- | --- |
| 框架层 | Java | 入口、配置、按键、GUI、渲染、模块基类、参数类型 |
| 业务层 | Kotlin | `KillAura.kt`（目前唯一的 Kotlin 文件） |

这么分的理由：业务模块（目标筛选、距离比较、瞄准计算）用 Kotlin 的空安全和
`when` 表达最省事；框架层要频繁对接 Minecraft 与 Fabric 的 API，Java 写法更直观。

**代价**：Minecraft 运行环境不带 Kotlin，`kotlin-stdlib` 会打进 jar，体积增加约 1.5MB。

**如果 Kotlin 编译出问题**：删掉 `src/main/kotlin` 整个目录，再从 `build.gradle`
移除 `org.jetbrains.kotlin.jvm` 插件、`kotlin {}` 块和 stdlib 两行依赖，
并注释掉 `ClientMod.java` 里注册 KillAura 的那两行，就是纯 Java 工程。

## 轻量化

### 运行时开销

- **实体扫描节流**：KillAura 默认每 2 tick 才扫一次世界，中间 tick 复用缓存目标。
  扫描间隔可在界面里调（低配机器建议 3~5）
- **复用 Predicate 实例**：避免每次扫描都新建 lambda
- **射线检测延后**：`canSee()` 会做射线投射，只在真正扫描时才算，
  缓存目标的校验只查存活与距离
- **HUD 每帧零分配**：复用同一个 StringBuilder；每行文本只在内容变化时
  才重建 `Text` 对象；时间按分钟缓存，不用 `String.format`
- **配置防抖**：拖滑块时 `onSet` 每帧触发，改成只打 dirty 标记，
  由 tick 每 20 tick 统一落盘，关闭界面时再兜底写一次
- 模块 tick 抛异常会被 ModuleManager 捕获，不会连累游戏主循环

### 体积与依赖

- **细粒度 Fabric API**：只引 `fabric-api-base`、`fabric-key-binding-api-v1`、
  `fabric-lifecycle-events-v1`、`fabric-rendering-v1` 四个模块，不再拖整个 fabric-api
- **无 Mixin**：`clientmod.mixins.json` 的 client 数组是空的，
  不改任何原版字节码，从根源上避免与其他模组冲突

## 优化模组兼容性

本模组只用公开 API，不注入原版代码，因此与主流优化模组共存：

| 模组 | 兼容情况 |
| --- | --- |
| Sodium / Iris | 兼容。渲染只用 `DrawContext` 绘制文字与标准 widget，不走自定义渲染管线 |
| Lithium | 兼容。它优化的是服务端与实体 AI，本模组只做客户端读取 |
| ImmediatelyFast / EntityCulling | 兼容。HUD 是 2D 叠加，实体剔除不影响 KillAura 读取世界实体列表 |
| Starlight / FerriteCore | 兼容。不涉及光照与内存结构 |

已知注意点：

- KillAura 在**客户端 tick** 里工作，不受服务端优化模组影响
- 若同时装了其他改攻击逻辑的模组（如自动点击类），可能与本模组的挥手动作叠加

## 设置界面

右 Shift 打开，**界面里的改动立即生效并落盘**，不用重启。

- **左栏**：所有模块。点一下切换开关，同时选中它
- **右栏**：选中模块的开关、快捷键、各项参数
  - 开关型参数 → 按钮，点击切换
  - 数值型参数 → 滑块，拖动调整
  - 快捷键 → 点击后按任意键即完成重绑，Esc 取消

再按右 Shift 或 Esc 关闭界面。界面打开时不响应模块快捷键，避免和按键重绑冲突。

**快捷键为什么存自己的 json**：原版 `GameOptions` 的保存接口各版本名字不一，容易编译失败。
所以模块按键代码写在 `config/clientmod.json` 的 `moduleKeys` 里，启动时读回来 `setBoundKey`。
打开界面那个键是顶层的 `guiKeyCode`（默认 344 = 右 Shift）。

## 新增一个模块

写个类继承 `Module`，在构造里声明参数，重写 `onTick`：

```java
public class MyModule extends Module {
    public MyModule() {
        super("MyModule", GLFW.GLFW_KEY_J);            // 名字 + 默认按键
        addSetting(new BoolSetting("开关项", true, v -> { /* 写配置 */ }));
        addSetting(new NumberSetting("数值项", 3.0, 1.0, 6.0, 0.1, v -> { /* 写配置 */ }));
    }

    @Override
    public void onTick(MinecraftClient client) {
        // 每 tick 的逻辑，只在开启时调用
    }
}
```

然后在 `ClientMod` 里 `ModuleManager.register(new MyModule())`。
界面的开关、参数、快捷键全部自动生成，还要记得在 lang 文件里加一行
`key.clientmod.module.mymodule` 让按键名正常显示。

## KillAura

按 **K** 键开关，也可以在设置界面（右 Shift）里点开。
状态会显示在 HUD 上（开着的时候会多一行显示当前锁定目标）。默认关闭。
下表这些参数都能在界面里直接调，也可以手改 `config/clientmod.json`。

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `killAuraEnabled` | false | 总开关，游戏内按 K 切换 |
| `killAuraRange` | 3.0 | 攻击距离。**原版生存只有 3 格**，调大只会挥空手，服务端会丢弃超距攻击 |
| `killAuraDelay` | 12 | 两次攻击间隔 tick（20 tick = 1 秒）。剑的冷却约 12~13 tick，再快也不会多造成伤害 |
| `killAuraScanInterval` | 2 | 实体扫描间隔 tick。**调大更省 CPU**（低配建议 3~5），调小锁定更跟手 |
| `killAuraAutoAim` | true | 自动把视角转向目标 |
| `killAuraRequireLineOfSight` | true | 需要看得见才打。关掉 = 隔墙攻击 |
| `killAuraTargetPlayers` | true | 攻击玩家 |
| `killAuraTargetHostile` | true | 攻击敌对生物 |
| `killAuraTargetPassive` | false | 攻击被动生物 |
| `killAuraTargetOther` | false | 攻击其他 LivingEntity（铁傀儡、蝙蝠等） |

以下情况自动停止工作：玩家死亡、旁观模式、打开背包/箱子/任何界面。

**使用边界**：单人世界，或你和朋友之间自己开的、彼此知情同意的服务器。
任何有反作弊的公共服务器都会封号，别拿去冒险。

### 实现要点

`src/main/java/com/example/clientmod/module/KillAura.java`：

1. 用 `player.getBoundingBox().expand(range)` 画一个搜索盒
2. `world.getEntitiesByClass(LivingEntity.class, box, 过滤条件)` 取出候选
3. 按距离排序取最近的一个
4. `interactionManager.attackEntity(player, target)` + `player.swingHand(Hand.MAIN_HAND)`
5. 冷却计数用 tick，不依赖版本敏感的攻击冷却 API

## 加功能的三个入口

- **要画东西在屏幕上** → 改 `render/HudOverlay.java` 的 `buildLines()`
- **要按键触发** → 在 `input/KeyBindings.java` 加一个 `KeyBinding`，并在 `onClientTick` 里判断
- **要改原版行为** → 在 `com.example.clientmod.mixin` 建 Mixin 类，然后填进 `clientmod.mixins.json` 的 `client` 数组

换包名 / 模组 ID 时，记得同步改：目录名、`ClientMod.MOD_ID`、`fabric.mod.json` 的 `id` 和入口类、`gradle.properties` 的 `maven_group` / `archives_base_name`、lang 目录名。

## 手机上怎么编译（没有电脑时）

手机上**本地编译基本跑不通**：Loom 要下载并合并、反编译整个 Minecraft jar，峰值内存 3~4GB，还要求 JDK 21 与桌面级 JVM；手机上的 Java 版启动器（Pojav / Fold Craft）只负责**运行**游戏，不提供编译环境。

可行路径是**让云端替你编译**，手机只负责改代码和收 jar：

1. 把整个工程上传到 GitHub（新建仓库 → 网页上 Upload files，或用 GitHub 手机 App）。
2. 工程已带 `.github/workflows/build.yml`，push 完会自动开始编译；也可在 Actions 页面点 **Run workflow** 手动触发。
3. 编译完成后，进 Actions → 对应任务 → 底部 **Artifacts** 下载 `clientmod-jar`，解压就是能直接用的 jar。
4. 改代码可以直接在 GitHub 网页打开仓库，按 **`.`** 键启动网页版 VS Code 编辑并 commit，全程不用电脑。

其他备选：有云服务器的话，Termux / JuiceSSH 连上去跑 `./gradlew build`；或直接用 GitHub Codespaces，浏览器里开一个完整的编译环境。

### 手机上传的坑（重要）

GitHub 网页端和官方 App **都不能创建子目录**：网页的 `Upload files` 会把多层目录的文件全部摊平上传，`src/main/java/...` 结构没了，编译必然失败。官方 App 只能看仓库、改不了文件。

安卓手机请用 **Termux + 本仓库自带的 `tools/push_to_github.sh`**，它走 GitHub REST API 按路径创建文件，目录会自动建好：

```bash
pkg install git curl
cd 你解压后的工程目录
export GITHUB_OWNER=你的GitHub用户名
export GITHUB_TOKEN=github_pat_xxxxx
bash tools/push_to_github.sh
```

### 清空仓库重新上传（PURGE）

仓库里堆积了来历不明的文件（典型：GitHub 新建仓库时自动生成的
`.github/workflows/gradle.yml`，它带一个 `dependency-submission` 任务，
会自己装一个和 wrapper 版本不一致的 Gradle，还会因为 gradlew 没有可执行
权限直接 126 退出），增量上传怎么都清不干净时，用 PURGE 全清再重填：

```bash
PURGE=1 bash tools/push_to_github.sh   # 只清 src/、*.gradle、.github/ 等工程文件
PURGE=all bash tools/push_to_github.sh # 连 LICENSE / README 一起全清
```

默认范围不会误删 LICENSE 和 README.md。清完脚本会立即重新上传本地工程，
结果是干净的一份。

Token 在 GitHub 网页端生成：头像 → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token，勾选 **repo**（读写仓库内容）。Token 只在生成时显示一次，记得先复制保存。

iOS 上没有 Termux，可用 App **Working Copy** 导入工程后再推送；或在仓库页面按 `.` 键进网页版 VS Code，用带斜杠的完整路径新建文件（如 `src/main/java/com/example/clientmod/ClientMod.java`），GitHub 会自动建出目录。

想在手机上**玩**到这个模组：装 PojavLauncher（或 Fold Craft Launcher）→ 装 Fabric 1.21.8 → 把 jar 和 Fabric API 一起放进 mods 目录。注意手机需要 6GB+ 内存才比较稳。

## 换 MC 版本

只改 `gradle.properties` 三行即可：`minecraft_version`、`yarn_mappings`、`fabric_version`。
版本号在 [fabricmc.net/develop](https://fabricmc.net/develop/) 和 [Fabric API 的 Modrinth 版本页](https://modrinth.com/mod/fabric-api/versions) 查，注意三者必须对应同一个 MC 版本。
换完执行 `./gradlew build --refresh-dependencies`。
另外 `fabric.mod.json` 里的 `"minecraft": "~1.21.8"` 也要跟着改。

## 编译报错排查

- `Unsupported class file major version 68` / `invalid target release: 21` → 用的是 JDK 8/11，换 JDK 21。
- `Plugin fabric-loom requires at least Gradle 8.14` → 改 `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 为 8.14 及以上，或跑 `./gradlew wrapper --gradle-version 8.14`。
- `getCurrentFps() 找不到` → 你的 MC 版本把 FPS 字段改名了，删掉 `HudOverlay` 里 "FPS" 那一行，或换成 `client.getWindow()` 相关的调试接口。
- `HudRenderCallback` 找不到 → 确认装了 Fabric API 依赖，且 `build.gradle` 里 `fabric_version` 与 `minecraft_version` 匹配。
- `Could not find org.jetbrains.kotlin:kotlin-stdlib` → 检查 `gradle.properties` 的 `kotlin_version` 是否与 `build.gradle` 的插件版本一致（都是 2.1.20）。
- `Kotlin plugin requires...` / 报 Kotlin 版本与 Gradle 不兼容 → 换 Kotlin 2.0.21，或把 Gradle 升到 8.14 以上（本项目已是 8.14）。
- `Unresolved reference: xxx`（Kotlin 里报 Minecraft 类找不到）→ Yarn 映射改名了，改回 Java 写法或直接引用对应的 intermediary 名。
- jar 变大到 1.5MB 以上 → 正常，`kotlin-stdlib` 打进去了。想瘦身就按「语言构成」一节的方法移除 Kotlin。

## 打包发布

```bash
./gradlew build
```

产物 `build/libs/clientmod-1.0.0.jar`（含 sources jar）。
