package aerix.flashback.compat.mixin;

import aerix.flashback.compat.AerixFFmpegProbe;
import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.VideoCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.moulberry.flashback.combo_options.VideoContainer", remap = false)
public class MixinVideoContainer {

    @Inject(method = "getSupportedVideoCodecs", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aerix$guardSupportedVideoCodecs(boolean transparency, CallbackInfoReturnable<VideoCodec[]> cir) {
        if (!AerixFFmpegProbe.isAvailable()) {
            cir.setReturnValue(new VideoCodec[0]);
        }
    }

    @Inject(method = "getSupportedAudioCodecs", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aerix$guardSupportedAudioCodecs(CallbackInfoReturnable<AudioCodec[]> cir) {
        if (!AerixFFmpegProbe.isAvailable()) {
            cir.setReturnValue(new AudioCodec[0]);
        }
    }
}
