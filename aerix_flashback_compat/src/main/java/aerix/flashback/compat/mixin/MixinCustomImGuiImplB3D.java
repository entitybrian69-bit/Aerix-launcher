package aerix.flashback.compat.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplB3D", remap = false)
public class MixinCustomImGuiImplB3D {

    @Unique
    private boolean aerix$skipCurrentDraw = false;

    @Redirect(
        method = "renderDrawData",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;enableScissor(IIII)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$guardEnableScissor(RenderPass pass, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            this.aerix$skipCurrentDraw = true;
            return;
        }
        this.aerix$skipCurrentDraw = false;
        pass.enableScissor(x, y, width, height);
    }

    @Redirect(
        method = "renderDrawData",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;disableScissor()V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$guardDisableScissor(RenderPass pass) {
        this.aerix$skipCurrentDraw = false;
        pass.disableScissor();
    }

    @Redirect(
        method = "renderDrawData",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void aerix$guardDrawIndexed(RenderPass pass, int indexCount, int instanceCount, int firstIndex, int firstVertex, int firstInstance) {
        if (this.aerix$skipCurrentDraw) {
            this.aerix$skipCurrentDraw = false;
            return;
        }
        pass.drawIndexed(indexCount, instanceCount, firstIndex, firstVertex, firstInstance);
    }
}
