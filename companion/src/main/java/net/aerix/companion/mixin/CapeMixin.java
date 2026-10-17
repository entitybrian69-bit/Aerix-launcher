package net.aerix.companion.mixin;

import net.aerix.companion.AerixCompanion;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class CapeMixin {
    @Inject(method = "getCapeTexture", at = @At("HEAD"), cancellable = true)
    private void aerix$cape(CallbackInfoReturnable<Identifier> callback) {
        Identifier cape = AerixCompanion.cape(((AbstractClientPlayerEntity) (Object) this).getUuid());
        if (cape != null) callback.setReturnValue(cape);
    }

    @Inject(method = "canRenderCapeTexture", at = @At("HEAD"), cancellable = true)
    private void aerix$hasCape(CallbackInfoReturnable<Boolean> callback) {
        if (AerixCompanion.cape(((AbstractClientPlayerEntity) (Object) this).getUuid()) != null) callback.setReturnValue(true);
    }
}
