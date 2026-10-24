# 游戏机（把 Mocha Doom 搬进 MC 1.7.10）可行性研究

评估对象：`tmp/mochadoom-master/`（Mocha Doom，纯 Java 的 Doom 移植，GPLv3）。目标：新道具「游戏机」，右键开一个
占半屏的窗口跑 Doom；`Esc` 关窗；`WASD` / 方向键移动，`E` 开门/使用，等等。

**结论：可行，而且比预想的容易。** 判据见 §1；必须改的 Mocha Doom 源码约 10 个文件、每处十几行（§3）；MC 侧新写
约 12 个类（§4）；剩下是 WAD 数据（§6）与线程/生命周期（§5）。

---

## 0. 源码规模

`tmp/mochadoom-master/src/`：**444 个 .java 文件、86,761 行**，无第三方依赖（只用 JDK 标准库），
`javac -cp . mochadoom/Engine.java` 一把编译（`build-jar.sh`）。按包（文件 / 行）：

| 包 | 文件 | 行 | 内容 |
| --- | --- | --- | --- |
| `p` | 83 | 15,907 | 关卡/物件/动作（playloop，`AbstractLevelLoader`、`mobj_t`、`Actions`） |
| `rr` | 87 | 15,782 | 软渲染器（`SceneRenderer`、`RendererState`、`parallel/`） |
| `doom` | 32 | 9,584 | 主循环（`DoomMain` 3,507 行）、事件、配置、CVar |
| `data` | 23 | 7,871 | 表格、`info`（state/mobjinfo 大表）、音效表 |
| `s` | 28 | 7,156 | 声音（SFX / 音乐 / 混音驱动） |
| `v` | 38 | 5,235 | 视频子系统：`DoomGraphicSystem` 接口 + 三种位深的软件渲染器 |
| `m` | 23 | 3,784 | 菜单、`Settings`、`MenuMisc` |
| `w` | 23 | 3,173 | WAD 加载（`WadLoader` 1,473 行） |
| `utils` | 10 | 2,666 | `C2JUtils` 等 |
| `f` | 7 | 2,371 | 终章 / 过场 / 擦屏（wipe） |
| `awt` | 9 | 2,023 | **AWT 窗口层（移植时整包丢弃）** |
| 其余 | ~100 | ~13,000 | `st` / `automap` / `hu` / `defines` / `savegame` / `g` / `i` / `boom` / `n` / `demo` / `timing` 等 |

---

## 1. 可行性判据

### 1.1 引擎本来就是「平台可替换」的架构

「引擎逻辑」与「宿主平台」已经拆干净，接口：

| 接口 | 作用 | 现成实现 |
| --- | --- | --- |
| `v.DoomGraphicSystem<T,V>` | 视频输出（`getScreenImage()`、`getScreen()`、调色板/擦屏/绘图 API） | `BufferedRenderer`(8bit) / `BufferedRenderer16` / `BufferedRenderer32` |
| `i.IDoomSystem` | 退出、报错、弹窗、计时等待 | `i.DoomSystem`(AWT) / `i.DummySystem`（**空实现已存在**） |
| `s.IDoomSound` | 高层音效 | `AbstractDoomAudio` / `DummySoundDriver` |
| `s.ISoundDriver` | 低层混音输出 | `SuperDoomSoundDriver` / `ClipSFXModule` / `SpeakerDoomSoundDriver` / **`DummySFX`** |
| `s.IMusic` | 音乐 | `DavidMusicModule`(MIDI) / `FinnwMusicModule` / **`DummyMusic`** |
| `doom.CVarManager` | 命令行参数 | 全走 `-xxx` 开关，宿主随便塞 |

「无声 / 无窗口运行」的桩类**仓库里就有**（`DummySystem`、`DummySFX`、`DummyMusic`、`DummySoundDriver`）；
`CommandVariable` 还留了个没接线的 `NOVOLATILEIMAGE` 开关（`doom/CommandVariable.java:42`）——原作者想过去掉 AWT 的
`VolatileImage`，没做完。

### 1.2 配置可以完全靠命令行参数喂进去，不用改代码

`DoomMain()` 只认 `CVarManager`（命令行）和 `ConfigManager`（cfg 文件）。关键开关：

- `-iwad <路径>`：直接指定 IWAD（`DoomMain.IdentifyVersion()`，`DoomMain.java:631`）。
- `-config <文件…>`：**整包替换** cfg 列表（`doom/ConfigBase.java:147`）→ `default.cfg` / `mochadoom.cfg` 读写都落
  在指定目录，不污染游戏根目录。
- `-multiply N`（1..5）：`320*N x 200*N`（`v/scale/VisualSettings.java:43`，`VideoScale.BASE_WIDTH = 320`、
  `BASE_HEIGHT = 200`）。**`-multiply 1` 跑原生 320x200**，GL 侧 `GL_NEAREST` 放大 —— CPU 最省、像素最原味。
- `-truecolor` / `-indexed` / `-hicolor`：位深（`v/renderers/BppMode.java:61`）。
- `-nosound` / `-nomusic` / `-nosfx`：一键静音（`s/IMusic.java:45`、`s/ISoundDriver.java:79`）。

### 1.3 帧缓冲是一个裸的 `int[]`，天然适合贴到 GL 纹理

