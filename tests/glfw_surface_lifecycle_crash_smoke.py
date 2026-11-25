#!/usr/bin/env python3
"""Regression checks for libandroid.so SIGSEGV / surface lifecycle race fixes and :game WebView deferral."""
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JNI = ROOT / "app_pojavlauncher/src/main/jni"
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

cmake = (JNI / "CMakeLists.txt").read_text(encoding="utf-8")
guard_c = (JNI / "fps/glfw_android_guard.c").read_text(encoding="utf-8")
platform_java = (JAVA / "game/platform/Platform.java").read_text(encoding="utf-8")
glfw_backend = (JAVA / "game/platform/backend/GLFWBackend.java").read_text(encoding="utf-8")
game_view = (JAVA / "game/GameView.java").read_text(encoding="utf-8")
sv_provider = (JAVA / "render/SurfaceViewSurfaceProvider.java").read_text(encoding="utf-8")
tv_provider = (JAVA / "render/TextureViewSurfaceProvider.java").read_text(encoding="utf-8")
game_activity = (JAVA / "game/GameActivity.java").read_text(encoding="utf-8")
app_java = (JAVA / "PojavApplication.java").read_text(encoding="utf-8")
skin_kt = (JAVA / "aerix/components/AerixPlayerSkin.kt").read_text(encoding="utf-8")

# 1. CMake wires glfw_android_guard.c without editing the glfw submodule.
assert "fps/glfw_android_guard.c" in cmake
for macro in (
    "ANativeWindow_fromSurface=aerix_ANativeWindow_fromSurface",
    "ANativeWindow_acquire=aerix_ANativeWindow_acquire",
    "ANativeWindow_release=aerix_ANativeWindow_release",
    "ANativeWindow_setBuffersGeometry=aerix_ANativeWindow_setBuffersGeometry",
    "ANativeWindow_getWidth=aerix_ANativeWindow_getWidth",
    "ANativeWindow_getHeight=aerix_ANativeWindow_getHeight",
    "pthread_cond_wait=aerix_pthread_cond_wait",
    "updateNativeWindowDimensions=aerixOriginalUpdateNativeWindowDimensions",
    "_glfwManageEglSurfaceAndroid=aerixOriginalManageEglSurfaceAndroid",
    "Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated=aerixOriginalNativeSurfaceCreated",
    "Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed=aerixOriginalNativeSurfaceDestroyed",
    "Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated=aerixOriginalNativeSurfaceUpdated",
    "_glfwDisablePrerotationAndroid=aerixOriginalDisablePrerotationAndroid",
):
    assert macro in cmake, f"Missing CMake guard macro: {macro}"

# 2. Platform & backends clear mPendingSurface on destroy, serialize surface ops, and check isValid().
assert "SURFACE_LOCK" in platform_java
assert "public static void destroySurface()" in platform_java
assert "public static void updateSurfaceDimensions()" in platform_java
assert "mPendingSurface = null;" in platform_java.split("public static void destroySurface()")[1].split("}")[0]
assert "surface.isValid()" in platform_java
assert "mPendingSurface.isValid()" in platform_java
assert "Platform.destroySurface();" in game_view
assert "Platform.updateSurfaceDimensions();" in game_view
assert "mHasSurface" in glfw_backend
assert "surface.isValid()" in glfw_backend
assert "mLastFixedWidth == windowWidth && mLastFixedHeight == windowHeight" in sv_provider
assert "surface.isValid()" in sv_provider
assert "mActiveSurface.release()" in tv_provider
assert "launcherGLView.isAttachedToWindow()" in game_activity

# 3. :game defers Chromium WebView init until AerixPlayerSkin actually builds a WebView.
assert 'procName.endsWith(":game")' in app_java
assert "ensureWebViewDataDirectory(" in app_java and "ensureWebViewDataDirectory(viewContext)" in skin_kt

