#!/usr/bin/env python3
"""Regression checks for silent :game startup/runtime crash fixes in jre_launcher."""
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JNI = ROOT / "app_pojavlauncher/src/main/jni"
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

jre_launcher = (JNI / "jre_launcher/jre_launcher.c").read_text(encoding="utf-8")
abort_wait = (JNI / "jre_launcher/abort_wait.c").read_text(encoding="utf-8")
hook_c = (JNI / "jre_launcher/native_library_hook.c").read_text(encoding="utf-8")
stdio_c = (JNI / "stdio_is.c").read_text(encoding="utf-8")
game_runner = (JAVA / "utils/jre/GameRunner.java").read_text(encoding="utf-8")

# 1. prepareSignalHandlers() must preserve SIGPIPE=SIG_IGN and must NOT wipe SIGSEGV/debuggerd/RT signals.
assert "sigid < NSIG" not in jre_launcher, "Do not blanket-reset all signals SIGHUP..NSIG"
assert "if(sigid == SIGSEGV) clean_sa.sa_handler = SIG_IGN" not in jre_launcher, \
    "Do not set SIGSEGV to SIG_IGN across ART threads"
assert "sigaction(SIGPIPE, &ignore_sa, NULL)" in jre_launcher, \
    "SIGPIPE must remain SIG_IGN so closed sockets/pipes do not silently kill :game"

# 2. abort_wait() must loop while futex_word == 1 so EINTR / spurious wakeups do not call System.exit(0).
assert "while (__atomic_load_n(&futex_word, __ATOMIC_ACQUIRE) == 1)" in abort_wait, \
    "abort_wait() must re-check futex_word after FUTEX_WAIT returns on EINTR or spurious wakeup"
assert "syscall(SYS_futex, &futex_word, FUTEX_WAKE, 1)" in abort_wait, \
    "abort_call() must wake with count 1, not NULL"

# 3. installClassLoaderHooks() must bail cleanly instead of calling RegisterNatives on uninitialized hookMethod.
assert "goto err;" in hook_c
unsupported_branch = hook_c.split('logger_append_cstr("jre_launcher: unsupported NativeLibraries.load signature");')[1].split("}")[0]
assert "goto err;" in unsupported_branch or "return false;" in unsupported_branch, \
    "Unsupported NativeLibraries.load signature must abort hook installation immediately"

# 4. Synchronous log breadcrumbs survive native crashes inside nativeLoadJVM().
assert "void logger_append_cstr(const char* text)" in stdio_c
assert "logger_append_cstr(buf);" in jre_launcher
assert 'LaunchDiagnostics.mark(activity, "Starting Java VM ("' in game_runner
assert 'LaunchDiagnostics.fail(activity, "Stopped: Java VM failed to load", e);' in game_runner

