#include "frame_counter.h"
#include <stdatomic.h>
// 32-bit atomics remain lock-free on ARMv7. Readers handle unsigned wraparound.
static _Atomic uint32_t frames;
static _Atomic int observed;
void aerix_record_frame(void) {
    if (atomic_fetch_add_explicit(&frames, 1, memory_order_relaxed) == 0)
        atomic_store_explicit(&observed, 1, memory_order_release);
}
int aerix_frames_available(void) { return atomic_load_explicit(&observed, memory_order_acquire); }
uint32_t aerix_frame_count(void) { return atomic_load_explicit(&frames, memory_order_relaxed); }