真彩渲染器 `v/renderers/BufferedRenderer32.java` 把带 gamma / 受伤红屏 / 无敌金身的 ARGB 写进 `BufferedImage` 的
底层数组 `int[] raster`（第 46 行），再 blit 到 `VolatileImage` 给 AWT（第 128-131 行）。**我们要的正是中间那个
`int[]`**：跳过 `VolatileImage`，`raster` 直接进 GL 纹理。320x200x4 = 256 KB/帧，`glTexSubImage2D` 无压力。

MC 1.7.10 的 LWJGL2 标准做法：`glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL12.GL_BGRA,
GL12.GL_UNSIGNED_INT_8_8_8_8_REV, IntBuffer.wrap(raster))`（小端机上 `0x00RRGGBB` 的内存布局正好是 B,G,R,A）。

### 1.4 已经跑通的无头验证（spike，`tmp/doom-spike/`）

上面的补丁在 `tmp/doom-spike/` 里**真做了一遍**（不碰仓库），并用 Freedoom 1 做无头冒烟测试：

| 步骤 | 结果 |
| --- | --- |
| 原版源码原样编译（`javac 25 --release 8`，442 文件） | ✅ 0 error |
| 删 `awt/**` + 打完 §3 补丁后编译（435 文件） | ✅ 0 error |
| `-Djava.awt.headless=true` 无头启动，`-iwad freedoom1.wad -multiply 1 -truecolor -nosound` | ✅ 1.6 s 引擎就绪 |
| 连续渲染 400 帧 | ✅ 13.4 s，**29.9 fps**，320x200 |
| 首帧非黑像素 | ✅ 64000 / 64000 |
| 宿主置停止位后 `DoomLoop()` 返回 | ✅ 正常返回，JVM 存活（**没有 `System.exit`**） |
| 帧内容 | ✅ 开枪中的第一人称画面 + 完整状态栏（`tmp/doom-spike/last-frame.png`） |

「无头启动 + 裸 ARGB 帧 + 干净停下」已验证；剩下搬仓库、包名重定位、MC 侧道具 / GUI / 按键 / 纹理上传。

（spike 用裸 `javac`，**没有**过本仓库的 `jvmDowngrader` 管线，见 §7。）

---

## 2. AWT 耦合面到底有多大

全仓库 import `java.awt` / `javax.swing` / `javax.sound` 的有 30 个文件；按「是否需要真实显示设备」分类后，
硬骨头很小：

| 类别 | 文件 | 处理 |
| --- | --- | --- |
| **需要真实显示设备** | `awt/*`(9)、`v/renderers/BufferedRenderer16.java`、`BufferedRenderer32.java`、`SoftwareParallelVideoRenderer.java`（`GraphicsEnvironment.getLocalGraphicsEnvironment()` 是 **static final 字段**，类初始化即触发） | `awt/*` 整包不进编译；Buffered16/32 的 `VolatileImage` 走「无头模式」分支（§3.4） |
| 纯几何类 | `java.awt.Rectangle`：`hu/HU`、`st/StatusBar`、`f/Finale`、`rr/RendererState`、`automap/Map`、`v/graphics/{Blocks,Palettes,Rectangles}`、`DoomStatus`… | **无头安全** |
| 纯图像类 | `java.awt.image.{BufferedImage,DataBuffer*,IndexColorModel,Raster}`：`MenuMisc`(截图)、`Palettes`、`BufferedRenderer`(8bit) | **无头安全**（`BufferedImage` 不需显示设备） |
| 输入常量 | `g/Signals.java` 用 `KeyEvent.VK_*` 命名扫描码；`doom/event_t.java` 用 `MouseEvent.BUTTON*` 和 `Robot`(仅 AWT 窗口) | 编译期常量，**无头安全**；只需 `ScanCode.virtualKey` 加 getter（§3.5） |
| 音频 | `javax.sound.sampled/midi`（`s/DoomSound`、`channel_t`、`ClipSFXModule`、`DavidMusicModule`…） | v1 `-nosound`，不碰 |

结论：**唯一的真障碍是 `VolatileImage` + `GraphicsEnvironment`，其余 AWT 都是纯数据类。**

---

## 3. 必须打的 Mocha Doom 补丁清单

计划：整棵（打过补丁的）源码树进仓库，**顶层包统一改名**成 `messtech.doom.*`，避免 `utils` / `data` /
`v` / `m` 和 GTNH 其他 mod 撞车（`tmp/` 下 26 棵依赖源码树已抽查，当前没撞；改名只是重写 `package` /
`import` 行）。License 无碍：本仓库 **GPLv3**（`LICENSE`），Mocha Doom 也是 GPLv3。

