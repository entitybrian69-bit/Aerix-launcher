"""Compile and run the Fabric mod's Trinkies loadout reader against the shipped catalog.

`aerix_client_mod` normally only gets compiled, which proves the reader exists but not that it agrees
with what the launcher writes. Because the reader is dependency-free Java (no Minecraft, no Android,
no Gson), the exact class the mod uses can be executed here instead: the real 300-item catalog is
parsed, a valid wardrobe is accepted, every documented way to break the contract is refused, and the
receipt the client leaves for the launcher round-trips through the file the Kotlin side reads.

Usage: python3 tests/trinkies_loadout_jvm.py [REPO_ROOT]
"""
import os
from pathlib import Path
import shlex
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MOD_SOURCE = ROOT / "aerix_client_mod/src/main/java/aerix/client/mod/TrinkiesLoadout.java"
HARNESS = ROOT / "tests/TrinkiesLoadoutSmokeTest.java"


def run_jvm_check() -> None:
    with tempfile.TemporaryDirectory() as raw:
        out = Path(raw)
        javac = shlex.split(os.environ.get("JAVAC", "javac"))
        java = shlex.split(os.environ.get("JAVA", "java"))
        subprocess.run(
            javac + ["-encoding", "UTF-8", "-source", "8", "-target", "8", "-nowarn",
                     "-d", str(out), str(MOD_SOURCE), str(HARNESS)],
            check=True,
        )
        subprocess.run(
            java + ["-Dfile.encoding=UTF-8", "-cp", str(out), "TrinkiesLoadoutSmokeTest", str(ROOT)],
            check=True,
        )


def check_the_wiring_uses_the_reader() -> None:
    """The reader has to be called from the client loop and answered by the launcher."""
    mod = (ROOT / "aerix_client_mod/src/main/java/aerix/client/mod/AerixClientMod.java").read_text()
    for needle in (
        "TrinkiesLoadout.read(equipped, TrinkiesLoadout.catalogFromJar())",
        "TrinkiesLoadout.frameTicksFromAnimation(",
        "TrinkiesLoadout.bakeMatchesLoadout(",
        "TrinkiesLoadout.writeApplied(trinkiesDir,",
        "if (++trinkiesFrameDwell >= trinkiesFrameTicks) {",
    ):
        assert needle in mod, f"AerixClientMod no longer consumes the Trinkies loadout: {needle}"
    # The wardrobe state must never be able to take the animator or live options down with it.
    refresh = mod[mod.index("private void refreshTrinkiesLoadout"):mod.index("private static String readTextFile")]
    assert "catch (Throwable ignored)" in refresh, "loadout failures must not escape the render tick"

    writer = (ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/client/TrinkiesLoadout.kt").read_text()
    assert 'private const val APPLIED_FILE_NAME = "applied.json"' in writer
    assert "fun describeGameConfirmation(context: Context): String?" in writer
    assert 'it.optInt("schema", -1) == SCHEMA' in writer, "the launcher must ignore a receipt it cannot read"
    # optString() reports JSON null as the string "null", so the raw value is the only correct probe.
    assert 'it != JSONObject.NULL' in writer

    screen = (ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/TrinkiesWardrobeScreen.kt").read_text()
    assert "TrinkiesLoadout.describeGameConfirmation(appContext)" in screen
    assert "Dispatchers.IO" in screen, "the receipt scan must not run on the composition thread"

    # Both sides enumerate the same six slots, in the same order, or a receipt key goes unread.
    java = MOD_SOURCE.read_text()
    assert 'public static final String[] CATEGORIES = {"hat", "suit", "back", "pet", "emote", "aura"}' in java
    assert 'private val CATEGORIES = listOf("hat", "suit", "back", "pet", "emote", "aura")' in writer

    schema = (ROOT / "docs/wardrobe/schema/equipped.schema.json").read_text()
    assert '^trinkies:[a-z0-9_]+$' in schema, "the shipped schema no longer declares the id pattern"
    # One predicate in the reader enforces it, so a widened pattern has to be a deliberate change.
    assert "static boolean isCatalogId(String value)" in java
    assert "if (!isCatalogId(id))" in java


if __name__ == "__main__":
    run_jvm_check()
    check_the_wiring_uses_the_reader()
    print("Trinkies loadout reader smoke test passed")
