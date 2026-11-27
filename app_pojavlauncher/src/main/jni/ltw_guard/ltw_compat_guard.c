#include "ltw_compat_guard.h"

#include <dlfcn.h>
#include <limits.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// Standard OpenGL / GLES ABI typedefs and constants (independent of host/NDK GL headers).
typedef unsigned int aerix_GLenum;
typedef unsigned int aerix_GLuint;
typedef int aerix_GLint;
typedef int aerix_GLsizei;
typedef float aerix_GLfloat;
typedef unsigned char aerix_GLboolean;
typedef char aerix_GLchar;

#define AERIX_GL_FALSE 0
#define AERIX_GL_TRUE 1
#define AERIX_GL_UNSIGNED_BYTE 0x1401u
#define AERIX_GL_UNSIGNED_SHORT 0x1403u
#define AERIX_GL_UNSIGNED_INT 0x1405u
#define AERIX_GL_UNPACK_SWAP_BYTES 0x0CF0u
#define AERIX_GL_PACK_ALIGNMENT 0x0D05u
#define AERIX_GL_FRAMEBUFFER_UNDEFINED 0x8219u
#define AERIX_GL_DRAW_FRAMEBUFFER_BINDING 0x8CA6u
#define AERIX_GL_READ_FRAMEBUFFER 0x8CA8u
#define AERIX_GL_DRAW_FRAMEBUFFER 0x8CA9u
#define AERIX_GL_READ_FRAMEBUFFER_BINDING 0x8CAAu
#define AERIX_GL_FRAMEBUFFER_COMPLETE 0x8CD5u

typedef void (*pfn_glShaderSource)(aerix_GLuint shader,
                                   aerix_GLsizei count,
                                   const aerix_GLchar* const* string,
                                   const aerix_GLint* length);
typedef aerix_GLenum (*pfn_glCheckFramebufferStatus)(aerix_GLenum target);
typedef void (*pfn_glClearTexImage)(aerix_GLuint texture,
                                    aerix_GLint level,
                                    aerix_GLenum format,
                                    aerix_GLenum type,
                                    const void* data);
typedef void (*pfn_glPixelStorei)(aerix_GLenum pname, aerix_GLint param);
typedef void (*pfn_glPixelStoref)(aerix_GLenum pname, aerix_GLfloat param);
typedef void (*pfn_glMultiDrawElementsBaseVertex)(aerix_GLenum mode,
                                                  const aerix_GLsizei* count,
                                                  aerix_GLenum type,
                                                  const void* const* indices,
                                                  aerix_GLsizei drawcount,
                                                  const aerix_GLint* basevertex);
typedef void (*pfn_glGetIntegerv)(aerix_GLenum pname, aerix_GLint* data);
typedef void (*pfn_glBindFramebuffer)(aerix_GLenum target, aerix_GLuint framebuffer);
typedef aerix_GLboolean (*pfn_glIsFramebuffer)(aerix_GLuint framebuffer);
typedef void* (*pfn_glXGetProcAddress)(const char* procname);

static pthread_mutex_t g_guard_mutex = PTHREAD_MUTEX_INITIALIZER;
static void* g_driver_handle = NULL;
static aerix_egl_get_proc_fn g_real_egl_get_proc = NULL;

static pfn_glGetIntegerv g_real_glGetIntegerv = NULL;
static pfn_glBindFramebuffer g_real_glBindFramebuffer = NULL;
static pfn_glIsFramebuffer g_real_glIsFramebuffer = NULL;

static pfn_glShaderSource g_real_glShaderSource = NULL;
static pfn_glShaderSource g_real_glShaderSourceARB = NULL;
static pfn_glCheckFramebufferStatus g_real_glCheckFramebufferStatus = NULL;
static pfn_glCheckFramebufferStatus g_real_glCheckFramebufferStatusEXT = NULL;
static pfn_glClearTexImage g_real_glClearTexImage = NULL;
static pfn_glPixelStorei g_real_glPixelStorei = NULL;
static pfn_glPixelStoref g_real_glPixelStoref = NULL;
static pfn_glMultiDrawElementsBaseVertex g_real_glMultiDrawElementsBaseVertex = NULL;
static pfn_glXGetProcAddress g_real_glXGetProcAddress = NULL;
static pfn_glXGetProcAddress g_real_glXGetProcAddressARB = NULL;

static _Thread_local aerix_GLboolean tls_unpack_swap_bytes = AERIX_GL_FALSE;

bool aerix_ltw_guard_is_active(void) {
    const char* val = getenv("AERIX_LTW_COMPAT_GUARD");
    return val != NULL && val[0] == '1' && val[1] == '\0';
}

