# 多功能集成加工中心 — 设计与方块溯源

> 机器：`MTIntegratedMachiningCenter` / 多功能集成加工中心
> 结构名：**MT 集成加工车间（Integrated Machining Workshop）**
> 本文只做**设计 + 溯源**，不改 `src/`。所有方块都来自工作区里已解包的 GT5U 源码，每条都给了
> 「注册名 + meta + 中文名 + 英文名 + 源码出处」。

## 0. 参考源码（全部在本地，未联网）

| 参考 | 路径 |
|---|---|
| GT5U 5.09.54.183 源码快照（当前编译依赖对应的版本族） | `tmp/GT5U-183-src/` |
| GT5U 5.09.54.133 基线快照 | `tmp/GT5U-Unofficial-beta3/` |
| GTNH 官方中文翻译（中文专有名词唯一权威来源） | `tmp/Translation-GTNH/GregTech_zh_CN.lang` |
| 英文本地化（`gt.blockcasings*` / `gt.blockconcretes*` 名字） | `tmp/GT5U-183-src/assets/gregtech/lang/en_US.lang` |
| MessTech 自己的机器模板（`Casings` 枚举用法范例） | `src/main/java/com/MessTech/common/machine/template/MachineTemplate.java` |
| 结构库 | `tmp/StructureLib-master/` |

`Casings` 枚举是 GT5U 5.09.54.183 引入的方块+casing 纹理总表，定义在
`tmp/GT5U-183-src/gregtech/api/casing/Casings.java`。MessTech 已有的
[`MachineTemplate.java`](../../src/main/java/com/MessTech/common/machine/template/MachineTemplate.java)
（第 21 行 `import gregtech.api.casing.Casings;`、第 78–93 行）已经在用它，
所以新机器同样用 `Casings.X.getBlock()` / `getBlockMeta()` / `textureId` / `asElement()`。

## 1. 结构方块总表（结构字母 → 方块）

| 字母 | 方块 | 注册名 | meta | 英文名 | 中文名 | 源码行 |
|---|---|---|---|---|---|---|
| **A** | 轻质混凝土 | `gt.blockconcretes` | 8 | Light Concrete | 浅色混凝土 † | `en_US.lang:9520` |
| **B** | 平滑浅色混凝土 | `gt.blockconcretes` | 15 | Smooth Light Concrete | 平滑浅色混凝土 † | `en_US.lang:9527` |
| **C** | 平滑深色混凝土 | `gt.blockconcretes` | 7 | Smooth Dark Concrete | 平滑深色混凝土 † | `en_US.lang:9519` |
| **D** | 减水剂处理高强度混凝土 | `gt.blockcasings9` | 3 | Superplasticizer-Treated High Strength Concrete | 减水剂强化高强混凝土 ‡ | `en_US.lang` + `Casings.java:277` |
| **E** | 洁净不锈钢机械方块 | `gt.blockcasings4` | 1 | Clean Stainless Steel Machine Casing | 洁净不锈钢机械方块 | `Casings.java:151` |
| **F** | 实心钢机械方块 | `gt.blockcasings2` | 0 | Solid Steel Machine Casing | 实心钢机械方块 | `Casings.java:81` |
| **G** | 稳定钛机械方块（车床机壳） | `gt.blockcasings4` | 2 | Stable Titanium Machine Casing | 稳定钛机械方块 ‡ | `Casings.java:153` |
| **H** | 防辐射钢框架 | `GoodGenerator:radiationProtectionSteelFrame` | 0 | Radiation Protection Steel Frame | 防辐射钢框架 | `Casings.java:1082`；`Loaders.java:167,392`；中文见 `frf` 结构说明「§664 §f防辐射钢框架」 |
| **I** | 格栅机械方块 | `gt.blockcasings3` | 10 | Grate Machine Casing | 格栅机械方块 | `Casings.java:135`；中文见 CAL 结构说明「§64 §f格栅机械方块」 |
| **K** | 硼玻璃（任意等级） | `bartworks:BW_TieredGlass` | — | Borosilicate Glass | 硼玻璃 / 任意等级玻璃 | `Casings.java:1091-1101`；`ItemRegistry.java:146, MainMod.java:142`；中文见藻类农场「§664 §f任意等级玻璃」 |
| **L** | 黄色条纹方块 | `gt.blockcasings3` | 0 | Yellow Stripes Block | 黄色条纹方块 † | `Casings.java:115` |
| **M** | 爆炸危险警示方块 | `gt.blockcasings3` | 4 | Explosion Hazard Sign Block | 爆炸危险警示方块 | `Casings.java:123`；中文见 EIC 结构说明 |
| **N** | 螺栓铱机壳 | `bartworks:bw.werkstoffblockscasing.01` | 31850 | Bolted Iridium Casing | 螺栓铱机壳 ‡ | `Casings.java:669-670`；`WerkstoffLoader.java:1997,2006` |
| **O** | 高级电脑机壳 | `tectech:gt.blockcasingsTT` | 3 | Advanced Computer Casing | 高级电脑机壳 ‡ | `Casings.java:930`；`BlockGTCasingsTT.java:33` |
| **P** | 高级分子机壳 | `tectech:gt.blockcasingsTT` | 5 | Advanced Molecular Casing | 高级分子机壳 ‡ | `Casings.java:934` |
| **Q** | 分子线圈方块 | `tectech:gt.blockcasingsTT` | 6 | Molecular Coil | 分子线圈方块 ‡ | `Casings.java:938` |
| **R** | 强化玻璃 | `gt.blockglass1` | 10 | Reinforced Glass | 强化玻璃 ‡ | `Casings.java:647-648` |
| **S** | 无菌水厂机壳 | `gt.blockcasings9` | 4 | Sterile Water Plant Casing | 无菌水厂机壳 ‡ | `Casings.java:279` |

