# 无线ME网络（Wireless ME Network）—— GTLCore 移植设计/契约

本文件是这条功能线的**唯一共享契约**。多个 agent 并行开发时，类名、方法签名、语言键、资源路径、NBT
格式都以本文为准；不要自行改名，需要新增契约先通知 Lead。

## 0. 来源与目标

源实现：`tmp/GTLCore-gtl-1431-skyblock`（1.20.1 / GT Modern / AE2 1.20）中
`src/main/java/org/gtlcore/gtlcore/integration/ae2/wireless/` 与 `client/ae2/wireless/` 三个内容：

1. **无线ME网络核心**（`wireless_network_core` 方块 + BlockEntity）：GT 单方块，只有一个朝向面暴露 AE2
   网格节点；贴着 ME 线缆/ME 方块就"接入 ME 网络"。自身持有 `frequency`(UUID)、网络名、owner，登记进世界级
   `SavedData`（核心坐标 + 成员表 + owner + name）。
2. **无线连接仓室功能**：核心的网格节点与任意 ME 仓室自己的 AE2 网格节点之间建立一条**逻辑
   `IGridConnection`**（跨距离、跨维度、区块卸载后自动重连），仓室因此像插在核心那根线上一样工作。
   绑定时检测仓室是否已有有线/共享连接并拒绝。
3. **无线ME网络绑定工具**（`wireless_network_binding_tool`）：Shift+右击核心保存网络；右击 ME 仓室绑定；
   Shift+右击改绑；Shift+左击解绑；右击空气打开网络选择 GUI。网络名缓存在工具 NBT。

附加（本次一并移植）：

- 核心 GUI：改名 / 显示是否接入 ME 网络 / 已连接仓室数量。
- 仓室侧「无线网络页签」：ME 仓室的 GUI 里挂一个无线页签，列出可访问网络，左键连接 / 断开。
- 来源标注：本模组新增内容 tooltip 末尾加「由GregTech Leisure添加」——**文字与颜色都照抄 GTLCore**
  `gtlcore.registry.add` + GT Modern `TooltipHelper.RAINBOW_SLOW`（整行单色、25 tick 一档的 10 色循环，不是逐字
  彩虹；**不改写成 MessTech 字样**）；「由GregTech Leisure修改」（`RAINBOW`）一并移植但暂不挂。不给 GT ME 仓室
  物品加任何修改标注。
- 不移植：ME 仓室管理终端、无线终端（Curios）、Jade provider、吞吐量监控等 GTLCore 的其它无线内容。
  例外：**绑定工具自己的那条 Curios 专用槽**要移植——1.7.10 用 Baubles Expanded 的自定义槽
  `wireless_connector` 复刻，见 §8.6。

## 1. 功能映射表

| GTLCore (1.20.1) | MessTech (1.7.10) |
| --- | --- |
| `WirelessNetworkCoreBlock` + `WirelessNetworkCoreBlockEntity` | `MTWirelessNetworkCoreBlock`（普通 `BlockContainer`）+ `MTWirelessNetworkCoreItemBlock` + `MTWirelessNetworkCoreTile`（`AENetworkTile`） |
| `WirelessAeSavedData`（`SavedData`） | `MTWirelessSavedData`（`WorldSavedData` + overworld `MapStorage`） |
| `WirelessAeNetworkRuntime`（`GridHelper.createConnection`） | `MTWirelessNetworkRuntime`（`AEApi.instance().createGridConnection`） |
| `WirelessNetworkBindingToolItem` | `MTWirelessNetworkBindingTool`（+ `MTWirelessToolEvents` 左键解绑） |
| `WirelessNetworkPickerMenu`/`Screen` | `MTWirelessNetworkPickerGui`（MUI2 `SimpleGuiFactory`） |
| `WirelessNetworkCoreMenu`/`Screen` | `MTWirelessCoreGui`（MUI2 `TileEntityGuiFactory`，由核心 TE 的 `buildUI` 委托） |
| `WirelessAeTargetMenu` / 仓室无线页签 | `MTWirelessHatchTab`（MUI2 `syncManager.syncedPanel`，由 mixin 挂到 GT5U 仓室 GUI） |
| `canAccessNetwork`（owner + FTB Teams） | 放置者 + **ServerUtilities** 同队（反射） |
| 核心节点 `setIdlePowerUsage(0)` | 同样 0（无线链路不额外耗 AE 电） |
| 绑定工具 Curios 专用槽：`WirelessConnectorCurioSlot`（`wireless_connector`，size 1，priority 150）+ item tag | `MTWirelessBaubles`（Baubles Expanded `wireless_connector` 类型）+ `MTWirelessNetworkBindingTool implements IBaubleExpanded` |
| `WirelessBindingToolLocator` / `CuriosCompat` | `MTWirelessToolLocator`（饰品栏 → 主手 → 背包）+ `MTWirelessBaubles`（找槽/回写） |
| `WirelessBindingPickerKeyMapping` / `OpenBindingPickerPacket` | `MTWirelessKeyBindings` / `OpenWirelessPickerPacket` |
| ME 仓室管理终端 / 无线终端 / Jade | 本次不做 |

## 2. 1.7.10 技术结论（已核实，勿再猜）

参考源码：`tmp/ae2-1079-src`（AE2 rv3）、`tmp/GT5-Unofficial-5.09.54.264`、`tmp/ServerUtilities-master`、
`tmp/mui2-2.3.92-src`。

- **无线网格连接**：`AEApi.instance().createGridConnection(IGridNode a, IGridNode b)`（实现 =
  `new GridConnection(a, b, ForgeDirection.UNKNOWN)`，见 `appeng/core/Api.java:109`）。AE2 自己的无线连接器
  `appeng/tile/networking/TileWirelessBase.java:156` 就是这套；跨维度可用（`QuantumCluster.java:127` 同 API）。
  方向 `UNKNOWN` = 不可见连接，不要求相邻。只会抛 checked `FailedConnection`（也可能有
  `SecurityConnectionException`），`catch (Exception)` 处理即可。
- **仓室节点**：GT5U 的 ME 仓室都实现 `appeng.me.helpers.IGridProxyable`（=`IGridHost`）：
  `MTEHatchInputBusME` / `MTEHatchInputME` / `MTEHatchOutputBusME` / `MTEHatchOutputME` /
  `MTEHatchCraftingInputME`，以及本模组的 `MTInventoryInputBusME` / `MTInventoryInputHatchME` /
  `MTEssentiaHatchMETileEntity` / `MTEssentiaOutputHatchMETileEntity`。节点 = `getProxy().getNode()`。
  proxy 生命周期 GT5U 已管好（`onFirstTick` → `proxy.onReady()`；`BaseMetaTileEntity#onChunkUnloadAE/invalidateAE`
  → `proxy.onChunkUnload()/invalidate()`），**区块重载后节点会重建**，运行时只需发现并重新连线。
- **目标识别**（只认 ME 仓室）：`world.getTileEntity` → `IGregTechTileEntity#getMetaTileEntity()` →
  `instanceof gregtech.api.metatileentity.implementations.MTEHatch` 且 `instanceof IGridProxyable`。
- **有线检测**：目标 `IGridNode.getConnections()` 非空 → 视为已有有线/共享连接，绑定报"已有连接"；
  已绑定后运行时发现目标与核心已在同一网格（`node.getGrid() == coreNode.getGrid()`）则不再建线。
- **连接端点**：用核心**自己的节点**（`core.getProxy().getNode()`）即可（GTLCore 用相邻 bridge 节点，
  1.7.10 用自身节点等价：同网格、同 security key，更简单）。
- **世界存档**：`WorldSavedData`（1.7.10 有 `markDirty()`；`MapStorage#loadData(Class,String)` + `setData`），
  overworld = `server.worldServerForDimension(0).mapStorage`。
- **权限**：`serverutils.lib.data.ServerUtilitiesAPI.arePlayersInSameTeam(UUID, UUID)`（静态工具方法，
  源码 `tmp/ServerUtilities-master/src/main/java/serverutils/lib/data/ServerUtilitiesAPI.java`）。反射调用，用
  `gregtech.api.enums.Mods.ServerUtilities.isModLoaded()` 守卫；取不到就退化成"仅放置者"。
- **MUI2 2.3.92**：`IGuiHolder#buildUI` **服务端和客户端都会调用**（`GuiManager.open` 在服务端先建 container）。
  因此 GUI 数据用**同步值**，不要自定义网络包：
  - 值：`new StringSyncValue(clientGetter, clientSetter, serverGetter, serverSetter)` /
    `BooleanSyncValue` / `IntSyncValue` 的 (client..., server...) 构造；服务端每 tick `detectAndSendChanges`
    自动 S2C（见 `AbstractGenericSyncValue#updateCacheFromSource`）。widget 用 `.getStringValue()` 等读。
  - 动作：`syncManager.registerSyncedAction(key, Side.SERVER, buf -> ...)` +
    `syncManager.callSyncedAction(key, buf -> buf.writeInt(...))`（参考
    `gregtech/common/gui/modularui/hatch/MTEHatchVacuumConveyorGui.java:101/225`）。
  - 子面板：`syncManager.syncedPanel(key, true, (parentPanel, player) -> panel)`；GT 例子
    `MTEHatchInputBusMEGui.java:207/281`。
  - 打开 TE GUI：`com.cleanroommc.modularui.factory.GuiFactories.tileEntity().open(player, tile)`（服务端调用）。
  - 打开无 TE 的 GUI：`SimpleGuiFactory.open(EntityPlayerMP)`（工厂在构造时自动注册，必须在 preInit 构造）。
- **服务端 tick**：`FMLCommonHandler.instance().bus().register(...)` + `TickEvent.ServerTickEvent`
  （参考 `com.MessTech.common.process.MTProcessHandler`）。
- **方块外观**：正面 = 照搬的 16 帧动画贴图 `wireless_network_core_front.png`（16x256，mcmeta `frametime: 2`）。
  GTLCore 的半透明 overlay 是额外 quad 叠在 EV 机壳 side 上的，1.7.10 一个面只能一个图标，因此已把 overlay
  **烘焙到 EV 机壳 side 贴图之上**（烘焙后完全不透明）。其余五面用从 GT5U 复制来的 EV 机壳贴图
  `wireless_network_core_{side,top,bottom}.png`；**不要**直接用 `Textures.BlockIcons.MACHINE_EV_*`：那些图标只为 GT
  自家方块拼接，拿到 null 会回退成半透明正面贴图（第一版实机就表现为方块透明/看不到纹理）。
- **扳手旋转**：`GTUtility.isStackInList(held, GregTechAPI.sWrenchList)` 判定，
  `GTModHandler.damageOrDechargeItem(held, 1, 1000, player)` 消耗（参考
  `BaseMetaTileEntity.java:1485` 附近）。

## 3. 文件所有权（写作用域）

**Lead（本文件作者）**：
- `docs/wireless-me-network.md`、`docs/knowledge.md`（索引）、`AGENTS.md`（仅索引，可选）
- `src/main/java/com/MessTech/common/wireless/MTWirelessContent.java`
- `src/main/java/com/MessTech/common/util/MTRegistryMark.java`
- `src/main/java/com/MessTech/common/recipe/MTWirelessRecipes.java`（配方）
- `src/main/java/com/MessTech/common/items/MTItemList.java`、`src/main/java/com/MessTech/common/block/MTBlocks.java`
- `src/main/java/com/MessTech/init/CommonProxy.java`（只加 preInit/postInit 调用）
- `src/main/resources/assets/messtech/textures/**`（新增文件）、`lang/zh_CN.lang`、`lang/en_US.lang`
- `docs/` 其它

