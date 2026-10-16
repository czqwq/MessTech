# machine-spec — MT 集成加工车间（多功能集成加工中心）

> **本文由 [`render_spec.py`](render_spec.py) 从
> [`generate_structure.py`](generate_structure.py) 生成，请不要手改。**
> 手工编辑请改生成器，然后重新跑：
> `python docs/machining-center/generate_structure.py && python docs/machining-center/render_spec.py`
>
> 方块溯源（注册名 / meta / 中英文名 / 源码行）见 [`design.md`](design.md) §1。
> 交互预览见 [`preview.html`](preview.html)，数据见 [`machine-structure.json`](machine-structure.json)。

## 1. 坐标系与包络

```text
x : 0 .. 18     (宽 19)
y : 0 .. 11     (高 12)
z : 0 .. 20     (深 21)
```

- **正面（南）= z=20**，控制器就在这一面。
- 控制器 `~` 位于 **(9, 2, 20)**，即大门的正中一格。
- 因为 `addShape(piece, transpose(structure_string))`，`checkPiece` 的偏移量就是 `~` 的坐标：

```java
private static final int HORIZONTAL_OFFSET = 9;   // x of '~'
private static final int VERTICAL_OFFSET   = 2;    // y of '~'
private static final int DEPTH_OFFSET      = 20;   // z of '~'
```

- 总方块数 **2096**（不含控制器所在的那一格空气）。

## 2. 结构字母表

| 字母 | 材料 id | 方块 | 注册名 | meta | Java 引用 | 角色 |
|---|---|---|---|---|---|---|
| `A` | `concrete_light` | Light Concrete | `gt.blockconcretes` | 8 | `GregTechAPI.sBlockConcretes` | 外墙填充板、柱 |
| `B` | `concrete_light_smooth` | Smooth Light Concrete | `gt.blockconcretes` | 15 | `GregTechAPI.sBlockConcretes` | 地坪、屋面板、女儿墙、门头 |
| `C` | `concrete_dark_smooth` | Smooth Dark Concrete | `gt.blockconcretes` | 7 | `GregTechAPI.sBlockConcretes` | 地坪压边 |
| `D` | `concrete_hsc` | Superplasticizer-Treated High Strength Concrete | `gt.blockcasings9` | 3 | `Casings.SuperplasticizerTreatedHighStrengthConcrete` | 地基整板、设备垫层 |
| `E` | `casing_stainless` | Clean Stainless Steel Machine Casing | `gt.blockcasings4` | 1 | `Casings.CleanStainlessSteelMachineCasing` | 加工平台、工件夹具 |
| `F` | `casing_solid_steel` | Solid Steel Machine Casing | `gt.blockcasings2` | 0 | `Casings.SolidSteelMachineCasing` | 结构钢柱、楣窗框、仓室挂点 |
| `G` | `casing_lathe` | Stable Titanium Machine Casing | `gt.blockcasings4` | 2 | `Casings.StableTitaniumMachineCasing` | 回转工作台、主轴、天车吊爪 |
| `H` | `frame_steel` | Radiation Protection Steel Frame | `GoodGenerator:radiationProtectionSteelFrame` | 0 | `Casings.RadiantProofSteelFrameBox` | 承重钢梁、通高角柱、天车轨道 |
| `I` | `grate` | Grate Machine Casing | `gt.blockcasings3` | 10 | `Casings.GrateMachineCasing` | 地沟盖板、吊装口、爬梯 |
| `K` | `glass_boro` | Borosilicate Glass | `bartworks:BW_TieredGlass` | — | `Casings.BorosilicateGlassAny` | 幕墙、高窗、天窗 |
| `L` | `stripe_yellow` | Yellow Stripes Block | `gt.blockcasings3` | 0 | `Casings.YellowStripesBlockA` | 安全警示 |
| `M` | `hazard_explosion` | Explosion Hazard Sign Block | `gt.blockcasings3` | 4 | `Casings.ExplosionHazardSignBlock` | 设备区四角安全桩 |
| `N` | `casing_iridium_bolted` | Bolted Iridium Casing | `bartworks:bw.werkstoffblockscasing.01` | 31850 | `Casings.BoltedIridiumCasing` | 钢柱柱脚 |
| `O` | `computer_advanced` | Advanced Computer Casing | `tectech:gt.blockcasingsTT` | 3 | `Casings.AdvancedComputerCasing` | 工控柜、刀库、主轴箱 |
| `P` | `molecular_advanced` | Advanced Molecular Casing | `tectech:gt.blockcasingsTT` | 5 | `Casings.AdvancedMolecularCasing` | 大门门框 |
| `Q` | `molecular_coil` | Molecular Coil | `tectech:gt.blockcasingsTT` | 6 | `Casings.MolecularCoil` | 天车滑车 |
| `R` | `glass_reinforced` | Reinforced Glass | `gt.blockglass1` | 10 | `Casings.ReinforcedGlass` | 备用高强玻璃 |
| `S` | `casing_sterile` | Sterile Water Plant Casing | `gt.blockcasings9` | 4 | `Casings.SterileWaterPlantCasing` | 夹层办公室 |
| `~` | 控制器 | 控制器本体 | — | — | — | (9, 2, 20) |

