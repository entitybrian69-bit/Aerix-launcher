#define _POSIX_C_SOURCE 200809L

#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "ltw_compat_guard.h"

typedef unsigned int GLenum;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef float GLfloat;
typedef unsigned char GLboolean;
typedef char GLchar;

#define GL_FALSE 0
#define GL_TRUE 1
#define GL_UNSIGNED_INT 0x1405u
#define GL_UNPACK_SWAP_BYTES 0x0CF0u
#define GL_PACK_ALIGNMENT 0x0D05u
#define GL_FRAMEBUFFER_UNDEFINED 0x8219u
#define GL_DRAW_FRAMEBUFFER_BINDING 0x8CA6u
#define GL_READ_FRAMEBUFFER 0x8CA8u
#define GL_DRAW_FRAMEBUFFER 0x8CA9u
#define GL_READ_FRAMEBUFFER_BINDING 0x8CAAu
#define GL_FRAMEBUFFER 0x8D40u
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5u
#define GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT 0x8CD7u

static char g_last_shader[256];
static GLint g_last_shader_len = -1;
static GLsizei g_last_shader_count = -1;

static GLint g_draw_fbo = 0;
static GLint g_read_fbo = 0;
static GLboolean g_fbo_tracked = GL_TRUE;
static int g_check_fbo_calls = 0;

static int g_clear_tex_calls = 0;
static uint32_t g_clear_tex_first_word = 0xDEADBEEFu;

static GLboolean g_unpack_swap_bytes_state = GL_FALSE;
static int g_multidraw_calls = 0;
static GLsizei g_multidraw_total = 0;

static void mock_glGetIntegerv(GLenum pname, GLint* data) {
    if (pname == GL_DRAW_FRAMEBUFFER_BINDING) *data = g_draw_fbo;
    else if (pname == GL_READ_FRAMEBUFFER_BINDING) *data = g_read_fbo;
    else *data = 0;
}

static void mock_glBindFramebuffer(GLenum target, GLuint fb) {
    if (target == GL_DRAW_FRAMEBUFFER || target == GL_FRAMEBUFFER) g_draw_fbo = (GLint) fb;
    if (target == GL_READ_FRAMEBUFFER || target == GL_FRAMEBUFFER) g_read_fbo = (GLint) fb;
}

static GLboolean mock_glIsFramebuffer(GLuint fb) {
    (void) fb;
    return g_fbo_tracked;
}

static void mock_glShaderSource(GLuint shader,
                                GLsizei count,
                                const GLchar* const* string,
                                const GLint* length) {
    (void) shader;
    g_last_shader_count = count;
    assert(count == 1);
    size_t len = (length != NULL && length[0] >= 0) ? (size_t) length[0] : strlen(string[0]);
    assert(len < sizeof(g_last_shader));
    memcpy(g_last_shader, string[0], len);
    g_last_shader[len] = '\0';
    g_last_shader_len = (GLint) len;
}

static GLenum mock_glCheckFramebufferStatus(GLenum target) {
    g_check_fbo_calls++;
    GLint fbo = (target == GL_READ_FRAMEBUFFER) ? g_read_fbo : g_draw_fbo;
    // Simulate LTW's NULL dereference crash if called on FBO 0 or untracked FBO
    assert(fbo > 0 && g_fbo_tracked);
    return GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;
}

static void mock_glClearTexImage(GLuint tex, GLint lvl, GLenum fmt, GLenum type, const void* data) {
    (void) tex; (void) lvl; (void) fmt; (void) type;
    g_clear_tex_calls++;
    assert(data != NULL); // LTW crashes if NULL reaches here
    g_clear_tex_first_word = *(const uint32_t*) data;
    // Simulate LTW clobbering GL_DRAW_FRAMEBUFFER to 0
    g_draw_fbo = 0;
}

static void mock_glPixelStorei(GLenum pname, GLint param) {
    if (pname == GL_UNPACK_SWAP_BYTES) {
        g_unpack_swap_bytes_state = (param == GL_TRUE) ? GL_TRUE : GL_FALSE;
    } else if (pname == GL_PACK_ALIGNMENT) {
        // Simulate LTW's missing break falling through to swap_bytes = param
        g_unpack_swap_bytes_state = (param == GL_TRUE) ? GL_TRUE : GL_FALSE;
    }
}

static void mock_glPixelStoref(GLenum pname, GLfloat param) {
    mock_glPixelStorei(pname, (GLint) param);
}

static void mock_glMultiDrawElementsBaseVertex(GLenum mode,
                                               const GLsizei* count,
                                               GLenum type,
                                               const void* const* indices,
                                               GLsizei drawcount,
                                               const GLint* basevertex) {
    (void) mode; (void) count; (void) indices; (void) basevertex;
    assert(drawcount > 0 && drawcount <= 4096);
    assert(type == GL_UNSIGNED_INT);
    g_multidraw_calls++;
    g_multidraw_total += drawcount;
}

