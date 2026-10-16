# 游戏机（把 Mocha Doom 搬进 MC 1.7.10）可行性研究

评估对象：`tmp/mochadoom-master/`（Mocha Doom，纯 Java 的 Doom 源码移植，GPLv3）。
目标：新增道具「游戏机」，右键打开一个占半个屏幕的窗口，窗口里运行 Doom；`Esc` 关闭窗口；`WASD` /
方向键控制移动，`E` 开门/使用，等等。

**结论：可行，而且比预想的容易。** 理由见 §1 的三条判据。真正必须改动的 Mocha Doom 源码只有
约 10 个文件、每处都是十几行量级（§3）；MC 侧需要新写约 12 个类（§4）。剩下的工作是 WAD 数据
（§6）和线程/生命周期（§5）。

---

## 0. 源码规模

`tmp/mochadoom-master/src/` 共 **444 个 .java 文件、86,761 行**，无第三方依赖（只用 JDK 标准库），
用 `javac -cp . mochadoom/Engine.java` 一把编译（`build-jar.sh`）。按包拆分（文件数 / 行数）：

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

代码里已经把「引擎逻辑」和「宿主平台」拆干净了，接口都在：

| 接口 | 作用 | 现成实现 |
| --- | --- | --- |
| `v.DoomGraphicSystem<T,V>` | 视频输出（`getScreenImage()`、`getScreen()`、调色板/擦屏/绘图 API） | `BufferedRenderer`(8bit) / `BufferedRenderer16` / `BufferedRenderer32` |
| `i.IDoomSystem` | 退出、报错、弹窗、计时等待 | `i.DoomSystem`(AWT) / `i.DummySystem`（**空实现已存在**） |
| `s.IDoomSound` | 高层音效 | `AbstractDoomAudio` / `DummySoundDriver` |
| `s.ISoundDriver` | 低层混音输出 | `SuperDoomSoundDriver` / `ClipSFXModule` / `SpeakerDoomSoundDriver` / **`DummySFX`** |
| `s.IMusic` | 音乐 | `DavidMusicModule`(MIDI) / `FinnwMusicModule` / **`DummyMusic`** |
| `doom.CVarManager` | 命令行参数 | 全部走 `-xxx` 开关，宿主可以随便塞 |

也就是说「无声运行」「无窗口运行」的桩类**仓库里本来就有**（`DummySystem`、`DummySFX`、
`DummyMusic`、`DummySoundDriver`）。`CommandVariable` 里甚至已经留了一个 `NOVOLATILEIMAGE`
开关（`doom/CommandVariable.java:42`），只是从来没接线——原作者当时就想过要去掉 AWT 的
`VolatileImage`，没做完。

### 1.2 配置可以完全靠命令行参数喂进去，不用改代码

`DoomMain()` 构造函数只认两样东西：`CVarManager`（命令行）和 `ConfigManager`（cfg 文件）。
关键的两个开关：

- `-iwad <路径>`：直接指定 IWAD（`DoomMain.IdentifyVersion()`，`DoomMain.java:631`）。
- `-config <文件…>`：**整包替换** cfg 文件列表（`doom/ConfigBase.java:147`），于是
  `default.cfg` / `mochadoom.cfg` 的读写都落在我们指定的目录里，不会污染游戏根目录。
- `-multiply N`（1..5）：分辨率倍数，`320*N x 200*N`（`v/scale/VisualSettings.java:43`，
  `VideoScale.BASE_WIDTH = 320`、`BASE_HEIGHT = 200`）。**用 `-multiply 1` 跑原生 320x200**，
  再在 GL 侧 `GL_NEAREST` 放大——CPU 最省、像素最原味。
- `-truecolor` / `-indexed` / `-hicolor`：位深（`v/renderers/BppMode.java:61`）。
- `-nosound` / `-nomusic` / `-nosfx`：一键静音（`s/IMusic.java:45`、`s/ISoundDriver.java:79`）。

### 1.3 帧缓冲是一个裸的 `int[]`，天然适合贴到 GL 纹理

真彩渲染器 `v/renderers/BufferedRenderer32.java` 的做法是：把带 gamma / 受伤红屏 / 无敌金身的
ARGB 结果直接写进一张 `BufferedImage` 的底层数组 `int[] raster`（第 46 行），然后 blit 到
`VolatileImage` 上交给 AWT 显示（第 128-131 行）。**我们要的正是那个中间产物 `int[]`**：跳过
`VolatileImage`，把 `raster` 拷进一张 GL 纹理即可。320x200x4 = 256 KB/帧，`glTexSubImage2D`
无压力。

MC 1.7.10 用的 LWJGL2 里，标准做法是 `glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, IntBuffer.wrap(raster))`（小端机上
`0x00RRGGBB` 的内存布局正好是 B,G,R,A）。

### 1.4 已经跑通的无头验证（spike，`tmp/doom-spike/`）

上面的补丁在 `tmp/doom-spike/` 里**真做了一遍**（不碰仓库），并用 Freedoom 1 做了一次完整的
无头冒烟测试：

