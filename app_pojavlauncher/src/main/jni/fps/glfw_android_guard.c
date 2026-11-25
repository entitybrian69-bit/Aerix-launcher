#ifndef _POSIX_C_SOURCE
#define _POSIX_C_SOURCE 200809L
#endif

#include "internal.h"
#include "android_egl_context_hook.h"

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <time.h>

#ifndef GLFW_ANDROID_WINDOW_MODE_SURFACE
#define GLFW_ANDROID_WINDOW_MODE_SURFACE 2
#endif

// Original symbols compiled from the upstream glfw submodule (renamed via CMake COMPILE_DEFINITIONS).
extern EGLSurface aerixOriginalManageEglSurfaceAndroid(_GLFWwindow* window);
extern void aerixOriginalUpdateNativeWindowDimensions(_GLFWwindow* window);
extern int aerixOriginalDisablePrerotationAndroid(struct ANativeWindow* nativeWindow);
extern JNIEXPORT void JNICALL aerixOriginalNativeSurfaceCreated(JNIEnv* env, jclass clazz, jobject surface);
extern JNIEXPORT void JNICALL aerixOriginalNativeSurfaceDestroyed(JNIEnv* env, jclass clazz);
extern JNIEXPORT void JNICALL aerixOriginalNativeSurfaceUpdated(JNIEnv* env, jclass clazz);

static pthread_mutex_t g_nw_guard_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_nw_guard_cond = PTHREAD_COND_INITIALIZER;
static bool g_nw_valid = false;
static bool g_in_manage_egl = false;
static bool g_last_from_surface_ok = false;
static bool g_in_native_surface_destroy = false;
static bool g_egl_surface_released = false;

ANativeWindow* aerix_ANativeWindow_fromSurface(JNIEnv* env, jobject surface) {
    if (env == NULL || surface == NULL) {
        g_last_from_surface_ok = false;
        return NULL;
    }
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    g_last_from_surface_ok = (window != NULL);
    return window;
}

void aerix_ANativeWindow_acquire(ANativeWindow* window) {
    if (window == NULL) return;
    ANativeWindow_acquire(window);
}

void aerix_ANativeWindow_release(ANativeWindow* window) {
    if (window == NULL) return;
    ANativeWindow_release(window);
}

int32_t aerix_ANativeWindow_setBuffersGeometry(ANativeWindow* window,
                                               int32_t width,
                                               int32_t height,
                                               int32_t format) {
    if (window == NULL) return -22;
    return ANativeWindow_setBuffersGeometry(window, width, height, format);
}

int32_t aerix_ANativeWindow_getWidth(ANativeWindow* window) {
    if (window == NULL) return 0;
    return ANativeWindow_getWidth(window);
}

int32_t aerix_ANativeWindow_getHeight(ANativeWindow* window) {
    if (window == NULL) return 0;
    return ANativeWindow_getHeight(window);
}

// Upstream android_window.c checks `surfaceInUse` outside `nw_egl_mutex` and calls
// `pthread_cond_wait` without a predicate loop, creating a lost-wakeup deadlock if
// `_glfwManageEglSurfaceAndroid` broadcasts `nw_egl_cond` before `pthread_cond_wait` enters.
// Guard the surface-destruction wait with a short timed wait that checks `g_egl_surface_released`.
int aerix_pthread_cond_wait(pthread_cond_t* cond, pthread_mutex_t* mutex) {
    pthread_mutex_lock(&g_nw_guard_mutex);
    bool destroying = g_in_native_surface_destroy;
    bool already_released = g_egl_surface_released;
    pthread_mutex_unlock(&g_nw_guard_mutex);

    if (!destroying) {
        return pthread_cond_wait(cond, mutex);
    }
    if (already_released) {
        return 0;
    }

    for (;;) {
        struct timespec ts;
        if (clock_gettime(CLOCK_REALTIME, &ts) != 0) {
            return pthread_cond_wait(cond, mutex);
        }
        ts.tv_nsec += 5000000L; // 5 ms
        if (ts.tv_nsec >= 1000000000L) {
            ts.tv_sec += 1;
            ts.tv_nsec -= 1000000000L;
        }
        int rc = pthread_cond_timedwait(cond, mutex, &ts);
        if (rc != ETIMEDOUT) {
            return rc;
        }
        pthread_mutex_lock(&g_nw_guard_mutex);
        bool released = g_egl_surface_released;
        pthread_mutex_unlock(&g_nw_guard_mutex);
        if (released) {
            return 0;
        }
    }
}