| # | 文件 | 改动 | 为什么必须 |
| --- | --- | --- | --- |
| 1 | `mochadoom/Engine.java` | 去 AWT 建窗；`Engine.instance` 改宿主注入；`getCVM()/getConfig()/updateFrame()` 签名保留 | **52 处** `Engine.getXxx()` 静态调用不能动；`Engine` 现在 `private` 构造 + 建 `DoomWindow`，`getConfig()` 会**懒加载出真窗口** |
| 2 | `doom/DoomMain.java` 构造器 | `new DoomSystem(this)` → `Engine.host().createDoomSystem(this)` | 用我们的 `IDoomSystem`，避开 `i/DoomSystem.java` 的 4 处 `System.exit` 和 `awt.MsgBox` |
| 3 | `doom/DoomMain.java:424` `DoomLoop()` | `while(true)` → `while(!Engine.host().running())` + try/catch 宿主异常 | 上游**没有退出条件**，只能 `System.exit` |
| 4 | `doom/DoomMain.java:340,364` `Display()` | `Engine.updateFrame()` → `Engine.host().frameReady(DOOM)` | 帧交付钩子（§1.3）；现在是 blit 到 AWT 窗口 |
| 5 | `doom/DoomMain.java:2743` / `utils/C2JUtils.java`(8) / `p/Actions/ActionsPlats.java`(2) / `p/UnifiedGameMap.java`(2) | `System.exit()` → `throw new MTDoomFatalError(...)` | **危险项**：WAD 缺 lump、地图超限时 `System.exit` **连 Minecraft 一起杀掉** |
| 6 | `v/renderers/SoftwareParallelVideoRenderer.java:44` | `GRAPHICS_CONF` 由 `static final` 立即求值改按需 + 无头分支 | 类加载即碰 `GraphicsEnvironment` |
| 7 | `v/renderers/BufferedRenderer32.java` | 加无头模式：不建 `VolatileImage`，`doWriteScreen()` 只做调色/gamma 后处理、交出 `int[] raster` | 无头 + 要裸帧 |
| 8 | `v/renderers/BppMode.java` | 加 `HeadlessTrueColor` 枚举项指向 7 的渲染器 | 不动 AWT 版行为 |
| 9 | `g/Signals.java:213` | `private final char virtualKey` → 加 `public int getVirtualKey()` | MC 侧要建 LWJGL 键码 → `ScanCode` 映射表 |
| 10 | `i/system.java` / `awt/**` / `i/i_*.c,h` | **不纳入编译** | 遗留 C 入口 / AWT 窗口层 |

可选（不影响首跑）：`doom/event_t.java` 的 `java.awt.Robot`（只有 AWT 窗口控制器用）、`m/Settings.java` 的
`parallelism_realcolor_tint` 默认 = 全部核心数（**cfg 压到 1~2**，否则 320x200 真彩后处理开满线程抢 CPU）。

---

## 4. MC 侧要新写的东西

| 类 | 职责 |
| --- | --- |
| `MTItems.gameConsole` + `MTItemList.GameConsole` | 道具本体（`MTItems.java:112` 的 `registerItems()` 里注册），右键开窗 |
| `MTItemGameConsole` | `onItemRightClick`：客户端 `FMLClientHandler.showGuiScreen(new MTGuiGameConsole())`；服务端只回一句提示（纯客户端娱乐，不涉容器/物品栏同步，不需要 `IGuiHandler`） |
| `MTGuiGameConsole extends GuiScreen` | 半屏窗口：320:200 比例、占屏幕 50% 面积的居中面板；`drawScreen` 上传纹理 + `Tessellator` 画；`Esc` → `mc.displayGuiScreen(null)` + 关会话 |
| `MTDoomSession` | 引擎生命周期：开线程、跑 `DoomMain.setupLoop()`、关闭时置停止位 + join |
| `MTDoomHost` | 实现 §3.1 的宿主接口：拿帧、`shouldStop()`、`createDoomSystem()`、日志重定向 |
| `MTDoomSystem implements IDoomSystem` | `Quit()` → 停止位；`Error()` → 抛宿主异常；`GenerateAlert()` → 直接放行 |
| `MTDoomKeyMap` | LWJGL 键码 → `g.Signals.ScanCode`；`sc.doomEventDown/Up` 喂 `DoomMain.PostEvent()` |
| `MTDoomFrames` | 双缓冲 `int[]`（前台/后台 + `volatile` 交换）+ GL 纹理分配与 `glTexSubImage2D` |
| `MTDoomSettings` | 拼命令行：`-iwad <wad>`、`-config <cfg>`、`-multiply 1`、`-truecolor`、`-nosound`；构造后覆盖 `key_use = SC_E`、`key_left/right = SC_LEFT/SC_RIGHT` 等 |
| `Config` 增量 | `wadPath`（默认 `config/messtech/doom/`，文件名自动嗅探）、`guiScale`、`enableSound`、`threads` |
| 资源 | `assets/messtech/textures/items/gameConsole.png`(16x16)、两个 lang 文件的 `item.messtech.gameConsole.name` 与 tooltip |

**按键方案（关键设计点）**

- MC 的 `GuiScreen` **只有 `keyTyped`，没有 `keyUp`**，而 Doom 没抬键就停不下脚步。方案：在
  `drawScreen()`/`updateScreen()` 里**按帧轮询 `org.lwjgl.input.Keyboard.isKeyDown(i)`**（i 遍历 LWJGL
  键码）与上一帧 diff，自己合成 keydown/keyup；轮询还绕开了 MC 对 `E`（背包）、`/`（聊天）的抢占。