| 步骤 | 结果 |
| --- | --- |
| 原版源码原样编译（`javac 25 --release 8`，442 文件） | ✅ 0 error |
| 删掉 `awt/**` + 打完 §3 补丁后编译（435 文件） | ✅ 0 error |
| `-Djava.awt.headless=true` 无头启动，`-iwad freedoom1.wad -multiply 1 -truecolor -nosound` | ✅ 1.6 s 引擎就绪 |
| 连续渲染 400 帧 | ✅ 13.4 s，**29.9 fps**，320x200 |
| 首帧非黑像素 | ✅ 64000 / 64000 |
| 宿主置停止位后 `DoomLoop()` 返回 | ✅ 正常返回，JVM 存活（**没有 `System.exit`**） |
| 帧内容 | ✅ 开枪中的第一人称画面 + 完整状态栏（`tmp/doom-spike/last-frame.png`） |

「无头启动 + 拿裸 ARGB 帧 + 能干净停下」这条最关键的链路**已经验证**。剩下的是搬进仓库、
包名重定位、以及 MC 侧的道具 / GUI / 按键 / 纹理上传。

（注意：spike 用的是裸 `javac`，**没有**经过本仓库的 `jvmDowngrader` 管线；那一步仍需在
真实构建里过一遍。见 §7。）

---

## 2. AWT 耦合面到底有多大

全仓库 import `java.awt` / `javax.swing` / `javax.sound` 的有 30 个文件，但按「是否需要真实显示
设备」分类后，剩下的硬骨头很小：

| 类别 | 文件 | 处理 |
| --- | --- | --- |
| **需要真实显示设备** | `awt/*`(9)、`v/renderers/BufferedRenderer16.java`、`BufferedRenderer32.java`、`SoftwareParallelVideoRenderer.java`（`GraphicsEnvironment.getLocalGraphicsEnvironment()` 是 **static final 字段**，类初始化即触发） | `awt/*` 整包不进编译；两个 Buffered16/32 的 `VolatileImage` 走「无头模式」分支（§3.4） |
| 纯几何类 | `java.awt.Rectangle`：`hu/HU`、`st/StatusBar`、`f/Finale`、`rr/RendererState`、`automap/Map`、`v/graphics/{Blocks,Palettes,Rectangles}`、`DoomStatus`… | **无头安全**，不用动 |
| 纯图像类 | `java.awt.image.{BufferedImage,DataBuffer*,IndexColorModel,Raster}`：`MenuMisc`(截图)、`Palettes`、`BufferedRenderer`(8bit) | **无头安全**（`BufferedImage` 不需要显示设备），不用动 |
| 输入常量 | `g/Signals.java` 用 `KeyEvent.VK_*` 给扫描码命名；`doom/event_t.java` 用 `MouseEvent.BUTTON*` 和 `Robot`(仅 AWT 窗口用) | 编译期常量，**无头安全**；只需给 `ScanCode.virtualKey` 加个 getter（§3.5） |
| 音频 | `javax.sound.sampled/midi`（`s/DoomSound`、`channel_t`、`ClipSFXModule`、`DavidMusicModule`…） | v1 直接 `-nosound`，不碰 |

结论：**唯一的真障碍是 `VolatileImage` + `GraphicsEnvironment`，其余 AWT 都是纯数据类。**

---

## 3. 必须打的 Mocha Doom 补丁清单

计划做法：把（打过补丁的）整棵源码树纳入仓库，**顶层包统一改名**成 `messtech.doom.*` 之类，
避免 `utils` / `data` / `v` / `m` 这种顶层包名和 GTNH 其他 mod 撞车（已抽查 `tmp/` 下 26 棵依赖
源码树，当前没撞；但改名是零成本的保险，只需重写 `package` / `import` 行）。
License 无碍：MessTech 仓库本身就是 **GPLv3**（`LICENSE`），Mocha Doom 也是 GPLv3。

| # | 文件 | 改动 | 为什么必须 |
| --- | --- | --- | --- |
| 1 | `mochadoom/Engine.java` | 去掉 AWT 建窗；`Engine.instance` 改成可由宿主注入；`getCVM()/getConfig()/updateFrame()` 保留签名 | 全仓 **52 处** `Engine.getXxx()` 静态调用要不能动。`Engine` 目前是 `private` 构造 + 建 `DoomWindow`，`getConfig()` 会**懒加载出一个真窗口** |
| 2 | `doom/DoomMain.java` 构造器 | `new DoomSystem(this)` → 宿主工厂 `Engine.host().createDoomSystem(this)` | 用我们自己的 `IDoomSystem`，避免 `i/DoomSystem.java` 里的 4 处 `System.exit` 和 `awt.MsgBox` |
| 3 | `doom/DoomMain.java:424` `DoomLoop()` | `while(true)` → `while(!Engine.host().running())`，整体 try/catch 宿主异常 | **目前没有任何退出条件**，只能 `System.exit` |
| 4 | `doom/DoomMain.java:340,364` `Display()` | `Engine.updateFrame()` → `Engine.host().frameReady(DOOM)` | 帧交付钩子（§1.3）。现在这两行是「blit 到 AWT 窗口」 |
| 5 | `doom/DoomMain.java:2743` / `utils/C2JUtils.java`(8 处) / `p/Actions/ActionsPlats.java`(2) / `p/UnifiedGameMap.java`(2) | `System.exit()` → `throw new MTDoomFatalError(...)` | **危险项**：WAD 缺 lump、地图超限时直接 `System.exit` 会**连 Minecraft 一起杀掉** |
| 6 | `v/renderers/SoftwareParallelVideoRenderer.java:44` | `GRAPHICS_CONF` 由 `static final` 立即求值改成按需 + 无头分支 | 类加载即碰 `GraphicsEnvironment` |
| 7 | `v/renderers/BufferedRenderer32.java` | 加「无头模式」：不建 `VolatileImage`，`doWriteScreen()` 只做调色/gamma 后处理并把 `int[] raster` 交出去 | 无头 + 我们要拿裸帧 |
| 8 | `v/renderers/BppMode.java` | 加一个 `HeadlessTrueColor` 枚举项指向 7 的渲染器 | 避免改到 AWT 版行为 |
| 9 | `g/Signals.java:213` | `private final char virtualKey` → 加 `public int getVirtualKey()` | MC 侧要建 LWJGL 键码 → `ScanCode` 映射表 |
| 10 | `i/system.java` / `awt/**` / `i/i_*.c,h` | **不纳入编译** | 遗留的 C 时代入口 / AWT 窗口层 |

