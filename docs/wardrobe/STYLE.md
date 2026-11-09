# Trinkies voxel-art style guide

## Visual identity

The vertical slice uses small, deliberately readable silhouettes with one strong focal accent and a compact palette. The catalog should feel like one handmade voxel collection, not a stack of hue-swapped templates. Use clean cube edges, directional lighting from above-left, cooler shadows, warmer highlights, and restrained glow. Distinguish siblings by silhouette, negative space, material and motion first; color is secondary.

All current art is procedural output from `tools/trinkies_assets/generate_slice.py` plus the hand-authored cube recipes in that file. It is original work for this repository. No Minecraft skin atlas, Mojang character/mob art, Millida assets, scraped images, or third-party models are sampled. The procedural icons/contact sheet are engineering previews, not final in-game screenshots.

## Palette and material recipes

- Each item defines a base hue and one accent hue in the generator. The deterministic ramp shifts shadows cooler/darker and highlights warmer/lighter, plus an accent and accent highlight. The item manifest stores the generated palette for review.
- Keep palettes to 5–8 distinct colors. Check luminance span and silhouette contrast against the dark preview background. Reserve the brightest color for a focal point; emissive masks are separate grayscale/alpha data.
- Material patterns are low-frequency and directional, not confetti noise: metal gets edge bands/rivets, glass gets a narrow diagonal glint, gems get a few facets, wool/fur get soft structured breaks, cloth gets a restrained weave, leather gets sparse grain, wood gets longitudinal grain, stone gets broad facets, and enamel gets a clean highlight.
- Face shading is baked per UV tile: top faces brighter, bottom faces darker, vertical faces intermediate. One-pixel edge highlights and dark seams approximate ambient occlusion at joints. Keep the pixel grid crisp; do not introduce linear filtering in the item viewer.

## Silhouette and motion

- Hats should read in a small head-sized thumbnail. Avoid a generic cube stacked on every item: vary brim, horns, band, hanging charm, fin, or halo shape.
- Suits should be coherent sets across torso, arms and legs; attachment cubes must intentionally clear the vanilla base model and armor.
- Back items and wings should have a distinct resting silhouette and explicit hinge bones. Pets need a recognizable outline, eye/face placement, and a restrained signature movement.
- Emote props remain optional and under 30 cubes. Auras/trails should use a sparse sheet and remain legible against both bright and dark worlds.
- Keep looping idle motion low amplitude. No fast flashing, high-frequency noise, or unreadable particle spam. Loop endpoints must match; one-shot clips should return to a valid vanilla pose.

## Slice review checklist

Before an item is called done, review the catalog metadata, texture at 1× nearest-neighbor, silhouette from front/side/back, UVs, cube intersections, emissive mask, animation loop, classic/slim fit, armor overlap, sneak/swim/fly poses, world lighting, and device performance. The current generated slice passes only the offline structural/schema/budget checks. Blockbench opening, game rendering, device testing, and human art approval are pending.
