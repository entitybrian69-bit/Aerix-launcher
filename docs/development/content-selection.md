# Content list viewport and bulk selection

Mods, resource packs, shaders, worlds/saves and screenshots share selection controls:
Select items, row checkboxes/tap-to-select, Select visible, Clear, Done and confirmed
Delete. Mods additionally support bulk Enable/Disable. Other kinds are deliberately
not "disabled" by renaming their folders: that would not reliably disable a world
or change Minecraft's active pack settings.

Reload no longer swaps a populated LazyColumn for a spinner. Each pane owns an
explicit rememberLazyListState outside the loading branch, with saveable state
scoped to instance/category. Disabling changes neither a normal row's key nor its
alphabetic sort position. Enabled/disabled duplicate filenames receive unique
keys rather than crashing the list. Deleting uses Compose's retained keyed list
anchor; if the anchor itself disappears or the list becomes shorter, the nearest
remaining position is used/clamped, not an unconditional scroll to index zero.

Selection uses exact file paths. Select visible adds only the filtered results;
already selected hidden entries are retained and their count is shown. Bulk
operations snapshot targets, run sequentially through the existing IO bridge,
refresh once, deselect successes and retain/select failures with a result message.
Delete always confirms the item count/names; world deletion explicitly warns
about removing all save data. Single deletes also use this confirmation.

Tests cover stable keys/order across disabling/deletion, duplicate identities,
selection changes, exact/deduplicated bulk targets, every content kind, partial
failure, cancellation and rejecting unsupported toggle actions. Device UI testing
is still required for pixel-level viewport behavior, large collections and font
scaling. No release/version bump is made with this change.
