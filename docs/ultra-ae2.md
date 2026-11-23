# 超致密 AE2 内容（超致密 ME 控制器 / ME中子注入控制器 / 压缩线缆）

> 2026-11-23 需求：新增 4 类物品，材质沿用对应 AE2 原版材质。
> 本文件是 Lead 与两个实现 teammate 之间的契约；以本文为准，不要自行改名。

## 0. 物品与数值

| 物品 | 对应原版 | 频道能力 | 配方（GT 压缩机 `RecipeMaps.compressorRecipes`） |
| --- | --- | --- | --- |
| 超致密ME控制器 | AE2 `BlockController`（ME控制器） | **每面** `Integer.MAX_VALUE`（2.1G） | 1000× AE2 ME控制器 → 1，`.eut(TierEU.RECIPE_IV)`，1 小时 |
| 超致密ME中子注入控制器 | AE2 `BlockCreativeEnergyController`（ME中子注入控制器 / Neutronium-Infused ME Controller） | **每面** `Integer.MAX_VALUE` | 1000× AE2 ME中子注入控制器 → 1，IV，1 小时 |
| 超致密压缩ME线缆 | AE2 致密线缆（dense cable，2.1G = `Integer.MAX_VALUE` 频道） | 线缆节点 `Integer.MAX_VALUE` | 64× 对应颜色的 AE2 致密线缆 → 1，IV，1 小时 |
| 压缩ME玻璃线缆 | AE2 玻璃线缆（glass cable，1024 频道） | 线缆节点 `1024` | 64× 对应颜色的 AE2 玻璃线缆 → 1，IV，1 小时 |

- 两种线缆都做 **17 个变体**：`AEColor.VALUES` 全部（`Transparent` 默认色 + 16 色），物品 damage = `AEColor` 的 ordinal。
- 配方 1000 个用 `GTValues.RA.stdBuilder().itemInputsUnsafe(GTUtility.copyAmountUnsafe(1000, stack)).itemOutputs(...).eut(TierEU.RECIPE_IV).duration(1 * HOURS).addTo(RecipeMaps.compressorRecipes)`。
  （只可由多方块压缩机供料；单方块 1 槽放不下 1000，这是已确认可接受的。）
- 线缆 64 个用普通 `.itemInputs(ae2CableStack.copy())`（≤64 可放一格）。

## 1. AE2 事实（已核实，别再猜）

1. **控制器识别靠 TE 的精确类**：
   - 路径计算 `PathingCalculation` 构造函数里 `grid.getMachines(TileController.class)` 与
     `grid.getMachines(TileCreativeEnergyController.class)` 两个循环；`IGrid.getMachines(Class)` 的 key 是
     `machine.getClass()`（精确类，不含子类）。`PathGridCache.recalcController()`/`ControllerValidator` 用
     `instanceof TileController`，`TileCreativeEnergyController extends TileController`。
   - **结论：我们的方块不要自定义 TE**，直接用 AE2 的 `TileController.class` / `TileCreativeEnergyController.class`
     （`BlockController` / `BlockCreativeEnergyController` 的构造器已经 `setTileEntity(...)`），这样 AE2 的控制器
     状态机、多人结构校验、能量、颜色、动画、渲染全部免费复用。
   - TE 类已由 AE2 注册（`GameRegistry.registerTileEntity`），**不要重复注册**，`AEBaseTile.registerTileItem` 也不用。
2. **方块外观**：`AEBaseBlock.getRenderType()` 是 AE2 的全局 ISBRH；`getRenderer()` 被
   `BlockController` 覆写为 `RenderBlockController`，会自动处理彩色/亮光/内部纹理。只要
   `setBlockTextureName("appliedenergistics2:BlockController")`（中子版用
   `"appliedenergistics2:BlockCreativeEnergyController"`），AE2 的 `registerBlockIcons` 就会注册同一套材质
   （材质都在 `assets/appliedenergistics2/textures/blocks/...`，我们**不复制**材质，直接复用）。
