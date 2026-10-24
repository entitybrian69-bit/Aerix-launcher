#ifndef AERIX_FRAME_COUNTER_H
#define AERIX_FRAME_COUNTER_H
#include <stdint.h>
__attribute__((visibility("default"))) void aerix_record_frame(void);
__attribute__((visibility("default"))) uint32_t aerix_frame_count(void);
__attribute__((visibility("default"))) int aerix_frames_available(void);
#endif
