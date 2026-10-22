#include "internal.h"
#include "frame_counter.h"

// Only context.c's original entry point is renamed by CMake. All callers still use
// this public GLFW API, regardless of their GL renderer (including MobileGlues).
GLFWAPI void aerixOriginalSwapBuffers(GLFWwindow* handle);
GLFWAPI void glfwSwapBuffers(GLFWwindow* handle) {
    aerixOriginalSwapBuffers(handle);
    _GLFWwindow* window = (_GLFWwindow*) handle;
    if (_glfw.initialized && window && window->context.client != GLFW_NO_API)
        aerix_record_frame();
}
