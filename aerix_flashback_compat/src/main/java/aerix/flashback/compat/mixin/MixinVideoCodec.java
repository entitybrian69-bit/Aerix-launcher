package aerix.flashback.compat.mixin;

import aerix.flashback.compat.AerixFFmpegProbe;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.moulberry.flashback.combo_options.VideoCodec", remap = false)
public class MixinVideoCodec {

    @Inject(method = "doesEncoderWork", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aerix$rejectUnsupportedHardwareEncoders(AVCodec codec, CallbackInfoReturnable<Boolean> cir) {
        if (codec != null) {
            try {
                String name = codec.name().getString();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.contains("mediacodec") || lower.contains("v4l2m2m")) {
                        cir.setReturnValue(false);
                    }
                }
            } catch (Throwable ignored) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "getEncoders", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aerix$guardEncodersHead(CallbackInfoReturnable<String[]> cir) {
        if (!AerixFFmpegProbe.isAvailable()) {
            cir.setReturnValue(new String[0]);
        }
    }

    @Inject(method = "getEncoders", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void aerix$filterEncodersList(CallbackInfoReturnable<String[]> cir) {
        String[] encoders = cir.getReturnValue();
        if (encoders != null && encoders.length > 0) {
            List<String> valid = new ArrayList<>();
            for (String enc : encoders) {
                if (enc == null) continue;
                String lower = enc.toLowerCase();
                if (lower.contains("mediacodec") || lower.contains("v4l2m2m")) {
                    continue;
                }
                valid.add(enc);
            }
            cir.setReturnValue(valid.toArray(new String[0]));
        }
    }

    @Inject(method = "supportsTransparency", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aerix$guardSupportsTransparency(CallbackInfoReturnable<Boolean> cir) {
        if (!AerixFFmpegProbe.isAvailable()) {
            cir.setReturnValue(false);
        }
    }
}
