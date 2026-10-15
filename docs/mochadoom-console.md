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

1. **编译 spike**：把源码树（先不重命名）放进一个 source set，只剔除 `awt/**` 与 `i/system.java`，
   验证 `compileJava` 能过、`jvmDowngrader` 不炸。← 最大的未知数，先做。
2. **无头启动 spike**：写 `MTDoomSystem` + 宿主钩子（§3 第 1~4、6、7 项），在一个纯 Java 的
   `main`（不进 MC）里用 Freedoom 跑起来，确认能稳定出帧、能干净退出。
3. **进 MC**：道具 + `GuiScreen` + 纹理上传 + 键盘轮询 + Esc 退出。
4. **打磨**：半个屏幕的排版、加载界面、tooltip/合成表、config、错误提示（没 WAD 时说什么）、
   音效（可选）、暂停世界（可选）。

---

## 9. 事实来源

- `tmp/mochadoom-master/README.md`、`build-jar.sh`（纯 javac、无第三方依赖、GPLv3）
- `src/mochadoom/Engine.java`（入口、AWT 建窗、`updateFrame` 静态钩子）
- `src/doom/DoomMain.java:2520-2735`（构造顺序、`DoomLoop` 死循环、`setupLoop`）
- `src/doom/DoomStatus.java:525`（`CM = Engine.getConfig()` 的 final 字段耦合）
- `src/v/DoomGraphicSystem.java`、`src/v/renderers/{BufferedRenderer32,SoftwareGraphicsSystem,SoftwareParallelVideoRenderer,BppMode}.java`
- `src/doom/ConfigBase.java:141-160`（`-config` 替换 cfg 列表）、`src/doom/CommandVariable.java`
- `src/defines/DoomVersion.java`（IWAD 名单）、`src/v/scale/{VideoScale,VideoScaleInfo,VisualSettings}.java`
- `src/g/Signals.java:68-232`（`ScanCode` 枚举即 DOS 扫描码表）
- `src/i/{DoomSystem,DummySystem}.java`、`src/s/{IMusic,ISoundDriver,IDoomSound}.java`