# 5. If gcc is available, compile and run a live C harness verifying abort_wait EINTR safety + hook fallback.
gcc = shutil.which("gcc")
if gcc is not None:
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        inc = tmp_path / "inc"
        (inc / "android").mkdir(parents=True)
        (inc / "jvm_hooks").mkdir(parents=True)
        (inc / "android/log.h").write_text(
            "#pragma once\n"
            "#define ANDROID_LOG_ERROR 6\n#define ANDROID_LOG_WARN 5\n"
            "#define ANDROID_LOG_INFO 4\n#define ANDROID_LOG_DEBUG 3\n"
            "static inline int __android_log_print(int p, const char* t, const char* fmt, ...) "
            "{ (void)p;(void)t;(void)fmt;return 0; }\n",
            encoding="utf-8",
        )
        (inc / "elf_defs.h").write_text("#pragma once\n", encoding="utf-8")
        (inc / "jvm_hooks/jvm_hooks.h").write_text(
            "#pragma once\n#include <jni.h>\n"
            "void hookExec(JNIEnv *env);\n"
            "void installLwjglDlopenHook(JNIEnv *env);\n"
            "void installEMUIIteratorMititgation(JNIEnv *env);\n",
            encoding="utf-8",
        )
        (inc / "jni.h").write_text(
            "#pragma once\n"
            "#include <stdint.h>\n#include <stdbool.h>\n#include <stddef.h>\n"
            "typedef int32_t jint; typedef int64_t jlong; typedef uint8_t jboolean; typedef int32_t jsize;\n"
            "typedef void* jobject; typedef jobject jclass; typedef jobject jstring;\n"
            "typedef jobject jthrowable; typedef jobject jobjectArray; typedef void* jmethodID;\n"
            "#define JNIEXPORT\n#define JNICALL\n#define JNI_FALSE 0\n#define JNI_TRUE 1\n"
            "#define JNI_OK 0\n#define JNI_ERR (-1)\n#define JNI_EDETACHED (-2)\n"
            "#define JNI_VERSION_1_6 0x00010006\n#define JNI_VERSION_1_4 0x00010004\n"
            "typedef struct { const char* name; const char* signature; void* fnPtr; } JNINativeMethod;\n"
            "typedef struct { const char* optionString; void* extraInfo; } JavaVMOption;\n"
            "typedef struct { jint version; jint nOptions; JavaVMOption* options; jboolean ignoreUnrecognized; } JavaVMInitArgs;\n"
            "struct JNINativeInterface_; typedef const struct JNINativeInterface_* JNIEnv;\n"
            "struct JNIInvokeInterface_; typedef const struct JNIInvokeInterface_* JavaVM;\n"
            "struct JNINativeInterface_ {\n"
            "    jclass (*FindClass)(JNIEnv*, const char*);\n"
            "    jmethodID (*GetMethodID)(JNIEnv*, jclass, const char*, const char*);\n"
            "    jmethodID (*GetStaticMethodID)(JNIEnv*, jclass, const char*, const char*);\n"
            "    jobject (*NewObject)(JNIEnv*, jclass, jmethodID, ...);\n"
            "    jstring (*NewStringUTF)(JNIEnv*, const char*);\n"
            "    const char* (*GetStringUTFChars)(JNIEnv*, jstring, jboolean*);\n"
            "    void (*ReleaseStringUTFChars)(JNIEnv*, jstring, const char*);\n"
            "    jsize (*GetStringUTFLength)(JNIEnv*, jstring);\n"
            "    jsize (*GetStringLength)(JNIEnv*, jstring);\n"
            "    void (*GetStringUTFRegion)(JNIEnv*, jstring, jsize, jsize, char*);\n"
            "    void (*DeleteLocalRef)(JNIEnv*, jobject);\n"
            "    jobject (*NewGlobalRef)(JNIEnv*, jobject);\n"
            "    void (*DeleteGlobalRef)(JNIEnv*, jobject);\n"
            "    jint (*Throw)(JNIEnv*, jthrowable);\n"
            "    jint (*ThrowNew)(JNIEnv*, jclass, const char*);\n"
            "    jthrowable (*ExceptionOccurred)(JNIEnv*);\n"
            "    jboolean (*ExceptionCheck)(JNIEnv*);\n"
            "    void (*ExceptionDescribe)(JNIEnv*);\n"
            "    void (*ExceptionClear)(JNIEnv*);\n"
            "    void (*CallStaticVoidMethod)(JNIEnv*, jclass, jmethodID, ...);\n"
            "    void (*CallVoidMethod)(JNIEnv*, jobject, jmethodID, ...);\n"
            "    jint (*GetJavaVM)(JNIEnv*, JavaVM**);\n"
            "    jint (*RegisterNatives)(JNIEnv*, jclass, const JNINativeMethod*, jint);\n"
            "};\n"
            "struct JNIInvokeInterface_ {\n"
            "    jint (*DestroyJavaVM)(JavaVM*);\n"
            "    jint (*AttachCurrentThread)(JavaVM*, void**, void*);\n"
            "    jint (*DetachCurrentThread)(JavaVM*);\n"
            "    jint (*GetEnv)(JavaVM*, void**, jint);\n"
            "};\n",
            encoding="utf-8",
        )
        driver = tmp_path / "driver.c"
        driver.write_text(
            "#define _GNU_SOURCE\n"
            "#include <assert.h>\n#include <dlfcn.h>\n#include <pthread.h>\n"
            "#include <signal.h>\n#include <stdatomic.h>\n#include <stdarg.h>\n"
            "#include <stdbool.h>\n#include <stdio.h>\n#include <string.h>\n"
            "#include <unistd.h>\n#include <jni.h>\n"
            "int android_get_device_api_level(void) { return 35; }\n"
            "bool apiRequiresHints(void) { return true; }\n"
            "typedef struct { int stack_top; void* popstack[512]; } hinter_t;\n"
            "void hinter_process(hinter_t* h, const char* l) { (void)h;(void)l; }\n"
            "void hinter_free(hinter_t* h) { (void)h; }\n"
            "void installLwjglDlopenHook(JNIEnv *e) { (void)e; }\n"
            "void installEMUIIteratorMititgation(JNIEnv *e) { (void)e; }\n"
            "void logger_append_cstr(const char* t) { (void)t; }\n"
            "static int g_throw_stage = -999;\n"
            "void throwException(JNIEnv *env, jint loadStage, jint errorCode, const char* textInfo) {\n"
            "    (void)env;(void)errorCode;(void)textInfo; g_throw_stage = loadStage;\n"
            "}\n"
            "static int g_register_calls = 0;\n"
            "static jclass mock_FindClass(JNIEnv* env, const char* name) {\n"
            "    (void)env;\n"
            "    if (strcmp(name, \"java/lang/ClassLoader$NativeLibrary\") == 0) return NULL;\n"
            "    if (strcmp(name, \"jdk/internal/loader/NativeLibraries\") == 0) return (jclass)0x1234;\n"
            "    return (jclass)0x5678;\n"
            "}\n"
            "static jmethodID mock_GetStaticMethodID(JNIEnv* env, jclass cls, const char* n, const char* s) {\n"
            "    (void)env;(void)cls;(void)s;\n"
            "    return strcmp(n, \"showExitMessage\") == 0 ? (jmethodID)0x9999 : NULL;\n"
            "}\n"
            "static jmethodID mock_GetMethodID(JNIEnv* e, jclass c, const char* n, const char* s) { (void)e;(void)c;(void)n;(void)s; return (jmethodID)1; }\n"
            "static void mock_ExceptionClear(JNIEnv* e) { (void)e; }\n"
            "static jboolean mock_ExceptionCheck(JNIEnv* e) { (void)e; return JNI_FALSE; }\n"
            "static jint mock_RegisterNatives(JNIEnv* e, jclass c, const JNINativeMethod* m, jint n) { (void)e;(void)c;(void)m;(void)n; g_register_calls++; return JNI_OK; }\n"
            "void* dlopen(const char* fn, int fl) { (void)fl; return (fn && strcmp(fn, \"libjava.so\") == 0) ? (void*)0xdead : NULL; }\n"
            "void* dlsym(void* h, const char* s) { return (h == (void*)0xdead && strcmp(s, \"Java_jdk_internal_loader_NativeLibraries_load\") == 0) ? (void*)0xbeef : NULL; }\n"
            "extern bool installClassLoaderHooks(JNIEnv *env, JNIEnv* vm_env);\n"
            "extern void setup_abort_wait(void);\n"
            "extern _Noreturn void abort_call(int code, bool is_signal);\n"
            "extern void Java_net_kdt_pojavlaunch_utils_jre_JavaRunner_nativeSetupExit(JNIEnv *env, jclass clazz, jobject context);\n"
            "static atomic_int g_exit_calls = 0, g_exit_code = -1;\n"
            "static atomic_bool g_exit_is_signal = false;\n"
            "static void mock_CallStaticVoidMethod(JNIEnv* env, jclass cls, jmethodID mid, ...) {\n"
            "    (void)env;(void)cls;(void)mid;\n"
            "    va_list ap; va_start(ap, mid); (void)va_arg(ap, jobject);\n"
            "    g_exit_code = va_arg(ap, int); g_exit_is_signal = (va_arg(ap, int) != 0); va_end(ap);\n"
            "    g_exit_calls++; pthread_exit(NULL);\n"
            "}\n"
            "static jint mock_GetJavaVM(JNIEnv* env, JavaVM** vm);\n"
            "static jobject mock_NewGlobalRef(JNIEnv* env, jobject o) { (void)env; return o; }\n"
            "static void mock_DeleteGlobalRef(JNIEnv* env, jobject o) { (void)env;(void)o; }\n"
            "static struct JNINativeInterface_ g_jni_iface = {\n"
            "    .FindClass = mock_FindClass, .GetMethodID = mock_GetMethodID,\n"
            "    .GetStaticMethodID = mock_GetStaticMethodID, .ExceptionClear = mock_ExceptionClear,\n"
            "    .ExceptionCheck = mock_ExceptionCheck, .RegisterNatives = mock_RegisterNatives,\n"
            "    .CallStaticVoidMethod = mock_CallStaticVoidMethod, .GetJavaVM = mock_GetJavaVM,\n"
            "    .NewGlobalRef = mock_NewGlobalRef, .DeleteGlobalRef = mock_DeleteGlobalRef,\n"
            "};\n"
            "static const struct JNINativeInterface_* g_env = &g_jni_iface;\n"
            "static jint mock_GetEnv(JavaVM* vm, void** penv, jint ver) { (void)vm;(void)ver; *penv = (void*)&g_env; return JNI_OK; }\n"
            "static struct JNIInvokeInterface_ g_vm_iface = { .GetEnv = mock_GetEnv };\n"
            "static const struct JNIInvokeInterface_* g_vm = &g_vm_iface;\n"
            "static jint mock_GetJavaVM(JNIEnv* env, JavaVM** vm) { (void)env; *vm = (JavaVM)&g_vm; return JNI_OK; }\n"
            "static void* call_abort_thread(void* unused) { (void)unused; abort_call(42, true); }\n"
            "static void noop_handler(int s) { (void)s; }\n"
            "int main(void) {\n"
            "    assert(!installClassLoaderHooks((JNIEnv*)&g_env, (JNIEnv*)&g_env));\n"
            "    assert(g_register_calls == 0 && g_throw_stage == 3);\n"
            "    struct sigaction sa = {0}; sa.sa_handler = noop_handler; sigaction(SIGUSR1, &sa, NULL);\n"
            "    Java_net_kdt_pojavlaunch_utils_jre_JavaRunner_nativeSetupExit((JNIEnv*)&g_env, NULL, (jobject)0x1);\n"
            "    setup_abort_wait();\n"
            "    usleep(50000);\n"
            "    for (int i = 0; i < 10; i++) { kill(getpid(), SIGUSR1); usleep(10000); }\n"
            "    assert(g_exit_calls == 0);\n"
            "    pthread_t t; pthread_create(&t, NULL, call_abort_thread, NULL); pthread_detach(t);\n"
            "    for (int i = 0; i < 100 && g_exit_calls == 0; i++) usleep(10000);\n"
            "    assert(g_exit_calls == 1 && g_exit_code == 42 && g_exit_is_signal == true);\n"
            "    return 0;\n"
            "}\n",
            encoding="utf-8",
        )
        bin_path = tmp_path / "jre_crash_smoke"
        subprocess.run(
            [
                gcc, "-std=c11", "-D_GNU_SOURCE", "-pthread",
                f"-I{inc}", f"-I{JNI}", f"-I{JNI / 'jre_launcher'}",
                str(driver),
                str(JNI / "jre_launcher/abort_wait.c"),
                str(JNI / "jre_launcher/native_library_hook.c"),
                "-o", str(bin_path),
            ],
            check=True,
        )
        subprocess.run([str(bin_path)], check=True, timeout=10)

print("jre_launcher_crash_fix_smoke: all checks passed")