† `gt.blockconcretes.*.name` 在 `tmp/GT5U-183-src/assets/gregtech/lang/en_US.lang` 有英文条目
（第 9512–9527 行，meta 0–7 = Dark 系列、8–15 = Light 系列），但**这个快照没有随附
`zh_CN` 的 `.name` 键**（`tmp/GT5U-183-src/assets/gregtech/lang/zh_CN.lang` 与
`tmp/Translation-GTNH/GregTech_zh_CN.lang` 都只收录了 `.32767` 通配条目）。因此这些中文名是**暂定译名**，
落地成 Java 时必须用游戏内实际 lang 里的名字替换（见 §6）。

‡ 这些词在 `tmp/Translation-GTNH/GregTech_zh_CN.lang` 里能搜到同词素（`机械方块` / `框架` / `玻璃` /
`机壳`），但没有直接搜到该方块整名，同样按暂定处理。

> 注册名全部按源码核对过：GT 方块取 `super(ItemCasings.class, "<name>", ...)` 的字面量
> （`BlockCasings2/3/4/9.java`、`BlockGlass1.java`），GT++ 取 `ModBlocks` 的登记名，
> BW / TT / GG 取各自的 `GameRegistry.registerBlock(..., "<name>")`。
> 表里的 `GoodGenerator:` 前缀是大写 G —— 来自 `Mods.ModIDs.GOOD_GENERATOR = "GoodGenerator"`
> （`Mods.java:446`），不要写成小写。

### 1.1 为什么选这些方块

- **混凝土（A/B/C/D）+ 玻璃（K/R）+ 钢框架（H）** 是「厂房 + 混凝土地基 + 现代化风格」的核心。
  D 是 GT5U 里唯一名字里就写着 high-strength 的混凝土板，用来做**厂房地面垫层**；C 做**地坪边缘**压边；
  B 做地坪；A 做外墙填充板。
- **H 防辐射钢框架**在 GTNH 里承担「预制钢结构」的角色（核燃料精炼厂的结构说明里明确写
  「§664 §f防辐射钢框架」），是这套设计里唯一真正的**钢结构柱 / 桁架**。
- **I 格栅机械方块**做设备区的地沟盖板（CAL 用它当结构地板）。
- **E 洁净不锈钢机壳**做**主加工区/加工平台**的外壳（工业挤出机 `MTEIndustrialExtruder`
  用的就是它）。
- **F 实心钢机械方块**做**天车大梁 / 立柱包边**这类承重钢构件。
- **G 车床机壳（稳定钛机壳）**是这台机器的「品牌件」：车床 = 本机的切削主轴隐喻。
- **M 爆炸危险警示方块 + L 黄色条纹方块**做安全标识（设备区四周、屋顶女儿墙前沿）。
- **N 螺栓铱机壳**做钢柱柱脚（有螺栓锚固的观感）。
- **O/P/Q** 是 TT 的电脑/分子线，做**工控柜、主轴箱、刀库**这类「高科技设备」。
- **S 无菌水厂机壳**做**计量/校正区**的浅色设备外壳，给深色工业风里补一点亮。