## 3. 通用几何

```text
# 外墙四角：通高的钢结构角柱
CORNER       = [(0, 0), (0, 19), (18, 0), (18, 19)]
# 正面柱 / 竖梃 x 位
FRONT_COL_X  = [0, 4, 9, 13, 18]
FRONT_MULLION_X = [4, 13]
# 侧面柱 / 竖梃 z 位
SIDE_COL_Z  = [0, 4, 9, 13, 18]
SIDE_MULLION_Z = [4, 9, 13]
# 幕墙玻璃带 y 范围
GLASS_Y      = [3, 4, 5, 6, 7]
# 大门洞（正面 z=20 上的开口）
GATE         = {(x, 2, 20) : x in (8, 9, 10)}
```

## 4. 逐层定义

> 下列每张图都是**该层俯视图**，横轴 x=0..18，纵轴 z=0..20；
> 左上角就是 (x=0, z=0)。`.` = 空气，字母见 §2。

### y = 0 — 混凝土地基（整层实心，无空气）

本层方块数：**399**

```text
     0123456789012345678
z00  CCCCCCCCCCCCCCCCCCC
z01  DDDDDDDDDDDDDDDDDDD
z02  DDDDDDDDDDDDDDDDDDD
z03  DDDDDDDDDDDDDDDDDDD
z04  DDDDDDDDDDDDDDDDDDD
z05  DDDDDDDDDDDDDDDDDDD
z06  DDDDDDDDDDDDDDDDDDD
z07  DDDDDDDDDDDDDDDDDDD
z08  DDDDDDDDDDDDDDDDDDD
z09  DDDDDDDDDDDDDDDDDDD
z10  DDDDDDDDDDDDDDDDDDD
z11  DDDDDDDDDDDDDDDDDDD
z12  DDDDDDDDDDDDDDDDDDD
z13  DDDDDDDDDDDDDDDDDDD
z14  DDDDDDDDDDDDDDDDDDD
z15  DDDDDDDDDDDDDDDDDDD
z16  DDDDDDDDDDDDDDDDDDD
z17  DDDDDDDDDDDDDDDDDDD
z18  DDDDDDDDDDDDDDDDDDD
z19  DDDDDDDDDDDDDDDDDDD
z20  CCCCCCCCCCCCCCCCCCC
```

### y = 1 — 车间地坪

本层方块数：**385**

```text
     0123456789012345678
z00  N...N....N...N....N
z01  BBBBBBBBBBBBBBBBBBB
z02  BBBBBBBBBBBBBBBBBBB
z03  BBBBBBBBBBBBBBBBBBB
z04  NBBBBBBBBBBBBBBBBBN
z05  BBBBBBBBBBBBBBBBBBB
z06  BBBBBBBBBBBBBBBBBBB
z07  BBBBBBBBBBBBBBBBBBB
z08  BBBBBBBBBBBBBBBBBBB
z09  NBBBBBBBBBBBBBBBBBN
z10  BBBBBBBBBBBBBBBBBBB
z11  BBMDDDDDDDDDDDDDMBB
z12  BBDDDDDDDDDDDDDDDBB
z13  NBDDDDDDDDDDDDDDDBN
z14  BBDDDDDDDDDDDDDDDBB
z15  BBMDDDDDDDDDDDDDMBB
z16  BIIIIIIIIIIIIIIIIIB
z17  BBBBBBBBBBBBBBBBBBB
z18  NBBBBBBBBBBBBBBBBBN
z19  NBIINIIIBNBIINIIIBN
z20  BBBBBBBLLLLLBBBBBBB
```

### y = 2 — 首层墙体 / 大门

本层方块数：**80**