**T1（核心/运行时，写作用域 `src/main/java/com/MessTech/common/wireless/MTWireless{MemberKey,SavedData,NetworkRuntime,TeamAccess,NetworkCoreBlock,NetworkCoreItemBlock,NetworkCoreTile}.java`）**

**T2（工具/GUI，写作用域 `src/main/java/com/MessTech/common/wireless/MTWireless{NetworkBindingTool,ToolEvents}.java` + `src/main/java/com/MessTech/common/wireless/gui/MTWireless{NetworkPickerGui,CoreGui}.java`）**

**T3（仓室页签，写作用域 `src/main/java/com/MessTech/common/wireless/gui/MTWirelessHatchTab.java`、
`src/main/java/com/MessTech/common/mixin/late/MixinMTETieredMachineBlockBaseGui_WirelessTab.java`、
`src/main/java/com/MessTech/common/mixin/late/MixinModularScreen_WirelessTab.java`、
`src/main/java/com/MessTech/common/mixin/Mixins.java`、
`src/main/java/com/MessTech/common/thaumcraft/MTEssentiaHatchMETileEntity.java`、
`src/main/java/com/MessTech/common/thaumcraft/MTEssentiaOutputHatchMETileEntity.java`）**

其它文件一律不要动（尤其 `lang/*.lang`、`MTItems.java`、`GTRecipes.java`）。需要改契约先发消息给 Lead。

## 4. 共享契约（精确签名）

### 4.1 `com.MessTech.common.wireless.MTWirelessMemberKey`

```java
public final class MTWirelessMemberKey {
    public final int dim, x, y, z;
    public MTWirelessMemberKey(int dim, int x, int y, int z);
    public static MTWirelessMemberKey of(TileEntity te);   // te 可为 null → null
    public NBTTagCompound write();                          // {"d","x","y","z"}
    public static MTWirelessMemberKey read(NBTTagCompound tag); // 非法返回 null
    // equals/hashCode（dim,x,y,z）
}
```

### 4.2 `com.MessTech.common.wireless.MTWirelessSavedData extends WorldSavedData`

数据名 `"messtech_wireless_me_networks"`。一个成员（仓室坐标）只属于一个网络，重复时按 frequency
UUID 字典序确定性保留（GTLCore `rebuildMemberIndex` 同策略）。所有修改 `markDirty()`。

```java
public static MTWirelessSavedData get(MinecraftServer server);   // overworld mapStorage, 可能 null（早期）
public static String DATA_NAME;                                  // "messtech_wireless_me_networks"

public static final class NetworkInfo {
    public final UUID frequency; public final String name; public final UUID owner;
    public final boolean hasCore; public final int memberCount;
    public UUID frequency(); public String name(); public UUID owner();
}

public void setCore(UUID frequency, int dim, int x, int y, int z);   // 同时建/更新记录
public boolean hasCore(UUID frequency);
public int[] getCore(UUID frequency);        // {dim,x,y,z}，没有返回 null
public List<UUID> getFrequencies();
public List<NetworkInfo> getNetworks();      // 按 name/frequency 稳定排序
public UUID getOwner(UUID frequency); public void setOwner(UUID frequency, UUID owner);
public String getName(UUID frequency); public void setName(UUID frequency, String name); // 截断到 32 字符
public long getMemberRevision(UUID frequency);
public List<MTWirelessMemberKey> getMembers(UUID frequency);
public UUID getMemberNetwork(MTWirelessMemberKey member);
public void addMember(UUID frequency, MTWirelessMemberKey member);   // 先从其它网络移除
public void removeMember(MTWirelessMemberKey member);
public List<UUID> removeMembersAt(int dim, int x, int y, int z);     // 返回被移除的网络
public UUID removeNetwork(UUID frequency);   // 返回 owner，供断线清理
public void clearAll();                      // 服务器停止时
```

### 4.3 `com.MessTech.common.wireless.MTWirelessNetworkRuntime`

```java
public enum ConnectionResult { CONNECTED, ALREADY_CONNECTED, CORE_MISSING, CORE_NOT_CONNECTED,
                               TARGET_MISSING, TARGET_WIRED, FAILED }
public enum BindResult { OK, OK_PENDING, ALREADY_BOUND, ALREADY_CONNECTED, WIRED, NO_ACCESS,
                         NO_CORE, CORE_NOT_CONNECTED, INVALID_TARGET }

public static void init();                    // 注册 server tick 事件；服务端停止时清缓存
public static void onServerStopped();         // 清 CONNECTIONS / 缓存（由 init 注册的 WorldEvent 调）
public static boolean isBindableTarget(World world, int x, int y, int z);  // ME 仓室 + IGridProxyable
public static MTWirelessMemberKey resolveTarget(World world, int x, int y, int z); // 不可绑返回 null
public static boolean canAccess(EntityPlayer player, UUID frequency);     // owner / ServerUtilities 同队
public static List<MTWirelessSavedData.NetworkInfo> getAccessibleNetworks(EntityPlayer player);
public static UUID getMemberNetwork(MinecraftServer server, MTWirelessMemberKey member); // 绑定关系（不一定已连）
public static UUID findConnectedNetwork(MinecraftServer server, MTWirelessMemberKey member); // 已连上的网络
public static boolean isMemberConnected(MinecraftServer server, UUID frequency, MTWirelessMemberKey member);
public static ConnectionResult connectMemberNow(MinecraftServer server, UUID frequency, MTWirelessMemberKey member);
public static void disconnectMember(MinecraftServer server, MTWirelessMemberKey member);
public static void disconnectMembersAt(MinecraftServer server, int dim, int x, int y, int z);
public static void disconnectFrequency(MinecraftServer server, UUID frequency);
public static void requestReconnect(MinecraftServer server, UUID frequency);  // 标脏，下一次 tick 重连
public static BindResult bindTarget(EntityPlayer player, MTWirelessMemberKey target, UUID frequency, boolean replace);
public static BindResult unbindTarget(EntityPlayer player, MTWirelessMemberKey target);
public static boolean isWired(MinecraftServer server, MTWirelessMemberKey member);  // 目标节点有连接
public static long networkIndexOf(...) // 不需要
```

行为要求（对齐 GTLCore）：

- `bindTarget`：`canAccess` 不通过 → `NO_ACCESS`；核心记录不存在 → `NO_CORE`；核心当前不在 ME 网格上
  （`coreNode == null || coreNode.getGrid() == null` 或没有连接）→ `CORE_NOT_CONNECTED`；目标不可绑 →
  `INVALID_TARGET`；目标有线连接 → `WIRED`；已有绑定且不 replace 且不是同一网络 → `ALREADY_BOUND`；
  目标节点与核心同网格 → `ALREADY_CONNECTED`；否则 `addMember` + 立刻 `connectMemberNow`，
  `CONNECTED/ALREADY_CONNECTED` → `OK`，其余 → `OK_PENDING`。
- `unbindTarget`：无绑定 → 返回 `INVALID_TARGET`（工具层转成"没有可解除的绑定"提示）；否则移除成员并断线。
- 运行时维护（每 20 tick）：对每个网络取核心 TE（`findLoadedCore`）；核心区块未加载则跳过；对每个成员：
  目标区块未加载 → 跳过（保留绑定）；目标不再是 ME 仓室 → 移除绑定；目标节点 null → 跳过；与核心同网格 →
  标记已连；否则 `createGridConnection` 并在失败时保留重试。核心不存在/被破坏 → `removeNetwork` 断线。
- 断线后必须销毁 `IGridConnection`（只销毁自己创建的）；再连时先销毁记录的旧连接。
- 服务端停止（`FMLServerStoppedEvent` 或 `WorldEvent.Unload`）清空静态缓存，避免下一张存档串数据。
- 线程：所有 AE2 操作只在服务端 tick / 事件线程执行。

### 4.4 核心方块与 TE

```java
public class MTWirelessNetworkCoreBlock extends BlockContainer {
    public static final MTWirelessNetworkCoreBlock INSTANCE = new MTWirelessNetworkCoreBlock();
    // setBlockName("wirelessNetworkCore")；**不要** setBlockTextureName：会让 ItemBlock 渲染成 2D 物品图标
    // Material.iron, hardness 5, resistance 5, harvestLevel "wrench",2; setCreativeTab(GregTechAPI.TAB_GREGTECH)
    // GregTechAPI.registerMachineBlock(this, -1) + onBlockAdded/breakBlock 的 causeMachineUpdate（照 MTEssentiaHatchBlock）
}
public class MTWirelessNetworkCoreItemBlock extends ItemBlock { /* placeBlockAt: GT 仓室式放置朝向 */ }
public class MTWirelessNetworkCoreTile extends AENetworkTile implements IGuiHolder<PosGuiData> {
    public static final String TE_ID = "messtech.wirelessNetworkCore";
    public static final int MAX_NAME_LENGTH = 32;
    public UUID getFrequency();                       // 懒生成，稳定
    public String getNetworkName();                   // 从 SavedData 读，空则返回 shortFrequency 风格默认名
    public String getShortFrequency();                // frequency 前 8 位
    public boolean isLinkedToAeNetwork();             // proxy.getNode()!=null && node.getGrid()!=null
    public boolean canAccess(EntityPlayer player);
    public ForgeDirection getConnectionSide();        // 方块 meta 0..5 → ForgeDirection.getOrientation
    public void setConnectionSide(ForgeDirection side);
    public void refreshConnectionSide();              // proxy.setValidSides + node.updateState + 邻居/方块更新
    public void setOwner(EntityPlayer player);        // proxy.setOwner + SavedData
    public boolean migrateLegacyOwner(EntityPlayer player);
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings);
    @SideOnly(Side.CLIENT) public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel);
}
```

- 方块 meta 就是 `ForgeDirection` ordinal（0=DOWN..5=EAST）。**玩家放置朝向照 GT5U 仓室**：ItemBlock 覆写
  `placeBlockAt(...)`，用 `BaseTileEntity.getSideForPlayerPlacing(player, ForgeDirection.UP, 全 true)`（即
  `GTUtility.getSideFromPlayerFacing`：朝向玩家的一侧，俯仰 >65° 时为 UP/DOWN）算出的面作为 meta；
  `onBlockPlaced` 只保留为非玩家放置路径（worldgen/方块放置器）的兜底。
- `getIcon(IBlockAccess, x,y,z,side)`：`side == tile.getConnectionSide()` → 正面动画图标，否则机壳图标。
  `getIcon(int side, int meta)`：`side == NORTH` → 正面，否则按面给 top/bottom/side 机壳（物品栏 3D 渲染用）。
- 右键：手持 GT 扳手 → 旋转连接面并消耗扳手；否则（服务端）打开核心 GUI
  `GuiFactories.tileEntity().open(player, tile)`。
- `breakBlock`：先 `tile.removeNetwork()`（清 SavedData + 断线），再 `super.breakBlock`。
- `onBlockPlacedBy`：`tile.setOwner(player)`；`onLoad/onReady` 时把自身登记回 SavedData 并 requestReconnect。
- TE 的 `getGridNode(dir)`：`dir == connectionSide` 返回节点，否则 null；
  `getCableConnectionType(dir)`：连接面 `SMART`，否则 `NONE`。
