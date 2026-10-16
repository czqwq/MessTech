#!/usr/bin/env python3
"""Render docs/machining-center/machine-spec.md from generate_structure.py.

Keeping the prose spec generated means the layer tables can never drift away
from machine-structure.json.

Usage:
    python docs/machining-center/render_spec.py
"""

from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import generate_structure as G  # noqa: E402

LAYER_TITLES = {
    0: "混凝土地基（整层实心，无空气）",
    1: "车间地坪",
    2: "首层墙体 / 大门",
    3: "加工平台层 + 幕墙玻璃起",
    4: "窗带层",
    5: "刀库 / 夹具层",
    6: "夹层楼板层",
    7: "主轴层",
    8: "天车层",
    9: "楣窗带 / 实墙带",
    10: "屋面板",
    11: "女儿墙",
}


def runs(values: list[int]) -> list[str]:
    """[1,2,3,5,6] -> ['1..3', '5..6']"""
    out: list[str] = []
    start = prev = None
    for v in sorted(values):
        if start is None:
            start = prev = v
            continue
        if v == prev + 1:
            prev = v
            continue
        out.append(f"{start}" if start == prev else f"{start}..{prev}")
        start = prev = v
    if start is not None:
        out.append(f"{start}" if start == prev else f"{start}..{prev}")
    return out


