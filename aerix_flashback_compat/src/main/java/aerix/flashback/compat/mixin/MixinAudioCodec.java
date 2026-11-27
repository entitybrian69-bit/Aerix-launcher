package aerix.flashback.compat.mixin;

import aerix.flashback.compat.AerixFFmpegProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.moulberry.flashback.combo_options.AudioCodec", remap = false)
public class MixinAudioCodec {

    @Inject(method = "getEncoders", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aerix$guardAudioEncoders(CallbackInfoReturnable<String[]> cir) {
        if (!AerixFFmpegProbe.isAvailable()) {
            cir.setReturnValue(new String[0]);
        }
    }
}