- 代理：`createProxy()` 覆写：`new AENetworkProxy(this, "proxy", new ItemStack(coreItem), true)`，然后
  `setFlags(GridFlags.DENSE_CAPACITY)`、`setIdlePowerUsage(0.0)`、`setValidSides(EnumSet.of(connectionSide))`。
- 核心被破坏/区块卸载时：`proxy` 生命周期 AE2 管；另外 `removeNetwork` 只应在方块被破坏时调用；
  区块卸载不要清 SavedData（成员绑定保留，重载后重连）。

### 4.5 `MTWirelessContent`（Lead）

```java
public static void preInit();          // 注册工具物品、初始化运行时/事件/GUI 工厂
public static void registerBlocks();   // registerBlock + ItemBlock + registerTileEntity（由 MTBlocks 调）
public static void registerMarkers();  // 给工具/核心物品挂「由GregTech Leisure添加」标注
public static ItemStack coreStack(); public static ItemStack toolStack();
```
`MTItemList` 新增枚举：`WirelessNetworkCore`、`WirelessNetworkBindingTool`。

### 4.6 `MTRegistryMark`（Lead）

```java
public static void markAdded(ItemStack stack);      // GTLCore 原样：文本 + RAINBOW_SLOW 单色循环（25 tick/档）
public static void markModified(ItemStack stack);   // GTLCore 原样：文本 + RAINBOW 单色循环（5 tick/档），暂不挂
```
文字取 lang（`messTech.registry.add` / `messTech.registry.modify`，照抄 GTLCore，中英各一行，颜色不写进 lang）；
颜色实现是 `MTTooltipFormattingCode`（GT Modern `TooltipHelper.GTFormattingCode` 的 1.7.10 移植，palette/rate/
顺序全同，时钟由 `ClientProxy` 注册的 `ClientTickEvent`（END）驱动），出处与验证见 §8.7。

只在**新增物品**（核心、绑定工具）上挂 added；GT ME 仓室物品**不挂**任何修改标注（实机反馈：不要
「由MessTech修改」，且标注文字要照抄 GTLCore，不许改写成 MessTech 字样）。`markModified` 按要求一并移植，当前
没有任何调用方（留着备用）。

### 4.7 GUI（T2/T3）

包 `com.MessTech.common.wireless.gui`（**不要放 client 包**：MUI2 面板构建服务端也会跑）。

```java
public final class MTWirelessCoreGui {
    public static ModularPanel build(MTWirelessNetworkCoreTile tile, PosGuiData data,
                                     PanelSyncManager syncManager, UISettings settings);
    @SideOnly(Side.CLIENT) public static ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel);
}
public final class MTWirelessNetworkPickerGui implements IGuiHolder<GuiData> {
    public static void init();                 // Lead 在 preInit 调；构造 SimpleGuiFactory
    public static void open(EntityPlayerMP player);
    // buildUI 里：列出 player 可访问网络（MTWirelessNetworkRuntime.getAccessibleNetworks），
    // 点击行 = registerSyncedAction 在服务端把网络写进绑定工具 NBT（复用 MTWirelessNetworkBindingTool 的工具方法）
}
public final class MTWirelessHatchTab {
    public static void attach(ModularPanel panel, PanelSyncManager syncManager, MetaTileEntity machine);
    // 仅当 machine instanceof MTEHatch && machine instanceof IGridProxyable 时挂页签
}
```

GUI 数据全部走 MUI2 同步值/同步动作（见 §2）。网络列表最多显示 8 条（`MAX_ROWS = 8`），超出显示
"还有 N 个网络"。

### 4.8 语言键（Lead 写入两个 lang 文件；代码里只能使用这些 key）

前缀 `wirelessme.`；物品/方块名沿用 GTLCore 中文名。

```
tile.wirelessNetworkCore.name=无线ME网络核心
item.wirelessNetworkBindingTool.name=无线ME网络绑定工具
wirelessme.tooltip.core.0=§7贴着 ME 线缆或 ME 方块放置，朝向面接入 ME 网络
wirelessme.tooltip.core.1=§7Shift+右键可用绑定工具保存此网络；扳手旋转连接面
wirelessme.tooltip.core.usage=§7用绑定工具 Shift+右键保存网络，再右击 ME 仓室完成绑定
wirelessme.tooltip.tool.select=§7Shift+右键无线ME网络核心：保存该网络
wirelessme.tooltip.tool.pick=§7右键空气：从已有无线ME网络中选择要保存的网络
wirelessme.tooltip.tool.bind=§7右键未绑定的 ME 仓室：绑定到已保存的网络
wirelessme.tooltip.tool.rebind=§7Shift+右键 ME 仓室：改绑到已保存的网络
wirelessme.tooltip.tool.disconnect=§7Shift+左键 ME 仓室：解除其无线网络绑定（保留工具保存的网络）
wirelessme.tooltip.tool.network=§7已保存网络：%s
wirelessme.tooltip.tool.empty=§e尚未保存网络
wirelessme.tooltip.tool.unknown=§7网络名当前不可用
wirelessme.chat.saved=已保存无线ME网络：%s
wirelessme.chat.cleared=已清除绑定工具中保存的无线ME网络
wirelessme.chat.select_core=请先 Shift+右键无线ME网络核心，或右键空气从列表中选择网络
wirelessme.chat.already_bound=此仓室已绑定网络；如需改绑，请 Shift+右键
wirelessme.chat.not_bound=此仓室没有可解除的无线ME网络绑定
wirelessme.chat.no_access=你没有访问此无线ME网络的权限
wirelessme.chat.missing_core=这个无线ME网络核心已经不存在
wirelessme.chat.core_not_connected=请先让无线ME网络核心贴着已有 ME 线缆或 ME 方块
wirelessme.chat.invalid_target=这个方块没有可连接的 ME 网格节点（只能绑定 ME 仓室）
wirelessme.chat.wired=此仓室已有有线或共享 ME 连接，请先断开
wirelessme.chat.already_connected=这个仓室已经连接到此网络
wirelessme.chat.linked=已将 ME 仓室接入无线ME网络（%s）
wirelessme.chat.linked_pending=已记录这个 ME 仓室，等待无线ME网络连接（%s）
wirelessme.chat.disconnected=已断开这个 ME 仓室的无线网络连接
wirelessme.chat.name_saved=无线ME网络已命名为：%s
wirelessme.chat.core_linked=无线ME网络核心已接入 ME 网络：%s
wirelessme.chat.core_unlinked=无线ME网络核心尚未接入 ME 网络
wirelessme.gui.core.title=无线ME网络核心
wirelessme.gui.core.name=网络名称
wirelessme.gui.core.save=保存名称
wirelessme.gui.core.status.linked=§a状态：已接入 ME 网络
wirelessme.gui.core.status.unlinked=§c状态：未接入 ME 网络
wirelessme.gui.core.frequency=网络 ID：%s
wirelessme.gui.core.members=已绑定仓室：%s
wirelessme.gui.picker.title=选择无线ME网络
wirelessme.gui.picker.empty=还没有可用的无线ME网络
wirelessme.gui.picker.entry=无线ME网络：%s
wirelessme.gui.picker.selected=§a已保存
wirelessme.gui.picker.clear=清除工具保存的网络
wirelessme.gui.picker.close=关闭
wirelessme.tab.title=无线网络
wirelessme.tab.current=§7当前网络：%s
wirelessme.tab.current.none=当前网络：未连接
wirelessme.tab.connect=§7左键：连接到此网络
wirelessme.tab.connected=§a已连接
wirelessme.tab.locked=§7仓室已有有线或共享 ME 连接
wirelessme.tab.disconnect=断开当前网络
wirelessme.tab.empty=还没有可用的无线ME网络
wirelessme.tab.entry=无线ME网络：%s
wirelessme.tab.more=还有 %s 个网络未显示
wirelessme.tab.close=关闭
messTech.registry.add=由GregTech Leisure添加
```

绑定工具的 Baubles 饰品槽与选择界面键位（§8.5）：

```
slot.wireless_connector=§a无线连接器§7
key.categories.messtech=MessTech
key.messtech.wirelessNetworkPicker=打开无线ME网络选择界面
```

### 4.9 资源路径（Lead 复制）

- `assets/messtech/textures/blocks/wireless_network_core_front.png`（16x256，来自
  `tmp/GTLCore-gtl-1431-skyblock/.../textures/block/wireless_network_core_front.png`）
  + `wireless_network_core_front.png.mcmeta`（只留 `animation` + `frametime: 2`，GTLCore 的
  `shimmer`/`gtlbloom` 段丢弃）
- `assets/messtech/textures/items/wireless_advanced_tool.png`（16x16，来自 GTLCore `textures/item/wireless_advanced_tool.png`）
- `assets/messtech/textures/gui/wireless/tab_wireless_{normal,hover,selected}.png`（24x24，来自 GTLCore 同名文件）
- `assets/baubles/textures/items/empty_bauble_slot_wireless_connector.png`（16x16，来自 GTLCore
  `assets/gtlcore/textures/slot/wireless_connector.png`）：Baubles Expanded 的空槽图标固定按
  `baubles:textures/items/empty_bauble_slot_<槽类型>.png` 注册，所以**必须放在 baubles 命名空间**（与 AE2
  的 `empty_bauble_slot_Terminal.png` 同做法，见 §8.6）。
- 方块物品图标：ItemBlock 按 3D 方块渲染（不设 blockTextureName），NORTH 面为不透明烘焙正面贴图、其余面为机壳贴图。

### 4.10 NBT

- 核心 TE：`frequency`(UUID: `freqMost`,`freqLeast` 或 `freq` String)、`owner` 不放 TE（SavedData 权威）。
- SavedData 根：`networks`(List)：每项 `freq`(UUID) + `core`(`d/x/y/z`) + `owner`(UUID) + `name`(String)
  + `members`(List of `d/x/y/z`) + `revision`(long)。
- 工具物品：`WirelessNetworkId`(UUID) + `WirelessNetworkName`(String)。

## 5. 配方（Lead 负责，GTLCore `MachineRecipe.java:918-936` 换算）

- **无线ME网络核心**：装配机（`GTValues.RA.stdBuilder`），EV 机壳（`ItemList.Hull_EV`）×1 +
  AE2 无线枢纽（`blocks().wirelessHub()`）×4 + 量子桥连接（`quantumLink()`）×4 + 量子环（`quantumRing()`）×36
  + 铂铀合金线（`OrePrefix.wireGtHex`）+ LuV 电路 ×8 + EV 场发生器 + 氡 1000mB；400t / EV。
- **无线ME网络绑定工具**：装配机，AE2 无线套件（`items().toolWirelessKit()`）×1 + EV 电路 + EV 发射器；
  200t / EV。
- 具体材料可用性 Lead 按 GT5U 5.09.54.264 的 `ItemList` / `OrePrefix` 实际条目换算（没有的用同档替代并在
  文档里记一笔）。

## 6. 行为/边界规范

- 绑定工具：主手右击核心且潜行 → 保存网络；主手右击仓室 → 未绑定则绑定，已绑定且非潜行 → 提示"已绑定，
  需改绑请 Shift+右击"；潜行右击 → 改绑；潜行左击仓室（`PlayerInteractEvent.LEFT_CLICK_BLOCK`）→ 解绑并
  取消事件；右击空气 → 打开网络选择 GUI。
