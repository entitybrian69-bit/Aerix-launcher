#define _GNU_SOURCE
#include "startup_mirror.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/resource.h>
#include <signal.h>
static void feed(const char *s) { aerix_startup_mirror_feed(s, strlen(s)); }
int main(void) {
    char directory[] = "/tmp/aerix-mirror-XXXXXX";
    assert(mkdtemp(directory));
    char path[256], other[256];
    snprintf(path, sizeof(path), "%s/latest.log", directory);
    snprintf(other, sizeof(other), "%s/other.log", directory);
    FILE *file = fopen(path, "w"); assert(file); fputs("previous log\n", file); fclose(file);
    aerix_startup_mirror_configure(path, "bundle=TEST");
    feed("[RustRenderer] [EGL] before JVM\naccount secret\n");
    file = fopen(path, "r"); char data[100000] = {0}; fread(data, 1, sizeof(data)-1, file); fclose(file);
    assert(strcmp(data, "previous log\n") == 0);
    file = fopen(path, "w"); fputs("current Minecraft log\n", file); fclose(file);
    feed("[main/INFO]: Backend library: LW"); feed("JGL version 3.2.3\n");
    feed("[RustRenderer] [Aerix startup] eglInitialize enter\n");
    feed("[RustRenderer] Session ID is SECRET\n");
    feed("[RustRenderer] [Aerix startup] first swap returned=1\n");
    feed("[RustRenderer] MUST_NOT_APPEAR\n");
    file = fopen(path, "r"); memset(data, 0, sizeof(data)); fread(data, 1, sizeof(data)-1, file); fclose(file);
    assert(strstr(data, "current Minecraft log\n")); assert(strstr(data, "bundle=TEST"));
    assert(strstr(data, "before JVM")); assert(strstr(data, "eglInitialize enter"));
    assert(!strstr(data, "secret")); assert(!strstr(data, "SECRET")); assert(!strstr(data, "MUST_NOT_APPEAR"));
    /* Never create a missing Minecraft log or follow a symlink. */
    unlink(path); aerix_startup_mirror_configure(path, "missing");
    feed("Backend library: LWJGL version x\n"); assert(access(path, F_OK) != 0);
    file = fopen(other, "w"); fputs("untouched", file); fclose(file);
    assert(symlink(other, path) == 0); aerix_startup_mirror_configure(path, "symlink");
    feed("Backend library: LWJGL version x\n[RustRenderer] NOT_WRITTEN\n");
    file = fopen(other, "r"); memset(data, 0, sizeof(data)); fread(data, 1, sizeof(data)-1, file); fclose(file);
    assert(strcmp(data, "untouched") == 0);
    unlink(path);
    file = fopen(path, "w"); assert(file); fputs("bounded\n", file); fclose(file);
    aerix_startup_mirror_configure(path, "bounded");
    feed("Backend library: LWJGL version x\n");
    char huge[10000]; memset(huge, 'x', sizeof(huge));
    memcpy(huge, "[RustRenderer] ", 15);
    aerix_startup_mirror_feed(huge, sizeof(huge)); feed("\n");
    for (int i = 0; i < 10000; ++i) feed("[RustRenderer] [EGL] bounded event\n");
    file = fopen(path, "r"); assert(file); fseek(file, 0, SEEK_END);
    assert(ftell(file) <= 65536 + (long)strlen("bounded\n")); fclose(file);
    aerix_startup_mirror_configure(NULL, NULL);
    /* Force a partial regular-file write, then EFBIG, then restore available capacity.
       The mirror must stay disabled instead of resuming writes with a stale byte count. */
    file = fopen(path, "w"); assert(file); fclose(file);
    struct rlimit previous; assert(getrlimit(RLIMIT_FSIZE, &previous) == 0);
    struct rlimit limited = previous; limited.rlim_cur = 120;
    void (*old_handler)(int) = signal(SIGXFSZ, SIG_IGN);
    assert(setrlimit(RLIMIT_FSIZE, &limited) == 0);
    aerix_startup_mirror_configure(path, "partial failure");
    feed("Backend library: LWJGL version x\n");
    for (int i = 0; i < 20; ++i) feed("[RustRenderer] [EGL] simulated file-size failure\n");
    assert(setrlimit(RLIMIT_FSIZE, &previous) == 0);
    signal(SIGXFSZ, old_handler);
    file = fopen(path, "r"); assert(file); fseek(file, 0, SEEK_END); long failed_size = ftell(file); fclose(file);
    assert(failed_size == 120);
    feed("[RustRenderer] must remain disabled after capacity returns\n");
    file = fopen(path, "r"); assert(file); fseek(file, 0, SEEK_END); assert(ftell(file) == failed_size); fclose(file);
    aerix_startup_mirror_configure(NULL, NULL);
    unlink(path); unlink(other); rmdir(directory);
    puts("PASS: startup-only mirror, split lines, stale log preservation, redaction, missing file and symlink rejection");
}
