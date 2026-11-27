"""Checks Aerix's LTW compatibility fixes and native guard wiring."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]


def test_c_guard_unit() -> None:
    src_guard = root / "app_pojavlauncher/src/main/jni/ltw_guard/ltw_compat_guard.c"
    src_test = root / "tests/ltw_compat_guard_test.c"
    inc_dir = root / "app_pojavlauncher/src/main/jni/ltw_guard"
    with tempfile.TemporaryDirectory() as tmpdir:
        exe = Path(tmpdir) / "ltw_compat_guard_test"
        cmd = [
            "gcc",
            "-std=c11",
            "-Wall",
            "-Wextra",
            "-Werror",
            f"-I{inc_dir}",
            str(src_guard),
            str(src_test),
            "-ldl",
            "-lpthread",
            "-o",
            str(exe),
        ]
        subprocess.run(cmd, check=True)
        out = subprocess.run([str(exe)], check=True, capture_output=True, text=True)
        assert "ALL PASSED" in out.stdout, out.stdout


def test_wiring() -> None:
    gles_spec = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/renderer/impl/GLESRenderSpec.java").read_text()
    for needle in [
        'envMap.put("LTW_HIDE_BUFFER_STORAGE", "1")',
        'envMap.put("LTW_COHERENT_DYNAMIC_STORAGE", "0")',
        'envMap.put("AERIX_LTW_COMPAT_GUARD", "1")',
    ]:
        assert needle in gles_spec, f"Missing {needle} in GLESRenderSpec.java"

    cmake = (root / "app_pojavlauncher/src/main/jni/CMakeLists.txt").read_text()
    for needle in [
        "add_library(aerix_ltw_guard SHARED ltw_guard/ltw_compat_guard.c)",
        '"_glfwInitEGL=aerixOriginalInitEGL"',
        "ltw_guard/glfw_ltw_guard.c",
        "aerix_ltw_guard",
    ]:
        assert needle in cmake, f"Missing {needle} in CMakeLists.txt"

    dlopen_hook = (root / "app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c").read_text()
    for needle in [
        "aerix_ltw_guard_set_driver",
        "ndlsym_bugfix",
        "aerix_ltw_wrap_proc",
        '"ndlsym", "(JJ)J"',
    ]:
        assert needle in dlopen_hook, f"Missing {needle} in lwjgl_dlopen_hook.c"


if __name__ == "__main__":
    test_c_guard_unit()
    test_wiring()
    print("ltw_compat_guard_smoke: ALL PASSED")
