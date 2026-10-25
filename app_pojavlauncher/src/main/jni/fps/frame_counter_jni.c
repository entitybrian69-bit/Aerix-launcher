#include <jni.h>
#include "frame_counter.h"
JNIEXPORT jlong JNICALL
Java_net_kdt_pojavlaunch_game_NativeFrameCounter_readFrames(JNIEnv *env, jclass clazz) {
    return aerix_frames_available() ? (jlong) aerix_frame_count() : -1;
}
