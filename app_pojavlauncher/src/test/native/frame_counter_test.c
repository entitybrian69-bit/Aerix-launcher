#include "frame_counter.h"
#include <assert.h>
#include <pthread.h>
#include <stdio.h>
static void* render(void* ignored) {
    for (int i = 0; i < 100000; i++) aerix_record_frame();
    return NULL;
}
int main(void) {
    assert(!aerix_frames_available());
    assert(aerix_frame_count() == 0);
    pthread_t a, b;
    assert(pthread_create(&a, NULL, render, NULL) == 0);
    assert(pthread_create(&b, NULL, render, NULL) == 0);
    pthread_join(a, NULL); pthread_join(b, NULL);
    assert(aerix_frames_available());
    assert(aerix_frame_count() == 200000);
    puts("PASS: native render-frame counter (200000 concurrent increments)");
}
