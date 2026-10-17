#include <jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <time.h>
#include <algorithm>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>

static double millis() {
    timespec time{};
    clock_gettime(CLOCK_MONOTONIC, &time);
    return time.tv_sec * 1000.0 + time.tv_nsec / 1000000.0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_aerix_tools_RendererProbeService_runNative(JNIEnv* env, jobject, jstring path, jint gles) {
    const char* name = env->GetStringUTFChars(path, nullptr);
    void* handle = dlopen(name, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(path, name);
    if (!handle) return env->NewStringUTF("ERROR\nUnable to load renderer library");
#define LOAD(func) auto func = reinterpret_cast<decltype(&::func)>(dlsym(handle, #func))
    LOAD(eglGetDisplay); LOAD(eglInitialize); LOAD(eglChooseConfig); LOAD(eglBindAPI);
    LOAD(eglCreatePbufferSurface); LOAD(eglCreateContext); LOAD(eglMakeCurrent);
    LOAD(eglDestroySurface); LOAD(eglDestroyContext); LOAD(eglTerminate); LOAD(eglGetProcAddress);
    if (!eglGetDisplay || !eglInitialize || !eglChooseConfig || !eglBindAPI || !eglCreatePbufferSurface || !eglCreateContext || !eglMakeCurrent || !eglDestroySurface || !eglDestroyContext || !eglTerminate || !eglGetProcAddress) {
        dlclose(handle);
        return env->NewStringUTF("ERROR\nRenderer does not export the required EGL entry points");
    }
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
    std::string error;
    std::ostringstream output;
    do {
        EGLint major, minor, count;
        if (display == EGL_NO_DISPLAY || !eglInitialize(display, &major, &minor)) { error = "EGL initialization failed"; break; }
        EGLint configArgs[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, gles == 3 ? 0x40 : EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE};
        EGLConfig config;
        if (!eglChooseConfig(display, configArgs, &config, 1, &count) || count == 0 || !eglBindAPI(EGL_OPENGL_ES_API)) { error = "No suitable pbuffer configuration"; break; }
        const EGLint surfaceArgs[] = {EGL_WIDTH, 256, EGL_HEIGHT, 256, EGL_NONE};
        surface = eglCreatePbufferSurface(display, config, surfaceArgs);
        const EGLint contextArgs[] = {EGL_CONTEXT_CLIENT_VERSION, gles, EGL_NONE};
        context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextArgs);
        if (surface == EGL_NO_SURFACE || context == EGL_NO_CONTEXT || !eglMakeCurrent(display, surface, surface, context)) { error = "Could not create test context"; break; }
#define GLLOAD(func) auto func = reinterpret_cast<decltype(&::func)>(dlsym(handle, #func)); if (!func) func = reinterpret_cast<decltype(&::func)>(eglGetProcAddress(#func))
        GLLOAD(glClearColor); GLLOAD(glClear); GLLOAD(glFinish); GLLOAD(glGetError); GLLOAD(glReadPixels); GLLOAD(glGetString);
        if (!glClearColor || !glClear || !glFinish || !glGetError || !glReadPixels || !glGetString) { error = "Required GL entry points are missing"; break; }
        std::vector<double> samples;
        for (int frame = 0; frame < 130; ++frame) {
            double start = millis();
            glClearColor(0.2f, 0.4f, 0.6f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glFinish();
            if (frame >= 10) samples.push_back(millis() - start);
        }
        unsigned char pixel[4] = {};
        glReadPixels(128, 128, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        if (glGetError() != GL_NO_ERROR || std::abs(int(pixel[0]) - 51) > 3 || std::abs(int(pixel[1]) - 102) > 3 || std::abs(int(pixel[2]) - 153) > 3) { error = "Pixel correctness check failed"; break; }
        double average = 0;
        for (double sample : samples) average += sample;
        average /= samples.size();
        std::sort(samples.begin(), samples.end());
        auto text = [&](GLenum key) { const GLubyte* value = glGetString(key); return value ? reinterpret_cast<const char*>(value) : "Unknown"; };
        output << "PASS\n" << text(GL_VENDOR) << '\n' << text(GL_RENDERER) << '\n' << text(GL_VERSION) << '\n' << average << '\n' << samples[113] << "\n120";
    } while (false);
    if (display != EGL_NO_DISPLAY) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        eglTerminate(display);
    }
    // The service exits after unbinding; keep driver destructors out of this JNI call.
    std::string result = error.empty() ? output.str() : "ERROR\n" + error;
    return env->NewStringUTF(result.c_str());
}
