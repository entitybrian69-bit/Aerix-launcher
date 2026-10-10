# Aerix Prism — visual direction

These **ten images are concept mockups**, not screenshots of an implemented launcher. View [the gallery](index.html) or open each PNG individually.

## Non-negotiable design rules

- Redesign the launcher as an immersive, edge-to-edge spatial interface. Do not reskin the existing screen hierarchy.
- Replace the left scrolling navigation rail with a floating **horizontal navigation system**. Keep every route reachable without a hidden scroll-only destination; account and settings have persistent contextual entry points.
- Replace the old rectangular Play button with a distinctive circular launch control that has an accessible label and clear pressed/loading/disabled states.
- Use genuinely transparent, backdrop-blurred crystal surfaces with light edges and controlled contrast, not opaque green blocks or merely tinted translucent rectangles. Provide a readable fallback on devices without a suitable blur pipeline and respect reduced-transparency preferences where possible.
- Build new widgets for search, selectors, profile cards, capability choices, appearance palettes and control editing; do not simply reuse the current launcher widgets with different backgrounds.
- Preserve features and workflows while changing presentation. Home: account, selected profile, launch, quick tools. Create: vanilla, loader profiles, legacy, OptiFine, modpack import. Library: profile selection/edit/clone/backup/delete, favorites/groups/pins/sorting. Discover: Modrinth, optional personal-key CurseForge, modpacks/mods/shaders/resource packs/world saves, details and installation. Accounts: Microsoft, Ely.by and local flows. Skins: public lookup/preview and confirmed authenticated upload. Servers: local server list editing, without implying launcher-native joining. Appearance: bundled/custom wallpapers, presets/custom/material palettes. Settings: renderer/game/Java/launcher/update routes. Controls: touch/gamepad/mouse/editor/presets/import/export.
- Optimize for landscape phones as well as tablets: adaptive columns, scrollable content **inside each page** when needed, no cropped essential actions, minimum comfortable touch targets, and proper cutout/gesture insets.

Image-generated example text and state are illustrative; implement from actual app data and verified service capabilities, not from any fictitious sample server or version shown in a mockup. No app code was changed for this concept set.