- **蹲下放置仓室自动绑定**（GTLCore `WirelessBindingToolPlacementHandler` 的移植）：玩家潜行、手持的是方块物品
  （`ItemBlock`，即正在放置 ME 仓室）、且绑定工具（带已保存网络）在背包里任意位置时，放置后延迟 2 tick、最多重试
  10 tick，`resolveTarget` 成功后 `bindTarget(..., replace=true)` 自动绑定该仓室。1.7.10 没有原版副手，所以工具是
  **在背包主物品栏里查找**（不是 GTLCore 的 Curios/副手）；放置前先记下 `Block.getBlockFromItem(held)`，只有该
  位置确实变成这个方块时才继续，因此放置失败不会误绑已存在的方块；延迟期间只 `chunkExists` 判断、绝不加载区块。
- 核心 GUI：显示网络 ID（前 8 位）、状态、已绑定仓室数；可改名（保存按钮 → synced action 写 SavedData）。
- 仓室页签：显示当前网络；列出至多 8 个可访问网络，左键连接、已连接的显示"已连接"并可左键断开；
  仓室已有有线连接时整页禁用并提示。
- 所有聊天消息用 `GTUtility.sendChatTrans(player, key, args...)` 或
  `player.addChatComponentMessage(new ChatComponentTranslation(...))`；数值/名字用 `%s`。
- 核心方块没有网络名时显示 "frequency 前 8 位"。
- 无权限的操作一律静默失败 + `wirelessme.chat.no_access`。
- 不加载区块：任何运行时逻辑只用 `world.getChunkProvider().chunkExists(...)` 判断，不用 `blockExists` 的
  加载语义，也不强制加载。

## 7. 工作规则

1. 只改自己写作用域内的文件；**不要动 lang / MTItems / MTBlocks / CommonProxy / GTRecipes / MTNetwork**。
2. **不要运行 gradle**（编译/spotless 由 Lead 统一跑；并行跑会互相锁）。写代码时以本文契约和
   `tmp/` 里的参考源码为准，编译错误由 Lead 汇总后回派。
3. 不要新增 `tools/**` 校验器；不要 `git commit`。
4. 中文名词按 GTNH 本地化习惯；用户可见文本走 §4.8 的 lang key。
5. 完成后在任务里写清楚：改了哪些文件、未决问题、需要 Lead 接线的地方。

## 8. 落地记录（实现完成后补充，2026-11-23）

### 8.1 最终文件清单

| 层 | 文件 |
| --- | --- |
| 内容/注册（Lead） | `common/wireless/MTWirelessContent.java`、`common/util/MTRegistryMark.java`、`common/recipe/MTWirelessRecipes.java` |
| 运行时（server） | `common/wireless/MTWirelessMemberKey.java`、`MTWirelessSavedData.java`、`MTWirelessNetworkRuntime.java`、`MTWirelessTeamAccess.java` |
| 核心方块 | `common/wireless/MTWirelessNetworkCoreBlock.java`、`MTWirelessNetworkCoreItemBlock.java`、`MTWirelessNetworkCoreTile.java` |
| 绑定工具 | `common/wireless/MTWirelessNetworkBindingTool.java`、`MTWirelessToolEvents.java`、`MTWirelessToolLocator.java` |
| 饰品栏（Baubles，§8.6） | `common/wireless/MTWirelessBaubles.java`、`client/MTWirelessKeyBindings.java`、`common/network/OpenWirelessPickerPacket.java` |
| GUI | `common/wireless/gui/MTWirelessCoreGui.java`、`MTWirelessNetworkPickerGui.java`、`MTWirelessHatchTab.java` |
| mixin | `common/mixin/late/MixinMTETieredMachineBlockBaseGui_WirelessTab.java`、`MixinModularScreen_WirelessTab.java`；`common/mixin/Mixins.java` 的 `WIRELESS_HATCH_TAB` 组 |
| 接线 | `MTBlocks.registerBlocks()` → `MTWirelessContent.registerBlocks()`；`CommonProxy.preInit()` → `MTWirelessContent.preInit()`（内含 `MTWirelessBaubles.init()`）；`ClientProxy.preInit()` → `MTWirelessKeyBindings.init()`；`MTNetwork.register()` 末尾 → `OpenWirelessPickerPacket`；`GTRecipes.loadRecipes()` 开头 → `MTWirelessRecipes.load()`；`MTEssentiaHatchMETileEntity#buildUI` 末尾 → `MTWirelessHatchTab.attach(...)` |
| 资源 | `assets/messtech/textures/blocks/wireless_network_core_front.png`(+`.mcmeta`，16 帧)、`textures/items/wireless_advanced_tool.png`、`textures/gui/wireless/tab_wireless_{normal,hover,selected}.png`、`assets/baubles/textures/items/empty_bauble_slot_wireless_connector.png`；两个 lang 文件的 `wirelessme.*` / `messTech.registry.*` / `slot.wireless_connector` / `key.*` |

### 8.2 与契约字面的偏差（均为落地必需，已与 Lead 确认口径）

1. **目标识别放宽**（`MTWirelessNetworkRuntime#isBindableTile`）：除「`IGregTechTileEntity` → `MTEHatch` 且
   `IGridProxyable`」外，还接受「非 `AEBaseTile` 的 `IGridProxyable` 普通 TileEntity」——这是本模组 ThE ME
   源质输入/输出仓（普通 TE）能绑定的前提；AE2 自家方块（drive/interface/线缆）因继承 `AEBaseTile` 仍被排除。
2. **`isWired` 口径**：只统计「不是本 runtime 创建的连接」，避免把无线连接本身误判成有线锁；`bindTarget` 的
   `WIRED` 判定同口径。
3. **同网络重复绑定**返回 `ALREADY_CONNECTED`（提示「已连接」而不是「已绑定，需 Shift 改绑」）；改绑到别的
   网络需要同时拥有旧网络的访问权（`replace=true` 时 `NO_ACCESS`）。
4. **方块物品栏图标固定 NORTH 正面**（`getIcon(int,int)`，3D 渲染）；世界内由 `getIcon(IBlockAccess,...)` 按 TE
   朝向决定。`MTWirelessNetworkCoreTile#canBeRotated()` 必须为 `false`，否则 GT 扳手的 `BehaviourWrench` 会把
   AE tile 当 `IOrientable` 旋转并吞掉右键。手持绑定工具时核心方块不打开 GUI（改由工具保存网络），见 §8.4。
5. **TE 认领 frequency**：`onReady()` 在 `super.onReady()` 前把真实朝向塞进 proxy、并从 `SavedData` 按核心
   坐标认领丢失的 frequency（TE NBT 损坏时不会把整个网络孤儿化）。
6. **核心写 `fre`（memory）**：TE NBT 用 `freqMost/freqLeast/freq`，SavedData 用 `m/l/s` 复合 UUID；不向
   客户端同步 frequency（GUI 走 sync value、图标走 meta）。
7. **GUI 无自定义网络包**：三套 GUI 全部走 MUI2 同步值/同步动作（`buildUI` 两侧执行）；`MTWirelessCoreGui`
   的改名输入用本地 `StringValue` + 保存 synced action（避免聚焦丢失时的 C2S 警告）；picker/页签的行点击只
   发 `int index`，服务端重读列表、越界忽略；写操作经 gtnhlib `ServerThreadUtil` 切回服务端线程。
8. **仓室页签结构**：常显 24x24 tab 面板挂在机器主面板左侧外（`.relative(mainPanel).left(-26).top(20)`），
   点击 toggle 176x172 的网络列表面板（覆盖主面板位置，tab 在左侧仍可点）；`MixinModularScreen_WirelessTab`
   在 `onOpen` 后从屏幕主 PSM 按 `TAB_PANEL_KEY` 查 handler 并打开，另有 client tick 兜底。

### 8.3 已知限制 / 未实测点

1. **AE2 Security Terminal**：GT 的 ME 仓室 proxy 没有 owner（节点 playerID = -1），如果核心接入的 ME 网络
   启用了「已通电的 Security Terminal」，`createGridConnection` 会抛 `SecurityConnectionException`：绑定返回
   `OK_PENDING`、100 tick 退避重试但永远连不上。GTLCore 同样的限制；要修得给仓室节点注入 playerID（侵入
   GT/第三方代码）。
2. **绑定后又接上有线**：现有无线连接不会被主动切断（也不做周期性有线复查）；这是维护流程里没有的步骤，若
   以后要对齐 GTLCore 的 `findWiredNetworkFrequency` 行为需要再加一块拓扑检查。
3. **扳手旋转不播放 GT 扳手音效**（功能正常）；要加就 `GTUtility.sendSoundToPlayers(...)` 一行，但仓库里
   GT 声效 API 版本相关，本次未加。
4. **进游戏实测**：用户已跑第一轮实机（贴图/放置朝向/Shift+右键 GUI/来源标注/页签灰字反馈见 §8.4，均已修）。
   修复后只做了 `gradlew spotlessApply build`（编译/格式化/checkstyle/reobf 全过），未再进游戏复测；页签/核心
   GUI 的视觉位置常量集中在 `MTWirelessHatchTab` 与 `MTWirelessCoreGui` 文件顶部，若首帧位置或尺寸要调，改常量
   即可；建议复测顺序：贴线缆放核心 → 工具存网络 → 绑仓室（跨距离/维度）→ 拆/重载双方区块 → 重启服务器 →
   拆核心 → 扳手旋转 → 无权限玩家右键。
5. **拆核心/重放 = 新网络**（物品不保留 frequency/名字）；`SavedData.clearAll()` 按契约保留但**不得**在
   服务器停止时调用（会 markDirty 成空存档）。
6. `MTRegistryMark` 的「由GregTech Leisure添加」由 `MTWirelessContent` 统一挂（核心/工具各一条）；GT ME 仓室
   物品不挂修改标注（`ItemStackMap` 对 NBT 不敏感，重复调用会出两条，所以只在内容注册处挂一次）。

### 8.4 频道上限提升（2026-11-24 追加）

- `MTWirelessNetworkCoreTile` 现在实现 `com.MessTech.common.ae2.IMTChannelCapacity`，
  `mtChannelCapacity() = Integer.MAX_VALUE`：无线核心自己的 AE2 节点是整张无线网络的 uplink，不再被 dense 的 32
  频道卡住（配合 `docs/ultra-ae2.md` 的 `MixinGridNode_ChannelCapacity`）。无线连接本身仍然不耗频道。

### 8.5 第一轮实机反馈与修复（2026-11-23）

| 反馈 | 原因 | 修复 |
| --- | --- | --- |
| 核心物品贴图透明 | ① 方块调了 `setBlockTextureName`，`ItemBlock#getSpriteNumber()` 变成 1，物品按 2D sprite 从 **items** 图集取图（该图集没有这张图）；② 正面贴图本身是半透明 overlay；③ 机壳图标走 `Textures.BlockIcons.MACHINE_EV_*`，非 GT 方块可能拿到 null 并回退到半透明正面 | ① 去掉 `setBlockTextureName`，物品按 3D 方块渲染；② 从 GT5U 复制 EV 机壳 side/top/bottom 成自己的贴图并注册；③ 把 GTLCore 的 overlay 烘焙到 EV 机壳 side 上，正面 16 帧贴图完全不透明 |
| 放下后所有面都看不到正面贴图 | 放置朝向 = 被点击面的相反面，平地上放置时连接面永远朝下（埋在地里） | 放置朝向照 GT5U 仓室：ItemBlock 覆写 `placeBlockAt`，用 `BaseTileEntity.getSideForPlayerPlacing`（朝向玩家的一侧；俯仰 >65° 时为 UP/DOWN） |
| Shift+右键核心会打开核心 GUI | 1.7.10 的潜行状态在 movement 包里同步，Shift+右键同 tick 到达服务端时 `player.isSneaking()` 可能仍是 false → 工具 `onItemUseFirst` 落到「非目标方块」分支 `return false` → 方块 `onBlockActivated` 打开 GUI | 方块 `onBlockActivated`：手持绑定工具时**永不打开 GUI**，直接进「保存该网络」分支（`canAccess` 校验 + `saveNetwork`），从根上消除竞态 |
| 仓室 tooltip 不要「由MessTech修改」；新增物品要照抄 GTLCore 的「由GregTech Leisure添加」 | 契约按本模组口径写了 added/modified 两行 | 删除 modified 机制（`markModified` / `registerModifiedHatches` / `MTMachineLoader` 调用 / lang 键）；added 文案改成 GTLCore 原文「由GregTech Leisure添加 / Added by GregTech Leisure」；颜色按 §8.7 换成 GTLCore 原样的 `RAINBOW_SLOW` |
| 页签「未连接」灰字贴近背景看不清 | lang 里 `§7` 前景色 | 去掉 `wirelessme.tab.current.none` 的 `§7`（`wirelessme.tab.current` 保留原样） |

