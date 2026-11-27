
cmake_minimum_required(VERSION 3.22.1)

# ------ MojoExec ------

add_subdirectory(mojoexec)

# ------   GLFW   ------

set(GLFW_BUILD_WAYLAND  OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_X11      OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_ANDROID  ON  CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GLFW_INSTALL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS   ON  CACHE BOOL "" FORCE)

add_subdirectory(glfw)

# Shared by GLFW's game JVM and the Android FPS overlay. No renderer interposition,
# swap-interval changes, Java callback per frame, or edits to the GLFW submodule.
add_library(aerix_frame_counter SHARED fps/frame_counter.c fps/frame_counter_jni.c)
set_target_properties(aerix_frame_counter PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON)
add_library(aerix_ltw_guard SHARED ltw_guard/ltw_compat_guard.c)
set_target_properties(aerix_ltw_guard PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON)
target_link_libraries(aerix_ltw_guard PRIVATE dl log)
get_target_property(AERIX_GLFW_SOURCE_DIR glfw SOURCE_DIR)
set_property(SOURCE "${AERIX_GLFW_SOURCE_DIR}/context.c"
    DIRECTORY "${AERIX_GLFW_SOURCE_DIR}" APPEND PROPERTY
    COMPILE_DEFINITIONS "glfwSwapBuffers=aerixOriginalSwapBuffers")
set_property(SOURCE "${AERIX_GLFW_SOURCE_DIR}/egl_context.c"
    DIRECTORY "${AERIX_GLFW_SOURCE_DIR}" APPEND PROPERTY
    COMPILE_DEFINITIONS "_glfwInitEGL=aerixOriginalInitEGL")
set_property(SOURCE "${AERIX_GLFW_SOURCE_DIR}/android_window.c"
    DIRECTORY "${AERIX_GLFW_SOURCE_DIR}" APPEND PROPERTY
    COMPILE_DEFINITIONS
        "ANativeWindow_fromSurface=aerix_ANativeWindow_fromSurface"
        "ANativeWindow_acquire=aerix_ANativeWindow_acquire"
        "ANativeWindow_release=aerix_ANativeWindow_release"
        "ANativeWindow_setBuffersGeometry=aerix_ANativeWindow_setBuffersGeometry"
        "ANativeWindow_getWidth=aerix_ANativeWindow_getWidth"
        "ANativeWindow_getHeight=aerix_ANativeWindow_getHeight"
        "pthread_cond_wait=aerix_pthread_cond_wait"
        "updateNativeWindowDimensions=aerixOriginalUpdateNativeWindowDimensions"
        "_glfwManageEglSurfaceAndroid=aerixOriginalManageEglSurfaceAndroid"
        "Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceCreated=aerixOriginalNativeSurfaceCreated"
        "Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed=aerixOriginalNativeSurfaceDestroyed"
        "Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated=aerixOriginalNativeSurfaceUpdated")
set_property(SOURCE "${AERIX_GLFW_SOURCE_DIR}/android_window_hook.c"
    DIRECTORY "${AERIX_GLFW_SOURCE_DIR}" APPEND PROPERTY
    COMPILE_DEFINITIONS
        "_glfwDisablePrerotationAndroid=aerixOriginalDisablePrerotationAndroid")
target_sources(glfw PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/fps/glfw_frame_hook.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/fps/glfw_android_guard.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/ltw_guard/glfw_ltw_guard.c")
target_include_directories(glfw PRIVATE
    "${AERIX_GLFW_SOURCE_DIR}"
    "${CMAKE_CURRENT_SOURCE_DIR}/fps"
    "${CMAKE_CURRENT_SOURCE_DIR}/ltw_guard")
target_link_libraries(glfw PRIVATE aerix_frame_counter aerix_ltw_guard)


target_compile_options(glfw PRIVATE -Wno-nullability-completeness)

# ------   SDL   ------

set(SDL_SHARED       ON CACHE BOOL "" FORCE)
set(SDL_VIDEO        ON CACHE BOOL "" FORCE)
set(SDL_AUDIO		 ON CACHE BOOL "" FORCE)
set(SDL_STATIC       OFF CACHE BOOL "" FORCE)
set(SDL_CAMERA		 OFF CACHE BOOL "" FORCE)
set(SDL_TESTS		 OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES	 OFF CACHE BOOL "" FORCE)
set(SDL3_MAINPROJECT OFF CACHE BOOL "" FORCE)
add_subdirectory(sdl)
target_link_libraries(SDL3-shared PRIVATE mojoexec)

# ----------------------

project("MojoLauncher")

add_library(pojavexec SHARED
        affinity.c
        jvm_hooks/emui_iterator_fix_hook.c
        jvm_hooks/java_exec_hooks.c
        jvm_hooks/lwjgl_dlopen_hook.c
        jre_launcher/jre_launcher.c
        jre_launcher/native_library_hook.c
        jre_launcher/elf_hinter.c
        jre_launcher/abort_wait.c
        utils.c
        stdio_is.c
        minibridge.c
)

target_include_directories(pojavexec PRIVATE . ltw_guard)
target_link_libraries(pojavexec PRIVATE log mojoexec aerix_ltw_guard)

add_library(pojavexec_awt SHARED
		awt/awt.c
		awt/awt_window.c
		awt/awt_util.c
		awt/awt_input.c
		awt/awt_clipboard.c
        awt/awt_mapper.c
)

target_link_libraries(pojavexec_awt PRIVATE android log)

add_library(awt_headless SHARED 
	awt_xawt/dummy.c
)

add_library(awt_xawt SHARED
        awt_xawt/xawt_fake.c
)
target_link_libraries(awt_xawt PRIVATE awt_headless)
