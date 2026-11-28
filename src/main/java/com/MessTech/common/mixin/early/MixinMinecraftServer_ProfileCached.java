package com.MessTech.common.mixin.early;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.MessTech.common.auth.MTSessionService;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

/**
 * Swaps the {@code MinecraftSessionService} vanilla creates in {@code MinecraftServer}'s constructor for the caching
 * {@link MTSessionService} (the ProfileCached port); {@code NetHandlerLoginServer} validates online-mode logins
 * through that service, so nothing else needs to be touched.
 * <p>
 * The constructor body is {@code this.field_147143_S = this.field_152364_T.createMinecraftSessionService();} (the auth
 * service field is the private final {@code field_152364_T}; see the bytecode at {@code MinecraftServer#<init>}), and
 * the redirect hands the very same auth service instance to the subclass - no proxy/environment is re-read, so any
 * {@code -Dminecraft.api.*} overrides the live service was built from are kept.
 * <p>
 * Timing: the dedicated server constructs {@code MinecraftServer} <em>before</em> FML loads the mods, so this has to
 * be an early mixin (the config is not read yet and {@link MTSessionService} therefore reads it lazily at the first
 * login). It is registered in the early config's {@code "server"} list - the client integrated server keeps vanilla's
 * service, per the ported mod being a server-only mod.
 * <p>
 * {@code remap = false} because the redirected call belongs to authlib, which is not covered by the MC mappings.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer_ProfileCached {

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/yggdrasil/YggdrasilAuthenticationService;createMinecraftSessionService()Lcom/mojang/authlib/minecraft/MinecraftSessionService;"),
        remap = false)
    private MinecraftSessionService messtech$profileCachedSessionService(
        YggdrasilAuthenticationService authenticationService) {
        return new MTSessionService(authenticationService);
    }
}
