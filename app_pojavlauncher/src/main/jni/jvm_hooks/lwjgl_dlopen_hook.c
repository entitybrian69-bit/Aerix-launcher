//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfcn.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>

#define TAG __FILE_NAME__
#include <log.h>

#include <mojoexec.h>
#include "ltw_compat_guard.h"

/**
 * Basically a verbatim implementation of ndlopen(), found at
 * https://github.com/PojavLauncherTeam/lwjgl3/blob/3.3.1/modules/lwjgl/core/src/generated/c/linux/org_lwjgl_system_linux_DynamicLinkLoader.c#L11
 * but with our own additions for stuff like vulkanmod.
 */
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) (uintptr_t) filename_ptr;

    // Oveeride vulkan loading to let us load vulkan ourselves
    if(strstr(filename, "libvulkan.so") == filename) {
        printf("LWJGL linkerhook: replacing load for libvulkan.so with custom driver\n");
        return (jlong) (uintptr_t) mojoexec_acq_vulkan_handle();
    }
    // Load renderer using egl_acquire
    if(strstr(filename, "libGLMojo.so") == filename) {
        printf("LWJGL linkerhook: replacing OpenGL with renderspec driver\n");
        void* handle = mojoexec_acq_egl_handle();
        if(handle != NULL && aerix_ltw_guard_is_active()) {
            aerix_ltw_guard_set_driver(
                    handle,
                    (aerix_egl_get_proc_fn) dlsym(handle, "eglGetProcAddress"));
        }
        return (jlong) (uintptr_t) handle;
    }

    // This hook also serves the task of mitigating a bug: the idea is that since, on Android 10 and
    // earlier, the linker doesn't really do namespace nesting.
    // It is not a problem as most of the libraries are in the launcher path, but when you try to run
    // VulkanMod which loads shaderc outside of the default jni libs directory through this method,
    // it can't load it because the path is not in the allowed paths for the anonymous namesapce.
    // This method fixes the issue by being in libpojavexec, and thus being in the classloader namespace

    int mode = (int)jmode;
    return (jlong) (uintptr_t) dlopen(filename, mode);
}

static jlong ndlsym_bugfix(__attribute__((unused)) JNIEnv *env,
                           __attribute__((unused)) jclass class,
                           jlong handle_ptr,
                           jlong name_ptr) {
    void* handle = (void*) (uintptr_t) handle_ptr;
    const char* name = (const char*) (uintptr_t) name_ptr;
    if(handle == NULL || name == NULL) return 0;

    void* sym = dlsym(handle, name);
    if(sym != NULL && aerix_ltw_guard_is_active() && handle == aerix_ltw_guard_get_driver_handle()) {
        sym = aerix_ltw_wrap_proc(name, sym);
    }
    return (jlong) (uintptr_t) sym;
}

/**
 * Install the LWJGL dlopen hook. This allows us to mitigate linker bugs and add custom library overrides.
 */
void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() hook");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if(dynamicLinkLoader == NULL) {
        LOGE("Failed to find the target class");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod ndlopenMethod[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, ndlopenMethod, 1) != 0) {
        LOGE("Failed to register the hooked method");
        (*env)->ExceptionClear(env);
    }
    JNINativeMethod ndlsymMethod[] = {
            {"ndlsym", "(JJ)J", &ndlsym_bugfix}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, ndlsymMethod, 1) != 0) {
        LOGW("Failed to register DynamicLinkLoader.ndlsym hook");
        (*env)->ExceptionClear(env);
    }
}