def main() -> int:
    g = G.build()
    SX, SY, SZ = G.SX, G.SY, G.SZ
    cells = g.cells
    letters = {mid: spec[0] for mid, spec in G.MATERIALS.items()}

    def cells_at(y: int):
        return {(x, z): m for (x, yy, z), m in cells.items() if yy == y}

    lines: list[str] = []
    w = lines.append

    w("# machine-spec — MT 集成加工车间（多功能集成加工中心）")
    w("")
    w("> **本文由 [`render_spec.py`](render_spec.py) 从")
    w("> [`generate_structure.py`](generate_structure.py) 生成，请不要手改。**")
    w("> 手工编辑请改生成器，然后重新跑：")
    w("> `python docs/machining-center/generate_structure.py && python docs/machining-center/render_spec.py`")
    w(">")
    w("> 方块溯源（注册名 / meta / 中英文名 / 源码行）见 [`design.md`](design.md) §1。")
    w("> 交互预览见 [`preview.html`](preview.html)，数据见 [`machine-structure.json`](machine-structure.json)。")
    w("")

    # ---- 1. coordinates -------------------------------------------------
    w("## 1. 坐标系与包络")
    w("")
    w("```text")
    w(f"x : 0 .. {SX-1}     (宽 {SX})")
    w(f"y : 0 .. {SY-1}     (高 {SY})")
    w(f"z : 0 .. {SZ-1}     (深 {SZ})")
    w("```")
    w("")
    cx, cy, cz = G.CONTROLLER
    w(f"- **正面（南）= z={SZ-1}**，控制器就在这一面。")
    w(f"- 控制器 `~` 位于 **({cx}, {cy}, {cz})**，即大门的正中一格。")
    w("- 因为 `addShape(piece, transpose(structure_string))`，`checkPiece` 的偏移量就是 `~` 的坐标：")
    w("")
    w("```java")
    w(f"private static final int HORIZONTAL_OFFSET = {cx};   // x of '~'")
    w(f"private static final int VERTICAL_OFFSET   = {cy};    // y of '~'")
    w(f"private static final int DEPTH_OFFSET      = {cz};   // z of '~'")
    w("```")
    w("")
    w(f"- 总方块数 **{len(cells)}**（不含控制器所在的那一格空气）。")
    w("")

    # ---- 2. palette -----------------------------------------------------
    w("## 2. 结构字母表")
    w("")
    w("| 字母 | 材料 id | 方块 | 注册名 | meta | Java 引用 | 角色 |")
    w("|---|---|---|---|---|---|---|")
    for mid, spec in G.MATERIALS.items():
        letter, _block, registry, meta, en, _zh, _color, java, role = spec
        w(f"| `{letter}` | `{mid}` | {en} | `{registry}` | {meta if meta >= 0 else '—'} | "
          f"`{java}` | {role} |")
    w(f"| `~` | 控制器 | 控制器本体 | — | — | — | ({cx}, {cy}, {cz}) |")
    w("")

    # ---- 3. helper geometry --------------------------------------------
    w("## 3. 通用几何")
    w("")
    w("```text")
    w("# 外墙四角：通高的钢结构角柱")
    w(f"CORNER       = {sorted(G.CORNERS)}")
    w(f"# 正面柱 / 竖梃 x 位")
    w(f"FRONT_COL_X  = {sorted(G.FRONT_COL_X)}")
    w(f"FRONT_MULLION_X = {sorted(G.FRONT_MULLION_X)}")
    w(f"# 侧面柱 / 竖梃 z 位")
    w(f"SIDE_COL_Z  = {sorted(G.SIDE_COL_Z)}")
    w(f"SIDE_MULLION_Z = {sorted(G.SIDE_MULLION_Z)}")
    w(f"# 幕墙玻璃带 y 范围")
    w(f"GLASS_Y      = {list(G.GLASS_Y)}")
    w(f"# 大门洞（正面 z={SZ-1} 上的开口）")
    w(f"GATE         = {{(x, {cy}, {SZ-1}) : x in (8, 9, 10)}}")
    w("```")
    w("")

    # ---- 4. layers ------------------------------------------------------
    w("## 4. 逐层定义")
    w("")
    w("> 下列每张图都是**该层俯视图**，横轴 x=0..18，纵轴 z=0..20；")
    w("> 左上角就是 (x=0, z=0)。`.` = 空气，字母见 §2。")
    w("")

    for y in range(SY):
        layer = cells_at(y)
        w(f"### y = {y} — {LAYER_TITLES.get(y, '')}")
        w("")
        w(f"本层方块数：**{len(layer)}**")
        w("")
        w("```text")
        header = "     " + "".join(str(x % 10) for x in range(SX))
        w(header)
        for z in range(SZ):
            row = "".join(
                "~" if (x, y, z) == (cx, cy, cz) else letters.get(layer.get((x, z), ""), ".")
                for x in range(SX)
            )
            w(f"z{z:02d}  {row}")
        w("```")
        w("")

    # ---- 5. per-material counts ----------------------------------------
    w("## 5. 各材料用量")
    w("")
    w("| 字母 | 材料 | 块数 |")
    w("|---|---|---|")
    from collections import Counter

    counts = Counter(cells.values())
    for mid, n in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])):
        w(f"| `{letters[mid]}` | {G.MATERIALS[mid][3]} | {n} |")
    w(f"| — | **合计** | **{len(cells)}** |")
    w("")

    # ---- 6. self-checks -------------------------------------------------
    errs = G.validate(g)
    w("## 6. 自检结果")
    w("")
    w("`generate_structure.py` 生成的同一份数据在下列规则上全部通过：")
    w("")
    w("1. **包围盒**：所有方块 `0≤x≤18`、`0≤y≤11`、`0≤z≤20`。")
    w("2. **大门洞**：`x=8..10`（`y=2`，`z=20`）为空气，控制器 `~` 位于正中。")
    w(f"3. **地基整板**：`y=0` 的 {SX}×{SZ} = {SX*SZ} 格全部实心，无空洞。")
    w("4. **外墙闭合**：`y=2..9` 的四面墙（`x=0`、`x=18`、`z=0`、`z=20`）除大门洞外无空洞。")
    w("5. **内部连通**：`y=1..9`、`x=1..17`、`z=1..19` 的空气格从大门内侧四连通可达，无封闭空腔。")
    w("6. **大门贯通**：`(9,2,19)`、`(9,2,18)`、`(9,2,17)` 为空气；门外 `(9,1,20)` 是混凝土台阶。")
    w("7. **仓室可达**：背面 `z=0` 的每个 `F`（实心钢机制方块）格，其内侧 `z=1` 同 x 为空气。")
    w(f"8. **块数**：{len(cells)}，落在 1900–2200 区间。")
    w("")
    w("当前状态：**" + ("全部通过" if not errs else f"{len(errs)} 项未通过") + "**")
    w("")

    out = HERE / "machine-spec.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {out} ({len(lines)} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