# 4. Compile & stress-test glfw_android_guard.c under gcc -std=c99 -Wall -Wextra -Werror.
gcc = shutil.which("gcc")
if gcc is not None:
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        inc = tmp_path / "inc"
        (inc / "android").mkdir(parents=True)
        (inc / "android/native_window.h").write_text(
            "#pragma once\n"
            "#include <stdint.h>\n"
            "typedef struct ANativeWindow { int alive; int refs; int w; int h; } ANativeWindow;\n"
            "void ANativeWindow_acquire(ANativeWindow* window);\n"
            "void ANativeWindow_release(ANativeWindow* window);\n"
            "int32_t ANativeWindow_setBuffersGeometry(ANativeWindow* window, int32_t w, int32_t h, int32_t fmt);\n"
            "int32_t ANativeWindow_getWidth(ANativeWindow* window);\n"
            "int32_t ANativeWindow_getHeight(ANativeWindow* window);\n",
            encoding="utf-8",
        )
        (inc / "android/native_window_jni.h").write_text(
            "#pragma once\n"
            "#include <jni.h>\n"
            "#include <android/native_window.h>\n"
            "ANativeWindow* ANativeWindow_fromSurface(JNIEnv* env, jobject surface);\n",
            encoding="utf-8",
        )
        (inc / "jni.h").write_text(
            "#pragma once\n"
            "#include <stdint.h>\n"
            "typedef int32_t jint; typedef void* jobject; typedef jobject jclass;\n"
            "typedef struct JNINativeInterface_ JNIEnv;\n"
            "#define JNIEXPORT\n#define JNICALL\n",
            encoding="utf-8",
        )
        (inc / "internal.h").write_text(
            "#pragma once\n"
            "typedef void* EGLSurface;\n"
            "#define EGL_NO_SURFACE ((EGLSurface)0)\n"
            "struct ANativeWindow;\n"
            "typedef struct _GLFWwindow { struct { int width; int height; int visualId; int mode; } android; } _GLFWwindow;\n",
            encoding="utf-8",
        )
        (inc / "android_egl_context_hook.h").write_text(
            "#pragma once\n"
            "#include \"internal.h\"\n"
            "EGLSurface _glfwManageEglSurfaceAndroid(_GLFWwindow* window);\n",
            encoding="utf-8",
        )
        mock_android_window = tmp_path / "mock_android_window.c"
        mock_android_window.write_text(
            "#include \"internal.h\"\n"
            "#include \"android_egl_context_hook.h\"\n"
            "#include <android/native_window.h>\n"
            "#include <android/native_window_jni.h>\n"
            "#include <assert.h>\n"
            "#include <pthread.h>\n"
            "#include <stdatomic.h>\n"
            "#include <stdbool.h>\n"
            "#include <unistd.h>\n"
            "static _GLFWwindow* surfaceOwner = NULL;\n"
            "static atomic_bool surfaceDestroyed = true;\n"
            "static atomic_bool surfaceUpdated = false;\n"
            "static atomic_bool surfaceInUse = false;\n"
            "static struct ANativeWindow* nativeWindow = NULL;\n"
            "static pthread_mutex_t nw_egl_mutex = PTHREAD_MUTEX_INITIALIZER;\n"
            "static pthread_cond_t nw_egl_cond = PTHREAD_COND_INITIALIZER;\n"
            "void aerix_set_surface_owner(_GLFWwindow* w) { surfaceOwner = w; }\n"
            "void updateNativeWindowDimensions(_GLFWwindow* window) {\n"
            "    ANativeWindow_setBuffersGeometry(nativeWindow, 0, 0, window->android.visualId);\n"
            "    window->android.width = ANativeWindow_getWidth(nativeWindow);\n"
            "    window->android.height = ANativeWindow_getHeight(nativeWindow);\n"
            "    surfaceUpdated = false;\n"
            "}\n"
            "EGLSurface _glfwManageEglSurfaceAndroid(_GLFWwindow* window) {\n"
            "    int wantedMode = (window != surfaceOwner || surfaceDestroyed) ? 1 : 2;\n"
            "    if (window->android.mode != wantedMode) {\n"
            "        if (window->android.mode == 2) {\n"
            "            pthread_mutex_lock(&nw_egl_mutex);\n"
            "            pthread_cond_broadcast(&nw_egl_cond);\n"
            "            pthread_mutex_unlock(&nw_egl_mutex);\n"
            "            surfaceInUse = false;\n"
            "        }\n"
            "        if (wantedMode == 2) {\n"
            "            updateNativeWindowDimensions(window);\n"
            "            surfaceInUse = true;\n"
            "            assert(nativeWindow != NULL && nativeWindow->alive);\n"
            "        }\n"
            "        window->android.mode = wantedMode;\n"
            "    }\n"
            "    return (EGLSurface)(uintptr_t)wantedMode;\n"
            "}\n"
            "int _glfwDisablePrerotationAndroid(struct ANativeWindow* nw) { return nw->alive ? 0 : -1; }\n"
            "JNIEXPORT void JNICALL Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated(JNIEnv* env, jclass clazz, jobject surface) {\n"
            "    (void)clazz;\n"
            "    ANativeWindow* w = ANativeWindow_fromSurface(env, surface);\n"
            "    ANativeWindow_acquire(w);\n"
            "    nativeWindow = w;\n"
            "    surfaceDestroyed = false;\n"
            "}\n"
            "JNIEXPORT void JNICALL Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated(JNIEnv* env, jclass clazz) {\n"
            "    (void)env;(void)clazz; surfaceUpdated = true;\n"
            "}\n"
            "JNIEXPORT void JNICALL Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed(JNIEnv* env, jclass clazz) {\n"
            "    (void)env;(void)clazz; surfaceDestroyed = true;\n"
            "    if (surfaceInUse) {\n"
            "        pthread_mutex_lock(&nw_egl_mutex);\n"
            "        pthread_cond_wait(&nw_egl_cond, &nw_egl_mutex);\n"
            "        ANativeWindow_release(nativeWindow);\n"
            "        nativeWindow = NULL;\n"
            "        pthread_mutex_unlock(&nw_egl_mutex);\n"
            "    } else {\n"
            "        ANativeWindow_release(nativeWindow);\n"
            "        nativeWindow = NULL;\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        driver = tmp_path / "driver.c"
        driver.write_text(
            "#define _DEFAULT_SOURCE\n"
            "#include \"internal.h\"\n"
            "#include \"android_egl_context_hook.h\"\n"
            "#include <android/native_window.h>\n"
            "#include <android/native_window_jni.h>\n"
            "#include <assert.h>\n"
            "#include <pthread.h>\n"
            "#include <stdatomic.h>\n"
            "#include <stdbool.h>\n"
            "#include <stdlib.h>\n"
            "#include <unistd.h>\n"
            "extern void aerix_set_surface_owner(_GLFWwindow* w);\n"
            "extern void updateNativeWindowDimensions(_GLFWwindow* window);\n"
            "extern int _glfwDisablePrerotationAndroid(struct ANativeWindow* nw);\n"
            "extern JNIEXPORT void JNICALL Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated(JNIEnv*, jclass, jobject);\n"
            "extern JNIEXPORT void JNICALL Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated(JNIEnv*, jclass);\n"
            "extern JNIEXPORT void JNICALL Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed(JNIEnv*, jclass);\n"
            "static atomic_int g_null_violations = 0;\n"
            "static ANativeWindow g_fake_nw = { .alive = 1, .refs = 0, .w = 1280, .h = 720 };\n"
            "static jobject DEAD_SURFACE = (jobject)0x1;\n"
            "static jobject LIVE_SURFACE = (jobject)0x2;\n"
            "ANativeWindow* ANativeWindow_fromSurface(JNIEnv* env, jobject surface) {\n"
            "    (void)env;\n"
            "    if (surface != LIVE_SURFACE) return NULL;\n"
            "    g_fake_nw.alive = 1;\n"
            "    g_fake_nw.refs++;\n"
            "    return &g_fake_nw;\n"
            "}\n"
            "void ANativeWindow_acquire(ANativeWindow* w) {\n"
            "    if (!w || !w->alive) { g_null_violations++; return; }\n"
            "    w->refs++;\n"
            "}\n"
            "void ANativeWindow_release(ANativeWindow* w) {\n"
            "    if (!w) { g_null_violations++; return; }\n"
            "    if (w->refs > 0) w->refs--;\n"
            "    if (w->refs <= 1) w->alive = 0;\n"
            "}\n"
            "int32_t ANativeWindow_setBuffersGeometry(ANativeWindow* w, int32_t wi, int32_t he, int32_t f) {\n"
            "    (void)wi;(void)he;(void)f;\n"
            "    if (!w || !w->alive) { g_null_violations++; return -22; }\n"
            "    return 0;\n"
            "}\n"
            "int32_t ANativeWindow_getWidth(ANativeWindow* w) {\n"
            "    if (!w || !w->alive) { g_null_violations++; return 0; }\n"
            "    return w->w;\n"
            "}\n"
            "int32_t ANativeWindow_getHeight(ANativeWindow* w) {\n"
            "    if (!w || !w->alive) { g_null_violations++; return 0; }\n"
            "    return w->h;\n"
            "}\n"
            "static atomic_bool g_stop = false;\n"
            "static _GLFWwindow g_win = { .android = { .width = 854, .height = 480, .visualId = 1, .mode = 0 } };\n"
            "static void* ui_thread(void* unused) {\n"
            "    (void)unused;\n"
            "    JNIEnv* env = (JNIEnv*)0x10;\n"
            "    for (int i = 0; i < 400; i++) {\n"
            "        Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated(env, NULL, DEAD_SURFACE);\n"
            "        Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed(env, NULL);\n"
            "        Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated(env, NULL, LIVE_SURFACE);\n"
            "        Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated(env, NULL);\n"
            "        usleep(50);\n"
            "        Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed(env, NULL);\n"
            "        Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated(env, NULL);\n"
            "    }\n"
            "    g_stop = true;\n"
            "    return NULL;\n"
            "}\n"
            "int main(void) {\n"
            "    assert(_glfwDisablePrerotationAndroid(NULL) == -1);\n"
            "    aerix_set_surface_owner(&g_win);\n"
            "    Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated((JNIEnv*)0x10, NULL, NULL);\n"
            "    Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated((JNIEnv*)0x10, NULL, DEAD_SURFACE);\n"
            "    Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed((JNIEnv*)0x10, NULL);\n"
            "    assert(_glfwManageEglSurfaceAndroid(&g_win) != EGL_NO_SURFACE);\n"
            "    assert(g_win.android.mode == 1);\n"
            "    pthread_t ui;\n"
            "    pthread_create(&ui, NULL, ui_thread, NULL);\n"
            "    while (!g_stop) {\n"
            "        _glfwManageEglSurfaceAndroid(&g_win);\n"
            "        updateNativeWindowDimensions(&g_win);\n"
            "        assert(g_win.android.width > 0 && g_win.android.height > 0);\n"
            "    }\n"
            "    pthread_join(ui, NULL);\n"
            "    assert(g_null_violations == 0);\n"
            "    return 0;\n"
            "}\n",
            encoding="utf-8",
        )
        obj_mock = tmp_path / "mock_android_window.o"
        obj_guard = tmp_path / "glfw_android_guard.o"
        obj_driver = tmp_path / "driver.o"
        bin_path = tmp_path / "glfw_guard_smoke"
        subprocess.run(
            [
                gcc, "-std=c99", "-Wall", "-Wextra", "-Werror", "-pthread",
                "-DANativeWindow_fromSurface=aerix_ANativeWindow_fromSurface",
                "-DANativeWindow_acquire=aerix_ANativeWindow_acquire",
                "-DANativeWindow_release=aerix_ANativeWindow_release",
                "-DANativeWindow_setBuffersGeometry=aerix_ANativeWindow_setBuffersGeometry",
                "-DANativeWindow_getWidth=aerix_ANativeWindow_getWidth",
                "-DANativeWindow_getHeight=aerix_ANativeWindow_getHeight",
                "-Dpthread_cond_wait=aerix_pthread_cond_wait",
                "-DupdateNativeWindowDimensions=aerixOriginalUpdateNativeWindowDimensions",
                "-D_glfwManageEglSurfaceAndroid=aerixOriginalManageEglSurfaceAndroid",
                "-DJava_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated=aerixOriginalNativeSurfaceCreated",
                "-DJava_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed=aerixOriginalNativeSurfaceDestroyed",
                "-DJava_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated=aerixOriginalNativeSurfaceUpdated",
                "-D_glfwDisablePrerotationAndroid=aerixOriginalDisablePrerotationAndroid",
                f"-I{inc}", "-c", str(mock_android_window), "-o", str(obj_mock),
            ],
            check=True,
        )
        subprocess.run(
            [
                gcc, "-std=c99", "-Wall", "-Wextra", "-Werror", "-pthread",
                f"-I{inc}", "-c", str(JNI / "fps/glfw_android_guard.c"), "-o", str(obj_guard),
            ],
            check=True,
        )
        subprocess.run(
            [
                gcc, "-std=c11", "-Wall", "-Wextra", "-Werror", "-pthread",
                f"-I{inc}", "-c", str(driver), "-o", str(obj_driver),
            ],
            check=True,
        )
        subprocess.run(
            [gcc, "-pthread", str(obj_mock), str(obj_guard), str(obj_driver), "-o", str(bin_path)],
            check=True,
        )
        subprocess.run([str(bin_path)], check=True, timeout=15)

print("glfw_surface_lifecycle_crash_smoke: all checks passed")