- 键码对得齐：`ScanCode.ordinal()` 就是 DOS 扫描码，LWJGL 的 `KEY_A=30 / KEY_W=17 / KEY_E=18 /
  KEY_SPACE=57 / KEY_1=2 …` 与之**逐位相同**；只有方向键/编辑键区要一张小手写表（LWJGL
  `KEY_UP=200/KEY_LEFT=203` ↔ Mocha `SC_UP=103/SC_LEFT=105`）。
- 默认键位：`W/S` 前后、`A/D` 平移、**方向键左右转身 / 上下前后**、`E` 使用（开门）、`左Ctrl` 开火、
  `1..0` 换武器、`Tab` 地图、`Shift` 跑、`Esc` **退出窗口**（不交给 Doom 开菜单；Doom 菜单另绑 `M` /
  `F1`）。

---

## 5. 线程与生命周期

```
MC 客户端线程：右键 → new MTGuiGameConsole ──start──▶ Doom 线程（daemon）
  MTDoomSession.run() → new CVarManager(args) / ConfigManager → new DoomMain<>()
  → Engine.host = MTDoomHost → setupLoop() ── while(!stop) ──┐
  ◀── Display() → frameReady(raster) → 拷贝/交换双缓冲 ◀──────┘
MC 每帧 drawScreen：if (front 变了) glTexSubImage2D、画 quad；轮询键盘 → PostEvent(keyevent_t)
Esc / onGuiClosed → session.stop() → 置停止位、join(2s)、释放纹理与缓冲
```

- 引擎**非线程安全**：调用全在 Doom 线程；MC 侧只经 `PostEvent`（原为 AWT EDT 异步队列，`DoomMain.java:145`）+
  双缓冲帧交换。
- 世界不暂停（1.7.10 开 GUI 不暂停单机），怪照打；可在 config 加「打开游戏机时暂停世界」。
- 首开要读 WAD + `R_Init`（几秒）→ 必须**先出加载中 GUI**，不能卡 MC 主线程。
- 关闭务必 `stop()` → join，不能让线程自然结束（引擎没有退出条件）。

---

## 6. WAD 数据（唯一真正的非技术障碍）

引擎要 IWAD 才能跑。`defines/DoomVersion.java:26` 认：`doom2.wad`、`doomu.wad`、`udoom.wad`、`doom.wad`、
`doom1.wad`、`plutonia.wad`、`tnt.wad`，以及 **`freedoom1.wad` / `freedoom2.wad` / `freedm.wad`**。

- **绝不打包 id 原版 WAD**（商业版权）。
- 推荐玩家自备，放 `config/messtech/doom/` 自动嗅探；找不到就在 tooltip / 打开时给中文指引，指向 Freedoom
  （BSD 系许可，可自由分发）。
- 备选：内置 Freedoom（约 20 MB，jar 变胖 + 要随附 COPYING）或首次打开联网下载——都不如自备干净，留作
  config 开关。
- 引擎还要因 `C2JUtils` 的 `System.exit` 加固（§3 第 5 项），坏 WAD 场景尤其重要。

---

## 7. 风险清单

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 86k 行源码进仓库 / 进 jar | 仓库变大，jar 约 +3 MB | 接受；或把 `messtech.doom.*` 拆成独立 source set |
| `jvmDowngrader`（本仓库 `enableModernJavaSyntax = jvmDowngrader`）编译这批 Java 8 源码 | 降级阶段可能报错 | **先做编译 spike**，最可能卡住的一步 |
| 顶层包名撞车（`utils`/`data`/`i`/`m`/`v`…） | 与其他 mod 类冲突 | 整体重命名到 `messtech.doom.*` |
| `System.exit` 残留 | 崩服务器 | §3 第 5 项全换宿主异常 |
| 真彩后处理默认开满线程 | 抢 CPU | cfg 压到 1~2；或默认 `-indexed` |
| AWT `GraphicsEnvironment` 在多显卡/Linux 下 | 首次初始化可能异常 | §3 第 6、7 项去掉依赖 |
| 音频设备争用（javax.sound vs OpenAL） | 没声音 / 卡顿 | v1 `-nosound`，之后按需接 |
| 引擎自带 MIDI 合成器缺失 | `DavidMusicModule` 初始化失败 | 同上，`-nomusic` |

---

## 8. 里程碑

1. ~~**编译 spike**~~：✅（§1.4）
2. ~~**无头启动 spike**~~：✅（§1.4）
3. ~~**进仓库 + 进 MC**~~：✅（§9）
4. **后续可做**：音效、鼠标视角、合成表、Doom 自己的存档界面文案、把引擎拆成独立 source set。

---

## 9. 实际落地（已完成）

### 9.1 代码放在哪