3. **注册**：`GameRegistry.registerBlock(block, AEBaseItemBlock.class, "ultraDenseMeController")`
   （`AEBaseItemBlock` 会把 tooltip 转给方块；中子版自动有 `ME中子注入控制器` 的 tooltip）。
   创造页 `appeng.core.CreativeTab.instance`。`GameRegistry.registerItem` 由 `registerBlock(..., itemClass, name)`
   自动完成。
4. **频道上限**：
   - `appeng.me.GridNode#getMaxChannels()` = `CHANNEL_COUNT[compressedData & 0x3]`（`{0,8,32,Integer.MAX_VALUE}`，
     第三档只有关频道功能时出现）。**mixin 这个方法的 RETURN**，用 `@Shadow @Final private IGridBlock gridProxy;`
     取 `gridProxy.getMachine()`，若实现我们的 `IMTChannelCapacity` 就 `cir.setReturnValue(capacity)`。
   - 控制器每面容量：`appeng.me.pathfinding.PathingCalculation` 的字段
     `private final int controllerFaceCapacity`（`CONTROLLER_FACE_CHANNELS = 32`），构造器里每个控制器面
     `new ControllerFace(this.controllerFaceCapacity)`。`ControllerFace` 是 `PathingCalculation` 的
     `static final` 内部类（`private final int capacity`）。
     **做法**：两个 mixin
     - `@Mixin(PathingCalculation.class)`：`<init>` 的 HEAD 扫描 `grid.getMachines(TileController.class)` +
       `grid.getMachines(TileCreativeEnergyController.class)`，只要某个 controller 的
       `getBlockType() instanceof IMTUltraDenseControllerBlock`，就把 `ThreadLocal<Boolean>` 置 true；`<init>` 的
       RETURN 清 false（防止异常残留可接受）。
     - `@Mixin(targets = "appeng.me.pathfinding.PathingCalculation$ControllerFace")`：
       `@Shadow @Final @Mutable private int capacity;`，`<init>` RETURN 时若 ThreadLocal 为 true 则
       `this.capacity = Integer.MAX_VALUE;`。
     - 效果：只要该 ME 网络里有超致密控制器，该网络所有控制器面都变成 2.1G（原版控制器面也变大；
       同一网络混用极罕见，接受并在文档记录）。
5. **自定义线缆 part**：AE2 的 `ItemMultiPart` 是 `final`，且 `PartCable` 构造器会
   `((ItemMultiPart) is.getItem()).variantOf(...)`；所以：
   - 我们的物品实现 `appeng.api.parts.IPartItem`，`getSpriteNumber()` 返回 0，
     构造器里 `AEApi.instance().partHelper().setItemBusRenderer(this)`（客户端 3D part 渲染）；
   - `createPartFromItemStack(ItemStack is)` 里用 **AE2 自己的线缆 ItemStack 作为父类构造入参**
     （这样颜色/材质/渲染全部与原版一致）：`AEApi.instance().definitions().parts().cableDense().stack(color, 1)`
     / `cableGlass()`；
   - 我们的 part 继承 `PartDenseCable` / `PartCableGlass`，覆写：
     `getItemStack()`、`getItemStack(PartItemStack type)` 返回**我们物品的 ItemStack**（存档/掉落/WAILA 用）；
     `int mtChannelCapacity()` 返回 `Integer.MAX_VALUE` / `1024`；
   - 掉落、NBT 持久化（`CableBusContainer` 用 `getItemStack(PartItemStack.World)` 存档、读档时
     `((IPartItem) item).createPartFromItemStack`）因此都能往返；
   - `PartDenseCable#getDenseTexture(Transparent)` 和 `PartCable#getGlassTexture(Transparent)` 的兜底会用
     `getItemStack().getIconIndex()`，所以物品要 `registerIcons` 重新注册 AE2 的图标（同一路径即可，ATLAS 会返回
     同一个 sprite）：
     `register.registerIcon("appliedenergistics2:ItemPart.CableDense")` /
     `"appliedenergistics2:ItemPart.CableGlass"`；`getIconFromDamage(int)` 返回该 icon。
     `getSpriteNumber()==0` 时 FML 会把 `registerIcons` 交给方块图集，因此这些路径在 `textures/blocks/` 下
     （AE2 原版就放在那里），可用。
   - 放置：`onItemUse(...)` → `return AEApi.instance().partHelper().placeBus(stack, x,y,z, side, player, world);`
