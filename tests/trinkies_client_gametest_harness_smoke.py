#!/usr/bin/env python3
"""Static wiring checks for the isolated Trinkies client-runtime harness."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HARNESS = ROOT / "tools" / "trinkies_client_gametest"


def fail(message: str) -> None:
    raise SystemExit(f"Trinkies client GameTest harness smoke failed: {message}")


def check_manifest(manifest_path: Path, source_root: Path, entrypoint_key: str) -> Path:
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"could not read {manifest_path.relative_to(ROOT)}: {exc}")

    entrypoints = manifest.get("entrypoints", {}).get(entrypoint_key, [])
    if len(entrypoints) != 1:
        fail(f"{manifest_path.name} must declare exactly one {entrypoint_key} entrypoint")

    class_name = entrypoints[0]
    source_path = source_root.joinpath(*class_name.split(".")).with_suffix(".java")
    if not source_path.is_file():
        fail(f"{entrypoint_key} class {class_name} does not exist at {source_path.relative_to(ROOT)}")
    return source_path


gametest_source = check_manifest(
    HARNESS / "src/gametest/resources/fabric.mod.json",
    HARNESS / "src/gametest/java",
    "fabric-client-gametest",
)
legacy_source = check_manifest(
    HARNESS / "src/legacy/resources/fabric.mod.json",
    HARNESS / "src/legacy/java",
    "client",
)

modern = gametest_source.read_text(encoding="utf-8")
if 'isModLoaded("trinkies")' not in modern:
    fail("modern Client GameTest does not assert that the packaged Trinkies mod loaded")
if "TRINKIES_CLIENT_GAMETEST_PASS:" not in modern:
    fail("modern Client GameTest is missing its explicit pass marker")
if "context.takeScreenshot(" not in modern or "Files.isRegularFile(screenshot)" not in modern:
    fail("modern Client GameTest must capture and assert a startup screenshot")
if "worldBuilder()" in modern:
    fail("modern startup smoke must not create an integrated world")

legacy = legacy_source.read_text(encoding="utf-8")
for marker in ("CLIENT_STARTED", 'isModLoaded("trinkies")', "TRINKIES_CLIENT_BOOT_SMOKE_PASS:"):
    if marker not in legacy:
        fail(f"legacy boot smoke is missing required marker/condition: {marker}")

# The harness must drive the wardrobe, not just boot it. A mod that loads and never reads
# trinkies/equipped.json used to pass here, which is exactly the gap this branch exists to close.
probe = HARNESS / "src/main/java/net/aerix/trinkies/gametest/LoadoutRoundTrip.java"
if not probe.is_file():
    fail("the shared loadout round-trip probe is missing from the harness")
probe_text = probe.read_text(encoding="utf-8")
for needle in (
    "TrinkiesLoadout.catalogFromJar()",
    "TrinkiesLoadout.parseApplied",
    "TrinkiesLoadout.FILE_NAME",
    "TrinkiesLoadout.APPLIED_FILE_NAME",
    "equipped.back",
    "frameTicks",
    "emoteWheelSlots",
    "emote_wheel",
):
    if needle not in probe_text:
        fail(f"the harness probe no longer asserts the client's own answer ({needle})")
for name in ("gametest", "legacy"):
    entrypoints = list((HARNESS / "src" / name / "java").rglob("*.java"))
    text = "\n".join(path.read_text(encoding="utf-8") for path in entrypoints)
    for needle in ("LoadoutRoundTrip.seed(", "LoadoutRoundTrip.firstReceipt(", "requireWardrobeApplied(",
                    "requireWheelAccepted("):
        if needle not in text:
            fail(f"the {name} entrypoint does not drive the loadout round trip: {needle}")

# Loom creates the client GameTest source set in its own afterEvaluate hook, so anything that touches
# `sourceSets.gametest` while the script is still being evaluated fails the whole build before a client
# starts - "Could not get unknown property 'gametest'" is what that looked like in CI. The classpath
# bridge has to be deferred, and it has to fail loudly if Loom stops creating the set it defers to.
build = (HARNESS / "build.gradle").read_text(encoding="utf-8")
if "sourceSets.gametest." in build:
    fail("build.gradle reaches for sourceSets.gametest eagerly; Loom creates it in afterEvaluate")
if "project.afterEvaluate" not in build or "findByName('gametest')" not in build:
    fail("build.gradle no longer defers the gametest classpath bridge until Loom has created the set")
if "Loom did not create the 'gametest' source set" not in build:
    fail("the deferred bridge must fail with an explanation rather than skip silently")

runner = (HARNESS / "run_matrix.sh").read_text(encoding="utf-8")
for target in (
    "run_target 26.3 25 '0.165.2+26.3' runClientGameTest",
    "run_target 1.21.11 21 '0.141.6+1.21.11' runClientGameTest",
    "run_target 1.20.1 17 '0.92.12+1.20.1' verifyClientBootSmoke",
):
    if target not in runner:
        fail(f"approved runtime matrix entry is missing: {target}")
if "trinkies_loadout_applied=true" not in runner:
    fail("the matrix no longer requires the legacy boot marker to prove the loadout was answered")
for contract in ("client-gametest-wardrobe-and-wheel-roundtrip-no-world", "client-boot-wardrobe-and-wheel-roundtrip"):
    if contract not in runner:
        fail(f"the matrix no longer names its asserted contract: {contract}")
if "trinkies_emote_wheel_slots=1" not in runner:
    fail("the matrix no longer requires the legacy marker to prove the emote wheel was accepted")
for marker in ("headless-graphics.log", "glxinfo -B", "runtime_java_version=", "client-matrix-result.txt"):
    if marker not in runner:
        fail(f"runtime evidence/diagnostic collection is missing: {marker}")

print("Trinkies client GameTest harness wiring verified: wardrobe + emote wheel round trip in the "
      "modern startup GameTest and the 1.20.1 boot fallback.")
