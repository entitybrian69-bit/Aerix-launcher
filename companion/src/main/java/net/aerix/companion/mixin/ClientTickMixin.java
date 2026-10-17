package net.aerix.companion.mixin;

import net.aerix.companion.AerixCompanion;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ClientTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void aerix$tick(CallbackInfo callback) { AerixCompanion.tick((MinecraftClient) (Object) this); }
}
