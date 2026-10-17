package net.aerix.companion.mixin;

import net.aerix.companion.AerixCompanion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Realms owns cached account-bound clients that this version does not rebuild. */
@Mixin(MinecraftClient.class)
public abstract class RealmsGuardMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void aerix$requireRestartForRealms(Screen screen, CallbackInfo ci) {
        if (!AerixCompanion.hasSwitchedSession() || !(screen instanceof RealmsMainScreen)) return;
        MinecraftClient.getInstance().setScreen(new DisconnectedScreen(new TitleScreen(),
            Text.literal("Restart required"), Text.literal("Restart Minecraft before opening Realms after switching accounts.")));
        ci.cancel();
    }
}
