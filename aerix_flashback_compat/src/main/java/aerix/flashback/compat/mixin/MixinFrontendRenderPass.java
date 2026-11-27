package aerix.flashback.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.renderpearl.frontend.FrontendRenderPass", remap = false)
public class MixinFrontendRenderPass {

    @Inject(method = "enableScissor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aerix$preventInvalidScissorCrash(int x, int y, int width, int height, CallbackInfo ci) {
        if (width <= 0 || height <= 0) {
            ci.cancel();
        }
    }
}