可选（不影响第一次跑通）：
`doom/event_t.java` 里的 `java.awt.Robot`（只有 AWT 窗口控制器在用）、`m/Settings.java` 里
`parallelism_realcolor_tint` 默认 = 全部核心数（**建议在 cfg 里压到 1~2**，否则 320x200 的真彩
后处理会开满线程和 MC 抢 CPU）。

---

## 4. MC 侧要新写的东西

| 类 | 职责 |
| --- | --- |
| `MTItems.gameConsole` + `MTItemList.GameConsole` | 道具本体（`MTItems.java:112` 的 `registerItems()` 里注册），右键开窗 |
| `MTItemGameConsole` | `onItemRightClick`：客户端 `FMLClientHandler.showGuiScreen(new MTGuiGameConsole())`；服务端只回一句提示（游戏机是纯客户端娱乐，不涉及容器/物品栏同步，也就不需要 `IGuiHandler`） |
| `MTGuiGameConsole extends GuiScreen` | 半个屏幕的窗口：算一个保持 320:200 比例、约占屏幕 50% 面积的居中面板；`drawScreen` 里上传纹理 + `Tessellator` 画；`Esc` → `mc.displayGuiScreen(null)` + 关会话 |
| `MTDoomSession` | 引擎生命周期：开线程、跑 `DoomMain.setupLoop()`、关闭时置停止位 + join |
| `MTDoomHost` | 实现 §3.1 的宿主接口：拿帧、`shouldStop()`、`createDoomSystem()`、日志重定向 |
| `MTDoomSystem implements IDoomSystem` | `Quit()` → 停止位；`Error()` → 抛宿主异常；`GenerateAlert()` → 直接放行 |
| `MTDoomKeyMap` | LWJGL 键码 → `g.Signals.ScanCode`；再用 `sc.doomEventDown/Up` 喂 `DoomMain.PostEvent()` |
| `MTDoomFrames` | 双缓冲 `int[]`（前台/后台 + `volatile` 交换），GL 纹理的分配与 `glTexSubImage2D` |
| `MTDoomSettings` | 拼命令行：`-iwad <wad>`、`-config <cfg>`、`-multiply 1`、`-truecolor`、`-nosound`；并在构造后覆盖 `key_use = SC_E`、`key_left/right = SC_LEFT/SC_RIGHT` 等 |
| `Config` 增量 | `wadPath`（默认 `config/messtech/doom/`，文件名自动嗅探）、`guiScale`、`enableSound`、`threads` |
| 资源 | `assets/messtech/textures/items/gameConsole.png`(16x16)、两个 lang 文件里的 `item.messtech.gameConsole.name` 与 tooltip |

**按键方案（关键设计点）**

- MC 的 `GuiScreen` **只有 `keyTyped`，没有 `keyUp`**——而 Doom 必须有抬键事件才能停下脚步。
  方案：在 `drawScreen()`/`updateScreen()` 里**按帧轮询 `org.lwjgl.input.Keyboard.isKeyDown(i)`**
  （i 遍历 LWJGL 键码），和上一帧的状态做 diff，自己合成 keydown/keyup。LWJGL 的键盘状态是原始
  缓冲区，随时可查，而且这样能绕开 MC 对 `E`（背包）、`/`（聊天）等键的抢占。
- 键码对应关系很好：Mocha 的 `ScanCode.ordinal()` 就是 DOS 扫描码，LWJGL 的 `KEY_A=30 / KEY_W=17 /
  KEY_E=18 / KEY_SPACE=57 / KEY_1=2 …` 与之**逐位相同**；只有方向键/编辑键区需要一张小手写表
  （LWJGL `KEY_UP=200/KEY_LEFT=203` ↔ Mocha `SC_UP=103/SC_LEFT=105`）。
