# NAC Module 数值与加成整理

> 用途：Nano-Scale Foundry / 后续 AIO 机器定数值参考。
> 来源：GT5U `MTENanochipAssemblyModuleBase`、各模块源码、`en_US.lang` 的 `GT5U.tooltip.nac.module.*`。
>
> **基线**：GT5U **5.09.54.133**（`tmp/GT5-Unofficial-beta3/`）。5.09.54.183 起重做模块供电模型（`Priority` /
> `getMaxRecipeDuration` / `NAC_WAITING_FOR_POWER` 全部删除，控制室按 `matrixPowerPortion` 比例分电 + 富余电量
> 2/4 超频）、SMD Processor 改名 Part Processor、上调各线路校准阈值（`tmp/Compare.md` A 部分）。标"183 起"的行
> 即已过时项，其余数值未逐条复核，引用前对照 183 源码。

## 0. 所有 NAC 模块共用的基础逻辑

来源：`MTENanochipAssemblyModuleBase`

- 默认 EU / 时长倍率 `1.0` / `1.0`；超频因子 `2`（每次除 2 时长、乘 2 EU/t）。
- 最大并行 `Integer.MAX_VALUE`（可被模块/机器限制）；可超频次数 `Energy Hatch Tier - Recipe Tier`。
- 最小时长下限 `5 秒`（`5 * SECONDS`），不低于此值；NAC 控制室供电，模块自身不接能源仓。
- 配方查找：输入为假 CircuitComponent 封包，普通机器转换后可用实体封包等价物；基础配方 tier 默认 `GTUtility.getTier(recipe.mEUt)`。

流程：`checkProcessing()` 刷新 VCI 封包 → 找当前模块 RecipeMap 配方、算并行 → 查 NAC 控制室 buffer EU 是否够 →
消耗 VCI 封包 + 流体 → 输出封包到对应颜色 VCO → `transformRecipe()` 应用模块自己的 EU/时长/超频倍率。

## 1. Assembly Matrix（装配矩阵）

来源：`MTEAssemblyMatrixModule`

- RecipeMap `nanochipAssemblyMatrixRecipes`；配方 Tier 用 `NanochipAssemblyMatrixTierKey`，不是默认 EU tier。
- 结构：机器 Casing Tier >= 配方 Tier，否则 `insufficientMachineTier`。
- 超频次数 `Energy Hatch Tier - Recipe Casing Tier`（同通用公式，recipe tier 来自 metadata）。
- Priority `-1`（**183 起该机制已删除**，控制室不再给模块排序）。
- 至少 1 个 InputHatch；成品写入 NAC 电路历史/校准（`baseMulti.addToHistory`）；把 CC + 组件装配成更高阶电路。

## 2. Part Processor（SMD 处理器，183 起改名为 Part Processor）

`MTESMDProcessorModule`（183 起为 `MTEPartProcessorModule`），无特殊 override；RecipeMap `nanochipSMDProcessorRecipes`
（183 起为 `nanochipPartProcessorRecipes`）。EU/时长倍率 `1.0`，超频因子 `2`，最大并行默认 `Integer.MAX_VALUE`，处理 SMD 元件。

## 3. Board Processor（基板处理器）

`MTEBoardProcessorModule`，RecipeMap `nanochipBoardProcessorRecipes`。

- 浸没液罐 `1,000,000 L`，液位必须 ≥ `50%`；运行逐渐增加 impurity，默认 `100%` 自动排出（GUI 可调）。
- 浸没液：FeCl3 / Sterilized Growth Medium / Sterilized Bio Medium / Prismatic Acid / **UU-Matter**（5.09.54.183 新增，type 5）。
- 杂质产物：ferrous chloride / Growth Medium (Raw) / Bio Medium (Raw) / Prismatic Gas / **UU-Amplifier**；Prismatic Gas 只排 `1/4`，其余液体全额排出。

### EU 倍率公式（按 impurity y，0~1）

`validateRecipe()`：

```
if y <= 0.15:     EU Multiplier = 1 - 0.3 + 2 * y = 0.7 + 2 * y   // 0% -> 0.70 (-30%)，15% -> 1.00 (默认)
if y >= 0.65:     EU Multiplier = 1 + 2 * (y - 0.65)              // 65% -> 1.00，100% -> 1.70 (+70%)
0.15 < y < 0.65:  EU Multiplier = 1.0
```

### 杂质增长

- 处理满 `1000` 个物品后增加杂质，`IMPURITY_INCREASE = 100`：
  ```
  impurity += min(
      IMPURITY_INCREASE * (1 / fillPercentage^1.5),
      fluidAmount - impurityFluidAmount
  )
  ```