static void* mock_egl_get_proc(const char* name) {
    if (strcmp(name, "glGetIntegerv") == 0) return (void*) mock_glGetIntegerv;
    if (strcmp(name, "glBindFramebuffer") == 0) return (void*) mock_glBindFramebuffer;
    if (strcmp(name, "glIsFramebuffer") == 0) return (void*) mock_glIsFramebuffer;
    if (strcmp(name, "glShaderSource") == 0) return (void*) mock_glShaderSource;
    if (strcmp(name, "glCheckFramebufferStatus") == 0) return (void*) mock_glCheckFramebufferStatus;
    if (strcmp(name, "glClearTexImage") == 0) return (void*) mock_glClearTexImage;
    if (strcmp(name, "glPixelStorei") == 0) return (void*) mock_glPixelStorei;
    if (strcmp(name, "glPixelStoref") == 0) return (void*) mock_glPixelStoref;
    if (strcmp(name, "glMultiDrawElementsBaseVertex") == 0) return (void*) mock_glMultiDrawElementsBaseVertex;
    return NULL;
}

int main(void) {
    unsetenv("AERIX_LTW_COMPAT_GUARD");
    assert(!aerix_ltw_guard_is_active());
    setenv("AERIX_LTW_COMPAT_GUARD", "1", 1);
    assert(aerix_ltw_guard_is_active());

    int dummy_handle = 42;
    aerix_ltw_guard_set_driver(&dummy_handle, mock_egl_get_proc);
    assert(aerix_ltw_guard_get_driver_handle() == &dummy_handle);

    // 1. glShaderSource with count > 1 and negative length[i]
    typedef void (*pfn_shader_src)(GLuint, GLsizei, const GLchar* const*, const GLint*);
    pfn_shader_src shader_src = (pfn_shader_src) aerix_ltw_egl_get_proc_address("glShaderSource");
    assert(shader_src != NULL && shader_src != mock_glShaderSource);
    const char* parts[3] = { "#version 150\n", "void main() {", "}\nXYZ" };
    GLint lens[3] = { -1, 13, 2 };
    shader_src(1, 3, parts, lens);
    assert(g_last_shader_count == 1);
    assert(strcmp(g_last_shader, "#version 150\nvoid main() {}\n") == 0);
    assert(g_last_shader_len == (GLint) strlen("#version 150\nvoid main() {}\n"));

    // 2. glCheckFramebufferStatus on FBO 0, untracked FBO, and valid FBO
    typedef GLenum (*pfn_check_fb)(GLenum);
    pfn_check_fb check_fb = (pfn_check_fb) aerix_ltw_egl_get_proc_address("glCheckFramebufferStatus");
    g_draw_fbo = 0;
    assert(check_fb(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);
    assert(g_check_fbo_calls == 0);
    g_draw_fbo = 9;
    g_fbo_tracked = GL_FALSE;
    assert(check_fb(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_UNDEFINED);
    assert(g_check_fbo_calls == 0);
    g_fbo_tracked = GL_TRUE;
    assert(check_fb(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT);
    assert(g_check_fbo_calls == 1);

    // 3. glClearTexImage with NULL data + FBO restoration
    typedef void (*pfn_clear_tex)(GLuint, GLint, GLenum, GLenum, const void*);
    pfn_clear_tex clear_tex = (pfn_clear_tex) aerix_ltw_egl_get_proc_address("glClearTexImage");
    g_draw_fbo = 7;
    clear_tex(3, 0, 0x1908u, 0x1401u, NULL);
    assert(g_clear_tex_calls == 1);
    assert(g_clear_tex_first_word == 0);
    assert(g_draw_fbo == 7);

    // 4. glPixelStorei / glPixelStoref PACK_ALIGNMENT fallthrough compensation
    typedef void (*pfn_ps_i)(GLenum, GLint);
    typedef void (*pfn_ps_f)(GLenum, GLfloat);
    pfn_ps_i ps_i = (pfn_ps_i) aerix_ltw_egl_get_proc_address("glPixelStorei");
    pfn_ps_f ps_f = (pfn_ps_f) aerix_ltw_egl_get_proc_address("glPixelStoref");
    ps_i(GL_UNPACK_SWAP_BYTES, GL_FALSE);
    assert(g_unpack_swap_bytes_state == GL_FALSE);
    ps_i(GL_PACK_ALIGNMENT, 1);
    assert(g_unpack_swap_bytes_state == GL_FALSE);
    ps_i(GL_UNPACK_SWAP_BYTES, GL_TRUE);
    ps_f(GL_PACK_ALIGNMENT, 4.0f);
    assert(g_unpack_swap_bytes_state == GL_TRUE);

    // 5. glMultiDrawElementsBaseVertex invalid/large drawcount
    typedef void (*pfn_mdebv)(GLenum, const GLsizei*, GLenum, const void* const*, GLsizei, const GLint*);
    pfn_mdebv mdebv = (pfn_mdebv) aerix_ltw_egl_get_proc_address("glMultiDrawElementsBaseVertex");
    GLsizei counts[5000] = { 0 };
    const void* idxs[5000] = { 0 };
    GLint bvs[5000] = { 0 };
    mdebv(0x0004u, counts, GL_UNSIGNED_INT, idxs, -5, bvs);
    mdebv(0x0004u, counts, 0x9999u, idxs, 10, bvs);
    assert(g_multidraw_calls == 0);
    mdebv(0x0004u, counts, GL_UNSIGNED_INT, idxs, 5000, bvs);
    assert(g_multidraw_calls == 2);
    assert(g_multidraw_total == 5000);

    printf("ltw_compat_guard_test: ALL PASSED\n");
    return 0;
}
