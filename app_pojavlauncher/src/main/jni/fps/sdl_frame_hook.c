#include <stdbool.h>
#include "frame_counter.h"

/* Wrap the Android GLES swap implementation rather than SDL's public dynapi,
 * preserving SDL's own function dispatch and recording successful swaps only. */
typedef struct SDL_VideoDevice SDL_VideoDevice;
typedef struct SDL_Window SDL_Window;
bool aerixOriginalSdlSwap(SDL_VideoDevice *device, SDL_Window *window);
bool Android_GLES_SwapWindow(SDL_VideoDevice *device, SDL_Window *window) {
    bool swapped = aerixOriginalSdlSwap(device, window);
    if (swapped) aerix_record_frame();
    return swapped;
}
