#include <assert.h>
#include <stdbool.h>
#include "frame_counter.h"
typedef struct SDL_VideoDevice SDL_VideoDevice;
typedef struct SDL_Window SDL_Window;
static bool result;
bool aerixOriginalSdlSwap(SDL_VideoDevice *device, SDL_Window *window) { (void)device; (void)window; return result; }
bool Android_GLES_SwapWindow(SDL_VideoDevice *device, SDL_Window *window);
int main(void) {
    result = false; assert(!Android_GLES_SwapWindow(0, 0)); assert(aerix_frame_count() == 0);
    result = true; assert(Android_GLES_SwapWindow(0, 0)); assert(aerix_frame_count() == 1);
    return 0;
}
