
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
get_target_property(AERIX_GLFW_SOURCE_DIR glfw SOURCE_DIR)
set_property(SOURCE "${AERIX_GLFW_SOURCE_DIR}/context.c"
    DIRECTORY "${AERIX_GLFW_SOURCE_DIR}" APPEND PROPERTY
    COMPILE_DEFINITIONS "glfwSwapBuffers=aerixOriginalSwapBuffers")
target_sources(glfw PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/fps/glfw_frame_hook.c")
target_include_directories(glfw PRIVATE "${AERIX_GLFW_SOURCE_DIR}" "${CMAKE_CURRENT_SOURCE_DIR}/fps")
target_link_libraries(glfw PRIVATE aerix_frame_counter)


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

target_include_directories(pojavexec PRIVATE .)
target_link_libraries(pojavexec PRIVATE log mojoexec)

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

# ------ MobileGlues info getter ------

add_library(mobileglues_info_getter SHARED
        mobileglues/mobileglues_info_getter.cpp
)

find_library(android-lib android)
find_library(log-lib log)

target_compile_options(mobileglues_info_getter PRIVATE
        -Oz
        -fdata-sections
        -ffunction-sections
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -fno-rtti
        -fno-exceptions
        -flto
)

target_link_options(mobileglues_info_getter PRIVATE
        -Wl,--gc-sections
        -Wl,--strip-all
        -flto
)

target_include_directories(mobileglues_info_getter PRIVATE
        mobileglues
)

target_link_libraries(mobileglues_info_getter
        ${android-lib}
        ${log-lib}
        dl
)
# Loaded only by the non-exported capability-probe service process.
add_library(aerix_vulkan_probe SHARED renderer/vulkan_probe.cpp)
target_compile_features(aerix_vulkan_probe PRIVATE cxx_std_17)
target_link_libraries(aerix_vulkan_probe PRIVATE dl)
