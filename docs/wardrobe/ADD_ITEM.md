# Adding or revising a Trinkies item

The current generated roster is a fixed **300-item catalog**: 50 entries in each of six categories. The full pack passes structural validation, but generated assets are not automatically considered art-approved or verified in Minecraft. Do not edit generated `trinkies_assets/trinkies/items/**` files directly; the deterministic Python sources are authoritative.

## Regenerate the full catalog

1. Set up Python 3.11+ and install the pinned asset dependencies:

   ```sh
   python3 -m venv .venv-trinkies
   . .venv-trinkies/bin/activate
   python -m pip install -r tools/trinkies_assets/requirements.txt
   ```

2. Revise an original slice concept in `tools/trinkies_assets/generate_slice.py`, or revise one of the 270 additional family/theme recipes in `tools/trinkies_assets/full_catalog.py`. For a new row or category, update the fixed 300-item/50-per-category invariants in `full_catalog.py`, `generate_full_catalog.py`, `validate_catalog.py`, the smoke tests, and the CI workflow together. New designs need distinct silhouettes/geometry, not only palette changes.
3. Regenerate and validate the **full** roster:

   ```sh
   python tools/trinkies_assets/generate_full_catalog.py
   python tools/trinkies_assets/validate_catalog.py --require-full
   ```

   `generate_full_catalog.py` emits all item folders, `catalog.json`, the SHA-256 manifest, `docs/wardrobe/ROSTER.md`, and the 300-item texture sheet. It also regenerates the original vertical-slice reference sheets. `generate_slice.py` alone writes only the original 30-item slice and must not be used to refresh the full pack.
4. Inspect the generated diffs, ensure no user-authored files were pruned, and keep the compressed asset pack below 15 MiB. The full generator prunes only item folders listed in the previous manifest.
5. Open changed `.bbmodel` files in Blockbench, inspect the UVs and pivots, then render and review them in Minecraft on classic/slim players before calling them accepted. The offline validator is structural only; it does not review art or render in-game.

## Hand-editing a model in Blockbench

- Treat a generated directory as the file structure only; create a distinct silhouette and palette.
- Use free-format cubes grouped under the shared rig bones (`head`, `body`, arms, legs, `root`) or named extra bones. Keep coordinates in model pixels and update `attach` offsets.
- Save a standalone `texture.png` in the item directory. The `.bbmodel` must embed byte-identical texture data and use the same UV texture size; `validate_catalog.py` checks equality and UV bounds/coverage.
- Keep six faces defined per cube, avoid zero-volume/duplicate cubes, check z-fighting and unintended intersections, and update named animation tracks after changing bones.
- Update catalog output only through the generator. Do not edit one copy of a texture or a manifest hash by hand.

## Gate before marking done

A passing schema/linter is only a structural pass. Also require: Blockbench opens the file; silhouette and palette are distinct; texture/UV/emissive checks pass; animation loops cleanly at the render partial tick; classic and slim players render correctly while standing, sneaking, swimming, wearing armor, and turning; no renderer-specific APIs are used; in-game screenshots exist; and device performance is measured. Record failures instead of marking the item complete.