6. **无线网络核心也提到 2.1G**：`MTWirelessNetworkCoreTile`（`AENetworkTile`）实现
   `com.MessTech.common.ae2.IMTChannelCapacity`，`mtChannelCapacity() = Integer.MAX_VALUE`；同一条
   `MixinGridNode_ChannelCapacity` 会自动生效（该 mixin 只看节点机器是否实现接口，与线缆无关），无线核心节点不再
   被 dense 的 32 频道卡住。
7. **颜色**：`appeng.api.util.AEColor`，`AEColor.VALUES` 含 `Transparent`（默认色/fluix，ordinal 位置以源码为准），
   `AEColor.VALID_COLORS` 是 16 色。物品 damage 直接用 `AEColor` 在 `VALUES` 里的 ordinal；
   `AEColoredItemDefinition.stack(color, amount)` 生成原版线缆堆。显示名可以用 `AEColor` 的本地化名
   （看 AE2 是否提供；没有就用我们自己的 17 个颜色 lang key）。

## 2. 文件所有权

### Lead（不要碰）
- `docs/ultra-ae2.md`、`docs/knowledge.md`、`AGENTS.md`
- `src/main/java/com/MessTech/common/ae2/MTUltraAeContent.java`（总接线）
- `src/main/java/com/MessTech/common/ae2/MTUltraAeRecipes.java`（配方）
- `src/main/java/com/MessTech/common/ae2/IMTChannelCapacity.java`、`IMTUltraDenseControllerBlock.java`（接口，见下）
- `src/main/java/com/MessTech/common/mixin/Mixins.java`、`src/main/java/com/MessTech/common/mixin/TargetMod.java`
- `MTBlocks.registerBlocks()`、`CommonProxy.preInit()`、`GTRecipes.loadRecipes()` 的接线
- 两个 lang 文件、`MTRegistryMark`（去彩虹动画）

### T-A（wireless-ultra-controller，任务 task-10）
写作用域（全部新建）：
- `src/main/java/com/MessTech/common/ae2/MTUltraDenseControllerBlock.java`
- `src/main/java/com/MessTech/common/ae2/MTUltraDenseNeutronControllerBlock.java`
- `src/main/java/com/MessTech/common/ae2/MTUltraDenseControllers.java`（注册入口）
- `src/main/java/com/MessTech/common/mixin/late/MixinPathingCalculation_UltraDense.java`
- `src/main/java/com/MessTech/common/mixin/late/MixinControllerFace_UltraDense.java`

要点：
- 两个方块分别 `extends appeng.block.networking.BlockController` /
  `extends appeng.block.networking.BlockCreativeEnergyController`，实现
  `com.MessTech.common.ae2.IMTUltraDenseControllerBlock`；
  `setBlockName("ultraDenseMeController")` / `setBlockName("ultraDenseNeutronController")`；
  `setBlockTextureName("appliedenergistics2:BlockController")` / `"appliedenergistics2:BlockCreativeEnergyController"`；
  `setHardness(6)`、`setResistance(?)`、`setCreativeTab(appeng.core.CreativeTab.instance)`，`INSTANCE` 单例。
- `MTUltraDenseControllers.registerBlocks()`：`GameRegistry.registerBlock(x, appeng.block.AEBaseItemBlock.class,
  "ultraDenseMeController")` 等；不要注册 TE。
