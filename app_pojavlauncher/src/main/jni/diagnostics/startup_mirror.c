#define _GNU_SOURCE
#include "startup_mirror.h"
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>
#include <limits.h>

#define LIMIT (64 * 1024)
#define LINE 4096
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static char target[PATH_MAX], pending[LIMIT], line[LINE];
static size_t used, line_size, written;
static int output = -1;
static int enabled, ready, dropping;

static int write_all(int fd, const char *data, size_t size) {
    while (size) {
        ssize_t n = write(fd, data, size);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return 0;
        data += n; size -= (size_t)n;
    }
    return 1;
}
static void close_output(void) {
    if (output >= 0) close(output);
    output = -1;
}
void aerix_startup_mirror_configure(const char *path, const char *identity) {
    pthread_mutex_lock(&lock);
    close_output(); used = line_size = written = 0; ready = dropping = enabled = 0;
    if (path && identity && path[0] == '/' && strnlen(path, sizeof(target)) < sizeof(target)) {
        strcpy(target, path);
        int n = snprintf(pending, sizeof(pending), "\n[Aerix renderer diagnostics] %.480s\n", identity);
        used = (size_t)n;
        enabled = 1;
    }
    pthread_mutex_unlock(&lock);
}
static void emit(const char *data, size_t size) {
    if (size > LIMIT - written) { enabled = 0; close_output(); return; }
    if (output >= 0) {
        // A partial write followed by ENOSPC must not permit repeated attempts past the budget.
        if (!write_all(output, data, size)) { enabled = 0; close_output(); return; }
        written += size;
        fdatasync(output); /* Startup events only. Pipe capture remains best-effort on abrupt process death. */
    }
}
static void process_line(void) {
    line[line_size] = 0;
    /* Log4j has already opened this run's latest.log when its backend banner is printed.
       Never append to yesterday's file merely because it exists before the JVM starts. */
    if (!ready && strstr(line, "Backend library: LWJGL version")) {
        int fd = open(target, O_WRONLY | O_APPEND | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
        struct stat status;
        if (fd >= 0 && fstat(fd, &status) == 0 && S_ISREG(status.st_mode)) {
            output = fd; ready = 1;
            emit(pending, used);
        } else if (fd >= 0) close(fd);
    }
    /* Never mirror auth arguments, environment dumps, accounts or arbitrary mod stdout. */
    int selected = (strncmp(line, "[RustRenderer] ", 15) == 0 ||
                    strncmp(line, "Renderer native load:", 21) == 0 ||
                    strncmp(line, "Renderer native provider loaded=", 32) == 0);
    if (!selected || strstr(line, "Session ID is")) return;
    if (ready) {
        emit("[Aerix native] ", 15);
        emit(line, line_size);
        emit("\n", 1);
    } else if (line_size + 1 <= sizeof(pending) - used) {
        memcpy(pending + used, line, line_size); used += line_size;
        pending[used++] = '\n';
    }
    if (strstr(line, "[Aerix startup] first swap returned=1")) {
        enabled = 0; close_output();
    }
}
void aerix_startup_mirror_feed(const char *bytes, size_t size) {
    pthread_mutex_lock(&lock);
    if (enabled) for (size_t i = 0; i < size && enabled; ++i) {
        if (bytes[i] == '\n') {
            if (!dropping) process_line();
            line_size = 0; dropping = 0;
        } else if (line_size < sizeof(line) - 1) line[line_size++] = bytes[i];
        else dropping = 1; /* Drop oversized lines, do not accidentally treat fragments as new events. */
    }
    pthread_mutex_unlock(&lock);
}
