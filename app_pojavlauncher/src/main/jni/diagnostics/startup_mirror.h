#ifndef AERIX_STARTUP_MIRROR_H
#define AERIX_STARTUP_MIRROR_H
#include <stddef.h>
/* Opt-in for bundled RustGL only. No file creation, truncation, signal handler or JNI per frame. */
void aerix_startup_mirror_configure(const char *path, const char *identity);
void aerix_startup_mirror_feed(const char *bytes, size_t size);
#endif
