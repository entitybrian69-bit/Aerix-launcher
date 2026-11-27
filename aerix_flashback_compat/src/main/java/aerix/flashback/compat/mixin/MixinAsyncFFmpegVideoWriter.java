package aerix.flashback.compat.mixin;

import com.moulberry.flashback.exporting.FlashbackFFmpegFrameRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.moulberry.flashback.exporting.AsyncFFmpegVideoWriter", remap = false)
public class MixinAsyncFFmpegVideoWriter {

    @Redirect(
        method = "tryStart",
        at = @At(
            value = "INVOKE",
            target = "Lcom/moulberry/flashback/exporting/FlashbackFFmpegFrameRecorder;setVideoCodecName(Ljava/lang/String;)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$sanitizeEncoderName(FlashbackFFmpegFrameRecorder recorder, String encoderName) {
        if (encoderName != null) {
            String lower = encoderName.toLowerCase();
            if (lower.contains("mediacodec") || lower.contains("v4l2m2m")) {
                if (lower.contains("av1")) {
                    encoderName = "libsvtav1";
                } else if (lower.contains("vp9")) {
                    encoderName = "libvpx-vp9";
                } else {
                    encoderName = "libopenh264";
                }
            }
        }
        recorder.setVideoCodecName(encoderName);
    }
}