```text
     0123456789012345678
z00  HFFFFFFFFFFFFFFFFFH
z01  A.................A
z02  A.................A
z03  A.................A
z04  F.................F
z05  A.................A
z06  A.................A
z07  A.................A
z08  A.................A
z09  F.................F
z10  A.................A
z11  A.F.....F.......F.A
z12  A.......F.F.......A
z13  F.................F
z14  A.......F.F.......A
z15  A.................A
z16  A.................A
z17  A.................A
z18  A.................A
z19  H.................H
z20  FAAAFAAP.~.PAFAAAAF
```

### y = 3 — 加工平台层 + 幕墙玻璃起

本层方块数：**88**

```text
     0123456789012345678
z00  HKKKHKKKKKKKKHKKKKH
z01  K.................K
z02  K.................K
z03  K.................K
z04  H.................H
z05  K.................K
z06  K.................K
z07  K.................K
z08  K.................K
z09  H.................H
z10  K.................K
z11  K.F.....F.......F.K
z12  K.......FEF.......K
z13  H.......EGE.......H
z14  K.......FEF.......K
z15  K.................K
z16  K.................K
z17  K.................K
z18  K.................K
z19  H.................H
z20  KKKKHKKKKKKKKHKKKKK
```

### y = 4 — 窗带层

本层方块数：**89**

```text
     0123456789012345678
z00  HKKKHKKKKKKKKHKKKKH
z01  K.................K
z02  K.................K
z03  K.................K
z04  H.................H
z05  K.................K
z06  K.................K
z07  K.................K
z08  K.................K
z09  H.................H
z10  K........I........K
z11  K.F.....F.......F.K
z12  K.......FEF.......K
z13  H.......EGE.......H
z14  K.......FEF.......K
z15  K.................K
z16  K.................K
z17  K.................K
z18  K.................K
z19  H.................H
z20  KKKKHKKKKKKKKHKKKKK
```

### y = 5 — 刀库 / 夹具层

本层方块数：**96**

```text
     0123456789012345678
z00  HKKKHKKKKKKKKHKKKKH
z01  K.................K
z02  K.................K
z03  K..OOO............K
z04  H..OOO............H
z05  K.................K
z06  K.................K
z07  K.................K
z08  K.................K
z09  H.................H
z10  K.......II........K
z11  K.F.....F.......F.K
z12  K.......FGF.......K
z13  H.......EEE.......H
z14  K.......FGF.......K
z15  K.................K
z16  K.................K
z17  K.................K
z18  K.................K
z19  H.................H
z20  KKKKHKKKKKKKKHKKKKK
```

### y = 6 — 夹层楼板层

本层方块数：**233**

```text
     0123456789012345678
z00  HKKKHKKKKKKKKHKKKKH
z01  K.................K
z02  K.................K
z03  K.....O...........K
z04  H.................H
z05  K.................K
z06  K.................K
z07  K.................K
z08  K.................K
z09  H.................H
z10  K.......FOF.......K
z11  KHHHHHHHHHHHHHHHHHK
z12  KBBBBBBBBBBBBBBBBBK
z13  HBBBBBBBBBBBBSSSSBH
z14  KBBBBBBBBBBBBSSSSBK
z15  KBBBBBBBBBBBBSSSSBK
z16  KBBBBBBBBBBBBSSSSBK
z17  KBBBBBBBBBBBBBBBBBK
z18  KBBBBBBBBBBBBBBBBBK
z19  HBBBBBBBBBBBBBBBBBH
z20  KKKKHKKKKKKKKHKKKKK
```

### y = 7 — 主轴层

本层方块数：**82**

```text
     0123456789012345678
z00  HKKKHKKKKKKKKHKKKKH
z01  K.................K
z02  K.................K
z03  K.................K
z04  H.................H
z05  K.................K
z06  K.................K
z07  K.................K
z08  K.................K
z09  H.................H
z10  K.......FGF.......K
z11  K.................K
z12  K.................K
z13  H....OOO..........H
z14  K.................K
z15  K.................K
z16  K.................K
z17  K.................K
z18  K.................K
z19  H.................H
z20  KKKKHKKKKKKKKHKKKKK
```

### y = 8 — 天车层

本层方块数：**93**

```text
     0123456789012345678
z00  HAAAAAAAAAAAAAAAAAH
z01  A.................A
z02  A.................A
z03  A.................A
z04  A.................A
z05  A.................A
z06  A.................A
z07  A.................A
z08  A.................A
z09  A.................A
z10  AHHHHHHHGQGHHHHHHHA
z11  A.................A
z12  A.................A
z13  A.................A
z14  A.................A
z15  A.................A
z16  A.................A
z17  A.................A
z18  A.................A
z19  H.................H
z20  AAAAAAAAAAAAAAAAAAA
```

