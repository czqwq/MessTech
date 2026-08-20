# Space Beacon &mdash; Design & Architecture Documentation

## Overview

The **Space Beacon** is a massive multi-block communication and power relay station for the MessTech mod. It acts as a central hub for up to four specialized modules, receiving power from an external energy network (like the Space Elevator) and distributing it to connected modules.

This document covers the design process, architectural decisions, internal mechanics, and extension guide.

---

## 1. Architecture

### 1.1 Class Hierarchy

```
MTEExtendedPowerMultiBlockBase  (GregTech)
    └── MEGAMultiMachineBase<T>  (MessTech common base)
            ├── SpaceBeacon      (controller)
            └── SpaceBeaconModuleBase  (module base class)
                    └── [Future: ConcreteModuleA, ConcreteModuleB, ...]

SpaceBeaconUtil  (static helpers, enums)
```

### 1.2 Why NOT reuse Space Elevator's module base

The Space Elevator (`TileEntityModuleBase`) extends `TTMultiblockBase` (TecTech) and uses a TecTech-specific parameter system (`Parameters.Group.ParameterOut`, `IStatusFunction`, etc.). The Space Beacon targets a simpler architecture using MessTech's own `MEGAMultiMachineBase` which extends standard GregTech's `MTEExtendedPowerMultiBlockBase`.

