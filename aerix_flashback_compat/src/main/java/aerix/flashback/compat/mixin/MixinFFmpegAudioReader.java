package aerix.flashback.compat.mixin;

import aerix.flashback.compat.AerixFFmpegProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

@Mixin(targets = "com.moulberry.flashback.sound.FFmpegAudioReader", remap = false)
public class MixinFFmpegAudioReader {

    @Inject(method = "read", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aerix$guardAudioRead(InputStream inputStream, CallbackInfoReturnable<Object> cir) {
        if (!AerixFFmpegProbe.isAvailable()) {
            cir.setReturnValue(null);
        }
    }
}