| 位置 | 内容 |
| --- | --- |
| `src/main/java/com/MessTech/doom/**` | **437 个文件**：Mocha Doom 全量源码，去掉 `awt/**` 与 `i/system.java`，顶层包重定位到 `com.MessTech.doom.*`（`p` / `rr` / `doom` / `data` / `s` / `v` / `m` / `w` / … 原结构保留） |
| `src/main/java/com/MessTech/common/gameconsole/` | MC 侧宿主：`MTGameConsoleHost`（引擎线程 + 双缓冲帧 + 输入轮询）、`MTGameConsoleSystem`（`IDoomSystem`）、`MTGameConsoleKeys`（LWJGL↔`ScanCode`）、`MTGameConsoleWads`（找/下载 IWAD） |
| `src/main/java/com/MessTech/common/gui/GuiGameConsole.java` | 半屏窗口：纹理上传 + 最近邻放大 + Esc 退出 + 加载/下载/失败三态 |
| `src/main/java/com/MessTech/common/items/MTItemGameConsole.java` | 道具「游戏机」，右键开窗（`onItemRightClick` 覆盖空中、`onItemUseFirst` 覆盖对块） |
| 其余 | `MTItemList.GameConsole`、`MTItems.gameConsole`、`ClientProxy.openGameConsole`、`Config.GAME_CONSOLE_*`、`assets/.../items/gameConsole.png`、两个 lang 文件 |

包名重定位是脚本做的（重写 `package` / `import` 行 + 12 处行内全限定名），`javac` 全量编译通过。

### 9.2 对引擎实际打了哪些补丁（比 §3 的计划多几处，都是实现时发现的）

| 补丁 | 原因 |
| --- | --- |
`mochadoom/Engine` 重写 | 去掉 AWT 建窗；`bootstrap(cvm, host)` 注入宿主；`getCVM()/getConfig()/updateFrame()` 签名不变，52 处静态调用无需改动 |
`Host` 接口（`frameReady` / `running` / `createDoomSystem` / `fatal`） | 帧交付、循环退出、`IDoomSystem` 注入 |
`DoomMain.DoomLoop` `while(true)` → `while(host.running())` | 上游没有任何退出条件 |
`v/renderers/BufferedRenderer32` 去掉 `VolatileImage`，新增 `HeadlessRaster.renderFrame()` | 无头提帧；顺带避免 320x200 真彩后处理把 CPU 全占了 |
`SoftwareParallelVideoRenderer.GRAPHICS_CONF` 延迟初始化 + `HEADLESS` 判断 | 类加载即碰 `GraphicsEnvironment` |
**17 处 `System.exit` → `DoomFatalError`** | 否则坏 WAD 会把整个 Minecraft 带走 |
`g/Signals.ScanCode.getVirtualKey()` | MC 侧建键码映射表 |
`DoomStatus.aliasKey()` + `DoomMain.applyKeyAliases()` | 让方向键和 WASD 同时生效（一个动作只有一个绑定槽） |
`HostPaths` + `ConfigBase.getFolder()` / `RendererState` / `DoomMain` / `Menu` 的路径改写 | 存档、`tranmap.dat`、`default.cfg`、`mochadoom.cfg` 都落在游戏机自己的目录，不污染游戏根目录 |
`WadLoader.finalize()` 删除 + 宿主显式 `CloseAllHandles()` | `finalize` 已废弃；Windows 下不关句柄会锁住 WAD |
`WadLoader` 的 `(CacheableDoomObject) thebuffer` → 抛异常 | JDK 25 起 `java.nio.ByteBuffer` 是 sealed，这死分支编不过 |
`awt/**` 不进编译 | AWT 窗口层不需要 |

另外把 `mochadoom.cfg` 的 `parallelism_realcolor_tint` 播种成 `Config.GAME_CONSOLE_THREADS`（默认 2），免得引擎
默认按 CPU 核数开线程和 MC 抢。

### 9.3 数据流与线程

```
MC 客户端线程：右键 → new GuiGameConsole ──start──▶ Doom 线程（daemon）
  找/下载 IWAD（进度回填 GUI）→ HostPaths.setBaseDirectory(<config>/messtech/gameconsole)
  → Engine.bootstrap → DoomMain → 覆盖键位 + commit() → setupLoop() ── while(host.running()) ──┐
MC 每帧：host.pollInput() ─PostEvent(keyevent_t)─▶ ProcessEvents / G_Ticker / D_Display        │
        host.copyFrame() + glTexSubImage2D ◀── frameReady()：后处理 + 双缓冲交换 ◀──────────────┘
Esc → onGuiClosed → host.stop()（停止位 + join 3s + 关闭 WAD 句柄 + 关线程池）
```

- 引擎只在 Doom 线程上被碰；MC 侧只经 `PostEvent` 队列 + 双缓冲帧交换。
- 键盘**按帧轮询 `Keyboard.isKeyDown`**（理由见 §4）：`GuiScreen` 没有抬起事件，DOOM 没抬键停不下来。
- `doesGuiPauseGame()` 默认 `false`（世界照常跑），`Config.GAME_CONSOLE_PAUSES_WORLD` 可改。

### 9.4 键位（默认值，全部可在游戏机里改）

| 键 | 作用 |
| --- | --- |
| `W` / `↑`，`S` / `↓` | 前进 / 后退 |
| `A` / `←`，`D` / `→` | 左转 / 右转 |
| `,` / `.` | 左平移 / 右平移（`Alt` + `A`/`D` 也行） |
| `E` / `Space` | 开门、按开关 |
| `Ctrl` | 开火 |
| `Shift` | 疾跑 |
| `1`..`0` | 换武器 |
| `Tab` | 地图 |
| `F1` | DOOM 自己的菜单（牺牲了原来的帮助画面，且**固定不可改**） |
| **`Esc`** | **关掉游戏机窗口**（固定不可改） |

