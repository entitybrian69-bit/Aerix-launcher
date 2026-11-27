package aerix.flashback.compat.mixin;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGl3", remap = false)
public class MixinCustomImGuiImplGl3 {

    @Redirect(
        method = "setupRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL32;glPolygonMode(II)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$guardSetupPolygonMode(int face, int mode) {
        try {
            GL32.glPolygonMode(face, mode);
        } catch (Throwable ignored) {}
    }

    @Redirect(
        method = "renderDrawData",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL32;glPolygonMode(II)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$guardRestorePolygonMode(int face, int mode) {
        try {
            GL32.glPolygonMode(face, mode);
        } catch (Throwable ignored) {}
    }

    @Redirect(
        method = "setupRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL33;glBindSampler(II)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$guardSetupBindSampler(int unit, int sampler) {
        try {
            GL33.glBindSampler(unit, sampler);
        } catch (Throwable ignored) {}
    }

    @Redirect(
        method = "renderDrawData",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL33;glBindSampler(II)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$guardRestoreBindSampler(int unit, int sampler) {
        try {
            GL33.glBindSampler(unit, sampler);
        } catch (Throwable ignored) {}
    }

    @Redirect(
        method = "renderDrawData",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL32;glDrawElementsBaseVertex(IIIJI)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$safeDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        if (basevertex == 0) {
            GL11.glDrawElements(mode, count, type, indices);
        } else {
            GL32.glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
        }
    }
}