- 默认键位按需求改：`W/S` 前后、`A/D` 平移、**方向键左右转身 / 上下前后**、`E` 使用（开门）、
  `左Ctrl` 开火、`1..0` 换武器、`Tab` 地图、`Shift` 跑、`Esc` **退出窗口**（不再交给 Doom 开菜单；
  Doom 自己的菜单另绑一个键，比如 `M` 或 `F1`）。

---

## 5. 线程与生命周期

```
MC 客户端线程                            Doom 线程（daemon）
─────────────────────────────           ────────────────────────────────
右键 → new MTGuiGameConsole  ──start──▶  MTDoomSession.run()
                                          new CVarManager(args) / ConfigManager
                                          new DoomMain<>()
                                          Engine.host = MTDoomHost
                                          setupLoop() ── while(!stop) ──┐
每帧 drawScreen:                        ◀──┐   Display()               │
  if (front 变了) glTexSubImage2D         │   └ frameReady(raster) → 拷贝/交换双缓冲
  画 quad                                 │
  轮询键盘 → PostEvent(keyevent_t)  ──────┘
Esc / onGuiClosed → session.stop() → 置停止位、join(2s)、释放纹理与缓冲
```

注意点：

- Doom 引擎**不是线程安全**的，所有引擎调用都必须在 Doom 线程上；MC 侧只通过
  `PostEvent`（本来就是设计给 AWT EDT 异步投递的队列，`DoomMain.java:145`）和双缓冲帧交换接触它。
- 世界不会暂停（1.7.10 开 GUI 不暂停单机），怪照打——这属于玩法的一部分，也可以在 config 里加
  「打开游戏机时暂停世界」的选项。
- 首次打开要读 WAD + `R_Init`（几秒），必须**先出一个加载中的 GUI**，不能卡 MC 主线程。
- 关闭时务必走 `stop()` → join，而不是让线程自然结束（引擎没有退出条件）。

---

## 6. WAD 数据（唯一真正的非技术障碍）

引擎需要一个 IWAD 才能跑。`defines/DoomVersion.java:26` 认这些文件名：`doom2.wad`、`doomu.wad`、
`udoom.wad`、`doom.wad`、`doom1.wad`、`plutonia.wad`、`tnt.wad`，以及 **`freedoom1.wad` /
`freedoom2.wad` / `freedm.wad`**。

- **绝不能打包 id 的原版 WAD**（商业版权）。
- 推荐：玩家自备，放到 `config/messtech/doom/` 下，模组自动嗅探；找不到就在 tooltip / 打开时给出
  中文指引，指向 Freedoom（BSD 系许可，可自由分发）。
- 备选：内置 Freedoom（约 20 MB，会让 jar 明显变胖 + 需要随附 COPYING），或首次打开时联网下载。
  两者都不如「玩家自备」干净，建议留作 config 开关。
- 引擎代码本身另外还会因为 `C2JUtils` 的 `System.exit` 需要加固（§3 第 5 项），这一点在「用户拿着
  一个坏 WAD」时尤其重要。

---

## 7. 风险清单

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 86k 行源码进仓库 / 进 jar | 仓库变大，jar 约 +3 MB | 接受；或后续把 `messtech.doom.*` 拆成独立 source set |
| `jvmDowngrader`（本仓库 `enableModernJavaSyntax = jvmDowngrader`）编译这批 Java 8 源码 | 可能在降级阶段报错 | **先做一次编译 spike** 验证，这是最可能卡住的一步 |
| 顶层包名撞车（`utils`/`data`/`i`/`m`/`v`…） | 与其他 mod 类冲突 | 整体重命名到 `messtech.doom.*` |
| `System.exit` 残留 | 崩服务器 | §3 第 5 项全部替换成宿主异常 |
| 真彩后处理默认开满线程 | 和 MC 抢 CPU | cfg 压到 1~2；或默认用 `-indexed` |
| AWT `GraphicsEnvironment` 在多显卡/Linux 下的行为 | 首次初始化可能异常 | §3 第 6、7 项去掉依赖 |
| 音频设备争用（javax.sound vs OpenAL） | 没声音 / 卡顿 | v1 `-nosound`，之后按需接 |
| 引擎自带 MIDI 合成器缺失 | `DavidMusicModule` 初始化失败 | 同上，`-nomusic` |

---

## 8. 里程碑

1. ~~**编译 spike**~~：✅ 已完成，见 §1.4。
2. ~~**无头启动 spike**~~：✅ 已完成，见 §1.4。
3. ~~**进仓库 + 进 MC**~~：✅ 已完成，见 §9。
4. **后续可做**：音效、鼠标视角、合成表、Doom 自己的存档界面文案、把引擎拆成独立 source set。

---

## 9. 实际落地（已完成）

### 9.1 代码放在哪