### y = 9 — 楣窗带 / 实墙带

本层方块数：**76**

```text
     0123456789012345678
z00  HAAFAAAFAAAFAAAFAAH
z01  A.................A
z02  A.................A
z03  A.................A
z04  A.................A
z05  A.................A
z06  A.................A
z07  A.................A
z08  A.................A
z09  A.................A
z10  A.................A
z11  A.................A
z12  A.................A
z13  A.................A
z14  A.................A
z15  A.................A
z16  A.................A
z17  A.................A
z18  A.................A
z19  H.................H
z20  FFFFFFFFFFFFFFFFFFF
```

### y = 10 — 屋面板

本层方块数：**399**

```text
     0123456789012345678
z00  BBBBBBBBBBBBBBBBBBB
z01  BBBBBBBBBBBBBBBBBBB
z02  BBBBBBBBBBBBBBBBBBB
z03  BBBKBBBKBBBKBBBKBBB
z04  BBBBBBBBBBBBBBBBBBB
z05  BBBBBBBBBBBBBBBBBBB
z06  BBBBBBBBBBBBBBBBBBB
z07  BBBKBBBKBHBKBBBKBBB
z08  BBBBBBBBBBBBBBBBBBB
z09  BBBBBIBBBBBBBIBBBBB
z10  BBBBBBBBBBBBBBBBBBB
z11  BBBKBBBKBHBKBBBKBBB
z12  BBBBBBBBBBBBBBBBBBB
z13  BBBBBBBBBBBBBBBBBBB
z14  BBBBBBBBBBBBBBBBBBB
z15  BBBKBBBKBBBKBBBKBBB
z16  BBBBBBBBBBBBBBBBBBB
z17  BBBBBBBBBBBBBBBBBBB
z18  BBBBBBBBBBBBBBBBBBB
z19  BBBBBBBBBBBBBBBBBBB
z20  BBBBBBBBBBBBBBBBBBB
```

### y = 11 — 女儿墙

本层方块数：**76**

```text
     0123456789012345678
z00  BBBBBBBBBBBBBBBBBBB
z01  B.................B
z02  B.................B
z03  B.................B
z04  B.................B
z05  B.................B
z06  L.................L
z07  L.................L
z08  L.................L
z09  L.................L
z10  L.................L
z11  L.................L
z12  L.................L
z13  L.................L
z14  L.................L
z15  B.................B
z16  B.................B
z17  B.................B
z18  B.................B
z19  B.................B
z20  BBLLLLLLLLLLLLLLLBB
```

## 5. 各材料用量

| 字母 | 材料 | 块数 |
|---|---|---|
| `B` | 15 | 802 |
| `D` | 3 | 432 |
| `K` | -1 | 326 |
| `A` | 8 | 161 |
| `H` | 0 | 115 |
| `F` | 0 | 82 |
| `C` | 7 | 38 |
| `L` | 0 | 38 |
| `I` | 10 | 32 |
| `N` | 31850 | 18 |
| `S` | 4 | 16 |
| `E` | 1 | 11 |
| `O` | 3 | 11 |
| `G` | 2 | 7 |
| `M` | 4 | 4 |
| `P` | 5 | 2 |
| `Q` | 6 | 1 |
| — | **合计** | **2096** |

## 6. 自检结果

`generate_structure.py` 生成的同一份数据在下列规则上全部通过：

1. **包围盒**：所有方块 `0≤x≤18`、`0≤y≤11`、`0≤z≤20`。
2. **大门洞**：`x=8..10`（`y=2`，`z=20`）为空气，控制器 `~` 位于正中。
3. **地基整板**：`y=0` 的 19×21 = 399 格全部实心，无空洞。
4. **外墙闭合**：`y=2..9` 的四面墙（`x=0`、`x=18`、`z=0`、`z=20`）除大门洞外无空洞。
5. **内部连通**：`y=1..9`、`x=1..17`、`z=1..19` 的空气格从大门内侧四连通可达，无封闭空腔。
6. **大门贯通**：`(9,2,19)`、`(9,2,18)`、`(9,2,17)` 为空气；门外 `(9,1,20)` 是混凝土台阶。
7. **仓室可达**：背面 `z=0` 的每个 `F`（实心钢机制方块）格，其内侧 `z=1` 同 x 为空气。
8. **块数**：2096，落在 1900–2200 区间。

当前状态：**全部通过**