资源变更：新增 `wireless_network_core_{side,top,bottom}.png`（从
`tmp/GT5-Unofficial-5.09.54.264/.../textures/blocks/iconsets/MACHINE_EV_*.png` 复制）；
`wireless_network_core_front.png` 改为 overlay × `MACHINE_EV_SIDE` 的烘焙结果（16x256，仍全不透明）。

### 8.6 绑定工具的 Baubles 饰品栏（2026-11-23 追加）

GTLCore 给绑定工具留了一条 **Curios 专用槽**（`WirelessConnectorCurioSlot`：`SLOT_ID = "wireless_connector"`、
size 1、priority 150、图标 `gtlcore:slot/wireless_connector`；工具靠
`data/curios/tags/items/wireless_connector.json` 才进得去，和无线终端抢的通用 `curio` 槽分开）。1.7.10 没有
Curios，本次用 GTNH 自带的 **Baubles Expanded** 复刻：

- **依赖**：`dependencies.gradle` 显式 `implementation(gtnhDev("Baubles-Expanded"))`（2.9.0 清单解析为
  `2.2.25/2.2.27-GTNH`；GT5U / Botania / ThaumicTinkerer 本来就传递依赖它，这里显式钉住），`@Mod` 的
  `dependencies` 追加 `required-after:Baubles|Expanded`（**硬依赖**，用户 2026-11-23 明确要求；FML 的
  `Loader#computeDependencies` 按 `;`/`:` 切分后 modid 原样比较，`|` 不影响解析）。
- **注册**：`MTWirelessBaubles.init()` 由 `MTWirelessContent#preInit` 调用，
  `BaubleExpandedSlots.tryRegisterType("wireless_connector")` + `tryAssignSlotOfType(...)`。**必须在
  PREINITIALIZATION**（Baubles Expanded 只在这时接受注册/分配；`BaublesConfig.loadConfig` 到 init 才跑，所以
  本模组注册的类型也会进它生成的默认列表）。新槽追加在默认 4 槽（amulet/ring/ring/belt）之后；`InventoryBaubles`
  按「已分配槽数」构造，旧存档玩家登录后自然多一个下标 4 的空槽，向后兼容。
- **材质**：源 `tmp/GTLCore-git/src/main/resources/assets/gtlcore/textures/slot/wireless_connector.png`
  （16x16）复制为 `src/main/resources/assets/baubles/textures/items/empty_bauble_slot_wireless_connector.png`。
  原因：Baubles Expanded 的空槽图标由 `ItemDebugger#registerIcons` 按
  `baubles:empty_bauble_slot_<registeredType>` 注册、`SlotBauble#getBackgroundIconIndex` 取用；自定义类型的
  贴图只能由注册方在 **baubles 命名空间**提供（与 AE2 的 `empty_bauble_slot_Terminal.png` 同做法）。图标表在
  全部 preInit 之后才拼进图集（`TextureMap` 在 `FMLClientHandler.beginMinecraftLoading` 之后构造），因此
  preInit 注册的类型一定进表。
- **语言键**：`slot.wireless_connector=§a无线连接器§7`（Baubles 的悬停 tooltip 用 `slot.<type>`；语义等同
  GTLCore 的 `curios.identifier.wireless_connector`）。
- **物品**：`MTWirelessNetworkBindingTool implements IBaubleExpanded`，`getBaubleTypes` 只返回
  `{ "wireless_connector" }`（等价 GTLCore item tag 只列这一槽，不会进通用槽），`canEquip/canUnequip` 恒
  true、`getBaubleType` 返回 null；tooltip 末尾追加 `BaubleItemHelper.addSlotInformation`（Baubles 标准的
  SHIFT 兼容槽提示）。
- **槽内功能**（对应 GTLCore 的 Curios 分支）：
  - 键位 `key.messtech.wirelessNetworkPicker`（默认不绑键）→ `MTWirelessKeyBindings` 发
    `OpenWirelessPickerPacket`，服务端线程打开 MUI2 picker —— 对应 GTLCore
    `WirelessBindingPickerKeyMapping` + `OpenBindingPickerPacket`；
  - picker 的打开 / 选择 / 清除 / “已保存行”全部改用 `MTWirelessToolLocator.find`（饰品栏优先，再主手、
    主物品栏），不再只看 `getHeldItem` —— 对应 GTLCore 在 `WirelessNetworkPickerMenu` 里的
    `WirelessBindingToolLocator`；
  - 蹲下放置 ME 仓室的自动绑定也走 locator（`MTWirelessToolLocator#findWithNetwork`：第一把**带已保存
    网络**的工具，保持原来「扫背包找有网络工具」的语义，不会因为饰品栏里先有一把空工具就失效）；
  - NBT 回写：`MTWirelessNetworkBindingTool#syncTool` 先试 `MTWirelessBaubles#syncSlot`
    （`InventoryBaubles#syncSlotToClients` → `PacketSyncBauble`），否则按主手/主物品栏槽位发
    `S2FPacketSetSlot`；
  - 饰品栏里的工具收不到 `Item#onUpdate`，缓存网络名的刷新改挂到 `IBauble#onWornTick`（每 20 tick，服务端
    改完回写同步）。
- **已知限制**：①槽位类型只在 preInit 注册；若玩家在 Baubles 配置里开了 `manualSlotSelection` 且
  `slotTypeOverrides` 是旧版本写下的 4 槽列表，需要手动把 `wireless_connector` 加进去，否则工具没有可穿的槽
  （重新分配槽位要重启游戏）。②GTLCore 用「副手右键 ME 仓室打开仓室无线页签」的交互 1.7.10 没有副手，本模组
  本来就没有这条交互（页签从机器 GUI 进），不属于这次移植范围。

### 8.7 来源标注按 GTLCore 1:1（连颜色一起，2026-11-23 追加）

用户要求：「由GregTech Leisure添加」要**一比一、连颜色和文本一起**照 GTLCore 应用到新增的无线内容上；顺手把
「由GregTech Leisure修改」也搬过来（暂时不用也没关系）。

- **GTLCore 的写法**（`GTLMachines.java:67-73`、`GTLItems.java:362-363` 同款）：
  `Component.translatable("gtlcore.registry.add").withStyle(style -> style.withColor(TooltipHelper.RAINBOW_SLOW.getCurrent()))`，
  modify 用 `TooltipHelper.RAINBOW`。两个 code 定义在 GT Modern 的
  `com/gregtechceu/gtceu/client/util/TooltipHelper`（1.20.1 dev jar `tmp/GTLCore-git/libs/gtceu-1.20.1-1.4.4.jar`，
  `javap -p -c` 可核对）：`ALL_COLORS = RED, GOLD, YELLOW, GREEN, AQUA, DARK_AQUA, DARK_BLUE, BLUE, DARK_PURPLE,
  LIGHT_PURPLE`；`RAINBOW = createNewCode(5, ALL_COLORS)`、`RAINBOW_SLOW = createNewCode(25, ALL_COLORS)`。
  `GTFormattingCode#updateIndex()` 在 `GTValues.CLIENT_TIME % rate == 0` 时把 index 前进一格，
  `ForgeClientEventListener` 随后才 `CLIENT_TIME++`：**整行一个颜色、每 rate 个 client tick 跳一档**，不是逐字
  彩虹。因为 `CLIENT_TIME` 从 0 开始，第一次 tick 就前进一档，所以实际第一档是 GOLD（RED 排在循环末尾）。
- **1.7.10 落地**：`MTTooltipFormattingCode` 是 `GTFormattingCode` 的移植（同一 palette、同一 rate、同一顺序、
  同一「先前进后计时」的时钟；`toString()` 返回当前颜色的 § 控制符），时钟由 `ClientProxy#preInit` 注册的
  `TickEvent.ClientTickEvent`（END）驱动。`MTRegistryMark#markAdded` 用 `RAINBOW_SLOW`、`#markModified` 用
  `RAINBOW`；文字仍取 lang（`messTech.registry.add` / `messTech.registry.modify`，中英各一行，内容照抄 GTLCore，
  颜色不写进 lang）。
- **现状**：added 挂在核心物品 + 绑定工具（`MTWirelessContent#registerBlocks`）；modified 已移植但**无调用方**。

## 9. v2：存档级网络 + 手动创建/选择 + Flux Networks 风格 GUI（2026-11-24）

本节**取代** §0-§8 中与之冲突的行为描述；§4 的旧签名只要没被本节改写就仍然有效。用户 2026-11-24 明确要求：

1. 无线ME核心的「一次性自动网络」改成**持久化**：网络是存档级独立对象，拆核心不销毁网络；
2. 玩家**手动创建网络 / 手动选择绑定**（= 用户说的「频道」，就是 Flux Networks 的「创建新网络」）；
3. 核心 GUI 重构为 **Flux Networks 1.20 风格**（背景/边框/页签/网络色/网络选择/创建网络），
   **只抄 GUI，不搬 Flux 的能量传输等功能**；
4. 绑定工具的网络选择界面、仓室无线页签也统一成同风格（用户选「全部统一」）；
5. 参考 Flux Networks 给出**移植建议**（§9.8）。

### 9.0 行为变化（旧 → 新）

| 场景 | 旧行为（v1） | 新行为（v2） |
| --- | --- | --- |
| 放置核心 | 自动生成随机 UUID 网络并登记 | 核心**未绑定**，GUI 里手动选择或创建网络 |
| 网络对象 | 依附于核心，核心没了记录也没了 | 独立记录，`WorldSavedData` 里永久保留（直到手动删除） |
| 拆核心 | `removeNetwork`：删记录 + 断所有仓室绑定 | 清空记录的 `core` 槽 + 断链路；**记录、名字、颜色、访问等级、仓室绑定全部保留**；核心物品 NBT 记住网络 |
| 重放核心 | 新网络 | 物品 NBT 里有网络 → 自动向该网络申请 `core` 槽（空闲时）并重连全部仓室 |
| 一个网络的核心数 | 恰好 1 | 仍为 1（`core` 槽）；别的核心选择已占用的网络会提示「已有核心」 |
| 网络改名 | 核心 GUI 改名 | 网络设置页改名（仅所有者） |
| 网络数量 | 无限制（每核心一个） | 每人可创建数量上限，`Config.wireless.NETWORK_LIMIT_PER_PLAYER`（默认 5，-1 不限） |
| 访问 | owner + ServerUtilities 同队 | 每网络三档：`PRIVATE`（仅所有者）/`TEAM`（所有者+同队）/`PUBLIC`（所有人）；**编辑/删除永远仅所有者** |
| 删除网络 | 拆核心即删 | 网络设置页「删除网络」（仅所有者，二次确认），删除时解绑所有仓室 |