- mixin 按 §1.4 写；`MixinControllerFace_UltraDense` 用 `@Mixin(targets = "appeng.me.pathfinding.PathingCalculation$ControllerFace")`，
  `@Shadow @Final @Mutable private int capacity;`，`@Inject(method = "<init>", at = @At("RETURN"))`。
  `MixinPathingCalculation_UltraDense` 的 `<init>` HEAD/RETURN 注入，扫描两个 `getMachines(...)`；
  注意 `TileCreativeEnergyController` 也 `instanceof TileController`，但两个 `getMachines` 集合分开取。
  ThreadLocal 放在 Lead 的 `MTUltraAeContent` 或自己的静态字段均可（由你决定，写进报告）。
- 不要跑 gradle；不要改我的文件；不 commit。

### T-B（wireless-ultra-cables，任务 task-11）
写作用域（全部新建）：
- `src/main/java/com/MessTech/common/ae2/MTUltraDenseCables.java`（注册入口 + 配方/NEI 用 stack 辅助方法）
- `src/main/java/com/MessTech/common/ae2/MTUltraDenseCableItem.java`
- `src/main/java/com/MessTech/common/ae2/MTCompressedGlassCableItem.java`
- `src/main/java/com/MessTech/common/ae2/MTUltraDenseCablePart.java`
- `src/main/java/com/MessTech/common/ae2/MTCompressedGlassCablePart.java`
- `src/main/java/com/MessTech/common/mixin/late/MixinGridNode_ChannelCapacity.java`

要点：
- 物品按 §1.5；两个物品类结构几乎一样，分别对应 dense / glass。
  `MTUltraDenseCables.preInit()` 里注册两个物品（`GameRegistry.registerItem(item, "mtUltraDenseCable")` /
  `"mtCompressedGlassCable"`），物品构造器里 `setHasSubtypes(true)`、`setMaxStackSize(64)`、
  `setCreativeTab(appeng.core.CreativeTab.instance)`、`setItemBusRenderer(this)`。
- 静态辅助：`public static ItemStack ultraDenseStack(AEColor color)`、
  `public static ItemStack compressedGlassStack(AEColor color)`（damage = color ordinal），
  `public static ItemStack ae2DenseCable(AEColor color, int amount)` 等 Lead 配方要用的方法；
  另外 `public static List<AEColor> colors()`（= `Arrays.asList(AEColor.VALUES)`）给配方遍历。
- 两个 part 按 §1.5，`implements com.MessTech.common.ae2.IMTChannelCapacity`（Lead 提供接口：
  `int mtChannelCapacity();`）。
- mixin 按 §1.4.4 第 1 条；`@Mixin(GridNode.class)`，`@Inject(method="getMaxChannels", at=@At("RETURN"), cancellable=true)`，
  用 `@Shadow @Final private IGridBlock gridProxy;`。
- 不要跑 gradle；不要改我的文件；不 commit。

## 3. Lead 提供的共享接口（已存在，不要改）

```java
package com.MessTech.common.ae2;

public interface IMTChannelCapacity { int mtChannelCapacity(); }

public interface IMTUltraDenseControllerBlock {}
```

## 4. 语言键（Lead 写，两个文件都要）

```properties
tile.ultraDenseMeController.name=超致密ME控制器
tile.ultraDenseNeutronController.name=超致密ME中子注入控制器
item.mtUltraDenseCable.name=超致密压缩ME线缆
item.mtCompressedGlassCable.name=压缩ME玻璃线缆
mtcolor.transparent=透明/默认
mtcolor.white=白色
... 16 色（white/orange/magenta/lightBlue/yellow/lime/pink/gray/lightGray/cyan/purple/blue/brown/green/red/black）
```

## 5. 已知风险

1. `ControllerFace` 容量变成 MAX 后，`PathingCalculation` 里 `usedChannels` 累加仍是 int；实际频道不会到 21 亿，安全。
2. WAILA 的频道显示会调用 `IUsedChannelProvider`（`PartDenseCable` 已实现），我们继承，显示 2.1G 时是
   `2147483647` 这种大数（可接受）。
3. 线缆与 AE2 原版线缆的连接兼容性：ultra 用 `AECableType.DENSE`、glass 用 `AECableType.GLASS`，与 AE2 一致。
4. 未进游戏实测；compile + checkstyle 由 Lead 统一跑。
## 6. 验证记录（2026-11-23）