void updateNativeWindowDimensions(_GLFWwindow* window) {
    if (window == NULL) return;
    pthread_mutex_lock(&g_nw_guard_mutex);
    bool can_update = g_nw_valid;
    pthread_mutex_unlock(&g_nw_guard_mutex);
    if (!can_update) return;
    aerixOriginalUpdateNativeWindowDimensions(window);
}

EGLSurface _glfwManageEglSurfaceAndroid(_GLFWwindow* window) {
    if (window == NULL) return EGL_NO_SURFACE;
    pthread_mutex_lock(&g_nw_guard_mutex);
    g_in_manage_egl = true;
    g_egl_surface_released = false;
    pthread_mutex_unlock(&g_nw_guard_mutex);

    EGLSurface surface = aerixOriginalManageEglSurfaceAndroid(window);

    pthread_mutex_lock(&g_nw_guard_mutex);
    g_in_manage_egl = false;
    if (window->android.mode != GLFW_ANDROID_WINDOW_MODE_SURFACE) {
        g_egl_surface_released = true;
    }
    pthread_cond_broadcast(&g_nw_guard_cond);
    pthread_mutex_unlock(&g_nw_guard_mutex);
    return surface;
}

int _glfwDisablePrerotationAndroid(struct ANativeWindow* nativeWindow) {
    if (nativeWindow == NULL) return -1;
    return aerixOriginalDisablePrerotationAndroid(nativeWindow);
}

JNIEXPORT void JNICALL
Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated(JNIEnv* env,
                                                             jclass clazz,
                                                             jobject surface) {
    if (env == NULL || surface == NULL) return;
    ANativeWindow* probe = ANativeWindow_fromSurface(env, surface);
    if (probe == NULL) {
        return;
    }
    ANativeWindow_release(probe);

    pthread_mutex_lock(&g_nw_guard_mutex);
    while (g_in_manage_egl) {
        pthread_cond_wait(&g_nw_guard_cond, &g_nw_guard_mutex);
    }
    if (g_nw_valid) {
        g_nw_valid = false;
        g_in_native_surface_destroy = true;
        pthread_mutex_unlock(&g_nw_guard_mutex);
        aerixOriginalNativeSurfaceDestroyed(env, clazz);
        pthread_mutex_lock(&g_nw_guard_mutex);
        g_in_native_surface_destroy = false;
        while (g_in_manage_egl) {
            pthread_cond_wait(&g_nw_guard_cond, &g_nw_guard_mutex);
        }
    }
    g_last_from_surface_ok = false;
    aerixOriginalNativeSurfaceCreated(env, clazz, surface);
    if (!g_last_from_surface_ok) {
        g_nw_valid = false;
        pthread_mutex_unlock(&g_nw_guard_mutex);
        aerixOriginalNativeSurfaceDestroyed(env, clazz);
        return;
    }
    g_nw_valid = true;
    g_egl_surface_released = false;
    pthread_mutex_unlock(&g_nw_guard_mutex);
}

JNIEXPORT void JNICALL
Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated(JNIEnv* env, jclass clazz) {
    pthread_mutex_lock(&g_nw_guard_mutex);
    bool valid = g_nw_valid;
    pthread_mutex_unlock(&g_nw_guard_mutex);
    if (!valid) return;
    aerixOriginalNativeSurfaceUpdated(env, clazz);
}

JNIEXPORT void JNICALL
Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed(JNIEnv* env, jclass clazz) {
    pthread_mutex_lock(&g_nw_guard_mutex);
    while (g_in_manage_egl) {
        pthread_cond_wait(&g_nw_guard_cond, &g_nw_guard_mutex);
    }
    bool was_valid = g_nw_valid;
    g_nw_valid = false;
    if (!was_valid) {
        pthread_mutex_unlock(&g_nw_guard_mutex);
        return;
    }
    g_in_native_surface_destroy = true;
    pthread_mutex_unlock(&g_nw_guard_mutex);

    aerixOriginalNativeSurfaceDestroyed(env, clazz);

    pthread_mutex_lock(&g_nw_guard_mutex);
    g_in_native_surface_destroy = false;
    pthread_mutex_unlock(&g_nw_guard_mutex);
}