static void* resolve_raw_unlocked(const char* name) {
    if (name == NULL) return NULL;
    if (g_real_egl_get_proc != NULL) {
        void* sym = g_real_egl_get_proc(name);
        if (sym != NULL) return sym;
    }
    if (g_driver_handle != NULL) {
        return dlsym(g_driver_handle, name);
    }
    return NULL;
}

static void ensure_aux_procs_locked(void) {
    if (g_real_glGetIntegerv == NULL) {
        g_real_glGetIntegerv = (pfn_glGetIntegerv) resolve_raw_unlocked("glGetIntegerv");
    }
    if (g_real_glBindFramebuffer == NULL) {
        g_real_glBindFramebuffer = (pfn_glBindFramebuffer) resolve_raw_unlocked("glBindFramebuffer");
    }
    if (g_real_glIsFramebuffer == NULL) {
        g_real_glIsFramebuffer = (pfn_glIsFramebuffer) resolve_raw_unlocked("glIsFramebuffer");
    }
    if (g_real_glPixelStorei == NULL) {
        g_real_glPixelStorei = (pfn_glPixelStorei) resolve_raw_unlocked("glPixelStorei");
    }
}

void aerix_ltw_guard_set_driver(void* driver_handle, aerix_egl_get_proc_fn real_egl_get_proc) {
    pthread_mutex_lock(&g_guard_mutex);
    if (driver_handle != NULL) {
        g_driver_handle = driver_handle;
    }
    if (real_egl_get_proc != NULL && real_egl_get_proc != aerix_ltw_egl_get_proc_address) {
        g_real_egl_get_proc = real_egl_get_proc;
    }
    ensure_aux_procs_locked();
    pthread_mutex_unlock(&g_guard_mutex);
}

void* aerix_ltw_guard_get_driver_handle(void) {
    pthread_mutex_lock(&g_guard_mutex);
    void* handle = g_driver_handle;
    pthread_mutex_unlock(&g_guard_mutex);
    return handle;
}

// LTW shader_wrapper.c fails to increment `offset` when `count > 1`, and uses
// negative `length[i]` as an unsigned memcpy size. Coalesce multi-part or
// negative-length shader sources into one NUL-terminated buffer before calling LTW.
static void shader_source_common(pfn_glShaderSource real_fn,
                                 aerix_GLuint shader,
                                 aerix_GLsizei count,
                                 const aerix_GLchar* const* string,
                                 const aerix_GLint* length) {
    if (real_fn == NULL || count <= 0 || string == NULL) return;

    if (count == 1 && string[0] != NULL && (length == NULL || length[0] >= 0)) {
        real_fn(shader, count, string, length);
        return;
    }

    size_t total_len = 0;
    for (aerix_GLsizei i = 0; i < count; i++) {
        if (string[i] == NULL) continue;
        size_t seg_len = (length != NULL && length[i] >= 0)
                ? (size_t) length[i]
                : strlen(string[i]);
        if (seg_len > (size_t) INT_MAX - total_len - 1) {
            real_fn(shader, count, string, length);
            return;
        }
        total_len += seg_len;
    }

    aerix_GLchar* merged = (aerix_GLchar*) malloc(total_len + 1);
    if (merged == NULL) {
        real_fn(shader, count, string, length);
        return;
    }

    size_t offset = 0;
    for (aerix_GLsizei i = 0; i < count; i++) {
        if (string[i] == NULL) continue;
        size_t seg_len = (length != NULL && length[i] >= 0)
                ? (size_t) length[i]
                : strlen(string[i]);
        if (seg_len > 0) {
            memcpy(merged + offset, string[i], seg_len);
            offset += seg_len;
        }
    }
    merged[offset] = '\0';

    const aerix_GLchar* merged_ptr = merged;
    aerix_GLint merged_len = (aerix_GLint) offset;
    real_fn(shader, 1, &merged_ptr, &merged_len);
    free(merged);
}

static void aerix_glShaderSource(aerix_GLuint shader,
                                 aerix_GLsizei count,
                                 const aerix_GLchar* const* string,
                                 const aerix_GLint* length) {
    shader_source_common(g_real_glShaderSource, shader, count, string, length);
}

static void aerix_glShaderSourceARB(aerix_GLuint shader,
                                    aerix_GLsizei count,
                                    const aerix_GLchar* const* string,
                                    const aerix_GLint* length) {
    shader_source_common(g_real_glShaderSourceARB, shader, count, string, length);
}

