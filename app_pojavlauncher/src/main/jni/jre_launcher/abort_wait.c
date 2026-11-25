//
// Created by maks on 28.09.2025.
//

#include <stdint.h>
#include <unistd.h>
#include <linux/futex.h>
#include <sys/syscall.h>
#include <pthread.h>
#include <stdbool.h>
#include <jni.h>
#include <stdlib.h>

static uint32_t futex_word = 1;
static pthread_mutex_t abort_setup_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool abort_thread_started = false;

struct {
    int code;
    bool is_signal;
} abort_info = {0, false};

struct {
    JavaVM *vm;
    jmethodID exit_method;
    jclass exit_class;
    jobject context;
} vm_exit_data = {NULL, NULL, NULL, NULL};

_Noreturn static void callExit() {
    JavaVM *vm = vm_exit_data.vm;
    if(vm == NULL || vm_exit_data.exit_class == NULL || vm_exit_data.exit_method == NULL) {
        abort();
    }
    JNIEnv *env;
    jint result = (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6);
    if(result == JNI_EDETACHED) {
        result = (*vm)->AttachCurrentThread(vm, &env, NULL);
    }
    if(result != JNI_OK) {
        abort();
    }
    // This will call System.exit()
    (*env)->CallStaticVoidMethod(env, vm_exit_data.exit_class, vm_exit_data.exit_method,
                                 vm_exit_data.context, abort_info.code, abort_info.is_signal);
    while (true) {}
}

static void* abort_wait(void* unused) {
    // FUTEX_WAIT can return early on EINTR (any signal) or spurious wakeups; only exit when
    // abort_call() has explicitly cleared futex_word.
    while (__atomic_load_n(&futex_word, __ATOMIC_ACQUIRE) == 1) {
        syscall(SYS_futex, &futex_word, FUTEX_WAIT, 1, NULL);
    }
    callExit();
    return NULL;
}

void setup_abort_wait() {
    pthread_mutex_lock(&abort_setup_mutex);
    if (!abort_thread_started) {
        __atomic_store_n(&futex_word, 1, __ATOMIC_RELEASE);
        pthread_t abort_thread;
        if (pthread_create(&abort_thread, NULL, abort_wait, NULL) == 0) {
            pthread_detach(abort_thread);
            abort_thread_started = true;
        }
    }
    pthread_mutex_unlock(&abort_setup_mutex);
}

_Noreturn void abort_call(int code, bool is_signal) {
    abort_info.code = code;
    abort_info.is_signal = is_signal;
    __atomic_store_n(&futex_word, 0, __ATOMIC_RELEASE);
    syscall(SYS_futex, &futex_word, FUTEX_WAKE, 1);
    while(true) {}
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_jre_JavaRunner_nativeSetupExit(JNIEnv *env, jclass clazz,
                                                              jobject context) {
    (*env)->GetJavaVM(env, &vm_exit_data.vm);
    jclass class = (*env)->FindClass(env, "net/kdt/pojavlaunch/ExitActivity");
    vm_exit_data.exit_class  = (*env)->NewGlobalRef(env, class);
    vm_exit_data.exit_method = (*env)->GetStaticMethodID(env, class, "showExitMessage", "(Landroid/content/Context;IZ)V");
    if(vm_exit_data.context != NULL) {
        jobject oldRef = vm_exit_data.context;
        vm_exit_data.context = (*env)->NewGlobalRef(env, context);
        (*env)->DeleteGlobalRef(env, oldRef);
    }else {
        vm_exit_data.context = (*env)->NewGlobalRef(env, context);
    }
}