### 9.5 数据（WAD）

`<游戏目录>/config/messtech/gameconsole/` 下的 IWAD；按 Mocha Doom 自己的 `DoomVersion` 名单嗅探（`doom.wad` /
`doom2.wad` / `tnt.wad` / `freedoom1.wad` …），大小写不敏感。一个都没有、且
`GAME_CONSOLE_DOWNLOAD_FREEDOOM=true` 时，第一次打开从 GitHub release 下载 **Freedoom Phase 1**（24 MB zip，
解包出 27 MB 的 `freedoom1.wad`），带进度条；下载走 `.part` 临时文件，中断不留半截 WAD。**id 的原版 WAD 既不下
也不打包。**

### 9.6 验证记录

| 检查 | 结果 |
| --- | --- |
| `./gradlew compileJava` | ✅ |
| `./gradlew spotlessApply build`（含 `downgradeJar` 的 jvmDowngrader 与 `checkstyleMain`） | ✅ |
| **重定位后**的引擎放进普通 JVM 无头跑（`tmp/relocated-smoke/`） | ✅ 120 帧 / 33–35 fps / 320x200、首帧 64000 像素全非黑 |
| 方向键别名真驱动了 WASD 绑定（调 `G_Responder` 后读 `gamekeydown`） | ✅ down=true / up=true |
| 存档、`tranmap.dat`、两个 cfg 落在游戏机目录、不污染工作目录 | ✅ |
| 全程不 `System.exit` | ✅ JVM 存活 |
| 声音：`I_InitSound: configured audio device` → `sound module ready`，关窗 `ShutdownSound` 7 ms 返回 | ✅ |
| 按键模型：默认值 / 改键 / 互斥 / 清空 / `keys.cfg` 往返（`tmp/relocated-smoke/BindingsCheck.java`） | ✅ ALL OK（并抓出一个注释解析 bug） |
| OPL2 音乐（真 WAD 的 D_E1M1，11142 个事件，8 s）：**Java 8 与 JDK 25 输出完全一致** | ✅ 94.02% 非零样本 / 峰值 13733，一字节不差（6 s 版 93.39% / 11503） |
| 音乐（JDK 25 / Java 8，**不加任何 JVM 参数**） | ✅ 不需要 `--add-opens`，已从构建脚本删掉 |
| 引擎端到端（音乐开）：`Music through the OPL2 emulator at 22050 Hz`，`music drained by the mixer = 310800 bytes` / 3.43 s ≈ **90.6 KB/s**（理论 88.2） | ✅ 帧率 **35.0 fps**（与无音乐相同），关音频 22 ms |
| GENMIDI 解码：175 个音色的 TL 全 ≤63、KSL 全 ≤3、波形 ≤6、feedback ≤7 | ✅ 0 处越界；钢琴 ATK=15、管风琴 SL=1、小提琴 ATK=6、低音鼓 ATK=15 |
| 发布 jar 里 `s/opl/*` 的字节码 | ✅ major 52（Java 8） |
| 产物体积 | 3.79 MB（移植前 1.37 MB，引擎约 +2.4 MB） |

**未验证**：真机 `runClient` 的画面、手感与**实际听感**（需 GUI 交互，只做到编译 + 无头运行；音乐「有没有
真从喇叭出来」靠 `drainedBytes` 计数 + 字节速率对齐推出）。

### 9.7 声音

引擎声音**不走 Minecraft 的 OpenAL**：Mocha Doom 自带混音器（`s.SuperDoomSoundDriver`，22050 Hz / 16bit /
立体声 / big-endian，2 线程 + 21 Hz `Timer`），混好的流写进第二条 `javax.sound` 数据线 → 两条音频路径互不干扰，
也不碰 OpenAL 上下文。

开关（`Config`，都在 `[GameConsole]` 段）：

| 配置 | 默认 | 作用 |
| --- | --- | --- |
| `GAME_CONSOLE_SOUND` | `true` | 音效；关掉传 `-nosfx` |
| `GAME_CONSOLE_MUSIC` | `true` | 音乐；关掉传 `-nomusic` |
| 两个都关 | — | 传 `-nosound`，引擎换成全空实现 |

- 设备打不开**不会拖垮游戏机**：`ISoundDriver.chooseModule` 本来就会在 `InitSound()` 失败时退回 `DummySFX`。
  上游 `ShutdownSound()` 是 `while(!done)` 死等 + 从不取消 Timer，已改成**上限 500 ms 的等待 +
  `MIXTIMER.cancel()` + 给 join 加超时**。
- 音量跟 Minecraft 主音量走（`GameSettings.getSoundLevel(SoundCategory.MASTER)` → 0..127）。
- 关窗时调 `music.ShutdownMusic()` + `soundDriver.ShutdownSound()`：否则那条数据线、混音线程和 21 Hz 守护
  Timer 会活到进程结束。

#### 9.7.1 音乐：自己仿真 OPL2