// LTW framebuffer.c dereferences `get_framebuffer(target)` without a NULL check when
// glCheckFramebufferStatus returns GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT.
// FBO 0 (or untracked FBO IDs) have no entry in LTW's framebuffer map.
static aerix_GLenum check_framebuffer_status_common(pfn_glCheckFramebufferStatus real_fn,
                                                    aerix_GLenum target) {
    if (real_fn == NULL) return 0;
    if (g_real_glGetIntegerv != NULL) {
        aerix_GLenum binding = (target == AERIX_GL_READ_FRAMEBUFFER)
                ? AERIX_GL_READ_FRAMEBUFFER_BINDING
                : AERIX_GL_DRAW_FRAMEBUFFER_BINDING;
        aerix_GLint bound_fbo = 0;
        g_real_glGetIntegerv(binding, &bound_fbo);
        if (bound_fbo == 0) {
            return AERIX_GL_FRAMEBUFFER_COMPLETE;
        }
        if (g_real_glIsFramebuffer != NULL && !g_real_glIsFramebuffer((aerix_GLuint) bound_fbo)) {
            return AERIX_GL_FRAMEBUFFER_UNDEFINED;
        }
    }
    return real_fn(target);
}

static aerix_GLenum aerix_glCheckFramebufferStatus(aerix_GLenum target) {
    return check_framebuffer_status_common(g_real_glCheckFramebufferStatus, target);
}

static aerix_GLenum aerix_glCheckFramebufferStatusEXT(aerix_GLenum target) {
    return check_framebuffer_status_common(g_real_glCheckFramebufferStatusEXT, target);
}

// LTW main.c passes `data` directly to glClearBufferiv (crashing when data == NULL, which
// is valid in GL_ARB_clear_texture) and leaves GL_DRAW_FRAMEBUFFER bound to 0 afterwards.
static void aerix_glClearTexImage(aerix_GLuint texture,
                                  aerix_GLint level,
                                  aerix_GLenum format,
                                  aerix_GLenum type,
                                  const void* data) {
    if (g_real_glClearTexImage == NULL) return;
    static const uint32_t kZeroClearData[4] = { 0u, 0u, 0u, 0u };

    aerix_GLint prev_draw_fbo = 0;
    if (g_real_glGetIntegerv != NULL) {
        g_real_glGetIntegerv(AERIX_GL_DRAW_FRAMEBUFFER_BINDING, &prev_draw_fbo);
    }

    g_real_glClearTexImage(texture, level, format, type, data != NULL ? data : kZeroClearData);

    if (g_real_glBindFramebuffer != NULL && prev_draw_fbo > 0) {
        g_real_glBindFramebuffer(AERIX_GL_DRAW_FRAMEBUFFER, (aerix_GLuint) prev_draw_fbo);
    }
}

// LTW unpack.c duplicates `case GL_PACK_ALIGNMENT:` without a `break`, falling through
// into `current_context->unpack.swap_bytes = param`. Re-sync UNPACK_SWAP_BYTES afterwards.
static void aerix_glPixelStorei(aerix_GLenum pname, aerix_GLint param) {
    if (g_real_glPixelStorei == NULL) return;
    if (pname == AERIX_GL_UNPACK_SWAP_BYTES) {
        tls_unpack_swap_bytes = (param != 0) ? AERIX_GL_TRUE : AERIX_GL_FALSE;
    }
    g_real_glPixelStorei(pname, param);
    if (pname == AERIX_GL_PACK_ALIGNMENT) {
        g_real_glPixelStorei(AERIX_GL_UNPACK_SWAP_BYTES, tls_unpack_swap_bytes);
    }
}

static void aerix_glPixelStoref(aerix_GLenum pname, aerix_GLfloat param) {
    if (g_real_glPixelStoref == NULL) return;
    if (pname == AERIX_GL_UNPACK_SWAP_BYTES) {
        tls_unpack_swap_bytes = (param != 0.0f) ? AERIX_GL_TRUE : AERIX_GL_FALSE;
    }
    g_real_glPixelStoref(pname, param);
    if (pname == AERIX_GL_PACK_ALIGNMENT && g_real_glPixelStorei != NULL) {
        g_real_glPixelStorei(AERIX_GL_UNPACK_SWAP_BYTES, tls_unpack_swap_bytes);
    }
}

