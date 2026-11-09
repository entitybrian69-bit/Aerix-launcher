# Trinkies contract and asset specification

**Status:** v1 asset contract and generated JSON assets exist. Runtime readers, sampler, equipped-state writer, and in-game renderer are not yet implemented. The contract is deliberately versioned so the launcher and mod can share one catalog without quietly changing meanings.

## Cosmetic item JSON

Each item lives in `trinkies_assets/trinkies/items/<category>_<slug>/`. `item.json` is validated against [`schema/item.schema.json`](schema/item.schema.json). The schema is category-agnostic: a new category uses a lowercase slug and a budget entry in the linter; it does not require an enum/schema rewrite.

Required fields:

- `id`: globally unique namespaced string, for example `trinkies:hat_copper_aviator`.
- `category`, `name`, `description`, `rarity`, and `tags`. Rarity is a display grouping only; it never controls ownership, price, or unlocks.
- `model`: relative Blockbench `.bbmodel` path; `texture`: separate PNG path; optional `emissive_mask` and `particle_sheet` paths.
- `attach`: player rig bone name and a three-number local offset.
- `animations`: named clip IDs present in `animation_file`.
- `version`: item-schema version (`1`). `variant_of` is optional and must not be used to disguise recolours as new designs.
- Generator-specific `palette` and `shape` fields are included in this slice for deterministic regeneration and lint checks.

Example (shortened):

```json
{
  "id": "trinkies:hat_copper_aviator",
  "category": "hat",
  "name": "Copper Aviator",
  "description": "A riveted sky-pilot cap with twin teal lenses and a low wind brim.",
  "rarity": "uncommon",
  "tags": ["flight", "metal", "goggles"],
  "model": "model.bbmodel",
  "texture": "texture.png",
  "emissive_mask": "emissive.png",
  "attach": {"bone": "head", "offset": [0, 0, 0]},
  "animations": ["idle", "equip"],
  "animation_file": "animations.json",
  "version": 1
}
```

The full generated files and actual palette values are in the repository. They are source data, not screenshots or claims that the game renderer is complete.

## Blockbench model and rig contract

- Model files use Blockbench free-format JSON, metadata `format_version: 4.9`, and cube elements with explicit `from`, `to`, `origin`, `rotation`, six face UV rectangles, and an outliner group. The texture is embedded in the `.bbmodel` for editing and also emitted as a standalone PNG for the launcher; CI verifies the embedded PNG equals the referenced PNG.
- One model pixel is one model-space unit. The initial rig pivot map uses `root (0,0,0)`, `head (0,12,0)`, `body (0,2,0)`, arms at `x=±5,y=6`, and legs at `x=±1.9,y=-4,z=-0.1`. Attachments use local coordinates and an offset; a version adapter must translate these to the selected game's actual model pivot/pose.
- Required player bones: `head`, `body`, `left_arm`, `right_arm`, `left_leg`, and `right_leg`; the asset model also carries a `root` group. Item-specific groups such as `left_wing`, `right_wing`, `tail`, `pet_root`, `pet_left_wing`, `pet_right_wing`, `pet_tail`, `prop`, and `aura_orbit` are allowed. The sampler must preserve parent-child transforms and reset each pose after rendering.
- Generated `.bbmodel` metadata, cube shape, UV bounds/coverage, and embedded texture are checked by the offline linter. Opening the result in Blockbench and rendering it in Minecraft are still required manual acceptance steps; neither tool was available in this environment.

## Animation format and runtime contract

`animations.json` uses `schema: 1` and a `clips` object. Each clip declares `duration` (seconds), `loop_mode` (`loop`, `once`, `ping_pong`), `blend_in`, `blend_out`, and named bone tracks. Keyframes include `time`, `position` (model units), `rotation` (Euler degrees, XYZ), `scale` (three components), and `easing` (`linear`, `ease_in_out`, `cubic`, `spring`). The schema is [`schema/animation.schema.json`](schema/animation.schema.json).

