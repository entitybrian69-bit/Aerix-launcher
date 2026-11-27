package aerix.flashback.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.FloatBuffer;

@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob", remap = false)
public class MixinExportJob {

    @Redirect(
        method = "doExport",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/openal/SOFTLoopback;alcRenderSamplesSOFT(JLjava/nio/FloatBuffer;I)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$guardAlcRenderSamplesSOFT(long device, FloatBuffer buffer, int samples) {
        try {
            org.lwjgl.openal.SOFTLoopback.alcRenderSamplesSOFT(device, buffer, samples);
        } catch (Throwable t) {
            if (buffer != null) {
                buffer.clear();
                while (buffer.hasRemaining()) {
                    buffer.put(0.0f);
                }
                buffer.flip();
            }
        }
    }
}