// LTW basevertex.c allocates a stack VLA `indirect_passes[drawcount]` and divides by
// `typeBytes` (which is -1 for invalid types). Guard invalid args and cap stack VLA size.
static void aerix_glMultiDrawElementsBaseVertex(aerix_GLenum mode,
                                                const aerix_GLsizei* count,
                                                aerix_GLenum type,
                                                const void* const* indices,
                                                aerix_GLsizei drawcount,
                                                const aerix_GLint* basevertex) {
    if (g_real_glMultiDrawElementsBaseVertex == NULL) return;
    if (drawcount <= 0 || count == NULL || indices == NULL || basevertex == NULL) return;
    if (type != AERIX_GL_UNSIGNED_BYTE &&
        type != AERIX_GL_UNSIGNED_SHORT &&
        type != AERIX_GL_UNSIGNED_INT) {
        return;
    }
    const aerix_GLsizei kMaxBatch = 4096;
    aerix_GLsizei done = 0;
    while (done < drawcount) {
        aerix_GLsizei batch = drawcount - done;
        if (batch > kMaxBatch) batch = kMaxBatch;
        g_real_glMultiDrawElementsBaseVertex(
                mode, count + done, type, indices + done, batch, basevertex + done);
        done += batch;
    }
}

static void* aerix_glXGetProcAddress(const char* procname) {
    if (g_real_glXGetProcAddress == NULL) return NULL;
    void* sym = g_real_glXGetProcAddress(procname);
    if (sym == NULL) return NULL;
    return aerix_ltw_wrap_proc(procname, sym);
}

static void* aerix_glXGetProcAddressARB(const char* procname) {
    if (g_real_glXGetProcAddressARB == NULL) return NULL;
    void* sym = g_real_glXGetProcAddressARB(procname);
    if (sym == NULL) return NULL;
    return aerix_ltw_wrap_proc(procname, sym);
}

void* aerix_ltw_wrap_proc(const char* procname, void* original) {
    if (procname == NULL || original == NULL) return original;

    pthread_mutex_lock(&g_guard_mutex);
    ensure_aux_procs_locked();

    void* wrapped = original;
    if (strcmp(procname, "glShaderSource") == 0) {
        g_real_glShaderSource = (pfn_glShaderSource) original;
        wrapped = (void*) aerix_glShaderSource;
    } else if (strcmp(procname, "glShaderSourceARB") == 0) {
        g_real_glShaderSourceARB = (pfn_glShaderSource) original;
        wrapped = (void*) aerix_glShaderSourceARB;
    } else if (strcmp(procname, "glCheckFramebufferStatus") == 0) {
        g_real_glCheckFramebufferStatus = (pfn_glCheckFramebufferStatus) original;
        wrapped = (void*) aerix_glCheckFramebufferStatus;
    } else if (strcmp(procname, "glCheckFramebufferStatusEXT") == 0) {
        g_real_glCheckFramebufferStatusEXT = (pfn_glCheckFramebufferStatus) original;
        wrapped = (void*) aerix_glCheckFramebufferStatusEXT;
    } else if (strcmp(procname, "glClearTexImage") == 0) {
        g_real_glClearTexImage = (pfn_glClearTexImage) original;
        wrapped = (void*) aerix_glClearTexImage;
    } else if (strcmp(procname, "glPixelStorei") == 0) {
        g_real_glPixelStorei = (pfn_glPixelStorei) original;
        wrapped = (void*) aerix_glPixelStorei;
    } else if (strcmp(procname, "glPixelStoref") == 0) {
        g_real_glPixelStoref = (pfn_glPixelStoref) original;
        wrapped = (void*) aerix_glPixelStoref;
    } else if (strcmp(procname, "glMultiDrawElementsBaseVertex") == 0) {
        g_real_glMultiDrawElementsBaseVertex = (pfn_glMultiDrawElementsBaseVertex) original;
        wrapped = (void*) aerix_glMultiDrawElementsBaseVertex;
    } else if (strcmp(procname, "glXGetProcAddress") == 0) {
        g_real_glXGetProcAddress = (pfn_glXGetProcAddress) original;
        wrapped = (void*) aerix_glXGetProcAddress;
    } else if (strcmp(procname, "glXGetProcAddressARB") == 0) {
        g_real_glXGetProcAddressARB = (pfn_glXGetProcAddress) original;
        wrapped = (void*) aerix_glXGetProcAddressARB;
    } else if (strcmp(procname, "eglGetProcAddress") == 0) {
        if ((aerix_egl_get_proc_fn) original != aerix_ltw_egl_get_proc_address) {
            g_real_egl_get_proc = (aerix_egl_get_proc_fn) original;
        }
        wrapped = (void*) aerix_ltw_egl_get_proc_address;
    }

    pthread_mutex_unlock(&g_guard_mutex);
    return wrapped;
}

void* aerix_ltw_egl_get_proc_address(const char* procname) {
    if (procname == NULL) return NULL;
    pthread_mutex_lock(&g_guard_mutex);
    void* sym = resolve_raw_unlocked(procname);
    pthread_mutex_unlock(&g_guard_mutex);
    if (sym == NULL) return NULL;
    return aerix_ltw_wrap_proc(procname, sym);
}
