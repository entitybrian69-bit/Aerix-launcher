#include "internal.h"
#include "ltw_compat_guard.h"

#include <dlfcn.h>
#include <mojoexec.h>

extern GLFWbool aerixOriginalInitEGL(void);

static GLFWglproc APIENTRY aerix_egl_get_proc_hook(const char* procname) {
    return (GLFWglproc) aerix_ltw_egl_get_proc_address(procname);
}

GLFWbool _glfwInitEGL(void) {
    if (!aerixOriginalInitEGL()) {
        return GLFW_FALSE;
    }
    if (aerix_ltw_guard_is_active() && _glfw.egl.GetProcAddress != NULL) {
        void* handle = _glfw.egl.handle != NULL ? _glfw.egl.handle : mojoexec_acq_egl_handle();
        aerix_ltw_guard_set_driver(handle, (aerix_egl_get_proc_fn) _glfw.egl.GetProcAddress);
        _glfw.egl.GetProcAddress = aerix_egl_get_proc_hook;
    }
    return GLFW_TRUE;
}
