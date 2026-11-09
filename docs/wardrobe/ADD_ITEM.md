# Adding a Trinkies item

The current generator is intentionally gated to the 30-item vertical slice. Hold new catalog entries and the remaining 270 until Steps 1–3 in the continuation prompt are verified or explicitly BLOCKED with documented attempts and reasons. Step 4 additionally requires the style lock, full roster, variety lint, and batch-level validation before generation.

## Generator workflow (current supported path)

1. Install the pinned toolchain in an isolated environment:

   ```sh
   python3 -m venv .venv-trinkies
   . .venv-trinkies/bin/activate
   python -m pip install -r tools/trinkies_assets/requirements.txt
   ```

2. Add an original hand-authored row in `tools/trinkies_assets/generate_slice.py`: choose a unique category/slug, short concept, tags, material recipe, two curated base/accent colors, attachment, animation recipe, and whether it needs emissive output. Add a distinct cube silhouette to `shape_parts()`; do not produce a palette-only variant.
3. Keep the linter budgets in mind: Hats 40 cubes/64×64, Suits 80/128×128, Back & Wings 60/128×128, Pets 70/128×128, Emote props 30/64×64, Auras 32×32 particle sheet (the current slice additionally caps its model at 24 cubes). Provide at least five ramp colors and a loop or one-shot animation appropriate to the item.
4. Generate and validate:

   ```sh
   python tools/trinkies_assets/generate_slice.py
   python tools/trinkies_assets/validate_catalog.py --require-slice
   ```

   The first command regenerates the fixed 30-item slice and contact sheets; it is not yet a general-purpose append-only CLI. Review `trinkies_assets/trinkies/manifest.json` and keep the compressed pack below 15 MiB.
5. Open the `.bbmodel` in Blockbench, inspect the external PNG and pivots, and render the item in the real launcher preview and Minecraft on classic/slim players before marking it accepted. The current offline test does not do those steps.

## Hand-editing a model in Blockbench

- Copy an item directory as a starting point only for the file structure; create a distinct silhouette and palette.
- Use free-format cubes grouped under the shared rig bones (`head`, `body`, arms, legs, `root`) or named extra bones. Keep coordinates in model pixels and update `attach` offsets.
- Save a standalone `texture.png` in the item directory. The `.bbmodel` must embed byte-identical texture data as well as use the same UV texture size; `validate_catalog.py` checks equality and UV bounds/coverage.
- Keep six faces defined per cube, avoid zero-volume/duplicate cubes, check z-fighting and unintended intersections, and update the named animation tracks after changing bones.
- Update `item.json`, `animations.json`, `catalog.json`, and the SHA-256 manifest through a reviewed catalog build step. A generic hand-edit/build command is not provided yet; do not edit only one copy of the texture.

## Gate before marking done

A passing schema/linter is only a structural pass. Also require: Blockbench opens the file; silhouette and palette are distinct; texture/UV/emissive checks pass; animation loops cleanly at the render partial tick; classic and slim player render correctly while standing, sneaking, swimming, wearing armor, and turning; no renderer-specific APIs are used; in-game screenshots exist; and device performance is measured. Record failures instead of marking the item complete.