| 位置 | 内容 |
| --- | --- |
| `src/main/java/com/MessTech/doom/**` | **437 个文件**：Mocha Doom 全量源码，去掉 `awt/**` 与 `i/system.java`，顶层包统一重定位到 `com.MessTech.doom.*`（`p` / `rr` / `doom` / `data` / `s` / `v` / `m` / `w` / … 全部保留原结构） |
| `src/main/java/com/MessTech/common/gameconsole/` | MC 侧宿主：`MTGameConsoleHost`（引擎线程 + 双缓冲帧 + 输入轮询）、`MTGameConsoleSystem`（`IDoomSystem`）、`MTGameConsoleKeys`（LWJGL↔`ScanCode`）、`MTGameConsoleWads`（找/下载 IWAD） |
| `src/main/java/com/MessTech/common/gui/GuiGameConsole.java` | 半个屏幕的窗口：纹理上传 + 最近邻放大 + Esc 退出 + 加载/下载/失败三种状态显示 |
| `src/main/java/com/MessTech/common/items/MTItemGameConsole.java` | 道具「游戏机」，右键开窗（`onItemRightClick` 覆盖空中、`onItemUseFirst` 覆盖对着方块） |
| 其余 | `MTItemList.GameConsole`、`MTItems.gameConsole`、`ClientProxy.openGameConsole`、`Config.GAME_CONSOLE_*`、`assets/.../items/gameConsole.png`、两个 lang 文件 |

包名重定位是脚本做的（重写 `package` / `import` 行 + 12 处行内全限定名），
`javac` 全量编译通过。

### 9.2 对引擎实际打了哪些补丁（比 §3 的计划多几处，都是实现时发现的）

| 补丁 | 原因 |
| --- | --- |
`mochadoom/Engine` 重写 | 去掉 AWT 建窗；`bootstrap(cvm, host)` 注入宿主；`getCVM()/getConfig()/updateFrame()` 签名不变，52 处静态调用无需改动 |
`Host` 接口（`frameReady` / `running` / `createDoomSystem` / `fatal`） | 帧交付、循环退出、`IDoomSystem` 注入 |
`DoomMain.DoomLoop` `while(true)` → `while(host.running())` | 上游没有任何退出条件 |
`v/renderers/BufferedRenderer32` 去掉 `VolatileImage`，新增 `HeadlessRaster.renderFrame()` | 无头提帧；顺带避免 320x200 的真彩后处理把 CPU 全占了 |
`SoftwareParallelVideoRenderer.GRAPHICS_CONF` 延迟初始化 + `HEADLESS` 判断 | 类加载即碰 `GraphicsEnvironment` |
**17 处 `System.exit` → `DoomFatalError`** | 否则坏 WAD 会把整个 Minecraft 带走 |
`g/Signals.ScanCode.getVirtualKey()` | MC 侧建键码映射表 |
`DoomStatus.aliasKey()` + `DoomMain.applyKeyAliases()` | 让方向键和 WASD 同时生效（一个动作只有一个绑定槽） |
`HostPaths` + `ConfigBase.getFolder()` / `RendererState` / `DoomMain` / `Menu` 的路径改写 | 让存档、`tranmap.dat`、`default.cfg`、`mochadoom.cfg` 都落在游戏机自己的目录里，而不是游戏根目录 |
`WadLoader.finalize()` 删除 + 宿主显式 `CloseAllHandles()` | `finalize` 已废弃；Windows 下不关句柄会锁住 WAD |
`WadLoader` 那处 `(CacheableDoomObject) thebuffer` → 抛异常 | JDK 25 起 `java.nio.ByteBuffer` 是 sealed，这个死分支已经编不过了 |
`awt/**` 不进编译 | 整个 AWT 窗口层不需要 |

另外把 `mochadoom.cfg` 里的 `parallelism_realcolor_tint` 播种成 `Config.GAME_CONSOLE_THREADS`（默认 2），
免得引擎默认按 CPU 核数开线程和 MC 抢。

### 9.3 数据流与线程

```
MC 客户端线程                                      Doom 线程（daemon）
右键 → new GuiGameConsole ────────start────────▶ 找/下载 IWAD（进度回填到 GUI）
                                                  HostPaths.setBaseDirectory(<config>/messtech/gameconsole)
                                                  Engine.bootstrap → DoomMain
                                                  覆盖键位 + commit()
每帧 drawScreen:                                  setupLoop()  ── while(host.running()) ──┐
  host.pollInput()  ── PostEvent(keyevent_t) ──▶  ProcessEvents / G_Ticker / D_Display   │
  host.copyFrame() + glTexSubImage2D        ◀──   frameReady(): 后处理 + 双缓冲交换 ─────┘
Esc → onGuiClosed → host.stop()（停止位 + join 3s + 关闭 WAD 句柄 + 关线程池）
```

- 引擎只在 Doom 线程上被碰；MC 侧只通过 `PostEvent` 队列和双缓冲帧交换交互。
- 键盘是**按帧轮询 `Keyboard.isKeyDown`**：`GuiScreen` 只有按下事件没有抬起事件，而 DOOM 没抬键就停不下来；
  轮询还顺带绕开了 MC 对 `E`、`/` 这些键的抢占。
- `doesGuiPauseGame()` 默认 `false`（世界照常跑），`Config.GAME_CONSOLE_PAUSES_WORLD` 可以改。

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

`<游戏目录>/config/messtech/gameconsole/` 下的 IWAD；按 Mocha Doom 自己的 `DoomVersion` 名单嗅探
（`doom.wad` / `doom2.wad` / `tnt.wad` / `freedoom1.wad` …），大小写不敏感。
一个都没有、且 `GAME_CONSOLE_DOWNLOAD_FREEDOOM=true` 时，第一次打开会从 GitHub release 下载
**Freedoom Phase 1**（24 MB zip，解包出 27 MB 的 `freedoom1.wad`），带进度条；下载走 `.part` 临时文件，
中断不会留下半截 WAD。**id 的原版 WAD 既不下也不打包。**

