#!/usr/bin/env python3
"""Guard the rules that make wardrobe cloud sync safe rather than merely possible.

The feature is small enough that the interesting failures are not compile errors: a missing row-level
security policy, a payload that grew a texture, a switch that defaults to on, or a restore that trusts
a remote id the local catalog cannot resolve. None of those break a build, so they are pinned here.

Usage: python3 tests/trinkies_cloud_sync_smoke.py
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
AERIX = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix"
STEP4 = ROOT / "supabase/step4-wardrobe.sql"
SCHEMA = ROOT / "supabase/schema.sql"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Trinkies cloud sync guard failed: {message}")


sql = read(STEP4)

# Row level security is the whole privacy story for this table: one row per user, visible to that user
# and to nobody else. An anon-readable wardrobe table would be a name-and-outfit directory.
check("alter table public.wardrobe_sync enable row level security" in sql,
      "wardrobe_sync is created without row level security enabled")
check("references public.profiles (id) on delete cascade" in sql,
      "a wardrobe row must disappear with the account, not linger after deletion")
for command in ("select", "insert", "update", "delete"):
    block = re.search(rf"create policy \w+ on public\.wardrobe_sync\s+for {command}([^;]*);", sql)
    check(block is not None, f"no {command} policy on wardrobe_sync, so the app cannot use the table")
    clause = block.group(1)
    check("to authenticated" in clause, f"the wardrobe_sync {command} policy is not limited to signed-in users")
    check("auth.uid() = id" in clause, f"the wardrobe_sync {command} policy does not require it to be your own row")
    check("using (true)" not in clause and "public" not in clause.split("using")[0],
          f"the wardrobe_sync {command} policy is broader than self-only")
check("for all to" not in sql, "a single 'for all' policy hides a widening; keep the four explicit ones")
check("pg_column_size(loadout) <= 4096" in sql,
      "the table accepts an arbitrary blob, so it is file storage rather than a wardrobe backup")
check("jsonb_typeof(loadout) = 'object'" in sql, "the table accepts a non-object payload")
check("drop table if exists public.wardrobe_sync" in sql,
      "an optional table with no documented removal path invites half-deleted schemas")

# The combined one-file schema has to carry the same rules, or the two paths diverge.
combined = read(SCHEMA)
check(combined.count("public.wardrobe_sync") >= sql.count("public.wardrobe_sync"),
      "supabase/schema.sql no longer mirrors step4-wardrobe.sql")

sync = read(AERIX / "client/TrinkiesCloudSync.kt")

# Privacy: the payload is ids. Anything else in this file means someone started backing up pixels.
for forbidden in ("Bitmap", "assets.open", "Base64", "getEncoder", "File(", "readBytes", "InputStream"):
    check(forbidden not in sync, f"the wardrobe payload reached for {forbidden}; ids only, never files")
keys = set(re.findall(r'\.put\("([a-z_]+)"', sync))
check(keys == {"schema", "slots", "emote_wheel"},
      f"the snapshot shape drifted: it writes {sorted(keys)} and the schema check expects three keys")
check('"emote_wheel"' in sync and "JSONObject.NULL" in sync,
      "unbound wheel slots must be explicit nulls, not omitted, or a pull cannot clear a binding")

# A restore is only as good as its refusals.
check("TrinkiesCatalog.hasId(context, id, category)" in sync,
      "a pulled slot id is not checked against the catalog for its own category")
check("TrinkiesCatalog.find(context, value) != null" in sync,
      "a pulled wheel id is not checked against the catalog at all")
check('Pattern.compile("trinkies:[a-z0-9_]+")' in sync,
      "the id pattern the launcher writes is not re-checked on the way in")
check('optInt("schema", -1) != SCHEMA' in sync,
      "a backup from a newer format must be refused rather than half-applied")
check("TrinkiesLoadout.refreshSupportedInstances(context)" in sync,
      "a restored wardrobe never reaches the running game without rewriting the instance loadouts")

# Three copies of the slot list have to agree, because each one is deliberately kept private to its
# own file: the reader in the mod, the launcher's writer, and this payload builder.
java = read(ROOT / "aerix_client_mod/src/main/java/aerix/client/mod/TrinkiesLoadout.java")
writer = read(AERIX / "client/TrinkiesLoadout.kt")
java_categories = re.search(r'CATEGORIES = \{([^}]*)\}', java).group(1)
java_categories = tuple(part.strip().strip('"') for part in java_categories.split(","))
for label, source in (("launcher writer", writer), ("cloud sync", sync)):
    kotlin = re.search(r'CATEGORIES = listOf\(([^)]*)\)', source).group(1)
    check(tuple(part.strip().strip('"') for part in kotlin.split(",")) == java_categories,
          f"the {label} category list no longer matches the mod's reader")

backend = read(AERIX / "social/SocialBackend.kt")
check("rest/v1/wardrobe_sync?on_conflict=id" in backend,
      "the push must upsert the player's own row by id, not append rows on every tap")
check('call("DELETE", "$PROJECT_URL/rest/v1/wardrobe_sync?id=eq.$id")' in backend,
      "erasing has to be scoped to the caller's own row")
check("suspend fun pullWardrobeSnapshot(): Pair<String, String>?" in backend,
      "the pull must be able to report 'no copy yet' as distinct from 'request failed'")
check(backend.count("return@attempt false") >= 1 and "attempt {" in backend,
      "the wardrobe calls must go through the object's error-reporting helper")

store = read(AERIX / "client/AerixClientStore.kt")
check("var trinkiesCloudSyncEnabled by mutableStateOf(false)" in store,
      "cloud sync must start off: no wardrobe leaves a device that never asked")
check('putBoolean("trinkiesCloudSyncEnabled", trinkiesCloudSyncEnabled)' in store,
      "the switch has to survive a restart, or it silently turns itself back on per screen")
transfer = read(AERIX / "client/ClientSettingsTransfer.kt")
check('"trinkiesCloudSyncEnabled"' in transfer.split("\n")[9],
      "the switch belongs in the settings bundle like every other client preference")

ui = read(AERIX / "ui/screens/TrinkiesWardrobeScreen.kt")
check("TrinkiesCloudSync.push(context)" in ui and "TrinkiesCloudSync.pull(context)" in ui,
      "the Wardrobe has to offer both directions, not just upload")
check("TrinkiesCloudSync.erase(context)" in ui, "a sync a player cannot undo is not opt-in")
check("SocialBackend.signedIn" in ui, "the line should say the account is missing, not fail silently")

print("Wardrobe cloud sync stays ids-only, self-only, and off until asked for.")