### 9.1 数据模型（T1）

**新文件 `common/wireless/MTWirelessAccess.java`**：

```java
public enum MTWirelessAccess {
    PRIVATE, TEAM, PUBLIC;
    public static final MTWirelessAccess[] VALUES = values();
    public int id();                        // ordinal
    public static MTWirelessAccess byId(int id); // 越界 → PRIVATE
    public MTWirelessAccess next();         // 循环
    public String langKey();                // "wirelessme.gui.access.private" / ".team" / ".public"
}
```

**新文件 `common/wireless/MTWirelessColors.java`**（14 色 = Flux `EnumNetworkColor.VALUES` 原值）：

```java
public final class MTWirelessColors {
    public static final int DEFAULT = 0x295E8A;      // Flux BLUE
    public static final int[] PALETTE = { 0x295E8A, 0x343477, 0x582A72, 0x882D60, 0xAA3939, 0xAA6F39, 0xC6B900,
                                          0x609732, 0x87CEFA, 0x86608E, 0xF08080, 0xFFC0CB, 0xFFDAB9, 0xEEDC82 };
    public static int sanitize(int rgb);             // 只留低 24 位
    public static String hex(int rgb);               // "RRGGBB"，调试/显示用
}
```

**`MTWirelessSavedData`**（旧方法全部保留，新增如下；`NetworkInfo` 扩字段）：

```java
public static final int SCHEMA_VERSION = 2;
public static final int MAX_NAME_LENGTH = 32;

public static final class NetworkInfo {
    public final UUID frequency; public final String name; public final UUID owner;
    public final int color; public final MTWirelessAccess access;
    public final boolean hasCore; public final int memberCount;
    public UUID frequency(); public String name(); public UUID owner();
    public int color(); public MTWirelessAccess access();
    public boolean hasCore(); public int memberCount();
}

public UUID createNetwork(UUID owner, String name, int color, MTWirelessAccess access); // 生成 UUID，返回新 id
public boolean isNetwork(UUID frequency);            // 记录存在
public boolean deleteNetwork(UUID frequency);        // 删记录 + 清成员索引；返回是否删掉
public List<NetworkInfo> getNetworks();              // 稳定排序（名字→UUID）
public List<NetworkInfo> getNetworksOwnedBy(UUID owner);
public int countOwnedNetworks(UUID owner);

public void setCore(UUID frequency, int dim, int x, int y, int z);
public void clearCore(UUID frequency);               // 只清 core 槽，保留记录/成员
public UUID findCoreNetwork(int dim, int x, int y, int z);  // 反向查询（TE NBT 损坏时按位置认领）

public int getColor(UUID frequency);                 // 未知 → DEFAULT
public void setColor(UUID frequency, int rgb);       // sanitize 后写
public MTWirelessAccess getAccess(UUID frequency);   // 未知 → PRIVATE
public void setAccess(UUID frequency, MTWirelessAccess access);
public String getRawName(UUID frequency);            // 存储的原始名（可能空串）
```

- NBT：根加 `version`(int)=2；每个网络 tag 加 `color`(int) 与 `access`(byte)。读 v1（无 version）时
  `access = TEAM`（保留旧「所有者+同队」语义）、`color = MTWirelessColors.DEFAULT`，其余同旧格式。
  `getName` 保持「空名 → 短 UUID」的显示语义。
- 旧 `removeNetwork(UUID)` 保留（返回 owner），实现可与 `deleteNetwork` 合并。

### 9.2 Runtime（T1）

`MTWirelessNetworkRuntime` 新增/改变：

```java
public enum SelectResult { OK, NO_ACCESS, NETWORK_NOT_FOUND, CORE_IN_USE, INVALID }

public static boolean canAccess(EntityPlayer player, UUID frequency);  // 访问等级判定（v2）
public static boolean canEdit(EntityPlayer player, UUID frequency);    // 仅所有者
public static List<NetworkInfo> getAccessibleNetworks(EntityPlayer player); // 保持旧签名
public static List<NetworkInfo> getEditableNetworks(EntityPlayer player);

public static int getNetworkLimit(EntityPlayer player);   // Config，-1 = 不限
public static boolean canCreateNetwork(EntityPlayer player);
public static UUID createNetwork(EntityPlayer player, String name, int color, MTWirelessAccess access);
    // 名字 trim 后为空 / 无权限 / 达到上限 → null；成功 → 新 UUID

public static boolean deleteNetwork(EntityPlayer player, UUID frequency);
    // 仅所有者；成功时 unbind 所有成员、断链路、清记录

public static SelectResult selectNetworkForCore(EntityPlayer player, MTWirelessNetworkCoreTile core,
                                                UUID frequency);       // frequency == null → 解除核心绑定
    // 语义：canAccess 校验 → 记录存在 → 记录 core 槽为空或就是本核心（否则 CORE_IN_USE）
    //       → 旧网络 clearCore + disconnectFrequency → data.setCore + core.setNetworkId
    //       → requestReconnect；返回 OK
public static void detachCore(MTWirelessNetworkCoreTile core);   // 拆方块时调用：clearCore + disconnect，无权限校验
public static void onCoreReady(MTWirelessNetworkCoreTile core);  // onReady 时调用：按位置认领/登记（见 9.3）
```

维护循环（`maintain`）必须改成「无核心 ≠ 删网络」：

- 记录 `core` 槽为 `null` → 只 `dropTrackedConnections(frequency, true)`，**不清绑定、不删记录**；
- core 槽有位置但区块未加载 → `dropDeadTrackedConnections` 后跳过（绑定保留）；
- core 槽位置上的 TE 不是核心/网络 id 不符 → `data.clearCore(frequency)` + 断链路（**不再 `removeNetwork`**）；
- 其余同旧（成员逐条维护、`isWired/foreign` 口径不变）。

`bindTarget` 的 `NO_CORE` 含义改为「网络存在但没有在线核心」；`CORE_NOT_CONNECTED` 含义不变。

### 9.3 核心 TE / 方块 / 绑定工具（T2）

`MTWirelessNetworkCoreTile`（NBT 键保持 `freq`/`freqMost`/`freqLeast`，GUI/物品 NBT 另见下）：

```java
public UUID getNetworkId();                 // null = 未绑定
public void setNetworkId(UUID id);          // 只改 TE NBT + markDirty（不动 SavedData）
public boolean isNetworkBound();
public String getNetworkName();             // SavedData 名/短 id；未绑定 → ""
public String getShortNetworkId();
public int getNetworkColor();               // 未绑定 → MTWirelessColors.DEFAULT
public MTWirelessAccess getNetworkAccess(); // 未绑定 → PRIVATE
public void releaseOnBreak();               // 拆方块：runtime.detachCore(this)
public ItemStack createDropStack();         // 生成本方块物品，NBT 见下（方块 breakBlock 抓取）
public void adoptFromItem(EntityPlayer player, ItemStack stack);  // 放置时从物品 NBT 认领网络（方块 onBlockPlacedBy 调）
```

- `onReady()`：先按旧逻辑从 SavedData 按位置认领丢失的 frequency；然后
  `runtime.onCoreReady(this)`（网络在且 core 槽空/是自己 → `data.setCore`；其它核心占用 → 清 TE id 变成未绑定；
  `requestReconnect`）；未绑定也允许 `onReady`。
- `setOwner`/`migrateLegacyOwner` 保持（owner 仍写到网络记录；未绑定核心不写）。
- 物品 NBT 键（核心物品，T2 定义并保持稳定）：
  `WirelessNetworkId`(String UUID)、`WirelessNetworkName`(String)、`WirelessNetworkColor`(int)、
  `WirelessNetworkAccess`(byte)。与绑定工具的 `WirelessNetworkId`/`WirelessNetworkName` 同名同格式，但
  两件物品互不影响（工具只认自己 NBT 的槽位语义即可）。
- 方块 `getDrops` + `breakBlock` 的**掉落带 NBT**办法（1.7.10 的绑定发生在 `breakBlock` 之后、`getDrops`
  之前，此时 TE 已经没了）：`breakBlock` 里先 `createDropStack()` 存进方块类的
  `static Map<PosKey, PendingDrop>`（带 `world.getTotalWorldTime()`，超过 ~40 tick 的条目在下次访问时清掉），
  再 `core.releaseOnBreak()`，再 `super.breakBlock`；`getDrops` 命中同维同坐标的 pending 条目 → 返回单元素
  列表（并移除），否则走 `super.getDrops`。`canSilkHarvest` 保持默认 false（否则精准采集路径绕过 getDrops）。
  未绑定核心掉落普通物品（无 NBT）。
- `onBlockPlacedBy`：`setOwner` + `adoptFromItem`（服务端）。认领失败（网络不存在/无权限/已有核心）时核心保持
  未绑定并给玩家聊天提示。
- 绑定工具：Shift+右键**未绑定**核心 → 新键 `wirelessme.chat.core_no_network`；其余交互不变；
  `refreshCachedName` 的判据从 `data.hasCore(id)` 改成「记录存在（`data.getName(id) != null`）」，因为网络可能
  暂时没有核心。tooltip 追加访问等级（新键 `wirelessme.tooltip.tool.access`）。

### 9.4 GUI（T3 核心 GUI + `MTWirelessGuiStyle`；T4 picker/仓室页签）

**资产**（Lead 复制，来源 Flux Networks 1.20，MIT License，见 §9.8 末尾许可说明）：

```
src/main/resources/assets/messtech/textures/gui/wireless/gui_background.png   (688x688, 圆角深色底板)
src/main/resources/assets/messtech/textures/gui/wireless/gui_frame.png        (688x688, 白色圆角边框环)
src/main/resources/assets/messtech/textures/gui/wireless/gui_icon.png         (512x512, 图标表)
```

`gui_icon.png` 里用到的子区（原图像素坐标，`UITexture.builder().location(...).imageSize(512,512).subAreaXYWH(...)`）：

| 用途 | 子区 |
| --- | --- |
| 页签图标 i（Flux `EnumNavigationTab` 顺序：0=主页,1=网络选择,2=无线充能,3=连接,4=统计,5=成员,6=设置,7=创建） | `(64*i, 192, 64, 64)` → 画 16x16 |
| 网络名称栏 | `(0, 320, 270, 24)` → 画 135x12 |
| 网络列表行条 | `(0, 352, 292, 24)` → 画 146x12 |
| 锁图标（已有核心/无权限） | `(384, 256, 64, 64)` → 画 10x10 |

**面板几何**：Flux 内容区 172x172；页签在内容区上方 16 高。MUI2 里用「面板 172x194 + 内容区背景画在
y=18..190」的做法（避免子 widget 超出面板被裁剪）：`ModularPanel` 关闭默认主题背景，子 widget 在 y=18 处画
`gui_background.png` + 动态 tint 的 `gui_frame.png`（172x172），页签行在 y=0..16。关闭按钮放右上角。

**视觉规则**：

- 面板底板 = `gui_background.png` 拉伸到 172x172；边框 = `gui_frame.png` 拉伸到 172x172、`withColorOverride`
  当前网络色（0xFF000000 | rgb）；未绑定核心用 `MTWirelessColors.DEFAULT`。