`DavidMusicModule` / `FinnwMusicModule` 把 MIDI 事件丢给机器上的 MIDI 设备 → 第三条音频路径（音量和延迟不受
控）。试过用 JDK 自带合成器 Gervill 把 MUS 渲成 PCM：能出声，但 Gervill 在 `java.desktop/com.sun.media.sound`，
**Java 9 起该包不导出**，要反射 + `--add-opens` 才拿得到 PCM，否则退回「独立设备」。为不带这条版本风险进发布
物，改成**自己仿真 YM3812 (OPL2)** —— DOOM 的音乐本就是给这块 FM 芯片写的，WAD 里还带配套的 `GENMIDI` 音色表：

```
MUS lump → MusReader → Sequence → OplMusicPlayer（事件→寄存器写入）→ OPL2 仿真 → PCM
        → MusicMixBuffer（32 KB 环形）→ PlaybackServer 把音乐叠加进已混好的音效块 → SourceDataLine
```

`s/opl/`：

| 文件 | 作用 |
| --- | --- |
| `OPL2.java` | 芯片仿真：9 通道 × 2 算子、包络、4 种波形、反馈、FM/加法合成 |
| `GenMidiBank.java` | WAD 的 `GENMIDI` 音色表解析（含字节布局的推导依据） |
| `DoomSong.java` | 曲谱：MIDI 走 JDK 的 `MidiSystem`、MUS 走引擎自带的 `MusReader`，归一成带时间戳的事件表 |
| `OplMusicPlayer.java` | 事件播放：音符分配通道、控制器、音高换算 |
| `OplMusicModule.java` | `IMusic` 实现，PCM 泵进 `MusicMixBuffer` |

**只依赖 JDK 普通的 `javax.sound.midi` 数据类**（`Sequence`/`Track`/`MidiEvent`/`ShortMessage`，各版本都在）：
不碰合成器、不碰 `com.sun.*`、不要 JVM 参数，构建脚本那行 `--add-opens` 已删。

关键点：

1. **`GENMIDI` 字节布局从数据反推**（网络无规范）：`#OPL_II#`(8) + 175×36 音色 + 175×32 名字；音色 =
   `flags:u16, finetune:u8(0x80|x), note:u8, voices[2][16]`；voice = 7 字节算子 + 7 字节算子 +
   `int16 base_note_offset`；算子 7 字节 = `0x20, 0x60, 0x80, 波形, KSL(<<6), TL(0..63), 未用`。验证靠**物理
   约束**：175 音色 TL 全 ≤63、KSL 全 ≤3、波形 ≤6、feedback ≤7，钢琴 ATK=15、管风琴 SL=1（延音）、小提琴
   ATK=6、低音鼓 ATK=15 —— 全合乐器常识。
2. **算子槽位不跟通道走**：`0x20/0x40/0x60/0x80/0xE0` 每组横跨 6 个算子槽（通道 0-2 的调制器、再是它们的载波，
   然后 3-5、6-8），故通道 `n` 用槽 `n + n/3`、载波 +3；写 `0x20 + n` 只有前三个通道对，后面打到别人载波上。
3. **环形缓冲的空闲空间就是节拍器**：芯片只在有空位时渲染，混音器每拍恰好腾出一块，**腾空间本身就是实时** ——
   播放器不需要自己的定时器，不会漂。
4. **音乐也算「有活干」**：上游 `MixServer` 只在有采样通道时 `checkOut` 一块缓冲并提交
   （`mixed = activeChannels()`），音乐**在提交那一刻**才叠加，于是「只有音乐没有音效」（标题画面、站着不动）时
   音乐永远出不去。改成 `mixed = activeChannels() || hasMusicWaiting()`；`drainedBytes` 抓出来的（修前 `0 bytes`）。
5. 音高用芯片的 F-Number 表（一个八度 12 值 + block 换八度），音色的 `base_note_offset` 加在音符号上；音量走
   **载波 TL**（每级约 0.75 dB 衰减），按衰减量线性映射才是听感均匀的淡入淡出。

**已知简化**：打击乐用打击乐音色当普通音符弹，没走芯片 rhythm mode（那几个通道能噪声合成军鼓），鼓点是短促音高
击打而非噪声 —— 通道管理和芯片模型因此简单，但和原版仍有差距。

### 9.8 已知限制 / 后续

- 只有键盘，无鼠标视角；`Esc`（退出窗口）、`F1`（DOOM 菜单）固定，不进按键设置。
- **OPL2 仿真非逐比特精确**：包络时间用几何级数（只对齐 AR0/RR15 这类端点）、衰减是连续 dB 未量化成芯片的
  0.1875 dB 台阶、KSL 用平均偏移而非逐键表、AM/VIB 是固定深度的正弦、打击乐是「类似」实现而非那五个专用算子
  → 听感像 AdLib 的 FM，不是硬件复刻。
- **算子槽位有串扰**：`OplMusicPlayer` 与芯片用 `通道 + 通道/3` 排布（硬件是 `通道 + 3*(通道/3)`），槽 4、5、8、9
  被相邻两通道共用 → 通道 1/3、2/4、4/6、5/7 耦合。改硬件排布要**同时**改 `OplMusicPlayer` 与 `OPL2`（槽位映射
  必须一致），试过一次，不一致会直接静音。
- 弯音轮没实现（曲子基本不用）。
- **MUS 分支没端到端验证**：只有 Freedoom 这个 WAD，其 `D_*` 全是 MIDI，id 原版才是 MUS。MUS 走引擎自带的
  `MusReader`（上游给原版 WAD 用的那份）再走同一事件转换；自写的 MUS 解析器跟它对同一串字节对不上，没留（两份
  实现比一份危险）。真机跑 id 原版 WAD 前值得确认。
