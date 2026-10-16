#!/usr/bin/env python3
"""Generate and validate docs/machining-center/machine-structure.json.

The authoritative prose spec is machine-spec.md, which is itself rendered from
this file by render_spec.py. This generator is the mechanical reading of the
design in design.md, plus the self-check rules.

Usage:
    python docs/machining-center/generate_structure.py            # write + verify
    python docs/machining-center/generate_structure.py --check     # verify only
    python docs/machining-center/generate_structure.py --dump-java  # print String[][]
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, deque
from pathlib import Path

SX, SY, SZ = 19, 12, 21  # x, y, z extents
CONTROLLER = (9, 2, 20)

# --------------------------------------------------------------------------
# material palette
#
# keys: letter, block(block id used in the preview), registry(real in-game
# registry name), meta, name_en, name_zh, color, java(how the Java
# machine would reference it), role
#
# traceability: docs/machining-center/design.md section 1
# --------------------------------------------------------------------------
MATERIALS = {
    "concrete_light": (
        "A", "gt.blockconcretes", "gt.blockconcretes", 8,
        "Light Concrete", "浅色混凝土", "#c9c4b8",
        "GregTechAPI.sBlockConcretes", "外墙填充板、柱",
    ),
    "concrete_light_smooth": (
        "B", "gt.blockconcretes", "gt.blockconcretes", 15,
        "Smooth Light Concrete", "平滑浅色混凝土", "#dcd8ce",
        "GregTechAPI.sBlockConcretes", "地坪、屋面板、女儿墙、门头",
    ),
    "concrete_dark_smooth": (
        "C", "gt.blockconcretes", "gt.blockconcretes", 7,
        "Smooth Dark Concrete", "平滑深色混凝土", "#4a4d52",
        "GregTechAPI.sBlockConcretes", "地坪压边",
    ),
    "concrete_hsc": (
        "D", "gt.blockcasings9", "gt.blockcasings9", 3,
        "Superplasticizer-Treated High Strength Concrete", "减水剂强化高强混凝土", "#8b8479",
        "Casings.SuperplasticizerTreatedHighStrengthConcrete", "地基整板、设备垫层",
    ),
    "casing_stainless": (
        "E", "gt.blockcasings4", "gt.blockcasings4", 1,
        "Clean Stainless Steel Machine Casing", "洁净不锈钢机械方块", "#b9c2cb",
        "Casings.CleanStainlessSteelMachineCasing", "加工平台、工件夹具",
    ),
    "casing_solid_steel": (
        "F", "gt.blockcasings2", "gt.blockcasings2", 0,
        "Solid Steel Machine Casing", "实心钢机械方块", "#6f7378",
        "Casings.SolidSteelMachineCasing", "结构钢柱、楣窗框、仓室挂点",
    ),
    "casing_lathe": (
        "G", "gt.blockcasings4", "gt.blockcasings4", 2,
        "Stable Titanium Machine Casing", "稳定钛机械方块（车床机壳）", "#9aa6b4",
        "Casings.StableTitaniumMachineCasing", "回转工作台、主轴、天车吊爪",
    ),
    "frame_steel": (
        "H", "GoodGenerator:radiationProtectionSteelFrame",
        "GoodGenerator:radiationProtectionSteelFrame", 0,
        "Radiation Protection Steel Frame", "防辐射钢框架", "#3f4247",
        "Casings.RadiantProofSteelFrameBox", "承重钢梁、通高角柱、天车轨道",
    ),
    "grate": (
        "I", "gt.blockcasings3", "gt.blockcasings3", 10,
        "Grate Machine Casing", "格栅机械方块", "#5c5f66",
        "Casings.GrateMachineCasing", "地沟盖板、吊装口、爬梯",
    ),
    "glass_boro": (
        "K", "bartworks:BW_TieredGlass", "bartworks:BW_TieredGlass", -1,
        "Borosilicate Glass", "硼玻璃（任意等级）", "#7fb6c9",
        "Casings.BorosilicateGlassAny", "幕墙、高窗、天窗",
    ),
    "stripe_yellow": (
        "L", "gt.blockcasings3", "gt.blockcasings3", 0,
        "Yellow Stripes Block", "黄色条纹方块", "#d8b23a",
        "Casings.YellowStripesBlockA", "安全警示",
    ),
    "hazard_explosion": (
        "M", "gt.blockcasings3", "gt.blockcasings3", 4,
        "Explosion Hazard Sign Block", "爆炸危险警示方块", "#c2703a",
        "Casings.ExplosionHazardSignBlock", "设备区四角安全桩",
    ),
    "casing_iridium_bolted": (
        "N", "bartworks:blockCasingsBW", "bartworks:bw.werkstoffblockscasing.01", 31850,
        "Bolted Iridium Casing", "螺栓铱机壳", "#5a6b76",
        "Casings.BoltedIridiumCasing", "钢柱柱脚",
    ),
    "computer_advanced": (
        "O", "tectech:gt.blockcasingsTT", "tectech:gt.blockcasingsTT", 3,
        "Advanced Computer Casing", "高级电脑机壳", "#2f3a46",
        "Casings.AdvancedComputerCasing", "工控柜、刀库、主轴箱",
    ),
    "molecular_advanced": (
        "P", "tectech:gt.blockcasingsTT", "tectech:gt.blockcasingsTT", 5,
        "Advanced Molecular Casing", "高级分子机壳", "#3e4a58",
        "Casings.AdvancedMolecularCasing", "大门门框",
    ),
    "molecular_coil": (
        "Q", "tectech:gt.blockcasingsTT", "tectech:gt.blockcasingsTT", 6,
        "Molecular Coil", "分子线圈方块", "#8a6fc0",
        "Casings.MolecularCoil", "天车滑车",
    ),
    "glass_reinforced": (
        "R", "gt.blockglass1", "gt.blockglass1", 10,
        "Reinforced Glass", "强化玻璃", "#6f9db5",
        "Casings.ReinforcedGlass", "备用高强玻璃",
    ),
    "casing_sterile": (
        "S", "gt.blockcasings9", "gt.blockcasings9", 4,
        "Sterile Water Plant Casing", "无菌水厂机壳", "#dfe6e9",
        "Casings.SterileWaterPlantCasing", "夹层办公室",
    ),
}

CORNERS = {(0, 0), (0, 19), (18, 0), (18, 19)}
FRONT_COL_X = {0, 4, 9, 13, 18}
SIDE_COL_Z = {0, 4, 9, 13, 18}
BACK_PANEL_X = {1, 2, 3, 5, 6, 7, 8, 10, 11, 12, 14, 15, 16, 17}
SIDE_PANEL_Z = {1, 2, 3, 5, 6, 7, 8, 10, 11, 12, 14, 15, 16, 17}
FRONT_GLAZE_X = {1, 2, 3, 5, 6, 7, 8, 10, 11, 12, 14, 15, 16, 17}

# glazing band of the curtain wall (y), and the mullion positions inside it
GLASS_Y = (3, 4, 5, 6, 7)
FRONT_MULLION_X = {4, 13}
SIDE_MULLION_Z = {4, 9, 13}

COLUMNS = set(CORNERS)
COLUMNS |= {(x, 19) for x in FRONT_COL_X}
COLUMNS |= {(0, z) for z in SIDE_COL_Z}
COLUMNS |= {(18, z) for z in SIDE_COL_Z}
COLUMNS |= {(x, 0) for x in (4, 9, 13)}

# gate opening in the front curtain wall: x = 8..10 at y = 2 (the controller,
# at x = 9, sits in the middle of it; the other two cells stay air)
GATE_CELLS = {(x, 2, 20) for x in (8, 9, 10)}


class Grid:
    """Sparse (x, y, z) -> material id grid."""

    def __init__(self) -> None:
        self.cells: dict[tuple[int, int, int], str] = {}
        self.controller = CONTROLLER

    def set(self, x: int, y: int, z: int, m: str) -> None:
        self.cells[(x, y, z)] = m

    def fill(self, y: int, xs, zs, m: str) -> None:
        for z in zs:
            for x in xs:
                self.set(x, y, z, m)

    def fill_xy(self, y: int, z: int, xa: int, xb: int, m: str) -> None:
        for x in range(xa, xb + 1):
            self.set(x, y, z, m)

    def fill_zy(self, y: int, x: int, za: int, zb: int, m: str) -> None:
        for z in range(za, zb + 1):
            self.set(x, y, z, m)

    def get(self, x: int, y: int, z: int) -> str | None:
        return self.cells.get((x, y, z))

    def is_air(self, x: int, y: int, z: int) -> bool:
        return (x, y, z) not in self.cells


def build() -> Grid:
    g = Grid()
    rng_x = range(SX)
    rng_z = range(SZ)

    # ---------------- y = 0 : foundation slab ---------------------------
    for z in rng_z:
        for x in rng_x:
            g.set(x, 0, z, "concrete_hsc")
    for x in rng_x:
        g.set(x, 0, 0, "concrete_dark_smooth")
        g.set(x, 0, 20, "concrete_dark_smooth")

    # ---------------- y = 1 : workshop floor ----------------------------
    for z in range(1, SZ - 1):                        # interior slab, z = 1..19
        for x in rng_x:
            g.set(x, 1, z, "concrete_light_smooth")
    g.fill_xy(1, 20, 0, 18, "concrete_light_smooth")  # projecting apron at the gate
    for z in range(11, 16):                           # equipment pad
        for x in range(2, 17):
            g.set(x, 1, z, "concrete_hsc")
    g.fill_xy(1, 16, 1, 17, "grate")                  # drainage trench
    for x in list(range(2, 8)) + list(range(11, 17)):
        g.set(x, 1, 19, "grate")
    g.fill_xy(1, 20, 7, 11, "stripe_yellow")          # threshold warning stripe
    for (x, z) in COLUMNS:                            # pillar feet
        g.set(x, 1, z, "casing_iridium_bolted")
    for (x, z) in ((2, 11), (16, 11), (2, 15), (16, 15)):
        g.set(x, 1, z, "hazard_explosion")

    # ---------------- y = 2 : first-floor walls / gate ------------------
    # rear curtain-wall base (F = hatch / bus mount band)
    for x in range(SX):
        g.set(x, 2, 0, "casing_solid_steel")
    # side curtain-wall base (mullions and end bays are steel)
    for z in range(SZ):
        m = "casing_solid_steel" if (z in SIDE_MULLION_Z or z in (0, SZ - 1)) else "concrete_light"
        g.set(0, 2, z, m)
        g.set(18, 2, z, m)
    # front curtain-wall base: panels, two door jambs, gate opening
    for x in range(SX):
        if x in FRONT_MULLION_X or x in (0, SX - 1):
            g.set(x, 2, 20, "casing_solid_steel")
        elif x in (7, 11):
            g.set(x, 2, 20, "molecular_advanced")
        elif x in (8, 9, 10):
            pass                                      # gate opening
        else:
            g.set(x, 2, 20, "concrete_light")
    # platform legs (ground -> y=2)
    for (x, z) in ((8, 12), (10, 12), (8, 14), (10, 14)):
        g.set(x, 2, z, "casing_solid_steel")
    # the controller occupies (9,2,20); it is NOT a filled block, so the gate
    # opening stays air and `~` marks the controller in the shape.

    # ---------------- y = 3..7 : curtain wall glazing -------------------
    for y in GLASS_Y:
        for x in range(SX):
            g.set(x, y, 0, "frame_steel" if x in FRONT_MULLION_X else "glass_boro")
        for z in range(SZ):
            m = "frame_steel" if z in SIDE_MULLION_Z else "glass_boro"
            g.set(0, y, z, m)
            g.set(18, y, z, m)
        for x in range(SX):
            g.set(x, y, 20, "frame_steel" if x in FRONT_MULLION_X else "glass_boro")

    # ---------------- y = 6 : mezzanine deck ---------------------------
    # The front half of the hall is a raised deck; the rear half stays
    # double height and keeps the overhead crane run clear.
    for z in range(11, 20):
        for x in range(1, 18):
            g.set(x, 6, z, "concrete_light_smooth")
    for x in range(1, 18):
        g.set(x, 6, 11, "frame_steel")               # deck edge girder
    for x in (2, 8, 16):                             # deck posts
        for y in (2, 3, 4, 5):
            g.set(x, y, 11, "casing_solid_steel")
    for (x, y) in ((9, 4), (9, 5), (8, 5)):          # service stair down
        g.set(x, y, 10, "grate")
    for x in range(13, 17):                          # deck office
        for z in range(13, 17):
            g.set(x, 6, z, "casing_sterile")

    # ---------------- y = 8..9 : solid band -----------------------------
    for y in (8, 9):
        for x in range(SX):
            g.set(x, y, 0, "concrete_light")
        for z in range(SZ):
            g.set(0, y, z, "concrete_light")
            g.set(18, y, z, "concrete_light")
    for x in range(SX):
        g.set(x, 8, 20, "concrete_light")
        g.set(x, 9, 20, "casing_solid_steel")        # front transom band
    for (x, z) in CORNERS:                           # steel corners run the full height
        for y in range(2, 10):
            g.set(x, y, z, "frame_steel")

    # ---------------- y = 3 : machining table --------------------------
    for z in range(12, 15):
        for x in range(8, 11):
            g.set(x, 3, z, "casing_stainless")
    g.set(9, 3, 13, "casing_lathe")
    for (x, z) in ((8, 12), (10, 12), (8, 14), (10, 14)):
        g.set(x, 3, z, "casing_solid_steel")

    # ---------------- y = 4 : table top layer --------------------------
    for z in range(12, 15):
        for x in range(8, 11):
            g.set(x, 4, z, "casing_stainless")
    g.set(9, 4, 13, "casing_lathe")
    for (x, z) in ((8, 12), (10, 12), (8, 14), (10, 14)):
        g.set(x, 4, z, "casing_solid_steel")

    # ---------------- y = 5 : tool magazine / fixtures -----------------
    for x in (3, 4, 5):                              # tool magazine, rear wall
        g.set(x, 5, 3, "computer_advanced")
        g.set(x, 5, 4, "computer_advanced")
    for x in (8, 9, 10):                             # workpiece fixture
        g.set(x, 5, 13, "casing_stainless")
    g.set(9, 5, 12, "casing_lathe")                  # spindle shroud
    g.set(9, 5, 14, "casing_lathe")
    for (x, z) in ((8, 12), (10, 12), (8, 14), (10, 14)):
        g.set(x, 5, z, "casing_solid_steel")         # table corner posts

    # ---------------- y = 6 : machining centreline ---------------------
    # (the mezzanine deck of the front half was built in the shell section)
    g.set(9, 6, 10, "computer_advanced")             # headstock
    g.set(8, 6, 10, "casing_solid_steel")
    g.set(10, 6, 10, "casing_solid_steel")
    g.set(6, 6, 3, "computer_advanced")              # magazine extension

    # ---------------- y = 7 : spindle ----------------------------------
    g.set(9, 7, 10, "casing_lathe")                  # spindle column
    g.set(8, 7, 10, "casing_solid_steel")
    g.set(10, 7, 10, "casing_solid_steel")
    for x in (5, 6, 7):                              # control room, on the deck
        g.set(x, 7, 13, "computer_advanced")

    # ---------------- y = 8 : overhead crane ---------------------------
    for x in range(1, 18):
        g.set(x, 8, 10, "frame_steel")               # crane rail along x
    g.set(9, 8, 10, "molecular_coil")                # trolley
    g.set(8, 8, 10, "casing_lathe")
    g.set(10, 8, 10, "casing_lathe")

    # ---------------- y = 9 : transom mullions, rear --------------------
    for x in (3, 7, 11, 15):
        g.set(x, 9, 0, "casing_solid_steel")

    # ---------------- y = 10 : roof slab -------------------------------
    for z in rng_z:
        for x in rng_x:
            g.set(x, 10, z, "concrete_light_smooth")
    for x in (3, 7, 11, 15):
        for z in (3, 7, 11, 15):
            g.set(x, 10, z, "glass_boro")            # skylights
    g.set(9, 10, 11, "frame_steel")                  # skylight reinforcement
    g.set(9, 10, 7, "frame_steel")
    g.set(5, 10, 9, "grate")                         # rigging hatches
    g.set(13, 10, 9, "grate")

    # ---------------- y = 11 : parapet ---------------------------------
    for x in rng_x:
        g.set(x, 11, 0, "concrete_light_smooth")
        g.set(x, 11, 20, "concrete_light_smooth")
    for z in range(1, SZ - 1):
        g.set(0, 11, z, "concrete_light_smooth")
        g.set(18, 11, z, "concrete_light_smooth")
    for x in range(2, 17):
        g.set(x, 11, 20, "stripe_yellow")            # front warning band
    for z in range(6, 15):
        g.set(0, 11, z, "stripe_yellow")             # side warning bands
        g.set(18, 11, z, "stripe_yellow")

    return g


# --------------------------------------------------------------------------
# validation
# --------------------------------------------------------------------------
def validate(g: Grid) -> list[str]:
    errs: list[str] = []

    # 1. bounding box
    for (x, y, z) in g.cells:
        if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
            errs.append(f"out of bounds: ({x},{y},{z})")
    if errs:
        return errs[:20]

    # 2. gate opening is air and the controller sits in the middle of it
    for x in (8, 9, 10):
        if not g.is_air(x, 2, 20):
            errs.append(f"gate opening not open at ({x},2,20)")
    if CONTROLLER not in GATE_CELLS:
        errs.append("controller is not inside the gate opening")

    # 3. foundation slab is solid
    holes = [(x, z) for z in range(SZ) for x in range(SX) if g.is_air(x, 0, z)]
    if holes:
        errs.append(f"foundation has {len(holes)} holes, e.g. {holes[:5]}")

    # 4. shell closed: every wall-plane cell above the gate is filled
    for y in range(2, 10):
        for x in range(SX):
            for z in (0, SZ - 1):
                if g.is_air(x, y, z) and (x, y, z) not in GATE_CELLS:
                    errs.append(f"shell hole at ({x},{y},{z})")
        for z in range(SZ):
            for x in (0, SX - 1):
                if g.is_air(x, y, z) and (x, y, z) not in GATE_CELLS:
                    errs.append(f"shell hole at ({x},{y},{z})")

    # 5. interior air connectivity
    interior = {
        (x, y, z)
        for y in range(1, 10)
        for x in range(1, 18)
        for z in range(1, 20)
        if g.is_air(x, y, z)
    }
    seed = None
    for z in range(18, 1, -1):                       # walk in through the gate
        if (9, 2, z) in interior:
            seed = (9, 2, z)
            break
    if seed is None:
        errs.append("no interior air cell found on the gate centreline")
    else:
        seen = {seed}
        q = deque([seed])
        while q:
            x, y, z = q.popleft()
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (x + dx, y + dy, z + dz)
                nx, ny, nz = n
                if not (1 <= nx <= 17 and 1 <= ny <= 9 and 1 <= nz <= 19):
                    continue
                if n in seen or not g.is_air(*n):
                    continue
                seen.add(n)
                q.append(n)
        trapped = sorted(interior - seen)
        if trapped:
            errs.append(f"{len(trapped)} trapped interior air cells, e.g. {trapped[:6]}")

    # 6. gate passage: air above the apron, concrete apron in front of the gate
    for cell in ((9, 2, 19), (9, 2, 18), (9, 2, 17)):
        if not g.is_air(*cell):
            errs.append(f"gate passage blocked at {cell}")
    if g.is_air(9, 1, 20):
        errs.append("gate apron (9,1,20) is missing its floor")

    # 7. hatch mounts reachable from inside
    for x in BACK_PANEL_X:
        if g.get(x, 2, 0) == "casing_solid_steel" and not g.is_air(x, 2, 1):
            errs.append(f"hatch mount ({x},2,0) has no inside neighbour")

    # 8. block count
    total = len(g.cells)
    if not (1900 <= total <= 2200):
        errs.append(f"block count {total} outside the 1900-2200 band")

    return errs


def to_json(g: Grid) -> dict:
    layers = []
    for y in range(SY):
        layers.append([
            {"x": x, "z": z, "m": m}
            for (x, yy, z), m in sorted(g.cells.items())
            if yy == y
        ])
    materials = {}
    for mid, (letter, block, registry, meta, en, zh, color, java, role) in MATERIALS.items():
        materials[mid] = {
            "letter": letter,
            "block": block,
            "registry": registry,
            "meta": meta,
            "nameEn": en,
            "nameZh": zh,
            "color": color,
            "java": java,
            "role": role,
        }
    return {
        "machine": "MTIntegratedMachiningCenter",
        "nameZh": "多功能集成加工中心",
        "nameEn": "Multifunction Integrated Machining Center",
        "structure": "integrated_machining_workshop",
        "size": {"x": SX, "y": SY, "z": SZ},
        "controller": {"x": CONTROLLER[0], "y": CONTROLLER[1], "z": CONTROLLER[2]},
        "offsets": {
            "horizontal": CONTROLLER[0],
            "vertical": CONTROLLER[1],
            "depth": CONTROLLER[2],
        },
        "materials": materials,
        "layers": layers,
        "blockCount": len(g.cells),
    }


def java_shape(g: Grid) -> list[str]:
    """Render the raw (pre-transpose) String[][] rows, s[y][z], x = character."""
    letters = {mid: spec[0] for mid, spec in MATERIALS.items()}
    out = []
    for y in range(SY):
        for z in range(SZ):
            line = "".join(
                "~" if (x, y, z) == CONTROLLER
                else (letters.get(g.get(x, y, z), " ") if g.get(x, y, z) else " ")
                for x in range(SX)
            )
            out.append(f'/* y={y:2d} z={z:2d} */ "{line}",')
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="validate without writing")
    ap.add_argument("--dump-java", action="store_true", help="print the String[][] rows")
    args = ap.parse_args()

    here = Path(__file__).resolve().parent
    g = build()

    if args.dump_java:
        print("\n".join(java_shape(g)))
        return 0

    errs = validate(g)
    total = len(g.cells)
    per_material = Counter(g.cells.values())
    print(f"blocks: {total}")
    for mid, n in per_material.most_common():
        print(f"  {MATERIALS[mid][0]} {mid:<24} {n:>5}")

    if errs:
        print(f"\nFAILED ({len(errs)} problems):")
        for e in errs[:40]:
            print("  -", e)
        return 1
    print("\nall self-checks passed")

    if not args.check:
        out = here / "machine-structure.json"
        out.write_text(json.dumps(to_json(g), ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