- 页签图标：选中 = 白色 tint，可点但未选中 = 0.7 灰，不可用 = 0.5 灰；悬停 tooltip 显示页签名。
- 网络名称栏/列表行条 = 对应图标子区，tint 网络色；行文字白色（当前行）或 0x606060（其它）。
- 文本统一白色/浅灰；`§` 颜色只写在 lang 里。

**页签与内容**（T3 核心 GUI；`PagedWidget` + `PagedWidget.Controller`）：

| 页签 | tabIndex | 内容 |
| --- | --- | --- |
| 主页 HOME | 0 | 网络栏（名字，点击→网络选择页）；ME 接入状态；绑定仓室数/已连接数；网络 ID；访问等级；未绑定时显示「未选择网络」+ 提示；「解除绑定」按钮 |
| 网络选择 SELECTION | 1 | 至多 8 行可访问网络（行条+名字+`§a当前`/`§c已有核心`）；左键 `selectNetworkForCore`；底部「还有 N 个未显示」 |
| 已绑定仓室 MEMBERS | 2 | 至多 8 行成员（`维度 %s：%s, %s, %s` + `§a已连接`/`§e等待核心`）；顶部合计 |
| 网络设置 SETTINGS | 3 | 名称输入框+保存；14 色色板；访问等级循环按钮；删除网络（两次点击确认）；非所有者只读提示 |
| 创建新网络 CREATE | 4 | 名称输入框（默认 `%s的网络`）+14 色色板+访问等级循环按钮+创建按钮（名称空/达上限时禁用）；成功后服务端建网+自动把核心绑定到新网络，客户端切回主页；底部达上限提示 |

**同步/动作契约**（MUI2，T3 定 key 名，T4 各自独立）：

- 值：`StringSyncValue`/`IntSyncValue`/`BooleanSyncValue` 用 (clientGetter, clientSetter, serverGetter,
  serverSetter) 形式；服务端 getter 只读 SavedData/TE；固定 8 行，行名/色/访问/是否当前/是否已有核心逐行同步。
- 动作：`registerSyncedAction(Side.SERVER, ...)`；服务端动作里一律 `ServerThreadUtil.addScheduledTask` 切回
  服务端线程（照抄现有 `MTWirelessCoreGui`/`MTWirelessNetworkPickerGui` 写法）；客户端只发 `int row`/字符串/
  颜色值，服务端重读列表并忽略越界。
- 页签切换只发生在客户端（`controller.setPage`）；服务端动作只改数据。

**`common/wireless/gui/MTWirelessGuiStyle.java`**（T3 新建，**T4 只读消费，不得修改**；T4 需要的都可以找 T3/Lead 加）：

```java
public final class MTWirelessGuiStyle {
    public static final int CONTENT_SIZE = 172;
    public static final int TAB_ROW_HEIGHT = 16;
    public static final int PANEL_WIDTH = 172;
    public static final int PANEL_HEIGHT = 194;
    public static final int TAB_HOME = 0, TAB_SELECTION = 1, TAB_MEMBERS = 2, TAB_SETTINGS = 3, TAB_CREATE = 4;
    public static final int[] PALETTE;                    // = MTWirelessColors.PALETTE
    public static IDrawable background();                 // 深色圆角底板（172x172）
    public static IDrawable frame(int rgb);               // 网络色 tint 的圆角边框（172x172）
    public static IDrawable networkBar(int rgb);          // 135x12 名称栏
    public static IDrawable rowBar(int rgb);              // 146x12 行条
    public static IDrawable lockIcon();                   // 10x10
    public static IDrawable tabIcon(int tabIndex, float shade);  // 16x16，shade=1/0.7/0.5
    public static IDrawable paletteSwatch(int rgb);       // 10x10 色块
    public static int panelBackgroundColor(int rgb);      // 可选：文字/装饰用
}
```

**绑定工具 picker（T4）**：Flux 风格 176x180 面板（同一背景/边框/行条）；标题、至多 8 个可访问网络行（tint
网络色，已保存行显示 `§a已保存`），左键保存到工具；「清除」「关闭」按钮；边框色 = 工具已保存网络的颜色
（没有则 DEFAULT）。保留 `SimpleGuiFactory`/`MTWirelessToolLocator` 语义不变。

**仓室无线页签（T4）**：左侧 24x24 页签按钮改用 `gui_icon.png` 的无线充能图标（tabIndex=2，画 16x16 居中）；
点开的网络列表弹窗改成 Flux 风格 176x172（同一背景/边框/行条），行 tint 网络色，语义（左键连接/断开、已连接
`§a已连接`、有线锁、core 不在线）不变；行的网络**没有核心**时（`NetworkInfo.hasCore() == false`，此时绑定只会
记成待连接）追加后缀 `§e等待核心`（键 `wirelessme.tab.waiting_core`）。注意：`§c已有核心` 只属于核心 GUI 的
网络选择页，不要在仓室页签上表示「已有核心」。

### 9.5 语言键（Lead 写入两个 lang 文件；代码只能使用下列 key）

`wirelessme.tooltip.*`（在旧列表基础上改/加）：

```
wirelessme.tooltip.core.0=§7贴着 ME 线缆或 ME 方块放置，朝向面接入 ME 网络
wirelessme.tooltip.core.1=§7右键打开界面：选择网络或创建新网络；扳手旋转连接面
wirelessme.tooltip.core.2=§7拆下后物品会记住网络，重新放置自动接回
wirelessme.tooltip.core.usage=§7用绑定工具 Shift+右键保存网络，再右击 ME 仓室完成绑定
wirelessme.tooltip.core.bound=§7网络：%s
wirelessme.tooltip.core.unbound=§e未选择网络
wirelessme.tooltip.tool.access=§7访问等级：%s
```

`wirelessme.chat.*` 新增（旧键保留）：

```
wirelessme.chat.network_created=已创建无线ME网络：%s
wirelessme.chat.network_deleted=已删除无线ME网络：%s
wirelessme.chat.network_limit=已达到可创建网络数量上限（%s）
wirelessme.chat.network_selected=已将核心绑定到网络：%s
wirelessme.chat.network_unselected=已解除核心与网络的绑定
wirelessme.chat.core_no_network=此核心尚未选择网络
wirelessme.chat.network_missing=这个无线ME网络已经不存在
wirelessme.chat.core_in_use=这个网络已经有一个核心了
wirelessme.chat.only_owner=只有网络所有者可以执行此操作
wirelessme.chat.name_empty=网络名称不能为空
```

`wirelessme.gui.*` 新增（旧键保留，冲突时以新语义为准）：

```
wirelessme.gui.tab.home=主页
wirelessme.gui.tab.selection=网络选择
wirelessme.gui.tab.members=已绑定仓室
wirelessme.gui.tab.settings=网络设置
wirelessme.gui.tab.create=创建新网络
wirelessme.gui.current=当前网络：%s
wirelessme.gui.current.none=未选择网络
wirelessme.gui.more=还有 %s 个未显示
wirelessme.gui.close=关闭
wirelessme.gui.cancel=取消
wirelessme.gui.access.private=私有
wirelessme.gui.access.team=团队
wirelessme.gui.access.public=公开
wirelessme.gui.access.private.desc=§7仅所有者可用
wirelessme.gui.access.team.desc=§7所有者与同队玩家可用
wirelessme.gui.access.public.desc=§7所有人可用
wirelessme.gui.home.title=无线ME网络核心
wirelessme.gui.home.unbound=§e未选择网络
wirelessme.gui.home.unbound.hint=§7在「网络选择」里选一个网络，或到「创建新网络」新建
wirelessme.gui.home.linked=§aME 网络：已接入
wirelessme.gui.home.unlinked=§cME 网络：未接入
wirelessme.gui.home.members=绑定仓室：%s
wirelessme.gui.home.connected=已连接：%s
wirelessme.gui.home.network_id=网络 ID：%s
wirelessme.gui.home.access=访问等级：%s
wirelessme.gui.home.detach=解除绑定
wirelessme.gui.selection.title=选择网络
wirelessme.gui.selection.empty=§7还没有可访问的网络
wirelessme.gui.selection.empty_hint=§7点击顶部的「创建新网络」
wirelessme.gui.selection.current=§a当前
wirelessme.gui.selection.core_in_use=§c已有核心
wirelessme.gui.members.title=已绑定仓室
wirelessme.gui.members.total=共 %s 个，已连接 %s 个
wirelessme.gui.members.entry=维度 %s：%s, %s, %s
wirelessme.gui.members.connected=§a已连接
wirelessme.gui.members.pending=§e等待核心
wirelessme.gui.members.empty=§7这个网络还没有绑定仓室
wirelessme.gui.settings.title=网络设置
wirelessme.gui.settings.name=名称
wirelessme.gui.settings.save=保存
wirelessme.gui.settings.color=颜色
wirelessme.gui.settings.access=访问等级
wirelessme.gui.settings.delete=删除网络
wirelessme.gui.settings.delete_confirm=§c再次点击确认删除
wirelessme.gui.settings.readonly=§7只有网络所有者可以修改这些设置
wirelessme.gui.create.title=创建新网络
wirelessme.gui.create.name=名称
wirelessme.gui.create.default_name=%s的网络
wirelessme.gui.create.color=颜色
wirelessme.gui.create.access=访问等级
wirelessme.gui.create.button=创建
wirelessme.gui.create.limit=§c已达到可创建网络上限（%s）
wirelessme.gui.create.name_empty=§c名称不能为空
wirelessme.gui.picker.hint=§7左键选择一个网络保存到绑定工具
wirelessme.tab.waiting_core=§e等待核心
```

`wirelessme.gui.picker.*` / `wirelessme.tab.*` 其余旧键保留。

配置 lang（GTNHLib `@LangKeyPattern`，字段名小写；`Config.wireless` 由 Lead 加）：

```
messtech.config.wireless=无线ME网络
messtech.config.wireless.network_limit_per_player=每人可创建网络数
messtech.config.wireless.network_limit_per_player.tooltip=每个玩家最多能创建多少个无线ME网络；-1 表示不限制。达到上限后仍可使用别人分享给你的网络。
```

### 9.6 配置（Lead）

`Config` 新增 `@Order(4) public static final Wireless wireless = new Wireless();`：

```java
public static class Wireless {
    @Comment({ "Maximum number of wireless ME networks one player may create (-1 = no limit).",
               "Reaching the limit does not stop the player from using networks shared with them." })
    @DefaultInt(5) @RangeInt(min = -1, max = 1000)
    public int NETWORK_LIMIT_PER_PLAYER = 5;
}
```

### 9.7 文件所有权（v2；取代 §3）

| 角色 | 写作用域 |
| --- | --- |
| **Lead** | 本文件、`docs/knowledge.md` 索引（如需）、`init/Config.java`、`resources/assets/messtech/lang/{zh_CN,en_US}.lang`、`resources/assets/messtech/textures/gui/wireless/**`、`common/wireless/MTWirelessContent.java`（如需）、最终 `spotlessApply`/`compileJava`/`build` |
| **T1 network-data** | `common/wireless/MTWirelessSavedData.java`、`MTWirelessNetworkRuntime.java`、新 `MTWirelessAccess.java`、新 `MTWirelessColors.java` |
| **T2 core-tool** | `common/wireless/MTWirelessNetworkCoreTile.java`、`MTWirelessNetworkCoreBlock.java`、`MTWirelessNetworkCoreItemBlock.java`、`MTWirelessNetworkBindingTool.java`、`MTWirelessToolEvents.java`、`MTWirelessToolLocator.java` |
| **T3 gui-kit-core** | 新 `common/wireless/gui/MTWirelessGuiStyle.java`、`common/wireless/gui/MTWirelessCoreGui.java` |
| **T4 gui-picker-tab**（依赖 T3 的 style 文件） | `common/wireless/gui/MTWirelessNetworkPickerGui.java`、`common/wireless/gui/MTWirelessHatchTab.java` |
| 不动 | `MTWirelessMemberKey`、`MTWirelessTeamAccess`、`MTWirelessBaubles`、`MTWirelessContent`、`common/network/OpenWirelessPickerPacket`、三个 wireless mixin、`MTWirelessRecipes`、`MTItemList`、`lang`、`docs` |

