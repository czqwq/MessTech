# NAC Module 数值与加成整理

> 用途：给 Nano-Scale Foundry / 后续 AIO 机器定数值参考。
> 来源：GT5U `MTENanochipAssemblyModuleBase` 与各模块源码、`en_US.lang` 的 `GT5U.tooltip.nac.module.*`。

## 0. 所有 NAC 模块共用的基础逻辑

来源：`MTENanochipAssemblyModuleBase`

| 项目 | 数值/规则 |
|---|---|
| 默认 EU 倍率 | `1.0` |
| 默认时长倍率 | `1.0` |
| 默认超频因子 | `2`（每次除 2 时长、乘 2 EU/t） |
| 最大并行 | `Integer.MAX_VALUE`（可被模块/机器限制） |
| 可超频次数 | `Energy Hatch Tier - Recipe Tier` |
| 最小时长下限 | 不低于 `5 秒`（`5 * SECONDS`） |
| 功率来源 | NAC 控制室供电，模块自身不接能源仓 |
| 配方查找 | 输入为假 CircuitComponent 封包；普通机器转换后可直接使用“实体封包”等价物 |
| 基础配方 tier | 默认按 `GTUtility.getTier(recipe.mEUt)` |

通用流程：

1. `checkProcessing()` 刷新 VCI 中封包；
2. 找到当前模块 RecipeMap 配方；
3. 计算并行；
4. 检查 NAC 控制室 buffer 是否有足够 EU；
5. 消耗 VCI 中的封包 + 流体；
6. 输出封包到对应颜色 VCO；
7. `transformRecipe()` 应用模块自己的 EU/时长/超频倍率。

---

## 1. Assembly Matrix（装配矩阵）

来源：`MTEAssemblyMatrixModule`

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipAssemblyMatrixRecipes` |
| 配方 Tier | 使用 `NanochipAssemblyMatrixTierKey`，不是默认 EU tier |
| 结构限制 | 机器 Casing Tier >= 配方 Tier，否则 `insufficientMachineTier` |
| 超频次数 | `Energy Hatch Tier - Recipe Casing Tier`（同通用公式，但 recipe tier 来自 metadata） |
| Priority | `-1` |
| 额外需求 | 至少 1 个 InputHatch |
| 输出 | 成品会写入 NAC 电路历史/校准（`baseMulti.addToHistory`） |
| 备注 | 负责把 CC + 组件装配成更高阶电路 |

---

## 2. SMD Processor（SMD 处理器）

来源：`MTESMDProcessorModule`，无特殊 override

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipSMDProcessorRecipes` |
| EU 倍率 | `1.0` |
| 时长倍率 | `1.0` |
| 超频因子 | `2` |
| 最大并行 | 默认 `Integer.MAX_VALUE` |
| 功能 | 处理 SMD 元件 |

---

## 3. Board Processor（基板处理器）

来源：`MTEBoardProcessorModule`

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipBoardProcessorRecipes` |
| 内部浸没液罐容量 | `1,000,000 L` |
| 最低液位 | 必须 ≥ `50%` |
| 杂质范围 | 运行会逐渐增加 impurity |
| 默认自动排出阈值 | `100%`（可在 GUI 调） |
| 支持浸没液 | FeCl3 / Sterilized Growth Medium / Sterilized Bio Medium / Prismatic Acid |

### EU 倍率公式（按 impurity y，0~1）

来源：`validateRecipe()`

```
if y <= 0.15:
    EU Multiplier = 1 - 0.3 + 2 * y
                  = 0.7 + 2 * y
    // 0%  -> 0.70 (-30%)
    // 15% -> 1.00 (默认)

if y >= 0.65:
    EU Multiplier = 1 + 2 * (y - 0.65)
    // 65%  -> 1.00
    // 100% -> 1.70 (+70%)

0.15 < y < 0.65:
    EU Multiplier = 1.0
```

### 杂质增长

- 处理满 `1000` 个物品后增加杂质；
- 杂质增加量：
  ```
  impurity += min(
      IMPURITY_INCREASE * (1 / fillPercentage^1.5),
      fluidAmount - impurityFluidAmount
  )
  ```
  其中 `IMPURITY_INCREASE = 100`；
- 液罐越满，杂质增长越慢。

---

## 4. Etching Array（蚀刻阵列）

来源：`MTEEtchingArrayModule`

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipEtchingArray` |
| 结构需求 | 必须安装 Laser Source Hatch（Dynamo Tunnel） |
| 激光参数 | `laserAmps` = laser hatch 最大输出电流；`laserTier` = laser hatch 输出 tier |

### EU 倍率

```
EU Multiplier = 1 / (log4ceil(laserAmps) - 3)
```

### 时长倍率

```
Duration Multiplier = 1 / max(1, laserTier - 9)
```

