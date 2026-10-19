#!/usr/bin/env python3
"""Host Mesa smoke test of the *real* pinned RustGL cdylib, not a mock or phone benchmark.

Run in its own process: a native SIGSEGV is a test failure, never caught as a Python exception.
Requires Mesa EGL/GLES development libraries (libEGL.so / libGLESv2.so) and surfaceless EGL.
"""
import argparse
import ctypes as c
import os
from pathlib import Path


def main(library):
    os.environ.update(EGL_PLATFORM="surfaceless", LIBGL_ALWAYS_SOFTWARE="1", RENDERER_BACKEND="gles")
    lib = c.CDLL(str(library.resolve()))
    proc = lib.eglGetProcAddress
    proc.restype, proc.argtypes = c.c_void_p, [c.c_char_p]
    functions = {}  # Keep callable wrappers alive until after EGL teardown.

    def bind(name, result, *args):
        address = proc(name.encode())
        if not address:
            raise RuntimeError(f"RustGL resolver missing {name}")
        fn = c.CFUNCTYPE(result, *args)(address)
        functions[name] = fn
        return fn

    ptr, integer, boolean = c.c_void_p, c.c_int32, c.c_uint32
    ints = c.POINTER(integer)
    get_display = bind("eglGetDisplay", ptr, ptr)
    initialize = bind("eglInitialize", boolean, ptr, ints, ints)
    choose = bind("eglChooseConfig", boolean, ptr, ints, c.POINTER(ptr), integer, ints)
    bind_api = bind("eglBindAPI", boolean, c.c_uint32)
    create_surface = bind("eglCreatePbufferSurface", ptr, ptr, ptr, ints)
    create_context = bind("eglCreateContext", ptr, ptr, ptr, ptr, ints)
    current = bind("eglMakeCurrent", boolean, ptr, ptr, ptr, ptr)
    destroy_context = bind("eglDestroyContext", boolean, ptr, ptr)
    destroy_surface = bind("eglDestroySurface", boolean, ptr, ptr)
    terminate = bind("eglTerminate", boolean, ptr)
    egl_error = bind("eglGetError", integer)

    def check(value, label):
        if not value:
            raise RuntimeError(f"{label} failed; EGL error=0x{egl_error():04x}")
        return value

    display = check(get_display(None), "eglGetDisplay")
    surface = context = None
    try:
        major, minor = integer(), integer()
        check(initialize(display, c.byref(major), c.byref(minor)), "eglInitialize")
        check(bind_api(0x30A0), "eglBindAPI GLES")
        attrs = (integer * 13)(0x3033, 1, 0x3040, 0x40, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038)
        config, count = ptr(), integer()
        check(choose(display, attrs, c.byref(config), 1, c.byref(count)), "eglChooseConfig")
        check(count.value, "ES3 pbuffer config")
        surface = check(create_surface(display, config, (integer * 5)(0x3057, 4, 0x3056, 4, 0x3038)), "pbuffer")
        context = check(create_context(display, config, None, (integer * 3)(0x3098, 3, 0x3038)), "ES3 context")
        check(current(display, surface, surface, context), "eglMakeCurrent")
        get_string = bind("glGetString", c.c_char_p, c.c_uint32)
        for name, enum in (("GL_VERSION", 0x1F02), ("GL_RENDERER", 0x1F01), ("GL_VENDOR", 0x1F00)):
            value = get_string(enum)
            if not value:
                raise RuntimeError(f"{name} is NULL")
            print(f"{name}={value.decode(errors='replace')}", flush=True)
        get_int = bind("glGetIntegerv", None, c.c_uint32, ints)
        get_string_i = bind("glGetStringi", c.c_char_p, c.c_uint32, c.c_uint32)
        count = integer()
        get_int(0x821D, c.byref(count))
        if not 0 < count.value < 16384:
            raise RuntimeError(f"Invalid GL_NUM_EXTENSIONS={count.value}")
        for i in range(count.value):
            if not get_string_i(0x1F03, i):
                raise RuntimeError(f"NULL extension {i}")
        viewport = bind("glViewport", None, integer, integer, integer, integer)
        clear_color = bind("glClearColor", None, c.c_float, c.c_float, c.c_float, c.c_float)
        clear = bind("glClear", None, c.c_uint32)
        read = bind("glReadPixels", None, integer, integer, integer, integer, c.c_uint32, c.c_uint32, ptr)
        get_error = bind("glGetError", c.c_uint32)
        viewport(0, 0, 4, 4)
        clear_color(0.25, 0.5, 0.75, 1.0)
        clear(0x4000)
        pixel = (c.c_uint8 * 4)()
        read(1, 1, 1, 1, 0x1908, 0x1401, pixel)
        if any(abs(a - b) > 2 for a, b in zip(pixel, (64, 128, 191, 255))):
            raise RuntimeError(f"Wrong clear/readback pixel: {list(pixel)}")
        if get_error() != 0:
            raise RuntimeError("GL error after extension queries and clear/readback")
        # Exercise desktop shader translation and real GLES compile/link, not just spoofed strings.
        create_shader = bind("glCreateShader", c.c_uint32, c.c_uint32)
        source = bind("glShaderSource", None, c.c_uint32, integer, c.POINTER(c.c_char_p), ints)
        compile_shader = bind("glCompileShader", None, c.c_uint32)
        shader_iv = bind("glGetShaderiv", None, c.c_uint32, c.c_uint32, ints)
        shader_log = bind("glGetShaderInfoLog", None, c.c_uint32, integer, ints, ptr)
        create_program = bind("glCreateProgram", c.c_uint32)
        attach = bind("glAttachShader", None, c.c_uint32, c.c_uint32)
        link = bind("glLinkProgram", None, c.c_uint32)
        program_iv = bind("glGetProgramiv", None, c.c_uint32, c.c_uint32, ints)
        shaders = []
        for kind, text in ((0x8B31, b"#version 330 core\nvoid main(){gl_Position=vec4(0.0,0.0,0.0,1.0);}\n"),
                           (0x8B30, b"#version 330 core\nout vec4 color;\nvoid main(){color=vec4(1.0);}\n")):
            shader = create_shader(kind)
            if not shader:
                raise RuntimeError("glCreateShader returned zero")
            source(shader, 1, (c.c_char_p * 1)(text), None)
            compile_shader(shader)
            status = integer()
            shader_iv(shader, 0x8B81, c.byref(status))
            if status.value != 1:
                log = c.create_string_buffer(8192)
                shader_log(shader, len(log), None, log)
                raise RuntimeError(f"Translated shader failed: {log.value!r}")
            shaders.append(shader)
        program = create_program()
        for shader in shaders:
            attach(program, shader)
        link(program)
        status = integer()
        program_iv(program, 0x8B82, c.byref(status))
        if status.value != 1:
            raise RuntimeError("Translated program link failed")
        bind("glDeleteProgram", None, c.c_uint32)(program)
        for shader in shaders:
            bind("glDeleteShader", None, c.c_uint32)(shader)
        check(bind("eglSwapBuffers", boolean, ptr, ptr)(display, surface), "pbuffer swap")
        # Context rebinding exercises RustGL's driver-function cache invalidation.
        check(current(display, None, None, None), "unbind context")
        check(current(display, surface, surface, context), "rebind context")
        clear(0x4000)
        read(1, 1, 1, 1, 0x1908, 0x1401, pixel)
        if list(pixel) != [64, 128, 191, 255] or get_error() != 0:
            raise RuntimeError(f"Rebind/readback failed: {list(pixel)}")
        print("PASS: real EGL ES3 context, resolver, extension enumeration, RGBA clear/readback, desktop shader compile/link, swap and context rebind. Host Mesa only; not Android/gameplay validation.", flush=True)
    finally:
        current(display, None, None, None)
        if context:
            destroy_context(display, context)
        if surface:
            destroy_surface(display, surface)
        terminate(display)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library", type=Path)
    main(parser.parse_args().library)
