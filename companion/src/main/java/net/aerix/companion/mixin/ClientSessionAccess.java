package net.aerix.companion.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.report.AbuseReportContext;
import net.minecraft.client.util.ProfileKeys;
import net.minecraft.client.util.Session;
import net.minecraft.client.util.telemetry.TelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface ClientSessionAccess {
    @Mutable @Accessor("session") void aerix$session(Session value);
    @Mutable @Accessor("userApiService") void aerix$api(UserApiService value);
    @Mutable @Accessor("socialInteractionsManager") void aerix$social(SocialInteractionsManager value);
    @Mutable @Accessor("profileKeys") void aerix$keys(ProfileKeys value);
    @Mutable @Accessor("telemetryManager") void aerix$telemetry(TelemetryManager value);
    @Accessor("abuseReportContext") void aerix$reports(AbuseReportContext value);
    @Accessor("authenticationService") YggdrasilAuthenticationService aerix$authentication();
    @Accessor("sessionPropertyMap") PropertyMap aerix$properties();
}