- 液罐越满，杂质增长越慢。

## 4. Etching Array（蚀刻阵列）

`MTEEtchingArrayModule`，RecipeMap `nanochipEtchingArray`；必须安装 Laser Source Hatch（Dynamo Tunnel）。`laserAmps` = laser hatch 最大输出电流，`laserTier` = laser hatch 输出 tier。

### EU 倍率

```
EU Multiplier = 1 / (log4ceil(laserAmps) - 3)
```

### 时长倍率

```
Duration Multiplier = 1 / max(1, laserTier - 9)
```

激光 tier 越高时长越短；电流每跨一个 4 的幂/Amperage interval，EU 越低；基础 256A? 附近开始收益。

## 5. Cutting Chamber（切割室）

`MTECuttingChamberModule`，无特殊数值 override；RecipeMap `nanochipCuttingChamber`，EU/时长倍率 `1.0`。切割晶圆/wafer。

## 6. Wire Tracer（导线追踪器）

`MTEWireTracerModule`，无特殊数值 override；RecipeMap `nanochipWireTracer`，EU/时长倍率 `1.0`。追踪/处理纳米导线。

## 7. Superconductor Splitter（超导分离器）

`MTESuperconductorSplitterModule`；RecipeMap `nanochipSuperconductorSplitter`，EU/时长倍率 `1.0`；需 **Super Coolant 1000 L/s**，拆分超导体。

## 8. Optical Organizer（光学整理器）

`MTEOpticalOrganizerModule`，RecipeMap `nanochipOpticalOrganizer`。

- 需求：两种**不同** Purified Water；每秒消耗 `waterDiscount * base amount`。
- 加成：同类型加成 **乘法叠加**；完全校准 NAC 时每种水加成**应用两次**（`baseMulti.opticalT3Active`）。

### 水列表

| 水 | 基础消耗 L/s | 效果 | 倍率 |
|---|---|---|---|
| Grade 3 Purified Water | 1000 | 降低后续水耗 | ×0.8 |
| Grade 4 Purified Water | 800 | 降低后续水耗 | ×0.6 |
| Grade 5 Purified Water | 800 | 加速（缩短时长） | ×0.9 |
| Grade 6 Purified Water | 600 | 加速（缩短时长） | ×0.7 |
| Grade 7 Purified Water | 600 | 降低 EU | ×0.9 |
| Grade 8 Purified Water | 400 | 降低 EU | ×0.7 |

注：`waterDiscount` 只影响水消耗量；`speedModifier` 是 Duration Modifier，越小越快；`euMultiplier` 是 EU Modifier，越小越省电。

## 9. Encasement Wrapper（封装机）

`MTEEncasementWrapperModule`，无特殊数值 override；RecipeMap `nanochipEncasementWrapper`，EU/时长倍率 `1.0`。将 Sheet + Framebox 组装成 Spool / Encasement。

## 10. Biological Coordinator（生物协调器）

`MTEBiologicalCoordinationModule`；RecipeMap `nanochipBiologicalCoordinator`，EU/时长倍率 `1.0`。Wetware T3 校准不消耗 Sterilized Growth Medium、Bio T3 校准不消耗 Sterilized Bio Medium（配方里可移除）；协调生物/湿件电路。

## 11. Splitter（分流器）

路由模块，不是普通 RecipeMap 模块：

- 默认：相同颜色输入均分到对应颜色输出；可编辑 Rules。
- Rule 条件：输入颜色、输出颜色、红石信号、物品过滤。
- 与 Nano-Scale Foundry 关系：Foundry 不需要真空传送/分流，只参考其颜色路由概念。

## 给 Nano-Scale Foundry 定数值时的建议锚点

若 Foundry 单机大致等价于“NAC 控制室 + 模块”：

- 基础超频保留 `OC factor = 2`，可超频次数 = `Energy Hatch Tier - Recipe Tier`；默认 EU/时长倍率按 1.0，再按模块特性做模式倍率。
- 并行上限可按模块原 `Integer.MAX_VALUE`，但机器级建议设安全上限。
- 复刻 Board Processor 建议引入浸没液/杂质机制，否则直接按原 EU 公式；复刻 Optical Organizer 保留双水 + 乘法叠加；复刻 Etching Array 从 Laser Hatch 读取 amps/tier 换算 EU/时长。

> 注：NAC 原版模块本身不直接接能源仓，由 NAC 控制室统一供电；Foundry 若按普通多方块接 Energy Hatch，基础耗电逻辑应把控制室供电等价成 Energy Hatch 供电，再套用模块倍率。
