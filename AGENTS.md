# Agent notes (MessTech)

Process rules for anyone working in this repo - human or agent. The detailed, per-feature knowledge lives in
`docs/`: `knowledge.md` (conventions and every implemented feature), `GT5U-NOTES.md` (GT5U API notes for the
version we build against), `repo-readme.md` and `nac-module.md`.

## Verification

- **`./gradlew compileJava` is the default check for a change, and it is all that is expected.**
- Do **not** write new harnesses or verifiers under `tools/**` for a change, and do not extend or run the
  existing ones, unless the user explicitly asks for it.
- Run the wider build (`./gradlew spotlessApply spotlessCheck checkstyleMain processResources`) only when the
  user asks for a full build, or when handing work over as finished and the formatter/checkstyle would
  otherwise be the only thing unchecked.
- The harnesses that already exist in `tools/**` stay where they are and are only run on request.
  `tmp/` holds the reference checkouts (GT5U, NEI, TST, ...) and scratch work.
- When a check *is* asked for, prefer the narrowest thing that proves the change; do not sweep the whole
  `tools/**` tree.

## Working rules

- Do not create git commits unless asked; leave the working tree for the user to review.
- Machine tooltips follow the house style in `docs/knowledge.md` ("Machine tooltips (house style)"): colour
  codes live in the lang files, the Java side is one `addInfo(translate(key))` per line, and the structure
  block calls stay untouched.
- Chinese proper nouns (blocks, casings, hatches, buses, coils, fluids, items) are copied verbatim from the
  GTNH localisation under `tmp/ZH-CN`; new user visible text goes into both lang files
  (`src/main/resources/assets/messtech/lang/{zh_CN,en_US}.lang`).
- Numbers and mechanics that a tooltip or a doc claims must be traceable to a source line; do not invent them.