- `./gradlew spotlessApply compileJava` → **BUILD SUCCESSFUL**；`./gradlew spotlessApply build`（compile + checkstyle + reobf）
  → **BUILD SUCCESSFUL**，产物 `build/libs/MessTech-5.11.45.140.jar`，jar 内 13 个新类 + 3 个 mixin 类齐全。
- 编译踩坑：直接引用 AE2 的 `TileController` / `TileCreativeEnergyController` 会因 GTNH 的 AE2 dev jar 在 tile 层级声明了
  RotaryCraft / Mekanism 可选接口（这两个 mod 不在编译 classpath）而报错；`MTUltraDenseControllers` 已改为
  `Class.forName` 取类 + `node.getMachine() instanceof TileEntity → getBlockType() instanceof IMTUltraDenseControllerBlock`，
  保持 `getMachines` 的 exact-class 语义。
- **实机验证（2026-11-23，`gradlew runClient25` + 测试存档副本，用 RCON 不可用后改用客户端自带单机整合服务器）**：
  - 启动/进世界全程无 mixin apply 报错；`MixinGridNode_ChannelCapacity` / `MixinPathingCalculation_UltraDense` /
    `MixinControllerFace_UltraDense` 均由 `mixins.MessTech.late.json` 载入，PathingCalculation 在控制器网络建立时
    正常转换，无 `InvalidInjectionException`、无 `NoClassDefFoundError`，本次测试**没有新增 crash report**。
  - `/give` 四个物品名全部正确：`超致密ME控制器`、`超致密ME中子注入控制器`、`超致密压缩ME线缆 白色`、
    `压缩ME玻璃线缆 白色`；物品图标在快捷栏正常显示；颜色后缀来自 AE2 自带颜色本地化。
  - 超致密ME中子注入控制器放置后 Waila：`超致密ME中子注入控制器 (#…)`、`内容: 922.34 TAE / 922.34 TAE`、
    `MessTech` —— 方块/材质/渲染/能量均复用 AE2 原版控制器（紫色创造控制器外观）。
  - 超致密压缩ME线缆放置后 Waila 显示 **`0 / 2147483647 频道`** —— `IMTChannelCapacity` + `MixinGridNode_ChannelCapacity`
    在真实节点上生效（2.1G = Int.MAX_VALUE）；白色/红色（damage 14）线缆渲染颜色正确。
  - 压缩ME玻璃线缆放置/连接渲染正常；玻璃线缆不实现 AE2 的 `IUsedChannelProvider`，所以 Waila 不显示频道行
    （原版玻璃线缆同样不显示），1024 容量走同一个 `IMTChannelCapacity` 分支。
- 实机过程中修掉的两个运行时问题（不属于设计变更）：
  1. FML 的 `ASMEventHandler` 在独立 classloader 里生成代理，**事件监听内部类必须是 public**，否则每个客户端 tick
     抛 `IllegalAccessError`（`MTWirelessKeyBindings.TickHandler` 已改 public）。
  2. Mixin 注入**构造器 `@At("HEAD")`（`super()` 之前）时 handler 必须是 static**，否则
     `InvalidInjectionException: handler before super() invocation must be static`（`MixinPathingCalculation_UltraDense`
     的 HEAD handler 已改 static）。
- 说明：`runServer25` 在本地 dev-run 里被 Angelica 的 `BlockAir → ThreadedBlockData$Getter` 类转换问题挡住（与 MessTech
  无关的模板/环境问题），因此服务端逻辑用客户端内置服务器验证；1000 输入配方的多方块压缩机行为未在本次测试中实际跑。
- 已知限制（来自实现自检）：AE2 的颜色涂抹器（Colour Applicator）换色后线缆本体渲染/频道跟随新色，但**破坏或存档
  得到的是旧色物品**（`PartCable.changeColor` 只换 AE2 part stack，未同步我们的 displayStack）；未修，需要时再补
  服务端同步。