即：

- 激光 tier 越高，时长越短；
- 电流每跨过一个“4 的幂/Amperage interval”，EU 越低；
- 基础 256A? 附近开始收益。

---

## 5. Cutting Chamber（切割室）

来源：`MTECuttingChamberModule`，无特殊数值 override

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipCuttingChamber` |
| EU 倍率 | `1.0` |
| 时长倍率 | `1.0` |
| 功能 | 切割晶圆/wafer |

---

## 6. Wire Tracer（导线追踪器）

来源：`MTEWireTracerModule`，无特殊数值 override

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipWireTracer` |
| EU 倍率 | `1.0` |
| 时长倍率 | `1.0` |
| 功能 | 追踪/处理纳米导线 |

---

## 7. Superconductor Splitter（超导分离器）

来源：`MTESuperconductorSplitterModule`

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipSuperconductorSplitter` |
| EU 倍率 | `1.0` |
| 时长倍率 | `1.0` |
| 流体需求 | **Super Coolant 1000 L/s** |
| 功能 | 拆分超导体 |

---

## 8. Optical Organizer（光学整理器）

来源：`MTEOpticalOrganizerModule`

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipOpticalOrganizer` |
| 需求 | 两种**不同** Purified Water |
| 每秒消耗 | `waterDiscount * base amount` |
| 加成叠加 | 同类型加成 **乘法叠加** |
| 完全校准 NAC | 每种水加成**应用两次**（`baseMulti.opticalT3Active`） |

### 水列表

| 水 | 基础消耗 L/s | 效果 | 倍率 |
|---|---|---|---|
| Grade 3 Purified Water | 1000 | 降低后续水耗 | ×0.8 |
| Grade 4 Purified Water | 800 | 降低后续水耗 | ×0.6 |
| Grade 5 Purified Water | 800 | 加速（缩短时长） | ×0.9 |
| Grade 6 Purified Water | 600 | 加速（缩短时长） | ×0.7 |
| Grade 7 Purified Water | 600 | 降低 EU | ×0.9 |
| Grade 8 Purified Water | 400 | 降低 EU | ×0.7 |

注：

- `waterDiscount` 只影响水消耗量；
- `speedModifier` 是 Duration Modifier，越小越快；
- `euMultiplier` 是 EU Modifier，越小越省电。

---

## 9. Encasement Wrapper（封装机）

来源：`MTEEncasementWrapperModule`，无特殊数值 override

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipEncasementWrapper` |
| EU 倍率 | `1.0` |
| 时长倍率 | `1.0` |
| 功能 | 将 Sheet + Framebox 组装成 Spool / Encasement |

---

## 10. Biological Coordinator（生物协调器）

来源：`MTEBiologicalCoordinationModule`

| 项目 | 数值/规则 |
|---|---|
| RecipeMap | `nanochipBiologicalCoordinator` |
| EU 倍率 | `1.0` |
| 时长倍率 | `1.0` |
| Wetware T3 校准 | 不消耗 Sterilized Growth Medium（配方里可移除） |
| Bio T3 校准 | 不消耗 Sterilized Bio Medium（配方里可移除） |
| 功能 | 协调生物/湿件电路 |

---

## 11. Splitter（分流器）

不是普通 RecipeMap 模块，而是路由模块：

| 项目 | 规则 |
|---|---|
| 默认行为 | 相同颜色输入均分到对应颜色输出 |
| 自定义 | 可编辑 Rules |
| Rule 条件 | 输入颜色、输出颜色、红石信号、物品过滤 |
| 与 Nano-Scale Foundry 关系 | Foundry 不需要真空传送/分流，只参考其颜色路由概念 |

---

## 给 Nano-Scale Foundry 定数值时的建议锚点

如果希望 Foundry 单机大致等价于“NAC 控制室 + 模块”：

- 基础超频：保留 `OC factor = 2`，可超频次数 = `Energy Hatch Tier - Recipe Tier`；
- 默认 EU/时长倍率按 1.0，再按模块特性做模式倍率；
- 并行上限可按模块原 `Integer.MAX_VALUE`，但机器级通常建议设一个安全上限；
- 若复刻 Board Processor，建议引入“浸没液/杂质”类机制，否则直接按原 EU 公式即可；
- 若复刻 Optical Organizer，建议保留双水 + 乘法叠加；
- 若复刻 Etching Array，建议从 Laser Hatch 读取 amps/tier 换算 EU/时长。

> 注：NAC 原版模块本身不直接接能源仓，而是由 NAC 控制室统一供电。Nano-Scale Foundry 如果按普通多方块接 Energy Hatch，基础耗电逻辑应把“NAC 控制室供电”等价成 Energy Hatch 供电，再套用模块倍率。
