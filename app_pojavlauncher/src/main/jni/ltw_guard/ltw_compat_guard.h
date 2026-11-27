#ifndef AERIX_LTW_COMPAT_GUARD_H
#define AERIX_LTW_COMPAT_GUARD_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* (*aerix_egl_get_proc_fn)(const char* procname);

/**
 * Returns true when the current launch enabled Aerix's LTW compatibility guard
 * via AERIX_LTW_COMPAT_GUARD=1.
 */
bool aerix_ltw_guard_is_active(void);

/**
 * Records the loaded LTW driver handle and its real eglGetProcAddress function
 * so wrappers can resolve auxiliary GL functions (glGetIntegerv, etc.) safely.
 */
void aerix_ltw_guard_set_driver(void* driver_handle, aerix_egl_get_proc_fn real_egl_get_proc);

/** Returns the recorded LTW driver handle, or NULL if unset. */
void* aerix_ltw_guard_get_driver_handle(void);

/**
 * Returns a crash-guarded wrapper for `procname` when `original` is non-NULL
 * and `procname` is one of the wrapped LTW entry points; otherwise returns `original`.
 */
void* aerix_ltw_wrap_proc(const char* procname, void* original);

/**
 * Replacement for libltw's eglGetProcAddress when AERIX_LTW_COMPAT_GUARD=1.
 * Resolves via the real eglGetProcAddress (falling back to dlsym on the LTW handle)
 * and applies aerix_ltw_wrap_proc().
 */
void* aerix_ltw_egl_get_proc_address(const char* procname);

#ifdef __cplusplus
}
#endif

#endif // AERIX_LTW_COMPAT_GUARD_H