- 同一时刻只有一个引擎实例（`Engine` 静态单例）→ 只能开一个窗口；关窗即销毁，再开要重读 WAD 约 1–3 秒（有
  「正在初始化」界面）。
- 没写合成表，道具只在 GT 创造标签里。
- 引擎 `C2JUtils` 等错误路径抛异常 → 显示「游戏机启动失败」而不崩游戏。
- Gradle daemon 默认 512 MB 堆在 `downgradeJar` 会 GC 抖动失败；本次用 `-Dorg.gradle.jvmargs=-Xmx4G`，建议
  `gradle.properties` 补一行 `org.gradle.jvmargs`。
- **降级后的 jar 拿普通 Java 8 单跑整个引擎会缺 `xyz.wagyourtail.jvmdg` 桩类**（`CommandVariable` /
  `CVarManager` / `DoomIO` 用到 JDK 12 的 `TypeDescriptor`），游戏里由 gtnhlib 提供；这三个类移植时就有，与音乐
  无关。引擎级验证在 JDK 25 上做，音乐模块级才是 Java 8 + 真 jar。

### 9.9 按键自定义（已实现）

**不接 Minecraft 的 `KeyBinding`**，用游戏机自己的界面：那些键只在游戏机窗口开着时有意义，且（E、Tab、1-9、
Ctrl、Shift）与原版键冲突 —— 塞进全局控制列表会让每个键报警，玩家为消冲突反而去改原版键；`KeyBinding` 也表达
不了「一个动作两个键」。

**机制**（[MTGameConsoleBindings.java](../src/main/java/com/MessTech/common/gameconsole/MTGameConsoleBindings.java)）：
物理映射（LWJGL 键码 → `ScanCode`）固定，玩家改「哪个键驱动哪个动作」：

- 20 个动作各有**一个参考扫描码**（DOOM 自己监听的那个：`SC_W`、`SC_LCTRL`、`SC_1` …）。
- 引擎启动后调 `apply(doom)`：先把 10 个 `key_*` 槽强制回参考码，再对每个绑定调
  `DoomStatus.aliasKey(物理键的 ScanCode, 槽)`（让方向键与 WASD 并存的钩子）→ 按 K（绑给前进）既发 `SC_K`
  （DOOM 的菜单照常见 K）又驱动 `key_up` 槽。
- `Tab` 是唯一例外：DOOM 直接按扫描码读地图键，绑给地图的键额外发一次 `SC_TAB`。
- 全键盘通吃：`Keyboard` 能看到的键都能绑；绑到映射表外的键会在日志里说一声，不静默失效。

**界面**：窗口右上角 `[按键设置]`，进去是**同一窗口的一个模式**（不是第二个 `GuiScreen` —— 换屏会关窗，关窗会
停引擎，看眼键位就得重开）。两页：动作 / 武器；点方框改键，`Backspace` 清空，`Esc` 取消；底部恢复默认 / 翻页 /
返回。改动存 `<console dir>/keys.cfg`（`forward=17,200  # W / UP`），**下次打开游戏机生效**，界面上也这么写。

**一并修掉的覆盖问题**：以前 `configureKeys()` 后跟 `commit()` + `SaveDefaults()`，每次启动都把整个
`mochadoom.cfg` 重写一遍，玩家在 DOOM 菜单改的东西全被冲掉。现在不写回：键位唯一来源是 `keys.cfg`，
`mochadoom.cfg` 恢复成 DOOM 自己的运行时配置（音量、gamma 照常保存）。

**验证**：`tmp/relocated-smoke/BindingsCheck.java`（LWJGL + log4j 挂 classpath 直接跑），条目见 §9.6。
**抓到过一个真 bug**：`keys.cfg` 行尾的 `# W / UP` 注释让第二个键解析失败、读回来变空，已在 `load()` 里先剥
注释。

---

## 10. 事实来源

- `tmp/mochadoom-master/README.md`、`build-jar.sh`（纯 javac、无第三方依赖、GPLv3）
- `src/mochadoom/Engine.java`（入口、AWT 建窗、`updateFrame` 静态钩子）
- `src/doom/DoomMain.java:2520-2735`（构造顺序、`DoomLoop` 死循环、`setupLoop`）
- `src/doom/DoomStatus.java:525`（`CM = Engine.getConfig()` 的 final 字段耦合）
- `src/v/DoomGraphicSystem.java`、`src/v/renderers/{BufferedRenderer32,SoftwareGraphicsSystem,SoftwareParallelVideoRenderer,BppMode}.java`
- `src/doom/ConfigBase.java:141-160`（`-config` 替换 cfg 列表）、`src/doom/CommandVariable.java`
- `src/defines/DoomVersion.java`（IWAD 名单）、`src/v/scale/{VideoScale,VideoScaleInfo,VisualSettings}.java`
- `src/g/Signals.java:68-232`（`ScanCode` 枚举即 DOS 扫描码表）
- `src/i/{DoomSystem,DummySystem}.java`、`src/s/{IMusic,ISoundDriver,IDoomSound}.java`