## 2. 结构总览

- **包络尺寸（宽 × 高 × 深）= 19 × 12 × 21**（x 0–18，y 0–11，z 0–20）。
- 控制器 `~` 位于 **(x=9, y=2, z=20)** —— 厂房正面（南面）大门中央。
  因为 `structure_string` 要 `transpose()`，所以 `checkPiece` 偏移量是
  `HORIZONTAL_OFFSET=9, VERTICAL_OFFSET=2, DEPTH_OFFSET=20`。
- 结构是「**厂房外壳 + 混凝土地基**」：地基（y0）是一整块实心混凝土台，厂房（y1–y11）是
  钢结构柱 + 混凝土填充墙 + 大面积玻璃幕墙的**中空**车间，车间里摆着加工设备。
- 竖向分区：

| 层 | 名称 | 内容 |
|---|---|---|
| y0 | 混凝土地基 | 整层实心：高强度混凝土 + 深色混凝土压边 |
| y1 | 车间地坪 | 地坪板 + 设备垫层 + 格栅地沟 + 柱脚 + 门前台阶（前沿 z=20 外挑一格） |
| y2 | 首层墙体 / 大门 | 四面幕墙基座（钢+混凝土）+ 3 格宽大门洞（中央就是控制器） |
| y3 | 加工平台层 | 五轴加工平台（3×3）+ 平台支腿柱 + 幕墙玻璃起 |
| y4–y5 | 窗带 / 刀库、夹具层 | 四面连续高窗；后墙立式刀库、工件夹具（y5） |
| y6 | 夹层楼板层 | **前半个车间抬高一层**：夹层楼板 + 边梁 + 三根夹层立柱 + 夹层办公室 + 爬梯 |
| y7 | 主轴层 | 主轴箱 + 主轴柱（在**后半个**通高区） |
| y8 | 天车层 | 天车轨道（贯通 x 向）+ 天车滑车 |
| y9 | 实墙带 | 实墙收口 + 正面钢楣带 |
| y10 | 屋面板 | 整块屋面板 + 中央天窗阵列（4×4）+ 吊装口 |
| y11 | 女儿墙 | 周边女儿墙压顶 + 正面与两侧黄条纹警示 |

建筑语言：**通高钢角柱（防辐射钢框架）夹住四面玻璃幕墙**，幕墙竖梃也是钢框架；
基座与屋面用混凝土，设备层用不锈钢机壳。
前半个车间做成**夹层**（首层通行、夹层办公/工控），后半个车间**通高 7 格**放主轴与天车 ——
这是现代精密制造车间的典型剖面。

详细坐标见 [`machine-spec.md`](machine-spec.md)（逐层逐格的权威定义，由生成器产出）。

## 3. 结构元素（`addElement` 计划）

```text
A/B/C -> ofBlock(GregTechAPI.sBlockConcretes, meta)   // 混凝土不是 Casings 成员
D -> Casings.SuperplasticizerTreatedHighStrengthConcrete.asElement()
E -> Casings.CleanStainlessSteelMachineCasing.asElement()
F -> Casings.SolidSteelMachineCasing.asElement()
G -> Casings.StableTitaniumMachineCasing.asElement()
H -> Casings.RadiantProofSteelFrameBox.asElement()
I -> Casings.GrateMachineCasing.asElement()
K -> Casings.BorosilicateGlassAny.asElement()
L -> Casings.YellowStripesBlockA.asElement()
M -> Casings.ExplosionHazardSignBlock.asElement()
N -> Casings.BoltedIridiumCasing.asElement()
O -> Casings.AdvancedComputerCasing.asElement()
P -> Casings.AdvancedMolecularCasing.asElement()
Q -> Casings.MolecularCoil.asElement()
R -> Casings.ReinforcedGlass.asElement()
S -> Casings.SterileWaterPlantCasing.asElement()
'~' -> 控制器（不需要 element）
```