**Key isolation principles:**
- `SpaceBeaconModuleBase` does NOT extend `TileEntityModuleBase`
- `SpaceBeaconUtil.BeaconModuleElement` is separate from `ElevatorUtil.ProjectModuleElement`
- Module detection uses `instanceof SpaceBeaconModuleBase`, so elevator modules will NOT be recognized by the beacon
- Conversely, beacon modules placed in an elevator will NOT be recognized (they won't pass `instanceof TileEntityModuleBase`)

This ensures clean separation: the Space Elevator and Space Beacon are independent systems.

---

## 2. Module System

### 2.1 Module Slots

Four designated positions around the controller (represented by `L` blocks in the structure definition). These are at the corners of the central platform:

```
       [L]         [L]
           \       /
            CONTROLLER
           /       \
       [L]         [L]
```

Modules **must** be placed at these exact positions. The beacon detects them during structure checking using `HatchElementBuilder` with `BeaconModuleElement.BeaconModule`.

### 2.2 Acceleration Module Tiers

Acceleration modules (tiered motor blocks, represented by `F` in the structure) determine how many module slots are available:

| Tier  | Slots Unlocked | Wireless Mode | Upgrade Modules |
|-------|---------------|---------------|-----------------|
| Mk-I  | 1             | No            | No              |
| Mk-II | 2             | No            | No              |
| Mk-III| 3             | No            | No              |
| Mk-IV | 4 (all)       | No            | No              |
| Mk-V  | 4 (all)       | **Yes**       | **Yes**         |

The tier is determined during structure checking via `StructureUtility.ofBlocksTiered()` with `SpaceBeaconUtil.accelerationTierConverter()`, using the GT Structure Channels system built into GregTech (`GTStructureChannels.SE_MOTOR`).

### 2.3 Power Distribution

Every 20 ticks (`MODULE_CHARGE_INTERVAL`), the beacon evenly distributes its internal EU buffer among all connected modules:

```java
long totalEnergy = getEUVar();
long energyPerModule = totalEnergy / activeModules * MODULE_CHARGE_INTERVAL;
```

Each module receives energy via `increaseStoredEU()`. The module's internal processing draws from this local buffer.

### 2.4 Wireless Mode (Mk-V)

Unlocked with a Mk-V acceleration module. Toggled by right-clicking the controller with a **screwdriver**.

In wireless mode:
- Modules operate with enhanced efficiency
- Upgrade modules become available (slots unlocked)
- The beacon's `getWirelessModeEnabled()` returns `true`, allowing modules to check this in their processing logic

---

## 3. Structure Definition

### 3.1 Structure Format

The structure is defined in the `STRUCTURE_DEFINITION` static field using **StructureLib**:

```java
StructureDefinition.<SpaceBeacon>builder()
    .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(new String[][]{...}))
    .addElement('A', StructureUtility.ofBlock(...))
    .addElement('L', HatchElementBuilder...buildAndChain(...))
    .build();
```

The structure is a **single-piece** definition (unlike the Space Elevator which has `main` + `extended` pieces). It is 35&times;35 blocks wide and 55 blocks tall.

### 3.2 Character Mapping

| Char | Block | Description |
|------|-------|-------------|
| `A`  | `sBlockCasingsBA0:7` | Outer shell casing |
| `B`  | `sBlockCasingsBA0:8` | Inner shell casing |
| `C`  | `sBlockCasingsSE:0` | Base casing (center) |
| `D`  | `sBlockCasingsSE:1` | Support structure |
| `E`  | `sBlockCasingsSE:2` | Internal structure |
| `F`  | `sBlockCasingsSEMotor` (tiered) | Acceleration module |
| `G,H`| Neutronium frame boxes | Structural frame |
| `I`  | `sBlockCasingsDyson:9` | Cosmetic floor |
| `J,K`| Dirt / Grass | Terrain integration |
| `L`  | `BeaconModuleElement` | Module slot |
| `M`  | `sBlockCasingsSE:0` | Controller position |

### 3.3 Structure Checking Flow

```
checkMachine()
  ├── clear mModuleHatches, reset accelerationTier
  ├── checkPiece(STRUCTURE_PIECE_MAIN, ...)
  │   ├── for each position in the 35x35x55 volume:
  │   │   ├── match character in definition
  │   │   ├── call element.check(t, world, x, y, z)
  │   │   │   ├── 'F': read block tier -> setAccelerationTier()
  │   │   │   ├── 'L': hatch builder -> addModuleToMachineList()
  │   │   │   ├── other: standard block check
  │   │   │   └── ...
  │   │   └── collect errors
  │   └── return success/fail
  └── validate module count vs unlocked slots
```

### 3.4 Structure Printing

#### Debug Structure Printer (Export)

The **Debug Structure Printer** is a development tool used to export a built multi-block structure into a `new String[][]` format (like `structure.txt`). This is how the original Space Elevator structure was extracted and how the Space Beacon structure blueprint was generated.

To use it:
1. Build the multi-block in a test world
2. Use the Debug Structure Printer on the controller
3. It scans the entire structure and outputs the `new String[][]` definition
4. Copy the output into your Java source or a `structure.txt` file

This tool is essential for converting a creatively-built structure into a code-verifiable definition.

#### Hologram Projector (Build)

The `construct()` and `survivalConstruct()` methods use StructureLib's Hologram Projector for in-game building:

- `construct(stackSize, hintsOnly)` &mdash; Called when the player uses a Hologram Projector. Places the entire structure in the world as a hologram or with real blocks.
- `survivalConstruct(stackSize, budget, env)` &mdash; Auto-building with item consumption. Each call places one "element budget" worth of blocks from the player's inventory.

---

## 4. Controller Behavior

### 4.1 Tick Cycle

```
onPostTick()
  ├── if server side and allowed to work:
  │   ├── every 20 ticks: distributePowerToModules()
  │   └── manage module connections (connect/disconnect based on tier)
  └── if not allowed to work:
      └── disconnect all modules
```

### 4.2 Recipe Check

The beacon does not process recipes. Its `checkProcessing()` returns `SUCCESSFUL` as long as the machine is allowed to work, keeping it in an "active" state so modules can run. In the future, this could be extended to require a minimum power threshold.

---

## 5. Module Design Guide (How to Extend)

### 5.1 Creating a New Module

1. **Extend `SpaceBeaconModuleBase`**:
   ```java
   public class SpaceBeaconModuleScanner extends SpaceBeaconModuleBase {
       public SpaceBeaconModuleScanner(int aID, String aName, String aNameRegional) {
           super(aID, aName, aNameRegional,
               /* moduleTier */ 6,       // voltage tier (IV = 6)
               /* moduleTechTier */ 1,   // tech tier (basic)
               /* minAccelTier */ 1);    // needs Mk-I+
       }
       
       @Override
       protected ProcessingLogic createProcessingLogic() {
           return new MEGAProcessingLogic() { /* your logic */ };
       }
   }
   ```

2. **Register with GT MetaTileEntity registry** in your mod init.

3. **Place at `L` position** in the Space Beacon structure.

### 5.2 Module Design Principles

- **Tier Requirements**: Each module declares its `minAccelTier` — the beacon will only connect it if the installed acceleration module meets or exceeds this tier.
- **Power Independence**: Modules receive power from the beacon but manage their own recipe processing independently.
- **Structure**: Each module has a compact 3&times;3&times;3 structure. Customize `MODULE_STRUCTURE` in your subclass if needed.
- **Processing Logic**: Override `createProcessingLogic()` to define custom recipe handling.

### 5.3 Wireless Mode Extensions

Modules can check `parentBeacon.isWirelessModeEnabled()` in their processing logic to provide enhanced behavior:

```java
@Override
protected CheckRecipeResult checkProcessing() {
    if (parentBeacon != null && parentBeacon.isWirelessModeEnabled()) {
        // Enhanced wireless processing: halved EU cost, doubled speed
        this.euModifier = 0.5f;
        this.speedBonus = 2.0f;
    }
    return super.checkProcessing();
}
```

### 5.4 Adding Upgrade Modules

Upgrade modules (Mk-V only) are placed at the same `L` positions. They differ in:
- They implement an `ISpaceBeaconUpgrade` interface (future)
- They don't process recipes but modify beacon/module behavior
- Example: `RangeExtenderUpgrade` increases wireless range, `EfficiencyUpgrade` reduces EU consumption

---

## 6. Key Differences from Space Elevator

| Feature | Space Elevator | Space Beacon |
|---------|---------------|-------------|
| Base class | `TTMultiblockBase` | `MEGAMultiMachineBase` |
| Module base | `TileEntityModuleBase` | `SpaceBeaconModuleBase` |
| Structure pieces | 2 (main + extended) | 1 (main) |
| Module positions | 6&ndash;24 (based on tier) | 4 (fixed) |
| Motor tier system | 5 tiers (T1&ndash;T5) | 5 tiers (Mk-I&ndash;Mk-V) |
| Wireless mode | N/A (not implemented) | Mk-V screwdriver toggle |
| Power source | Elevator's internal buffer | Elevator external + internal buffer |
| Recipe processing | No (passive energy relay) | No (power distribution hub) |
| Module processing | Modules run recipes | Modules run recipes |

---

## 7. Future Enhancements

- **Upgrade Modules**: Special modules at Mk-V that modify beacon behavior (range, efficiency, parallel)
- **Beacon Network**: Multiple beacons linking together for extended range
- **Visual Effects**: Beam rendering from beacon to sky (like a real lighthouse/beacon)
- **Recipe System**: Allow the beacon itself to execute "transmission" recipes for inter-base item/fluid transport
- **Config Integration**: Add all beacon parameters to `Config.java` for server-adjustable tuning

---

## 8. File Index

| File | Location |
|------|----------|
| SpaceBeacon (controller) | `src/main/java/com/MessTech/common/machine/beacon/SpaceBeacon.java` |
| SpaceBeaconModuleBase | `src/main/java/com/MessTech/common/machine/beacon/SpaceBeaconModuleBase.java` |
| SpaceBeaconUtil | `src/main/java/com/MessTech/common/machine/beacon/SpaceBeaconUtil.java` |
| Structure blueprint | `tmp/structure.txt` (source) |
| Base machine class | `src/main/java/com/MessTech/common/machine/Base/MEGAMultiMachineBase.java` |

---

## 9. Build Instructions

After implementing the Space Beacon or any of its modules, verify compilation with:

```bash
./gradlew compileJava
```

Fix any compilation errors before proceeding. The GTNH convention plugin and StructureLib integration require that all StructureLib-dependent classes properly implement `IConstructable`/`ISurvivalConstructable` and override `getStructureDefinition()`.