### 9.6 验证记录

| 检查 | 结果 |
| --- | --- |
| `./gradlew compileJava` | ✅ |
| `./gradlew spotlessApply build`（含 `downgradeJar` 的 jvmDowngrader 与 `checkstyleMain`） | ✅ |
| 把**重定位后**的引擎放进普通 JVM 无头跑（`tmp/relocated-smoke/`） | ✅ 120 帧 / 33–35 fps / 320x200、首帧 64000 像素全非黑 |
| 方向键别名真的驱动了 WASD 绑定（直接调 `G_Responder` 后读 `gamekeydown`） | ✅ down=true / up=true |
| 存档、`tranmap.dat`、两个 cfg 是否落在游戏机目录、不污染工作目录 | ✅ |
| 全程不 `System.exit` | ✅ JVM 存活 |
| 声音：`I_InitSound: configured audio device` → `sound module ready`，关窗时 `ShutdownSound` 7 ms 返回 | ✅ |
| 按键模型：默认值 / 改键 / 互斥 / 清空 / `keys.cfg` 往返（`tmp/relocated-smoke/BindingsCheck.java`） | ✅ ALL OK（并抓出一个注释解析 bug） |
| OPL2 音乐（真 WAD 的 D_E1M1，11142 个事件，8 s）：**Java 8 与 JDK 25 输出完全一致** | ✅ 94.02% 非零样本 / 峰值 13733，两边一字节不差（6 s 版本为 93.39% / 11503） |
| 音乐（JDK 25 / Java 8，**都不加任何 JVM 参数**） | ✅ 不需要 `--add-opens`，已从构建脚本里删掉 |
| 引擎端到端（音乐开）：`Music through the OPL2 emulator at 22050 Hz`，`music drained by the mixer = 310800 bytes` / 3.43 s ≈ **90.6 KB/s**（理论 88.2） | ✅ 帧率 **35.0 fps**（与无音乐时完全相同），关音频 22 ms |
| GENMIDI 解码：175 个音色的 TL 全 ≤63、KSL 全 ≤3、波形 ≤6、feedback ≤7 | ✅ 0 处越界；钢琴 ATK=15、管风琴 SL=1、小提琴 ATK=6、低音鼓 ATK=15 |
| 发布 jar 里 `s/opl/*` 的字节码 | ✅ major 52（Java 8） |
| 产物体积 | 3.79 MB（移植前 1.37 MB，引擎约 +2.4 MB） |

**未验证**：真机 `runClient` 里的画面、手感与**实际听感**（需要 GUI 交互，本次只做到编译 + 无头运行；
音乐"有没有真的从喇叭出来"是靠 `drainedBytes` 计数 + 字节速率对齐推出来的）。

### 9.7 声音

引擎的声音**不走 Minecraft 的 OpenAL**：Mocha Doom 自己有一台完整的混音器
（`s.SuperDoomSoundDriver`，22050 Hz / 16bit / 立体声 / big-endian，自己两个线程 + 一个 21 Hz 的 `Timer`），
它把混好的流直接写进第二条 `javax.sound` 数据线。所以两条音频路径互不干扰，也不用碰 OpenAL 上下文。

开关（`Config`，都在 `[GameConsole]` 段）：

| 配置 | 默认 | 作用 |
| --- | --- | --- |
| `GAME_CONSOLE_SOUND` | `true` | 音效。关掉就传 `-nosfx` |
| `GAME_CONSOLE_MUSIC` | `true` | 音乐。关掉传 `-nomusic` |
| 两个都关 | — | 传 `-nosound`，引擎换成全空实现 |

- 设备打不开时**不会拖垮游戏机**：`ISoundDriver.chooseModule` 本来就会在 `InitSound()` 失败时退回
  `DummySFX`。上游的 `ShutdownSound()` 是一个 `while(!done)` 死等 + 从不取消 Timer，已经改成
  **上限 500 ms 的等待 + `MIXTIMER.cancel()` + 给 join 加超时**。
- 音量跟着 Minecraft 主音量走（`GameSettings.getSoundLevel(SoundCategory.MASTER)` → 映射到 0..127）。
- 关窗时会调 `music.ShutdownMusic()` + `soundDriver.ShutdownSound()`：不关的话那条数据线、
  混音线程和那个 21 Hz 的守护 Timer 会一直活到进程结束。

#### 9.7.1 音乐：自己仿真 OPL2

上游的 `DavidMusicModule` / `FinnwMusicModule` 都是把 MIDI 事件丢给**机器上某个 MIDI 设备**，那样在游戏机
里就是第三条音频路径（音量和延迟都不受我们控制）。

中间试过一版「让 JDK 自带的软件合成器 Gervill 把 MUS 渲成 PCM 再混音」，能出声音，但 Gervill 住在
`java.desktop/com.sun.media.sound`，**Java 9 起该包不导出**，要靠反射 + `--add-opens` 才能拿到 PCM，
拿不到就只能退回"独立设备"模式。为了不把这条版本风险带进发布物，最终改成**自己仿真 YM3812 (OPL2)** ——
DOOM 的音乐本来就是给这块 FM 芯片写的，WAD 里还带着配套的 `GENMIDI` 音色表：