> 上面每个 `Casings.X` 都在 `tmp/GT5U-183-src/gregtech/api/casing/Casings.java` 里核对过是真实枚举常量
> （不是猜的名字）。混凝土方块在 GT5U 里的字段是 `GregTechAPI.sBlockConcretes`
> （`gregtech.common.blocks.BlockConcretes`，注册名 `gt.blockconcretes`，构造器第 17 行，
> 由 `LoaderGTBlockFluid.java:754` 赋值）。`Casings` 枚举里没有混凝土，所以 A/B/C 走 `ofBlock` + meta。

仓室（hatch）挂点已经定死：**后墙 `z=0`、`y=2` 的那一整条**（`x=1..17`，全是实心钢机械方块，
内侧 `z=1` 同 x 都是车间空气，生成器的自检 #7 会保证这一点）。这一条放
`InputBus / InputHatch / OutputBus / OutputHatch / Energy.or(ExoticEnergy) / Maintenance / Muffler`；
正面幕墙基座（`z=20`、`y=2` 的混凝土段）可以再挂一排总线，将来接装配线模式时留给 `DataAccess`。

## 4. 多模式（功能集成）

一台机器跑四套配方池，用**螺丝刀切模式**（`MTMultiMachineBase` 已经内置
`totalMachineMode()` / `getMachineModeName(int)` / `nextMachineMode()` / 模式按钮，
见 `MTMultiMachineBase.java:734-782`）：

| 模式 | 名称 | 配方池（GT5U `RecipeMaps`） | 现实隐喻 |
|---|---|---|---|
| 0 | 切削模式 | `cutterRecipes` / `latheRecipes` / `wiremillRecipes` | 锯切、车削、拉丝 |
| 1 | 成形模式 | `benderRecipes` / `extruderRecipes` / `formingPressRecipes` / `hammerRecipes` | 折弯、挤压、冲压、锻打 |
| 2 | 装配模式 | `assemblerRecipes` / `circuitAssemblerRecipes` / `laserEngraverRecipes` | 组装、电路贴装、激光刻蚀 |
| 3 | 热处理模式 | `arcFurnaceRecipes` / `alloySmelterRecipes` / `chemicalBathRecipes` | 电弧炉、合金炉、化学浴 |

（配方池名全部来自 `tmp/GT5U-183-src/gregtech/api/recipe/RecipeMaps.java`。
模式数、每模式的池子、是否开完美超频由实施时定；这里只锁定**结构怎么支持多模式**。）

### 4.1 模式与结构的绑定

不建议把模式做成「结构上多摆一个方块」——那样每换一次模式就要拆一次厂房。
更好的做法是复用 MessTech 现有的两条约定：

1. **模式开关**走螺丝刀（和 `MTAssFactory` 一样，`MTAssFactory.java:133-159`），
   机器内部限制「正在运行 / 有余热时不能切」；
2. **加工单元等级**走 `ofBlocksTiered`（和 `MTDTPF` 的线圈 / 外壳一样，
   `MTDTPF.java:466-482`）：把「刀库」那一排（后墙内侧 `x=3..5`、`z=3..4`、`y=5`）换成
   **可检测等级的加工单元块**，等级决定并行数与超频档位，和模式互相独立。

这样：厂房一次建成，模式用 GUI/螺丝刀切，加工能力用换刀库块来升级。

## 5. 预览

交互式 3D 预览：[`preview.html`](preview.html)（自包含、无外部依赖，双击即可在浏览器打开；
`file://` 下会自动回退到内联数据快照，所以打开就有图）。
它读 [`machine-structure.json`](machine-structure.json)，后者与逐层坐标一起由
[`generate_structure.py`](generate_structure.py) 生成、[`render_spec.py`](render_spec.py) 渲染成
[`machine-spec.md`](machine-spec.md)。

## 6. 落地成 Java 时还要做的事（本次不做）

1. 用游戏内实际 lang 覆盖 §1 表里标 †/‡ 的中文名（`gt.blockconcretes.15.name` 等），
   中文名一律以运行时的 `zh_CN` 为准。
2. `MTBlocks` 里新增「加工单元」方块（带 meta 分级）如果要做 §4.1 的等级系统。
3. `MTMachineLoader` 注册 + `MTItemList` + 两个 lang 文件（`messtech` 自己的
   `machine.integratedmachiningcenter.*` 键）。
4. 结构错误本地化键：`structure.error.*`（见 `docs/GT5U-NOTES.md` §8）。
5. 跑 `./gradlew spotlessApply compileJava`。