规则：只改自己作用域；**不要运行 gradle**（编译/格式化由 Lead 统一跑）；不要新增 `tools/**` 校验器；不要
`git commit`；需要改契约先发消息给 Lead。

### 9.8 Flux Networks 1.20 功能移植建议（哪些抄、哪些不抄）

| Flux Networks 功能 | 本模组结论 | 理由 / 落点 |
| --- | --- | --- |
| 网络是独立存档对象（`FluxNetworkData` SavedData） | **抄** | §9.1；拆设备不丢网络是这次要修的核心问题 |
| 「创建新网络」页（名称+颜色+安全等级+创建按钮） | **抄** | §9.4 CREATE 页；只保留名称/颜色/访问等级，不做能量类型/密码 |
| 网络选择页（列表+当前高亮+tooltip ID/安全/访问） | **抄** | §9.4 SELECTION 页、绑定工具 picker、仓室页签 |
| 主页顶部网络色名称栏（点击进选择页） | **抄** | `renderNetwork` 的 1.7.10 对应物 |
| 14 色 `EnumNetworkColor` | **抄**（原值） | `MTWirelessColors.PALETTE`；原因：Flux 贴图边框是白色 tint，色板原值才能还原观感 |
| 每人网络数上限 `maximumPerPlayer`（默认 5，-1 不限） | **抄** | `Config.wireless.NETWORK_LIMIT_PER_PLAYER` |
| 安全等级 `SecurityLevel`（Public/Encrypted/Private）+ 访问等级成员表 | **裁剪为三档访问** | 1.7.10 用 `PRIVATE/TEAM/PUBLIC`：TEAM 保持旧 ServerUtilities 同队语义；**不抄** 加密密码和成员/权限组（需要额外 UI、密码存储与校验，收益低） |
| 删除网络（仅所有者，二次确认） | **抄** | §9.4 SETTINGS 页；没有删除的网络列表会无限膨胀 |
| 连接/成员列表页（Connections/Members） | **抄其形、不抄其管理** | 只做「已绑定仓室」只读列表（坐标/维度/连接状态）；不抄成员权限管理 |
| 统计页（能量输入输出/优先级/传输上限/超载） | **不抄** | 无线ME网络不传能量；等价物是「绑定数/已连接数」，已放到主页 |
| 能量存储/接入点/输入点/控制器/无线充能 | **不抄** | 不同玩法（AE2 网格 vs FE 能量） |
| Chunk loading / 优先级 / 上限绕过 | **不抄** | 无线链路不加载区块（§6 旧约定） |
| 网络文件夹、按 ID/名称排序、批量编辑 | **不抄（可后续）** | 需要多网络大量管理才用得上 |
| 管理员网络配置器 / 超级管理员 | **不抄** | 只有所有者可编辑/删除已够用 |
| Flux 的 GUI 贴图与配色（背景/边框/图标表） | **抄贴图**（MIT） | 见下方许可；只作纯客户端贴图使用 |
| Flux 的按钮音效 / 悬停 tooltip | **可后续** | 需要注册音效，本次不引 |
| Jade/TOP 信息、ME 管理终端、无线终端 | **不抄** | 超出本次范围 |

**许可说明**：Flux Networks 1.20 采用 MIT License（`tmp/Flux-Networks-1.20/LICENSE`，Copyright (c) 2017-2021
SonarSonic, BloCamLimb）。本次只复制 `gui_background.png` / `gui_frame.png` / `gui_icon.png` 三张纯客户端贴图到
`assets/messtech/textures/gui/wireless/`，并保留本段版权声明；不复制其代码。

### 9.9 验证清单（Lead）

1. `./gradlew spotlessApply compileJava` 通过；交接时跑 `build`。
2. 旧存档迁移：进存档后旧网络显示默认色/团队访问；旧核心仍在线、仓室仍连接。
3. 新流程：放核心（未绑定）→ 创建网络（名称/色/访问）→ 自动绑定 → 绑仓室 → 拆核心（网络与绑定保留）→
   重新放置（自动接回、仓室重连）→ 删除网络（仓室解绑）。
4. 权限：别人（非所有者/非同队）看不到私有网络、不能编辑/删除；TEAM 档同队可见；PUBLIC 所有人可见。
5. 上限：创建到 `NETWORK_LIMIT_PER_PLAYER` 后创建页禁用并提示。
6. 跨维度、区块卸载重载、重启服务器后仍持久。

### 9.10 落地记录（2026-11-24）

实现文件（全部在 §9.7 写作用域内）：

| 层 | 文件 |
| --- | --- |
| 数据（T1） | `common/wireless/MTWirelessAccess.java`（新）、`MTWirelessColors.java`（新）、`MTWirelessSavedData.java`（v2）、`MTWirelessNetworkRuntime.java`（v2） |
| 核心/工具（T2） | `MTWirelessNetworkCoreTile.java`、`MTWirelessNetworkCoreBlock.java`、`MTWirelessNetworkCoreItemBlock.java`、`MTWirelessNetworkBindingTool.java`、`MTWirelessToolEvents.java`、`MTWirelessToolLocator.java` |
| GUI（T3/T4） | `common/wireless/gui/MTWirelessGuiStyle.java`（新）、`MTWirelessCoreGui.java`（重写）、`MTWirelessNetworkPickerGui.java`（重写）、`MTWirelessHatchTab.java`（重写） |
| 接线/资源/配置（Lead） | `init/Config.java`（`Config.wireless.NETWORK_LIMIT_PER_PLAYER=5`）、两个 lang 文件、`assets/messtech/textures/gui/wireless/{gui_background,gui_frame,gui_icon}.png` + `LICENSE-FluxNetworks.txt`（MIT） |

与 §9 契约的偏差（均为落地必需，已确认）：

1. **T1**：`selectNetworkForCore(..., null)`（GUI 解除绑定）先校验旧网络的 `canAccess`（NO_ACCESS 时不解绑）；
   `deleteNetwork` 额外把在线核心的 TE id 清空并清 `RECONNECT_REQUESTS`/`RETRY_AT_TICK`；`detachCore` 在 TE id
   为 null 时按位置 `findCoreNetwork` 反查；`onCoreReady` 自身也按位置认领（与 T2 的 `adoptNetworkFromSavedData`
   幂等）；`getRawName(未知网络)` 返回 `""`；`NetworkInfo` 保留旧 5 参构造（源码兼容，默认 DEFAULT/TEAM）。
2. **T2**：工具 NBT 新增 `WirelessNetworkAccess`(byte) 缓存（客户端 tooltip 显示访问等级，SavedData 客户端读不到）；
   未绑定核心不再懒生成 UUID/不写 id；删除旧 TE 方法 `getFrequency()`/`getShortFrequency()`/`removeNetwork()`
   （换成 `getNetworkId`/`getShortNetworkId`/`releaseOnBreak`）。
3. **Lead 集成补丁（T2 文件）**：
   - **爆炸掉落**：1.7.10 `Explosion` 先 `dropBlockAsItemWithChance`（`getDrops`）再 `onBlockExploded`（`breakBlock`），
     PENDING_DROPS 还来不及写入。`MTWirelessNetworkCoreBlock#getDrops` 增加分支：pending 为空但该位置 TE 仍在
     且是核心 → 直接 `createDropStack()` 返回（`breakBlock` 随后写的快照永不被取走、40 tick 过期）。
   - **未绑定核心的权限**：未绑定核心没有网络记录可查，若一律放行，任何玩家都能替别人的核心选网。TE 现在把放置者
     记进 NBT `placedBy`（UUID 字符串），`canAccess` 在未绑定时仅放行放置者（旧存档/方块放置器没有 `placedBy`
     时仍放行，保持兼容）；绑定后仍按网络访问等级判定。
   - **实机崩溃修复（2026-11-24，第一轮实测）**：设置页的 `rawNameSync.changeListener` 会**在服务端**
     `GuiManager.open → detectAndSendChanges` 时被调用（MUI2 两侧都构建面板），此时 `TextFieldWidget` 还没挂到
     屏幕（`isValid()==false`），`isFocused()` 抛 `IllegalStateException: TextFieldWidget is not in a valid state!`
     导致服务端崩溃。修复：监听器只调用 `seedNameField(nameField)`，函数先 `!nameField.isValid()` 直接返回、再
     `isFocused()`；另注册一个 `syncManager.onClientTick` 兜底，在面板 attach 后补一次名字（首包可能早于 widget
     attach）。**教训**：GUI 的 sync getter/listener 在服务端也会跑，任何需要 GUI context 的 widget 方法
     （`isFocused/getContext/getScreen/getPanel/isHovering`）必须先 `isValid()` 守卫或只放在 `draw`/点击回调里。
4. **T3**：内容区 172x172 + 顶部 16 页签行，面板 172x194、内容区画在 y=18；`MTWirelessGuiStyle` 只加不改
   （新增 `panel/dynamicPanel/lockIcon(float)/tabSprite/paletteSwatchSelected/argb/shade/shadeColor/
   panelBackgroundColor/buttonFrame/dynamicButtonFrame`）；主页网络 ID 显示短 8 位（完整 UUID 36 字符放不下）；
   未绑定时 MEMBERS/SETTINGS 页签置灰；删除网络二次确认只在客户端（切页签不重置）；非所有者设置页输入框仍可输入但
   所有动作服务端 `canEdit` 校验并提示 `wirelessme.chat.only_owner`；网络被删时 clientTick 自动回主页。
5. **T4**：picker 面板 176x180、仓室弹窗 176x172（背景/边框 172x172 居中）；`hasCore()==false` 的仓室行后缀
   `wirelessme.tab.waiting_core`（不是 `core_in_use`，后者已弃用）；picker 的 `清除/关闭`、仓室的 `断开/关闭` 改用
   T3 同款 `buttonFrame` Flux 按钮（不可用时灰显不隐藏）；旧 picker 的「一行一个名字的 `\n` 拼接同步」改为逐行
   name+color，仍然固定 8 行；仓室弹窗的锁图标在「有线锁」状态行左侧额外装饰。
6. **资源来源**：三张 GUI 贴图来自 Flux Networks 1.20（MIT，见 `assets/messtech/textures/gui/wireless/
   LICENSE-FluxNetworks.txt` 与 §9.8 末尾），只作纯客户端贴图；Flux 代码未复制。

**验证（本次能做的部分）**：`gradlew spotlessApply build` 通过（编译/格式化/checkstyle/reobf）；游戏内手工测试
按 §9.9 清单（迁移、建网/选网/拆放核心/爆炸拆核心、跨维度、删除、三档权限、上限、重启）**尚未执行**，交给用户实机
验证。GUI 布局常量集中在 `MTWirelessGuiStyle` 与两个 GUI 文件顶部，首帧位置/尺寸如需微调改常量即可。