```
MUS lump → MusReader → Sequence → OplMusicPlayer（事件→寄存器写入）→ OPL2 仿真 → PCM
        → MusicMixBuffer（32 KB 环形）→ PlaybackServer 把音乐叠加进已混好的音效块 → SourceDataLine
```

四个文件（`s/opl/`）：

| 文件 | 作用 |
| --- | --- |
| `OPL2.java` | 芯片仿真：9 通道 × 2 算子、包络、4 种波形、反馈、FM/加法合成 |
| `GenMidiBank.java` | WAD 的 `GENMIDI` 音色表解析（含字节布局的推导依据） |
| `DoomSong.java` | 曲谱读取：MIDI 由 JDK 的 `MidiSystem` 解析、MUS 交给引擎自带的 `MusReader`，两者都归一成同一张带时间戳的事件表 |
| `OplMusicPlayer.java` | 事件播放：音符分配通道、控制器、音高换算 |
| `OplMusicModule.java` | `IMusic` 实现，把 PCM 泵进 `MusicMixBuffer` |

**只依赖 JDK 里普通的 `javax.sound.midi` 数据类**（`Sequence`/`Track`/`MidiEvent`/`ShortMessage` —— 它们
所有版本都在），不碰合成器、不碰 `com.sun.*`、不需要任何 JVM 参数，构建脚本里那行 `--add-opens` 已经删掉。

几处关键点：

1. **`GENMIDI` 的字节布局是从数据本身反推出来的**（网络抓不到规范）。最终确定：
   `#OPL_II#`(8) + 175×36 音色 + 175×32 名字；音色 = `flags:u16, finetune:u8(0x80|x), note:u8, voices[2][16]`；
   一个 voice = 7 字节算子 + 7 字节算子 + `int16 base_note_offset`；算子的 7 个字节是
   `0x20, 0x60, 0x80, 波形, KSL(<<6), TL(0..63), 未用`。
   验证方式是**物理约束**：解析出来 175 个音色的 TL 全部 ≤63、KSL 全部 ≤3、波形 ≤6、feedback ≤7，
   而且钢琴 ATK=15、管风琴 SL=1（延音）、小提琴 ATK=6、低音鼓 ATK=15 —— 全都符合乐器常识。
2. **算子槽位不是跟着通道走的**。`0x20/0x40/0x60/0x80/0xE0` 每组横跨 6 个算子槽（通道 0-2 的调制器、
   再是它们的载波，然后 3-5、6-8），所以通道 `n` 用槽 `n + n/3`、载波是它 +3。
   写成 `0x20 + n` 只有前三个通道对，后面会打到别人的载波上。
3. **环形缓冲的空闲空间就是节拍器**。芯片只在缓冲区有空位时才渲染，而混音器每拍恰好腾出一块，
   **腾空间这件事本身就是实时** —— 所以播放器不需要自己的定时器，也就不会漂。
4. **音乐也算"有活干"**。上游 `MixServer` 只在有采样通道在播时才 `checkOut` 一块缓冲并提交
   （`mixed = activeChannels()`），而音乐是**在提交的那一刻**才叠加进去的 —— 于是"只有音乐、没有音效"
   （标题画面、站着不动）时音乐永远出不去。改成 `mixed = activeChannels() || hasMusicWaiting()`。
   这一条是 `drainedBytes` 计数抓出来的：修之前是 `0 bytes`。
5. 音高用芯片自带的 F-Number 表（一个八度的 12 个值 + block 换八度），音色自带的 `base_note_offset` 直接
   加在音符号上。音量则走**载波的 TL**：TL 是每级约 0.75 dB 的衰减，所以按衰减量线性映射才是听感上均匀的
   淡入淡出。

**已知简化**：打击乐是用打击乐音色当普通音符弹的，没走芯片的 rhythm mode（那几个通道能用噪声合成军鼓），
所以鼓点是短促的音高击打而不是噪声 —— 通道管理和芯片模型因此简单了不少，但和原版仍有差距。
另外 MIDI 弯音轮没实现。

### 9.8 已知限制 / 后续

- 没有鼠标视角；只做了键盘。
- `Esc`（退出窗口）和 `F1`（DOOM 菜单）是固定的，不进按键设置。
- **OPL2 仿真不是逐比特精确的**：包络时间用的是几何级数（只对齐了 AR0/RR15 这类端点）、衰减是连续 dB 没量化成
  芯片的 0.1875 dB 台阶、KSL 用平均偏移而不是逐键表、AM/VIB 是两个固定深度的正弦、打击乐是"类似"实现
  而不是芯片那五个专用算子。听感上是"有点像 AdLib 的 FM"，不是硬件复刻。
