//
// Created by maks on 20.09.2025.
//

#include <jni.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <jvm_hooks/jvm_hooks.h>
#include "elf_hinter.h"
#include "load_stages.h"
#include "utils.h"

#define TAG __FILE_NAME__
#include <log.h>

extern bool apiRequiresHints();

// Java 21 style hook
typedef jboolean (*NativeLibraries_load)(JNIEnv *env, jclass cls, jobject lib, jstring name, jboolean isBuiltin, jboolean throwExceptionIfFail);

// Java 17 style hook
typedef jboolean (*j17_NativeLibraries_load)(JNIEnv *env, jclass cls, jobject lib, jstring name, jboolean isBuiltin, jboolean isJNI, jboolean throwExceptionIfFail);

// Java 8 style hook
typedef void(*ClassLoader_00024NativeLibrary_load)(JNIEnv *env, jobject this, jstring name, jboolean isBuiltin);

union {
    NativeLibraries_load j21;
    j17_NativeLibraries_load j17;
    ClassLoader_00024NativeLibrary_load j8;
    void* generic;
} original_func;

static void library_preload_hook(JNIEnv *env, const char* name) {
    const char* lib_filename = strrchr(name, '/');
    if(lib_filename == NULL) lib_filename = name;
    else lib_filename++;
    if(strcmp(lib_filename, "liblwjgl.so") == 0) {
        LOGI("Running LWJGL preload hooks...");
        installLwjglDlopenHook(env);
        installEMUIIteratorMititgation(env);
    }
}

#define NATIVES_HOOK(CALLTHROUGH, RESULT, RESULT_RETURN) \
do { \
    const char *name_n = name != NULL ? (*env)->GetStringUTFChars(env, name, NULL) : NULL; \
    const bool hintsRequired = apiRequiresHints() && name_n != NULL; \
    hinter_t hinter; \
    if (name_n != NULL) { \
        library_preload_hook(env, name_n); \
        if (hintsRequired) hinter_process(&hinter, name_n); \
        (*env)->ReleaseStringUTFChars(env, name, name_n); \
    } \
    RESULT CALLTHROUGH; \
    if (hintsRequired) hinter_free(&hinter); \
    RESULT_RETURN \
} while(0);


static jboolean hook_NativeLibraries_load(JNIEnv *env, jclass cls, jobject lib, jstring name, jboolean isBuiltin, jboolean throwExceptionIfFail) {
    NATIVES_HOOK(original_func.j21(env, cls, lib, name, isBuiltin, throwExceptionIfFail), jboolean result =, return result;)
}

static jboolean hook_j17_NativeLibraries_load(JNIEnv *env, jclass cls, jobject lib, jstring name, jboolean isBuiltin, jboolean isJNI, jboolean throwExceptionIfFail) {
    NATIVES_HOOK(original_func.j17(env, cls, lib, name, isBuiltin, isJNI, throwExceptionIfFail), jboolean result =, return result;)
}

static void hook_ClassLoader_00024NativeLibrary_load(JNIEnv *env, jobject this, jstring name, jboolean isBuiltin) {
    NATIVES_HOOK(original_func.j8(env, this, name, isBuiltin),,)
}

static bool testMethod(JNIEnv *vm_env, jclass hookClass, const char* sign, JNINativeMethod* hookMethod) {
    jmethodID hookMethod_j17 = (*vm_env)->GetStaticMethodID(vm_env, hookClass, hookMethod->name, sign);
    if(hookMethod_j17 != NULL) {
        hookMethod->signature = sign;
        return true;
    }else {
        (*vm_env)->ExceptionClear(vm_env);
        return false;
    }
}

bool installClassLoaderHooks(JNIEnv *env, JNIEnv* vm_env) {
    void* libjava = dlopen("libjava.so", RTLD_NOLOAD);
    if(libjava == NULL) {
        logger_append_cstr("jre_launcher: libjava.so not loaded after VM startup");
        throwException(env, STAGE_FIND_HOOKS_NATIVE, JNI_ERR, "Failed to find libjava.so after VM startup");
        return false;
    }
    jclass hookClass;
    JNINativeMethod hookMethod;
    memset(&hookMethod, 0, sizeof(hookMethod));
    const char* hookKind = "unknown";
    hookClass = (*vm_env)->FindClass(vm_env,"java/lang/ClassLoader$NativeLibrary");
    if(hookClass != NULL) {
        hookMethod.name = "load";
        hookMethod.signature = "(Ljava/lang/String;Z)V";
        hookMethod.fnPtr = hook_ClassLoader_00024NativeLibrary_load;
        original_func.j8 = dlsym(libjava, "Java_java_lang_ClassLoader_00024NativeLibrary_load");
        if(original_func.j8 == NULL) {
            logger_append_cstr("jre_launcher: missing Java_java_lang_ClassLoader_00024NativeLibrary_load");
            throwException(env, STAGE_FIND_HOOKS_NATIVE, JNI_ERR, "Failed to find Java 8 native load symbol");
            return false;
        }
        hookKind = "java8";
    }else {
        (*vm_env)->ExceptionClear(vm_env);
        hookClass = (*vm_env)->FindClass(vm_env, "jdk/internal/loader/NativeLibraries");
        if(hookClass == NULL) goto err;
        hookMethod.name = "load";
        original_func.generic = dlsym(libjava, "Java_jdk_internal_loader_NativeLibraries_load");
        if(original_func.generic == NULL) {
            logger_append_cstr("jre_launcher: missing Java_jdk_internal_loader_NativeLibraries_load");
            throwException(env, STAGE_FIND_HOOKS_NATIVE, JNI_ERR, "Failed to find NativeLibraries.load symbol");
            return false;
        }
        if(testMethod(vm_env, hookClass, "(Ljdk/internal/loader/NativeLibraries$NativeLibraryImpl;Ljava/lang/String;ZZZ)Z", &hookMethod)) {
            hookMethod.fnPtr = hook_j17_NativeLibraries_load;
            hookKind = "java17";
        }else if(testMethod(vm_env, hookClass, "(Ljdk/internal/loader/NativeLibraries$NativeLibraryImpl;Ljava/lang/String;ZZ)Z", &hookMethod)) {
            hookMethod.fnPtr = hook_NativeLibraries_load;
            hookKind = "java21+";
        }else {
            logger_append_cstr("jre_launcher: unsupported NativeLibraries.load signature");
            goto err;
        }
    }
    jint result = (*vm_env)->RegisterNatives(vm_env, hookClass, &hookMethod, 1);
    if(result != JNI_OK) {
        if((*vm_env)->ExceptionCheck(vm_env)) (*vm_env)->ExceptionDescribe(vm_env);
        logger_append_cstr("jre_launcher: RegisterNatives failed for classloader hook");
        throwException(env, STAGE_INSERT_HOOKS, result, "Cannot register hooks");
        return false;
    }
    if(strcmp(hookKind, "java8") == 0) logger_append_cstr("jre_launcher: registered java8 classloader hook");
    else if(strcmp(hookKind, "java17") == 0) logger_append_cstr("jre_launcher: registered java17 classloader hook");
    else logger_append_cstr("jre_launcher: registered java21+ classloader hook");
    return true;
    err:
    if((*vm_env)->ExceptionCheck(vm_env)) (*vm_env)->ExceptionClear(vm_env);
    throwException(env, STAGE_FIND_HOOKS, JNI_ERR, "Cannot find hook target.");
    return false;
}