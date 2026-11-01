#include "frame_counter.h"
#include <stdatomic.h>
#include <time.h>

// 32-bit atomics remain lock-free on ARMv7. Readers handle unsigned wraparound.
static _Atomic uint32_t frames;
static _Atomic int observed;
static _Atomic int timing_enabled;
static _Atomic uint32_t timing_epoch;
static _Atomic uint32_t frame_time_buckets[AERIX_FRAME_TIME_BUCKETS];

void aerix_record_frame(void) {
    if (atomic_fetch_add_explicit(&frames, 1, memory_order_relaxed) == 0)
        atomic_store_explicit(&observed, 1, memory_order_release);
    if (!atomic_load_explicit(&timing_enabled, memory_order_acquire)) return;
    // GLFW renders from one thread; keep the previous timestamp on that thread only.
    static _Thread_local uint64_t previous_ns;
    static _Thread_local uint32_t previous_epoch;
    uint32_t epoch = atomic_load_explicit(&timing_epoch, memory_order_relaxed);
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) return;
    uint64_t now = (uint64_t) ts.tv_sec * 1000000000ull + (uint64_t) ts.tv_nsec;
    if (epoch == previous_epoch && previous_ns && now >= previous_ns) {
        uint64_t ms = (now - previous_ns) / 1000000ull;
        unsigned bucket = ms >= AERIX_FRAME_TIME_BUCKETS - 1 ? AERIX_FRAME_TIME_BUCKETS - 1 : (unsigned) ms;
        atomic_fetch_add_explicit(&frame_time_buckets[bucket], 1, memory_order_relaxed);
    }
    previous_epoch = epoch;
    previous_ns = now;
}
int aerix_frames_available(void) { return atomic_load_explicit(&observed, memory_order_acquire); }
uint32_t aerix_frame_count(void) { return atomic_load_explicit(&frames, memory_order_relaxed); }
void aerix_set_frame_timing(int enabled) {
    if (enabled) atomic_fetch_add_explicit(&timing_epoch, 1, memory_order_relaxed);
    atomic_store_explicit(&timing_enabled, enabled != 0, memory_order_release);
}
uint32_t aerix_frame_time_bucket(unsigned bucket) {
    return bucket < AERIX_FRAME_TIME_BUCKETS ? atomic_load_explicit(&frame_time_buckets[bucket], memory_order_relaxed) : 0;
}