Runtime requirements before any item can pass the game gate:

1. Sample using accumulated game time plus the render partial tick; do not step only on game ticks.
2. Interpolate between surrounding frames; define and test the cubic/spring easing precisely and clamp spring overshoot to a safe transform range.
3. Blend in/out against the player's normal vanilla pose. Emotes cancel by movement, damage, and item action unless configured otherwise.
4. Avoid per-frame allocations: cache parsed clips, bone tracks, and baked vertices when loading; keep render calls on the render thread.
5. For loop clips, the linter checks equivalent start/end poses (rotation is compared modulo 360°). A passing structural loop test is not a visual animation review.

The current 30-item pack has animation JSON and generated keyframe strips only. No runtime sampler is shipped yet.

## Equipped state

The launcher's target path is `<instance game directory>/trinkies/equipped.json`; the v1 contract is validated by [`schema/equipped.schema.json`](schema/equipped.schema.json):

```json
{
  "schema": 1,
  "hat": "trinkies:hat_copper_aviator",
  "suit": null,
  "back": null,
  "aura": null,
  "pet": null,
  "emote_wheel": [null, null, null, null, null, null, null, null]
}
```

The launcher must validate each ID against `catalog.json`, write a sibling temporary file, flush it, and atomically rename it before launch. The mod must watch for changes with debounce and preserve the last valid state on parse failure. Current code does not yet write this file or consume it in-game.

## Budgets

| Category | Maximum cubes | Texture ceiling | Additional contract |
|---|---:|---:|---|
| Hats | 40 | 64×64 | At least 30% animated in a full catalog |
| Suits | 80 | 128×128 | `suit_mode`: `overlay` or `replace` |
| Back & Wings | 60 | 128×128 | Extra wing joints; idle sway and action-specific flap planned |
| Pets | 70 | 128×128 | Shared archetype plus per-item proportions |
| Emotes | 30 prop cubes | 64×64 | Play clips 1–8 s; props optional |
| Auras & Trails | 24 small cubes in slice | 32×32 particle sheet | Enforce configured per-player particle cap and client settings |

The zipped generated asset pack must stay below 15 MiB. The linter measures the current committed pack and rejects growth at or above that limit. The initial five-per-category slice uses no `variant_of` recolours.

## Rendering and compatibility contract

- In-game accessories must go through the Minecraft/Fabric vanilla model layer and `VertexConsumer`/render-layer path. No direct OpenGL calls, custom shaders, or custom framebuffers. Respect world lighting, pose transforms, first-person visibility, armor overlap, and classic/slim arms.
- Initial quality defaults from the master brief: cosmetics for at most the nearest 24 players, simplified far LOD, none beyond a hard distance; at most 16 visible pets; cull particles and attachments; never allocate temporary vertex/model objects per frame.
- Start with local player only. Other-player rendering and multiplayer events require Phase 3 identity/privacy work and are not silently enabled.

## Multiplayer threat model and privacy

No Trinkies multiplayer sync is shipped by this change. The existing Supabase social features are not an authorization mechanism for Minecraft UUIDs.

If the owner approves a future opt-in sync:

- State payloads are only catalog IDs (`hat`, `suit`, `back`, `aura`, `pet`) tied to a verified Minecraft UUID; no skin PNGs, server addresses, account tokens, coordinates, or chat are part of the cosmetic protocol.
- Microsoft/Ely.by identity requires a proof bound to the UUID before writes. Offline profiles are local-only by default because their UUID is not authenticated.
- The client sends nothing until one clear opt-in with a plain-language explanation. A **Delete my data** action must remove the player's row and any event state. The service has a remote kill switch; game launch/join never waits for it.
- Supabase must enforce RLS and per-UUID ownership in database functions, rate limit writes, validate IDs against the item catalog, batch tab-list reads, use a TTL/ETag or version cache, and exponential backoff. Realtime emotes are transient session events, not durable state; room identifiers should be derived from a privacy-reviewed server-session hash. No protocol or RLS SQL is included yet.