- **算子槽位有串扰**：`OplMusicPlayer` 与芯片用的是 `通道 + 通道/3` 这套排布（硬件是 `通道 + 3*(通道/3)`），
  槽 4、5、8、9 会被相邻两个通道共用，所以通道 1/3、2/4、4/6、5/7 之间存在耦合。
  改成硬件排布需要**同时**改 `OplMusicPlayer` 与 `OPL2`（两边的槽位映射必须一致），试过一次，
  两边不一致会直接静音；要动就一起动、并且两个方向都跑一遍验证。
- 弯音轮没实现（曲子基本不用）。
- **MUS 这条分支没有端到端验证**。手上只有 Freedoom 这个 WAD，而它的 `D_*` 全是 MIDI；id 原版的 WAD 才是 MUS。
  所以 MUS 是交给引擎自带的 `MusReader`（上游对原版 WAD 用过的那份）再走同一条事件转换，我自己写过一个 MUS
  解析器，但拿手工构造的样例跟 `MusReader` 对同一串字节对不上，就没有留它 —— 两份实现比一份更危险。
  真机上如果要跑 id 原版 WAD，值得先确认一次。
- 同一时刻只有一个引擎实例（`Engine` 是静态单例），也就是同时只能开一个游戏机窗口 —— 打开 GUI 本身就
  限制了这一点。
- 关窗即销毁引擎；再开一次会重新读 WAD，约 1–3 秒（有「正在初始化」界面）。
- 没写合成表，道具目前只在 GT 创造标签里。
- 引擎的 `C2JUtils` 等错误路径现在是抛异常，会显示成「游戏机启动失败」而不是崩游戏。
- Gradle daemon 默认 512 MB 堆在 `downgradeJar` 会 GC 抖动失败，本次用
  `-Dorg.gradle.jvmargs=-Xmx4G` 跑过；建议在 `gradle.properties` 里补一行 `org.gradle.jvmargs`。
- **把降级后的 jar 拿普通 Java 8 单独跑整个引擎会缺 `xyz.wagyourtail.jvmdg` 的桩类**
  （`CommandVariable` / `CVarManager` / `DoomIO` 用到 JDK 12 的 `TypeDescriptor`），游戏里由 gtnhlib 提供；
  这三个类都是移植时就有的，与音乐无关。所以引擎级验证是在 JDK 25 上做的，音乐模块级验证才是 Java 8 + 真 jar。

### 9.9 按键自定义（已实现）

**选了「游戏机自己的界面」**，不接 Minecraft 的 `KeyBinding`：那些键只在游戏机窗口开着时有意义，而且
（E、Tab、1-9、Ctrl、Shift）本来就和原版键冲突 —— 塞进全局控制列表会让每个键都报警，玩家为了消冲突反
而去改原版键。`KeyBinding` 也表达不了「一个动作两个键」。

**机制**（[MTGameConsoleBindings.java](../src/main/java/com/MessTech/common/gameconsole/MTGameConsoleBindings.java)）：
底层的物理映射（LWJGL 键码 → `ScanCode`）是固定的，玩家改的是「哪个键驱动哪个动作」：

- 20 个动作各有**一个参考扫描码**（DOOM 自己监听的那个：`SC_W`、`SC_LCTRL`、`SC_1` …）。
- 引擎启动后调用 `apply(doom)`：先把 10 个 `key_*` 槽强制回参考码，再对每个绑定调用
  `DoomStatus.aliasKey(物理键的 ScanCode, 槽)` —— 就是让方向键和 WASD 并存的那个钩子。
  于是按 K（绑给前进）既发 `SC_K`（DOOM 的菜单照常见 K）又驱动 `key_up` 槽。
- `Tab` 是唯一例外：DOOM 是直接按扫描码读地图键的，所以绑给地图的键额外发一次 `SC_TAB`。
- 全键盘仍然通吃：能看到 `Keyboard` 的键都能绑，绑到映射表外的键会在日志里说一声而不是静默失效。

**界面**：游戏机窗口右上角一个 `[按键设置]`，点进去是**同一个窗口的一个模式**（不是第二个 `GuiScreen`
—— 换屏会把窗口关掉，而关窗会停引擎，那玩家看一眼键位就得重开游戏）。两页：动作 / 武器；点方框改键，
`Backspace` 清空，`Esc` 取消；底部有恢复默认 / 翻页 / 返回。改动存
`<console dir>/keys.cfg`（`forward=17,200  # W / UP`），**下次打开游戏机生效**，界面上也这么写。

**一并修掉的覆盖问题**：以前 `configureKeys()` 后面跟着 `commit()` + `SaveDefaults()`，等于每次启动都把
整个 `mochadoom.cfg` 重写一遍，玩家在 DOOM 菜单里改的东西全被冲掉。现在不再写回，键位以 `keys.cfg` 为
唯一来源，`mochadoom.cfg` 恢复成 DOOM 自己的运行时配置（音量、gamma 之类照常保存）。

**验证**：`tmp/relocated-smoke/BindingsCheck.java`（把 LWJGL + log4j 挂到 classpath 直接跑）——
默认值、改键、重复绑定互斥、清空、`keys.cfg` 存读往返、未知键、ScanCode 查表，全部通过。
**这个检查抓到过一个真 bug**：`keys.cfg` 行尾的 `# W / UP` 注释会让第二个键解析失败、读回来变空，
已在 `load()` 里先剥注释。

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
